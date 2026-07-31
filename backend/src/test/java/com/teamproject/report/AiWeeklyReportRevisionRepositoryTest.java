package com.teamproject.report;

import com.teamproject.report.domain.AiWeeklyReportRevision;
import com.teamproject.report.domain.AiWeeklyReportRevisionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AiWeeklyReportRevisionRepositoryTest {

    @Autowired
    private AiWeeklyReportRevisionRepository repository;

    @Test
    @DisplayName("V34 migration으로 엔티티를 저장하고 재조회 시 원문 JSON이 동일하다")
    void savesAndRetrievesExactJsonContent() {
        String snapshotJson = "{\"schemaVersion\":\"ai-weekly-report-snapshot.v1\",\"testKey\":\"snapshotValue\"}";
        String analysisJson = "{\"schemaVersion\":\"ai-weekly-report-analysis.v1\",\"testKey\":\"analysisValue\"}";

        AiWeeklyReportRevision revision = new AiWeeklyReportRevision(
                7L,
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 27),
                "KO",
                1,
                "FINALIZED",
                "OPENAI",
                "FINGERPRINT_123",
                snapshotJson,
                analysisJson,
                "v7-2-prompt-001",
                "gpt-4o",
                100,
                200,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        AiWeeklyReportRevision saved = repository.save(revision);
        assertThat(saved.getId()).isNotNull();

        Optional<AiWeeklyReportRevision> found = repository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getSnapshotJson()).isEqualTo(snapshotJson);
        assertThat(found.get().getAnalysisJson()).isEqualTo(analysisJson);
        assertThat(found.get().getSourceFingerprint()).isEqualTo("FINGERPRINT_123");
    }

    @Test
    @DisplayName("동일 그룹·기간·언어에서 revision 번호가 정상적으로 증가한다")
    void revisionIncrementsSequentially() {
        LocalDate from = LocalDate.of(2026, 7, 20);
        LocalDate toExclusive = LocalDate.of(2026, 7, 27);

        assertThat(repository.findMaxRevision(7L, from, toExclusive, "KO")).isEmpty();

        AiWeeklyReportRevision r1 = new AiWeeklyReportRevision(
                7L, from, toExclusive, "KO", 1, "FINALIZED", "OPENAI", "FP1", "{}", "{}", "v7-2", "gpt-4o", 10, 20, LocalDateTime.now(), LocalDateTime.now()
        );
        repository.save(r1);

        assertThat(repository.findMaxRevision(7L, from, toExclusive, "KO")).contains(1);

        AiWeeklyReportRevision r2 = new AiWeeklyReportRevision(
                7L, from, toExclusive, "KO", 2, "FINALIZED", "SERVER_FALLBACK", "FP2", "{}", "{}", "v7-2", null, null, null, LocalDateTime.now(), LocalDateTime.now()
        );
        repository.save(r2);

        assertThat(repository.findMaxRevision(7L, from, toExclusive, "KO")).contains(2);

        Optional<AiWeeklyReportRevision> top = repository.findTopByGroupIdAndPeriodFromAndPeriodToExclusiveAndLanguageOrderByRevisionDesc(7L, from, toExclusive, "KO");
        assertThat(top).isPresent();
        assertThat(top.get().getRevision()).isEqualTo(2);
    }

    @Test
    @DisplayName("동일 sourceFingerprint로 이전 저장건을 검색할 수 있다")
    void findBySourceFingerprint() {
        LocalDate from = LocalDate.of(2026, 7, 20);
        LocalDate toExclusive = LocalDate.of(2026, 7, 27);
        String fingerprint = "UNIQUE_FP_999";

        AiWeeklyReportRevision r = new AiWeeklyReportRevision(
                7L, from, toExclusive, "KO", 1, "FINALIZED", "OPENAI", fingerprint, "{}", "{}", "v7-2", "gpt-4o", 50, 50, LocalDateTime.now(), LocalDateTime.now()
        );
        repository.save(r);

        Optional<AiWeeklyReportRevision> found = repository.findByGroupIdAndPeriodFromAndPeriodToExclusiveAndLanguageAndSourceFingerprint(
                7L, from, toExclusive, "KO", fingerprint
        );

        assertThat(found).isPresent();
        assertThat(found.get().getSourceFingerprint()).isEqualTo(fingerprint);
    }

    @Test
    @DisplayName("신규 revision 저장 시 이전 revision 1이 보존되고 revision 2가 생성된다")
    void preservesRevision1WhenRevision2Created() {
        LocalDate from = LocalDate.of(2026, 7, 20);
        LocalDate toExclusive = LocalDate.of(2026, 7, 27);

        AiWeeklyReportRevision r1 = new AiWeeklyReportRevision(
                7L, from, toExclusive, "KO", 1, "FINALIZED", "OPENAI", "FP1", "{\"rev\":1}", "{}", "v7-2", "gpt-4o", 10, 20, LocalDateTime.now(), LocalDateTime.now()
        );
        repository.save(r1);

        AiWeeklyReportRevision r2 = new AiWeeklyReportRevision(
                7L, from, toExclusive, "KO", 2, "FINALIZED", "SERVER_FALLBACK", "FP2", "{\"rev\":2}", "{}", "v7-2", null, null, null, LocalDateTime.now(), LocalDateTime.now()
        );
        repository.save(r2);

        var allRevisions = repository.findAll();
        assertThat(allRevisions).hasSize(2);
        assertThat(allRevisions).extracting(AiWeeklyReportRevision::getRevision).containsExactlyInAnyOrder(1, 2);
        assertThat(allRevisions).filteredOn(r -> r.getRevision() == 1).extracting(AiWeeklyReportRevision::getSnapshotJson).containsExactly("{\"rev\":1}");
        assertThat(allRevisions).filteredOn(r -> r.getRevision() == 2).extracting(AiWeeklyReportRevision::getSnapshotJson).containsExactly("{\"rev\":2}");
    }
}
