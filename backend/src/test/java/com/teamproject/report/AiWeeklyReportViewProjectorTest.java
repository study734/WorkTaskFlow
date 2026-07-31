package com.teamproject.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.report.application.AiWeeklyReportFallbackFactory;
import com.teamproject.report.application.AiWeeklyReportPolicyEngine;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private final AiWeeklyReportPolicyEngine policyEngine = new AiWeeklyReportPolicyEngine();
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

    /**
     * 실제 관측된 결함. projector가 구조화 ref 필드만 재결합해서, 모델이 문장 안에 써 넣은
     * "TASK-6은 URGENT 우선순위이며"가 사용자 문서에 그대로 찍혔다. 명세 §8.1은 재결합을
     * 서버 책임으로 두므로 텍스트 필드도 실제 제목으로 바뀌어야 한다.
     */
    @Test
    @DisplayName("분석 문장 안의 원시 ref를 실제 제목·이름으로 치환한다")
    void replacesRawRefsInsideAnalysisProse() {
        Fixture fixture = fixture();
        Task urgent = task(fixture, "결제 실패 로그 확인", Task.Status.TODO, null);
        flush();

        AiWeeklyReportView view = projector.project(revision(fixture,
                snapshotWith(fixture, urgent), analysisWithRefsInProse(fixture, urgent), "OPENAI"));

        assertThat(view.executiveJudgment().headline())
                .isEqualTo(urgent.getTitle() + "은 URGENT 우선순위이며 아직 TODO이다.");
        assertThat(view.executiveJudgment().interpretation())
                .contains(urgent.getTitle())
                .contains(fixture.leaderName);
        assertThat(view.achievement().summary()).contains(urgent.getTitle());

        var issue = view.issues().get(0);
        assertThat(issue.title()).contains(urgent.getTitle());
        assertThat(issue.impact()).contains(urgent.getTitle());
        assertThat(issue.integratedJudgment()).contains(fixture.leaderName);
        assertThat(issue.requiredDecision()).contains(urgent.getTitle());
        assertThat(issue.decision().title()).contains(urgent.getTitle());
        assertThat(issue.decision().question()).contains(fixture.leaderName);
        assertThat(issue.decision().recommendation()).contains(urgent.getTitle());

        assertThat(proseOf(view)).noneMatch(text -> text.matches(".*(TASK|MEMBER|EVENT)-\\d+.*"));
    }

    /** 매칭에 실패한 ref는 실제 제목이 없더라도 원시 식별자를 남기지 않는다. */
    @Test
    @DisplayName("해석할 수 없는 ref도 원시 식별자 대신 비식별 라벨로 바꾼다")
    void replacesUnresolvableRefsWithANonIdentifyingLabel() {
        Fixture fixture = fixture();
        Task urgent = task(fixture, "결제 실패 로그 확인", Task.Status.TODO, null);
        flush();

        String analysisJson = """
                {"schemaVersion":"ai-weekly-report-analysis.v1","analysisStatus":"NORMAL",
                 "executiveJudgment":{"headline":"TASK-999가 지연되었다.",
                 "interpretation":"담당자는 MEMBER-999, 관련 일정은 EVENT-999이다.",
                 "metricRefs":["PERIOD_TASK_COUNT"],"evidenceTaskRefs":["TASK-999"],
                 "confidence":"MEDIUM","missingEvidence":[]}}
                """;

        AiWeeklyReportView view = projector.project(revision(fixture,
                snapshotWith(fixture, urgent), analysisJson, "OPENAI"));

        assertThat(view.executiveJudgment().headline()).isEqualTo("확인할 수 없는 업무가 지연되었다.");
        assertThat(view.executiveJudgment().interpretation())
                .isEqualTo("담당자는 확인할 수 없는 팀원, 관련 일정은 확인할 수 없는 일정이다.");
        assertThat(view.executiveJudgment().evidenceTaskTitles()).containsExactly("확인할 수 없는 업무");
        assertThat(proseOf(view)).noneMatch(text -> text.matches(".*(TASK|MEMBER|EVENT)-\\d+.*"));
    }

    /**
     * candidateRef도 사용자에게는 의미 없는 내부 식별자다. 실제 문서에 "RISK-001의 OVERDUE
     * 근거는"이 그대로 찍혔다. missingEvidence는 목록이라 치환 경로에서 빠져 있었다.
     */
    @Test
    @DisplayName("candidateRef와 missingEvidence 문장의 내부 식별자도 치환한다")
    void replacesCandidateRefsIncludingInsideMissingEvidence() {
        Fixture fixture = fixture();
        Task urgent = task(fixture, "결제 실패 로그 확인", Task.Status.TODO, null);
        flush();

        String analysisJson = """
                {"schemaVersion":"ai-weekly-report-analysis.v1","analysisStatus":"NORMAL",
                 "executiveJudgment":{"headline":"RISK-001을 확인해야 한다.",
                 "interpretation":"근거가 부족하다.","metricRefs":["PERIOD_TASK_COUNT"],
                 "evidenceTaskRefs":[],"confidence":"INSUFFICIENT_EVIDENCE",
                 "missingEvidence":["RISK-001의 OVERDUE 상태를 뒷받침하는 근거"]},
                 "globalMissingEvidence":["RISK-002의 근거"]}
                """;

        AiWeeklyReportView view = projector.project(revision(fixture,
                snapshotWith(fixture, urgent), analysisJson, "OPENAI"));

        assertThat(view.executiveJudgment().headline()).isEqualTo("해당 위험 후보를 확인해야 한다.");
        assertThat(view.executiveJudgment().missingEvidence())
                .containsExactly("해당 위험 후보의 OVERDUE 상태를 뒷받침하는 근거");
        assertThat(view.globalMissingEvidence()).containsExactly("해당 위험 후보의 근거");
        assertThat(proseOf(view)).noneMatch(text -> text.matches(".*(TASK|MEMBER|EVENT|RISK)-\\d+.*"));
    }

    /**
     * 모델은 ref 발음에 맞춰 조사를 붙인다("TASK-5은"). 제목으로 바꾸면 받침이 달라져
     * "결제 실패 로그 확인은"이 "...정리은"처럼 어긋난다. 실제 문서에서 관측된 증상이다.
     */
    @Test
    @DisplayName("치환된 제목의 받침에 맞게 조사를 다시 고른다")
    void fixesKoreanParticlesAfterSubstitution() {
        Fixture fixture = fixture();
        Task noBatchim = task(fixture, "정책 정리", Task.Status.TODO, null);
        flush();

        String analysisJson = """
                {"schemaVersion":"ai-weekly-report-analysis.v1","analysisStatus":"NORMAL",
                 "executiveJudgment":{"headline":"TASK-%1$d은 TASK-%1$d를 TASK-%1$d이 TASK-%1$d과 TASK-%1$d으로 끝난다.",
                 "interpretation":"TASK-%1$d가 남아 있다.","metricRefs":["PERIOD_TASK_COUNT"],
                 "evidenceTaskRefs":[],"confidence":"MEDIUM","missingEvidence":[]}}
                """.formatted(noBatchim.getId());

        AiWeeklyReportView view = projector.project(revision(fixture,
                snapshotWith(fixture, noBatchim), analysisJson, "OPENAI"));

        // "정리"는 받침이 없고 앞 글자가 ㄹ이므로 는/를/가/와/로를 골라야 한다.
        assertThat(view.executiveJudgment().headline())
                .isEqualTo("정책 정리는 정책 정리를 정책 정리가 정책 정리와 정책 정리로 끝난다.");
        assertThat(view.executiveJudgment().interpretation()).isEqualTo("정책 정리가 남아 있다.");
    }

    /**
     * ref 치환을 넣은 뒤 fallback 경로를 투영해 보지 않아 회귀를 놓쳤다. fallback이 문장에
     * candidateRef를 박아 두면 치환이 "위험 후보 해당 위험 후보"처럼 같은 말을 겹치게 만든다.
     * 검증을 OPENAI 경로로만 했던 것이 원인이라 fallback 투영을 따로 고정한다.
     */
    @Test
    @DisplayName("위험 후보가 있는 fallback을 투영해도 문구가 겹치지 않는다")
    void projectsFallbackWithRiskCandidatesWithoutDoubledWording() {
        Fixture fixture = fixture();
        Task unassigned = task(fixture, "담당자 없는 업무", Task.Status.TODO, null);
        ReflectionTestUtils.setField(unassigned, "assignee", null);
        tasks.save(unassigned);
        flush();

        AiWeeklyReportSnapshotV1 snapshot = policyEngine.evaluate(assembler.assemble(
                fixture.group.getId(), FROM, TO_EXCLUSIVE, Language.KO, "v7-2-prompt-001"));
        assertThat(snapshot.riskCandidates()).isNotEmpty();

        AiWeeklyReportView view;
        try {
            view = projector.project(revision(fixture, json.writeValueAsString(snapshot),
                    json.writeValueAsString(fallback.create(snapshot)), "SERVER_FALLBACK"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        assertThat(view.issues()).isNotEmpty();
        assertThat(proseOf(view))
                .noneMatch(text -> text.contains("해당 위험 후보"))
                .noneMatch(text -> text.matches(".*(TASK|MEMBER|EVENT|RISK)-\\d+.*"));
    }

    /** 사용자에게 문장으로 보이는 필드 전부. 구조화된 ref 배열은 계약상 ref를 그대로 담는다. */
    private List<String> proseOf(AiWeeklyReportView view) {
        List<String> texts = new ArrayList<>();
        if (view.executiveJudgment() != null) {
            texts.add(view.executiveJudgment().headline());
            texts.add(view.executiveJudgment().interpretation());
            texts.addAll(view.executiveJudgment().evidenceTaskTitles());
        }
        if (view.achievement() != null) {
            texts.add(view.achievement().headline());
            texts.add(view.achievement().summary());
            texts.addAll(view.achievement().evidenceTaskTitles());
        }
        view.issues().forEach(issue -> {
            texts.add(issue.title());
            texts.add(issue.realTaskTitle());
            texts.add(issue.impact());
            texts.add(issue.integratedJudgment());
            texts.add(issue.requiredDecision());
            texts.addAll(issue.taskTitles());
            if (issue.decision() != null) {
                texts.add(issue.decision().title());
                texts.add(issue.decision().question());
                texts.add(issue.decision().recommendation());
                if (issue.decision().deadline() != null) {
                    texts.add(issue.decision().deadline().referenceTitle());
                }
            }
        });
        view.tasks().forEach(task -> {
            texts.add(task.realTitle());
            texts.add(task.assigneeName());
        });
        view.members().forEach(member -> texts.add(member.realName()));
        view.calendarConstraints().forEach(event -> texts.add(event.realTitle()));
        texts.addAll(view.globalMissingEvidence());
        if (view.executiveJudgment() != null) texts.addAll(view.executiveJudgment().missingEvidence());
        view.issues().forEach(issue -> texts.addAll(issue.missingEvidence()));
        texts.removeIf(Objects::isNull);
        return texts;
    }

    private String snapshotWith(Fixture fixture, Task task) {
        return """
                {"schemaVersion":"ai-weekly-report-snapshot.v1",
                 "reportContext":{"groupRef":"GROUP-%d","language":"KO","promptVersion":"v7-2-prompt-001"},
                 "metrics":{"periodTaskCount":1,"completionRatePercent":0,
                 "onTimeRatePercent":null,"delayedCount":0,"averageCompletionHours":null},
                 "tasks":[{"taskRef":"TASK-%d","safeLabel":"우선순위 높은 업무","status":"TODO",
                 "priority":"URGENT","assigneeRef":"MEMBER-%d","createdAt":"%s",
                 "calendarEventRefs":[]}],
                 "members":[{"memberRef":"MEMBER-%d","role":"LEADER","assignedCount":1,
                 "activeCount":1,"completedCount":0,"delayedCount":0,"upcomingCalendarCount":0}],
                 "calendarConstraints":[]}
                """.formatted(fixture.group.getId(), task.getId(), fixture.leader.getId(),
                task.getCreatedAt().toInstant(ZoneOffset.UTC).toString(), fixture.leader.getId());
    }

    private String analysisWithRefsInProse(Fixture fixture, Task task) {
        return """
                {"schemaVersion":"ai-weekly-report-analysis.v1","analysisStatus":"NORMAL",
                 "executiveJudgment":{"headline":"TASK-%1$d은 URGENT 우선순위이며 아직 TODO이다.",
                 "interpretation":"TASK-%1$d의 담당자는 MEMBER-%2$d이다.",
                 "metricRefs":["PERIOD_TASK_COUNT"],"evidenceTaskRefs":["TASK-%1$d"],
                 "confidence":"MEDIUM","missingEvidence":[]},
                 "achievement":{"status":"NONE","headline":"","summary":"TASK-%1$d은 미완료다.",
                 "evidenceTaskRefs":[]},
                 "issues":[{"priority":"P1","candidateRef":"RISK-1","severity":"HIGH",
                 "title":"TASK-%1$d 지연 위험","impact":"TASK-%1$d이 후속 작업을 막는다.",
                 "confidence":"MEDIUM","taskRefs":[],"evidenceCodes":[],"missingEvidence":[],
                 "integratedJudgment":"MEMBER-%2$d의 부하가 원인일 수 있다.",
                 "requiredDecision":"TASK-%1$d의 담당자 재배정 여부",
                 "decision":{"title":"TASK-%1$d 재배정","question":"MEMBER-%2$d에게 계속 맡길까?",
                 "recommendedOptionCode":"KEEP_CURRENT_PLAN",
                 "recommendation":"TASK-%1$d을 우선 처리한다.",
                 "decisionMakerRole":"LEADER","actionOwnerRole":"CURRENT_ASSIGNEE",
                 "deadline":{"source":"LEADER_DECISION_REQUIRED","referenceRef":null},
                 "executionStepCodes":[],"completionSignalCodes":[]}}]}
                """.formatted(task.getId(), fixture.leader.getId());
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
