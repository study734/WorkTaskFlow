package com.teamproject.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.report.application.AiWeeklyReportAnalysisValidator;
import com.teamproject.report.application.AiWeeklyReportFallbackFactory;
import com.teamproject.report.application.AiWeeklyReportGenerationService;
import com.teamproject.report.application.AiWeeklyReportGenerationService.GenerateCommand;
import com.teamproject.report.application.AiWeeklyReportPolicyEngine;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.*;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.*;
import com.teamproject.report.application.port.AiWeeklyReportGateway;
import com.teamproject.report.domain.AiWeeklyReportRevision;
import com.teamproject.report.domain.AiWeeklyReportRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AiWeeklyReportGenerationServiceTest {

    @Autowired
    private AiWeeklyReportRevisionRepository revisionRepository;

    private final ObjectMapper json = new ObjectMapper();
    private final AiWeeklyReportPolicyEngine policyEngine = new AiWeeklyReportPolicyEngine();
    private final AiWeeklyReportAnalysisValidator validator = new AiWeeklyReportAnalysisValidator();
    private final AiWeeklyReportFallbackFactory fallbackFactory = new AiWeeklyReportFallbackFactory();

    private AiWeeklyReportSnapshotV1 initialRawSnapshot;
    private GenerateCommand baseCommand;

    @BeforeEach
    void setUp() throws IOException {
        InputStream stream = getClass().getResourceAsStream("/ai/ai-weekly-report-snapshot-v1.example.json");
        initialRawSnapshot = json.readValue(stream, AiWeeklyReportSnapshotV1.class);

        baseCommand = new GenerateCommand(
                7L,
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 27),
                "KO",
                false,
                "v7-2-prompt-001",
                "gpt-4o"
        );
    }

    @Test
    @DisplayName("Gateway 정상 성공 시 OPENAI 모드로 FINALIZED revision이 저장된다")
    void normalOpenAiSuccess() {
        AtomicInteger callCount = new AtomicInteger(0);
        AiWeeklyReportGateway fakeGateway = snapshot -> {
            callCount.incrementAndGet();
            return fallbackFactory.create(snapshot); // valid analysis
        };

        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, fakeGateway, validator, fallbackFactory, revisionRepository, json
        );

        AiWeeklyReportRevision revision = service.generate(initialRawSnapshot, baseCommand);

        assertThat(revision.getRevision()).isEqualTo(1);
        assertThat(revision.getStatus()).isEqualTo("FINALIZED");
        assertThat(revision.getAnalysisMode()).isEqualTo("OPENAI");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Gateway 예외 발생 시 SERVER_FALLBACK 모드로 전환되어 저장된다")
    void gatewayFailureTriggersFallback() {
        AtomicInteger callCount = new AtomicInteger(0);
        AiWeeklyReportGateway failingGateway = snapshot -> {
            callCount.incrementAndGet();
            throw new RuntimeException("Gateway network failure");
        };

        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, failingGateway, validator, fallbackFactory, revisionRepository, json
        );

        AiWeeklyReportRevision revision = service.generate(initialRawSnapshot, baseCommand);

        assertThat(revision.getRevision()).isEqualTo(1);
        assertThat(revision.getStatus()).isEqualTo("FINALIZED");
        assertThat(revision.getAnalysisMode()).isEqualTo("SERVER_FALLBACK");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Gateway 결과가 Validator를 실패하면 SERVER_FALLBACK 모드로 전환된다")
    void validatorFailureTriggersFallback() {
        AiWeeklyReportGateway invalidOutputGateway = snapshot -> new AiWeeklyReportAnalysisV1(
                "ai-weekly-report-analysis.v1",
                AnalysisStatus.NORMAL,
                new ExecutiveJudgment("H", "I", List.of(), List.of("NON_EXISTENT_TASK"), Confidence.HIGH, List.of()),
                Achievement.none(),
                List.of(),
                List.of()
        );

        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, invalidOutputGateway, validator, fallbackFactory, revisionRepository, json
        );

        AiWeeklyReportRevision revision = service.generate(initialRawSnapshot, baseCommand);

        assertThat(revision.getStatus()).isEqualTo("FINALIZED");
        assertThat(revision.getAnalysisMode()).isEqualTo("SERVER_FALLBACK");
    }

    @Test
    @DisplayName("동일 source에 regenerate=false일 때 Gateway를 0회 호출하고 기존 revision을 반환한다")
    void deduplicationReusesExistingRevisionWithoutGatewayCall() {
        AtomicInteger callCount = new AtomicInteger(0);
        AiWeeklyReportGateway gateway = snapshot -> {
            callCount.incrementAndGet();
            return fallbackFactory.create(snapshot);
        };

        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, gateway, validator, fallbackFactory, revisionRepository, json
        );

        // First creation
        AiWeeklyReportRevision rev1 = service.generate(initialRawSnapshot, baseCommand);
        assertThat(callCount.get()).isEqualTo(1);

        // Second creation with same input
        AiWeeklyReportRevision rev2 = service.generate(initialRawSnapshot, baseCommand);

        assertThat(callCount.get()).isEqualTo(1); // Not called again!
        assertThat(rev2.getId()).isEqualTo(rev1.getId());
        assertThat(rev2.getRevision()).isEqualTo(1);
    }

    @Test
    @DisplayName("regenerate=true 시 새 revision이 생성되며 Gateway가 다시 호출된다")
    void regenerateCreatesNewRevision() {
        AtomicInteger callCount = new AtomicInteger(0);
        AiWeeklyReportGateway gateway = snapshot -> {
            callCount.incrementAndGet();
            return fallbackFactory.create(snapshot);
        };

        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, gateway, validator, fallbackFactory, revisionRepository, json
        );

        // First creation
        AiWeeklyReportRevision rev1 = service.generate(initialRawSnapshot, baseCommand);
        assertThat(rev1.getRevision()).isEqualTo(1);

        // Regenerate command
        GenerateCommand regenCommand = new GenerateCommand(
                baseCommand.groupId(),
                baseCommand.periodFrom(),
                baseCommand.periodToExclusive(),
                baseCommand.language(),
                true, // regenerate=true
                baseCommand.promptVersion(),
                baseCommand.model()
        );

        AiWeeklyReportRevision rev2 = service.generate(initialRawSnapshot, regenCommand);

        assertThat(callCount.get()).isEqualTo(2);
        assertThat(rev2.getRevision()).isEqualTo(2);
        assertThat(rev2.getId()).isNotEqualTo(rev1.getId());
    }

    @Test
    @DisplayName("Gateway에 전달되는 Snapshot JSON에 사용자 실명 및 원문이 포함되지 않는다")
    void gatewayReceivesPrivacyProtectedSnapshot() {
        AtomicInteger callCount = new AtomicInteger(0);
        AiWeeklyReportGateway gateway = snapshot -> {
            callCount.incrementAndGet();
            // Verify snapshot tasks do not contain raw titles/emails/names
            for (SnapshotTask task : snapshot.tasks()) {
                assertThat(task.safeLabel()).doesNotContain("@");
                assertThat(task.safeLabel()).doesNotContain("김민준");
            }
            return fallbackFactory.create(snapshot);
        };

        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, gateway, validator, fallbackFactory, revisionRepository, json
        );

        service.generate(initialRawSnapshot, baseCommand);
        assertThat(callCount.get()).isEqualTo(1);
    }
}
