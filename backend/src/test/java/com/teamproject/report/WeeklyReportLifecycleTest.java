package com.teamproject.report;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.report.application.AiNarrativeGenerator;
import com.teamproject.report.application.ReportContracts.AiGenerationResult;
import com.teamproject.report.application.ReportContracts.ActionNarrativeItem;
import com.teamproject.report.application.ReportContracts.EditWeeklyReportDraft;
import com.teamproject.report.application.ReportContracts.FinalizeWeeklyReport;
import com.teamproject.report.application.ReportContracts.GenerateWeeklyReport;
import com.teamproject.report.application.ReportContracts.Narrative;
import com.teamproject.report.application.ReportContracts.NarrativeItem;
import com.teamproject.report.application.ReportContracts.RegenerateWeeklyReport;
import com.teamproject.report.application.ReportContracts.WeeklyReportView;
import com.teamproject.report.application.WeeklyReportModule;
import com.teamproject.report.domain.WeeklyReport;
import com.teamproject.report.domain.WeeklyReportRepository;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class WeeklyReportLifecycleTest {
    @Autowired WeeklyReportModule reports;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired TaskRepository tasks;
    @Autowired WeeklyReportRepository weeklyReports;
    @MockBean AiNarrativeGenerator narrativeGenerator;

    @Test
    void draftEditUsesOptimisticVersionAndFinalizedContentIsImmutable() {
        Fixture fixture = paidTeamWithTask();
        when(narrativeGenerator.generate(any())).thenReturn(generation("초기 초안"));
        WeeklyReportView created = generate(fixture);

        WeeklyReportView edited = reports.editWeeklyAiReportDraft(
                new EditWeeklyReportDraft(
                        fixture.user().getId(), fixture.group().getId(),
                        created.reportId(), created.editorVersion(),
                        withOwner(narrative("편집된 초안"))));

        assertThat(edited.editorVersion()).isEqualTo(1);
        assertThat(edited.analysis().headline()).isEqualTo("편집된 초안");
        assertThatThrownBy(() -> reports.editWeeklyAiReportDraft(
                new EditWeeklyReportDraft(
                        fixture.user().getId(), fixture.group().getId(),
                        created.reportId(), 0, withOwner(narrative("충돌 초안")))))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("AI_REPORT_EDITOR_VERSION_CONFLICT"));

        WeeklyReportView finalized = reports.finalizeWeeklyAiReport(
                new FinalizeWeeklyReport(
                        fixture.user().getId(), fixture.group().getId(),
                        edited.reportId(), edited.editorVersion()));

        assertThat(finalized.publicationStatus()).isEqualTo("FINALIZED");
        assertThat(finalized.analysis().headline()).isEqualTo("편집된 초안");
        assertThatThrownBy(() -> reports.editWeeklyAiReportDraft(
                new EditWeeklyReportDraft(
                        fixture.user().getId(), fixture.group().getId(),
                        finalized.reportId(), finalized.editorVersion(),
                        withOwner(narrative("확정 후 변경")))))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("AI_REPORT_STATE_CONFLICT"));
    }

    @Test
    void regenerationCreatesNextRevisionAndReusesFrozenSnapshot() {
        Fixture fixture = paidTeamWithTask();
        when(narrativeGenerator.generate(any()))
                .thenReturn(generation("첫 리비전"), generation("두 번째 리비전"));
        WeeklyReportView first = generate(fixture);
        addTaskInReportWeek(fixture, "나중에 추가된 업무");

        WeeklyReportView second = reports.regenerateWeeklyAiReport(
                new RegenerateWeeklyReport(
                        fixture.user().getId(), fixture.group().getId(),
                        first.reportId(), first.editorVersion()));

        assertThat(second.revision()).isEqualTo(2);
        assertThat(second.reportId()).isNotEqualTo(first.reportId());
        assertThat(second.metrics()).isEqualTo(first.metrics());
        assertThat(second.analysis().headline()).isEqualTo("두 번째 리비전");
        assertThat(weeklyReports.findById(first.reportId()).orElseThrow()
                .getPublicationStatus()).isEqualTo(WeeklyReport.PublicationStatus.SUPERSEDED);
    }

@Test
    void weeklySuccessBudgetAllowsOnlyOneConcurrentThirdGeneration() throws Exception {
        Fixture fixture = paidTeamWithTask();
        when(narrativeGenerator.generate(any()))
                .thenReturn(generation("첫 리비전"), generation("두 번째 리비전"));
        WeeklyReportView first = generate(fixture);
        WeeklyReportView second = reports.regenerateWeeklyAiReport(
                new RegenerateWeeklyReport(
                        fixture.user().getId(), fixture.group().getId(),
                        first.reportId(), first.editorVersion()));

        CountDownLatch generationStarted = new CountDownLatch(1);
        CountDownLatch releaseGeneration = new CountDownLatch(1);
        when(narrativeGenerator.generate(any())).thenAnswer(invocation -> {
            generationStarted.countDown();
            if (!releaseGeneration.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test generation release timed out");
            }
            return generation("세 번째 리비전");
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            RegenerateWeeklyReport command = new RegenerateWeeklyReport(
                    fixture.user().getId(), fixture.group().getId(),
                    second.reportId(), second.editorVersion());
            Future<WeeklyReportView> winning = executor.submit(
                    () -> reports.regenerateWeeklyAiReport(command));
            assertThat(generationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<WeeklyReportView> collision = executor.submit(
                    () -> reports.regenerateWeeklyAiReport(command));

            ExecutionException collisionError = assertThrows(ExecutionException.class,
                    () -> collision.get(5, TimeUnit.SECONDS));
            assertThat(collisionError.getCause()).isInstanceOfSatisfying(
                    ApplicationException.class,
                    exception -> assertThat(exception.code())
                            .isEqualTo("AI_REPORT_STATE_CONFLICT"));

            releaseGeneration.countDown();
            WeeklyReportView third = winning.get(5, TimeUnit.SECONDS);
            assertThat(third.revision()).isEqualTo(3);

            assertThatThrownBy(() -> reports.regenerateWeeklyAiReport(
                    new RegenerateWeeklyReport(
                            fixture.user().getId(), fixture.group().getId(),
                            third.reportId(), third.editorVersion())))
                    .isInstanceOfSatisfying(ApplicationException.class,
                            exception -> assertThat(exception.code())
                                    .isEqualTo("AI_REPORT_WEEKLY_LIMIT"));
            verify(narrativeGenerator, times(3)).generate(any());
        } finally {
            releaseGeneration.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedRegenerationRetriesSameRevisionWithFrozenSnapshot() {
        Fixture fixture = paidTeamWithTask();
        when(narrativeGenerator.generate(any()))
                .thenReturn(generation("첫 리비전"))
                .thenThrow(new RuntimeException("provider unavailable"))
                .thenReturn(generation("복구된 두 번째 리비전"));
        WeeklyReportView first = generate(fixture);
        addTaskInReportWeek(fixture, "실패 뒤 추가된 업무");

        assertThatThrownBy(() -> reports.regenerateWeeklyAiReport(
                new RegenerateWeeklyReport(
                        fixture.user().getId(), fixture.group().getId(),
                        first.reportId(), first.editorVersion())))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("provider unavailable");

        WeeklyReport failed = weeklyReports
                .findByGroupIdAndTypeAndPeriodStartAndPeriodEndAndLanguageAndRevision(
                        fixture.group().getId(), WeeklyReport.Type.WEEKLY_AI,
                        fixture.weekStart(), fixture.weekStart().plusDays(6), "ko", 2)
                .orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(WeeklyReport.Status.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(1);

        WeeklyReportView recovered = reports.regenerateWeeklyAiReport(
                new RegenerateWeeklyReport(
                        fixture.user().getId(), fixture.group().getId(),
                        failed.getId(), failed.getEditorVersion()));

        assertThat(recovered.reportId()).isEqualTo(failed.getId());
        assertThat(recovered.revision()).isEqualTo(2);
        assertThat(recovered.metrics()).isEqualTo(first.metrics());
        assertThat(recovered.analysis().headline()).isEqualTo("복구된 두 번째 리비전");
        assertThat(weeklyReports.findById(failed.getId()).orElseThrow().getAttemptCount())
                .isEqualTo(2);
        assertThat(weeklyReports.findById(first.reportId()).orElseThrow()
                .getPublicationStatus()).isEqualTo(WeeklyReport.PublicationStatus.SUPERSEDED);
    }

    @Test
    void finalizingNewRevisionLeavesOnlyLatestReportFinalized() {
        Fixture fixture = paidTeamWithTask();
        when(narrativeGenerator.generate(any()))
                .thenReturn(generation("첫 확정 리비전"), generation("새 확정 리비전"));
        WeeklyReportView first = generate(fixture);
        WeeklyReportView firstFinalized = reports.finalizeWeeklyAiReport(
                new FinalizeWeeklyReport(
                        fixture.user().getId(), fixture.group().getId(),
                        first.reportId(), first.editorVersion()));

        WeeklyReportView second = reports.regenerateWeeklyAiReport(
                new RegenerateWeeklyReport(
                        fixture.user().getId(), fixture.group().getId(),
                        firstFinalized.reportId(), firstFinalized.editorVersion()));
        WeeklyReportView secondFinalized = reports.finalizeWeeklyAiReport(
                new FinalizeWeeklyReport(
                        fixture.user().getId(), fixture.group().getId(),
                        second.reportId(), second.editorVersion()));

        assertThat(secondFinalized.publicationStatus()).isEqualTo("FINALIZED");
        assertThat(weeklyReports.findById(first.reportId()).orElseThrow()
                .getPublicationStatus()).isEqualTo(WeeklyReport.PublicationStatus.SUPERSEDED);
        assertThat(weeklyReports.findById(second.reportId()).orElseThrow()
                .getPublicationStatus()).isEqualTo(WeeklyReport.PublicationStatus.FINALIZED);
    }

    private WeeklyReportView generate(Fixture fixture) {
        return reports.generateWeeklyAiReport(new GenerateWeeklyReport(
                fixture.user().getId(), fixture.group().getId(), fixture.weekStart(), "ko"));
    }

    private Fixture paidTeamWithTask() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(new User(
                "lifecycle_" + suffix,
                "lifecycle_" + suffix + "@example.com",
                "hash", "리포트 팀장", true));
        Group group = Group.team("리포트 라이프사이클 팀", null, "Asia/Seoul", user);
        ReflectionTestUtils.setField(group, "membershipPlan", Group.MembershipPlan.PAID);
        group = groups.save(group);
        GroupMember leader = members.save(GroupMember.leader(group, user));
        LocalDate weekStart = LocalDate.now()
                .with(TemporalAdjusters.previous(DayOfWeek.MONDAY))
                .minusWeeks(1);
        Fixture fixture = new Fixture(user, group, leader, weekStart);
        addTaskInReportWeek(fixture, "초기 업무");
        return fixture;
    }

    private void addTaskInReportWeek(Fixture fixture, String title) {
        Task task = new Task(
                fixture.group(), fixture.leader(), title, null,
                Task.Priority.HIGH, fixture.weekStart().plusDays(4).atTime(18, 0));
        ReflectionTestUtils.setField(
                task, "createdAt", fixture.weekStart().plusDays(1).atTime(9, 0));
        ReflectionTestUtils.setField(
                task, "updatedAt", fixture.weekStart().plusDays(1).atTime(9, 0));
        tasks.save(task);
    }

    private AiGenerationResult generation(String headline) {
        return new AiGenerationResult(
                withOwner(narrative(headline)), "fake-ai", 10, 10, 20);
    }

    private Narrative withOwner(Narrative narrative) {
        return new Narrative(
                narrative.headlineTemplate(), narrative.summary(),
                narrative.changes(), narrative.achievements(), narrative.risks(),
                narrative.topActions().stream()
                        .map(action -> new ActionNarrativeItem(
                                action.priority(), action.actionTemplate(),
                                action.reasonTemplate(), "MEMBER-01",
                                action.evidenceKeys(), action.taskRefs(),
                                action.objectiveRefs()))
                        .toList(),
                narrative.leaderDecisions(), narrative.limitations());
    }

    private Narrative narrative(String headline) {
        return new Narrative(
                headline,
                "확정된 주간 업무 흐름을 바탕으로 작성한 실행 요약입니다.",
                List.of(new NarrativeItem(
                        "이번 주 업무 흐름을 확인했습니다.", List.of("tasks.total"))),
                List.of(),
                List.of(new NarrativeItem(
                        "우선순위가 높은 업무를 점검하세요.",
                        List.of("tasks.highPriority"))),
                List.of(new NarrativeItem(
                        "활동 이력 수집 전 데이터가 포함될 수 있습니다.",
                        List.of("coverage.partial"))));
    }

    private record Fixture(
            User user, Group group, GroupMember leader, LocalDate weekStart) {}
}
