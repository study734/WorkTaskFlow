package com.teamproject.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.report.application.AiWeeklyReportFallbackFactory;
import com.teamproject.report.application.AiWeeklyReportSnapshotAssembler;
import com.teamproject.report.application.AiWeeklyReportViewProjector;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.AiWeeklyReportAnalysisV1;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.AiWeeklyReportSnapshotV1;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.Language;
import com.teamproject.report.domain.AiWeeklyReportRevision;
import com.teamproject.report.presentation.dto.AiWeeklyReportApiDtos.AiWeeklyReportView;
import com.teamproject.report.presentation.dto.AiWeeklyReportApiDtos.SnapshotTaskView;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 저장된 v7-2 revision을 상세 화면 뷰로 되돌릴 수 있는지 확인한다.
 *
 * <p>상세 조회는 OpenAI를 다시 부르지 않고 저장된 Snapshot/Analysis와 DB 엔티티만으로
 * 실제 제목·이름을 재결합해야 한다. 여기서 깨지면 상세 화면이 통째로 500이 된다.
 */
@SpringBootTest
@Transactional
class AiWeeklyReportViewProjectorTest {
    private static final LocalDate FROM = LocalDate.of(2026, 7, 20);
    private static final LocalDate TO_EXCLUSIVE = FROM.plusDays(7);

    @Autowired AiWeeklyReportSnapshotAssembler assembler;
    @Autowired AiWeeklyReportViewProjector projector;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired TaskRepository tasks;
    @Autowired EntityManager entityManager;

    private final AiWeeklyReportFallbackFactory fallback = new AiWeeklyReportFallbackFactory();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("저장된 SERVER_FALLBACK revision을 실제 업무·팀원과 다시 결합한다")
    void projectsStoredFallbackRevisionWithRealEntities() throws Exception {
        Fixture fixture = fixture();
        Task inProgress = task(fixture, "모바일 화면 최종 점검", Task.Status.IN_PROGRESS, null);
        Task completed = task(fixture, "API 계약 검토 완료", Task.Status.COMPLETED,
                FROM.plusDays(3).atTime(9, 0));
        flush();

        AiWeeklyReportView view = project(fixture);

        assertThat(view.analysisMode()).isEqualTo("SERVER_FALLBACK");
        assertThat(view.from()).isEqualTo(FROM);
        assertThat(view.toExclusive()).isEqualTo(TO_EXCLUSIVE);
        assertThat(view.metrics().periodTaskCount()).isEqualTo(2);
        assertThat(view.workflow().inProgressCount()).isEqualTo(1);
        assertThat(view.workflow().completedCount()).isEqualTo(1);
        assertThat(view.tasks()).extracting(SnapshotTaskView::realTitle)
                .containsExactlyInAnyOrder(inProgress.getTitle(), completed.getTitle());
        assertThat(view.members()).extracting(value -> value.realName())
                .contains(fixture.leaderName);
        assertThat(view.downloadUrl())
                .isEqualTo("/api/v1/groups/" + fixture.group.getId() + "/reports/ai-weekly/1/pdf");
    }

    /** 실제 500의 원인이었던 경로. 조회 트랜잭션 밖에서 팀원 이름을 읽으면 여기서 터졌다. */
    @Test
    @DisplayName("담당자가 있는 업무의 팀원 이름을 조회 중에 해결한다")
    void resolvesAssigneeNamesDuringProjection() throws Exception {
        Fixture fixture = fixture();
        Task assigned = task(fixture, "담당자 지정 업무", Task.Status.IN_PROGRESS, null);
        ReflectionTestUtils.setField(assigned, "assignee", fixture.leader);
        tasks.save(assigned);
        flush();

        AiWeeklyReportView view = project(fixture);

        SnapshotTaskView task = view.tasks().get(0);
        assertThat(task.assigneeRef()).isEqualTo("MEMBER-" + fixture.leader.getId());
        assertThat(task.assigneeName()).isEqualTo(fixture.leaderName);
    }

    @Test
    @DisplayName("선택 배열이 없는 revision도 빈 목록으로 안전하게 투영한다")
    void projectsRevisionWithMissingOptionalArrays() {
        Fixture fixture = fixture();
        flush();

        String snapshotJson = """
                {"schemaVersion":"ai-weekly-report-snapshot.v1",
                 "metrics":{"periodTaskCount":0,"completionRatePercent":null,
                 "onTimeRatePercent":null,"delayedCount":0,"averageCompletionHours":null}}
                """;
        String analysisJson = """
                {"schemaVersion":"ai-weekly-report-analysis.v1","analysisStatus":"NO_ACTION_REQUIRED"}
                """;

        AiWeeklyReportView view = projector.project(
                revision(fixture, snapshotJson, analysisJson, "SERVER_FALLBACK"));

        assertThat(view.tasks()).isEmpty();
        assertThat(view.members()).isEmpty();
        assertThat(view.calendarConstraints()).isEmpty();
        assertThat(view.issues()).isEmpty();
        assertThat(view.globalMissingEvidence()).isEmpty();
        assertThat(view.workflow().completedCount()).isZero();
    }

    /**
     * ref 규칙이 바뀌기 전에 저장된 revision은 순번 ref를 담고 있다. 그 번호를 업무 ID로
     * 믿으면 같은 그룹의 전혀 다른 업무 제목이 나온다. 대조에 실패하면 비식별 라벨로 남긴다.
     */
    @Test
    @DisplayName("생성 시각이 맞지 않는 ref는 실제 제목 대신 안전 라벨로 남긴다")
    void fallsBackToSafeLabelWhenTheReferencedRowDoesNotMatch() {
        Fixture fixture = fixture();
        Task other = task(fixture, "전혀 다른 업무", Task.Status.TODO, null);
        flush();

        String snapshotJson = """
                {"schemaVersion":"ai-weekly-report-snapshot.v1",
                 "metrics":{"periodTaskCount":1,"completionRatePercent":0,
                 "onTimeRatePercent":null,"delayedCount":0,"averageCompletionHours":null},
                 "tasks":[{"taskRef":"TASK-%d","safeLabel":"담당자가 없는 업무","status":"TODO",
                 "createdAt":"1999-01-01T00:00:00Z","calendarEventRefs":[]}],
                 "members":[],"calendarConstraints":[]}
                """.formatted(other.getId());
        String analysisJson = """
                {"schemaVersion":"ai-weekly-report-analysis.v1","analysisStatus":"NO_ACTION_REQUIRED"}
                """;

        AiWeeklyReportView view = projector.project(
                revision(fixture, snapshotJson, analysisJson, "SERVER_FALLBACK"));

        assertThat(view.tasks()).hasSize(1);
        assertThat(view.tasks().get(0).realTitle()).isEqualTo("담당자가 없는 업무");
        assertThat(view.tasks().get(0).realTitle()).isNotEqualTo(other.getTitle());
    }

    // ---------- fixture ----------

    private AiWeeklyReportView project(Fixture fixture) throws Exception {
        AiWeeklyReportSnapshotV1 snapshot = assembler.assemble(fixture.group.getId(),
                FROM, TO_EXCLUSIVE, Language.KO, "v7-2-prompt-001");
        AiWeeklyReportAnalysisV1 analysis = fallback.create(snapshot);
        return projector.project(revision(fixture,
                json.writeValueAsString(snapshot), json.writeValueAsString(analysis),
                "SERVER_FALLBACK"));
    }

    private AiWeeklyReportRevision revision(Fixture fixture, String snapshotJson,
            String analysisJson, String analysisMode) {
        AiWeeklyReportRevision revision = new AiWeeklyReportRevision(
                fixture.group.getId(), FROM, TO_EXCLUSIVE, "KO", 1, "FINALIZED", analysisMode,
                "fingerprint", snapshotJson, analysisJson, "v7-2-prompt-001", null, null, null,
                LocalDateTime.now(), LocalDateTime.now());
        ReflectionTestUtils.setField(revision, "id", 1L);
        return revision;
    }

    private Task task(Fixture fixture, String title, Task.Status status,
            LocalDateTime completedAt) {
        Task task = new Task(fixture.group, fixture.leader, title, null,
                Task.Priority.NORMAL, FROM.plusDays(5).atTime(18, 0));
        ReflectionTestUtils.setField(task, "createdAt", FROM.plusDays(1).atStartOfDay());
        ReflectionTestUtils.setField(task, "updatedAt", FROM.plusDays(1).atStartOfDay());
        ReflectionTestUtils.setField(task, "status", status);
        ReflectionTestUtils.setField(task, "completedAt", completedAt);
        return tasks.save(task);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String leaderName = "리더" + suffix;
        User leaderUser = users.save(new User("leader_" + suffix,
                "leader-" + suffix + "@test.local", "hash", leaderName, true));
        Group group = groups.save(Group.team("그룹-" + suffix, null, "Asia/Seoul", leaderUser));
        GroupMember leader = members.save(GroupMember.leader(group, leaderUser));
        return new Fixture(group, leader, leaderName);
    }

    private record Fixture(Group group, GroupMember leader, String leaderName) {}
}
