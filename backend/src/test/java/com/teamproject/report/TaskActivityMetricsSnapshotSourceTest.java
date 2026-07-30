package com.teamproject.report;

import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.report.application.MetricsSnapshotSource;
import com.teamproject.report.application.ReportPeriod;
import com.teamproject.report.application.ReportContracts.MetricsSnapshot;
import com.teamproject.report.application.ReportContracts.HistoryCoverageStatus;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskActivityEvent;
import com.teamproject.task.domain.TaskActivityEventRepository;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TaskActivityMetricsSnapshotSourceTest {
    @Autowired MetricsSnapshotSource snapshots;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired TaskRepository tasks;
    @Autowired TaskActivityEventRepository events;

    @Test
    void laterTaskChangesDoNotRewriteTheCompletedWeeksSnapshot() {
        Fixture fixture = fixture();
        LocalDate weekStart = LocalDate.of(2026, 7, 15);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Task task = new Task(fixture.group(), fixture.leader(), "결정적 스냅샷",
                null, Task.Priority.HIGH, weekStart.plusDays(4).atTime(18, 0));
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(9, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(9, 0));
        tasks.save(task);
        events.save(new TaskActivityEvent(task, fixture.leader(), TaskActivityEvent.Type.TASK_CREATED,
                weekStart.plusDays(1).atTime(9, 0).atZone(zone).toInstant(), 1, 0, true));

        ReflectionTestUtils.setField(task, "status", Task.Status.COMPLETED);
        ReflectionTestUtils.setField(task, "completedAt", weekStart.plusDays(7).atTime(10, 0));
        events.save(new TaskActivityEvent(task, fixture.leader(), TaskActivityEvent.Type.STATUS_CHANGED,
                weekStart.plusDays(7).atTime(10, 0).atZone(zone).toInstant(), 2, 2, true));

        ReportPeriod period = ReportPeriod.completedWeek(weekStart, "Asia/Seoul",
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), zone));
        MetricsSnapshot snapshot = snapshots.snapshot(fixture.group().getId(), period);

        assertThat(snapshot.totalTasks()).isEqualTo(1);
        assertThat(snapshot.statuses().requested()).isEqualTo(1);
        assertThat(snapshot.statuses().completed()).isZero();
        assertThat(snapshot.checklist().total()).isEqualTo(1);
        assertThat(snapshot.checklist().completed()).isZero();
        assertThat(snapshot.historyCoverage().status()).isEqualTo(HistoryCoverageStatus.PARTIAL);
    }

    @Test
    void legacyTaskWithoutActivityHistoryIsMarkedPartial() {
        Fixture fixture = fixture();
        Fixture otherGroup = fixture();
        LocalDate weekStart = LocalDate.of(2026, 7, 15);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Task otherTask = new Task(otherGroup.group(), otherGroup.leader(), "다른 그룹 기준선",
                null, Task.Priority.NORMAL, null);
        ReflectionTestUtils.setField(otherTask, "createdAt", weekStart.minusDays(2).atTime(9, 0));
        ReflectionTestUtils.setField(otherTask, "updatedAt", weekStart.minusDays(2).atTime(9, 0));
        tasks.save(otherTask);
        events.save(new TaskActivityEvent(otherTask, null, TaskActivityEvent.Type.BASELINE,
                weekStart.minusDays(1).atTime(9, 0).atZone(zone).toInstant(), 0, 0, false));
        Task task = new Task(fixture.group(), fixture.leader(), "기존 업무",
                null, Task.Priority.NORMAL, null);
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(9, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(9, 0));
        tasks.save(task);

        ReportPeriod period = ReportPeriod.completedWeek(weekStart, "Asia/Seoul",
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), zone));
        MetricsSnapshot snapshot = snapshots.snapshot(fixture.group().getId(), period);

        assertThat(snapshot.totalTasks()).isEqualTo(1);
        assertThat(snapshot.historyCoverage().status()).isEqualTo(HistoryCoverageStatus.PARTIAL);
        assertThat(snapshot.historyCoverage().trackingStartedAt()).isNull();
        assertThat(snapshot.evidence()).containsEntry("coverage.partial", 1);
    }

    @Test
    void includesActiveMembersWithoutAssignedWorkInTeamFlow() {
        Fixture fixture = fixture();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User teammate = users.save(new User(
                "member_" + suffix, "member_" + suffix + "@example.com",
                "hash", "업무 없는 팀원", true));
        members.save(GroupMember.member(fixture.group(), teammate));
        LocalDate weekStart = LocalDate.of(2026, 7, 15);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Task task = new Task(fixture.group(), fixture.leader(), "팀원 흐름 검증",
                null, Task.Priority.NORMAL, weekStart.plusDays(4).atTime(18, 0));
        task.assign(fixture.leader());
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(9, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(9, 0));
        tasks.save(task);
        events.save(new TaskActivityEvent(task, fixture.leader(),
                TaskActivityEvent.Type.TASK_CREATED,
                weekStart.plusDays(1).atTime(9, 0).atZone(zone).toInstant(),
                2, 0, true));

        ReportPeriod period = ReportPeriod.completedWeek(weekStart, "Asia/Seoul",
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));
        MetricsSnapshot snapshot = snapshots.snapshot(fixture.group().getId(), period);

        assertThat(snapshot.members()).hasSize(2);
        assertThat(snapshot.members()).extracting(value -> value.assigned())
                .containsExactly(1L, 0L);
    }

    @Test
    void convertsStructuredBlockerEnumsFromActivityEventsIntoReportContext() {
        Fixture fixture = fixture();
        LocalDate weekStart = LocalDate.of(2026, 7, 15);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        List<Task> blockedTasks = List.of(
                blockedTask(fixture, "접근 권한", Task.BlockerType.ACCESS,
                        Task.BlockerNextActionType.UNBLOCK_ACCESS, weekStart),
                blockedTask(fixture, "계획 재조정", Task.BlockerType.DEPENDENCY,
                        Task.BlockerNextActionType.REPLAN, weekStart),
                blockedTask(fixture, "외부 응답", Task.BlockerType.EXTERNAL,
                        Task.BlockerNextActionType.WAIT_EXTERNAL, weekStart));
        for (int index = 0; index < blockedTasks.size(); index++) {
            Task task = blockedTasks.get(index);
            events.save(new TaskActivityEvent(task, fixture.leader(),
                    TaskActivityEvent.Type.BLOCKER_CHANGED,
                    weekStart.plusDays(1).atTime(9 + index, 0).atZone(zone).toInstant(),
                    0, 0, true));
        }

        ReportPeriod period = ReportPeriod.completedWeek(weekStart, "Asia/Seoul",
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), zone));
        var snapshot = snapshots.capture(fixture.group().getId(), period);

        assertThat(snapshot.aiContext().tasks())
                .extracting(value -> value.blockerType() + ":" + value.blockerNextActionType())
                .containsExactlyInAnyOrder(
                        "ACCESS:UNBLOCK_ACCESS",
                        "DEPENDENCY:REPLAN",
                        "EXTERNAL:WAIT_EXTERNAL");
    }

    @Test
    void exposesStatusFlowAndTaskScopedEvidenceKeys() {
        Fixture fixture = fixture();
        LocalDate weekStart = LocalDate.of(2026, 7, 15);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Task task = new Task(fixture.group(), fixture.leader(), "근거 키 확대 검증",
                null, Task.Priority.HIGH, weekStart.plusDays(4).atTime(18, 0));
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(9, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(9, 0));
        tasks.save(task);
        events.save(new TaskActivityEvent(task, fixture.leader(),
                TaskActivityEvent.Type.TASK_CREATED,
                weekStart.plusDays(1).atTime(9, 0).atZone(zone).toInstant(), 3, 1, true));

        ReportPeriod period = ReportPeriod.completedWeek(weekStart, "Asia/Seoul",
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), zone));
        var snapshot = snapshots.capture(fixture.group().getId(), period);

        assertThat(snapshot.evidence()).containsKeys(
                "tasks.requested", "tasks.todo", "tasks.inProgress",
                "tasks.rejected", "tasks.cancelled",
                "daily.2026-07-16.created", "daily.2026-07-21.completed",
                "flow.zeroCompletionDays",
                "task.TASK-01.checklistTotal", "task.TASK-01.checklistCompleted");
        assertThat(snapshot.evidence().get("daily.2026-07-16.created").value()).isEqualTo("1");
        assertThat(snapshot.evidence().get("flow.zeroCompletionDays").value()).isEqualTo("7");
        assertThat(snapshot.evidence().get("task.TASK-01.checklistTotal").value()).isEqualTo("3");
        assertThat(snapshot.evidence()).doesNotContainKeys(
                "flow.peakCompletedDay", "time.averageCompletionHours");
    }

    // 체류 시간은 기간 시작 이전에 막힌 구간까지 포함해야 "3주째 막혀 있다"를 말할 수 있다.
    @Test
    void measuresBlockedDwellFromBeforeThePeriodAndFlagsOverdueBlockerReview() {
        Fixture fixture = fixture();
        LocalDate weekStart = LocalDate.of(2026, 7, 15);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Task task = new Task(fixture.group(), fixture.leader(), "창 이전부터 막힌 업무",
                null, Task.Priority.HIGH, weekStart.minusDays(1).atTime(18, 0));
        task.assign(fixture.leader());
        ReflectionTestUtils.setField(task, "createdAt", weekStart.minusDays(4).atTime(9, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.minusDays(3).atTime(9, 0));
        ReflectionTestUtils.setField(task, "status", Task.Status.ON_HOLD);
        ReflectionTestUtils.setField(task, "blockerType", Task.BlockerType.EXTERNAL);
        ReflectionTestUtils.setField(task, "blockerNextActionType",
                Task.BlockerNextActionType.WAIT_EXTERNAL);
        ReflectionTestUtils.setField(task, "blockerReviewDate", weekStart.plusDays(2));
        tasks.saveAndFlush(task);

        // 창 이전 전이 1건 + 창 안 전이 1건. 두 구간의 합이 보류 체류 시간이다.
        events.save(new TaskActivityEvent(task, fixture.leader(),
                TaskActivityEvent.Type.STATUS_CHANGED,
                weekStart.minusDays(3).atTime(9, 0).atZone(zone).toInstant(), 2, 1, true));
        events.save(new TaskActivityEvent(task, fixture.leader(),
                TaskActivityEvent.Type.BLOCKER_CHANGED,
                weekStart.plusDays(2).atTime(9, 0).atZone(zone).toInstant(), 2, 1, true));

        ReportPeriod period = ReportPeriod.completedWeek(weekStart, "Asia/Seoul",
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), zone));
        var snapshot = snapshots.capture(fixture.group().getId(), period);

        // 07-17T00:00Z → 07-22T00:00Z = 120h, 07-22T00:00Z → 07-27T00:00+09 = 111h
        assertThat(snapshot.evidence().get("task.TASK-01.blockedHours").value()).isEqualTo("231");
        assertThat(snapshot.evidence().get("flow.longestBlockedHours").value()).isEqualTo("231");
        assertThat(snapshot.evidence().get("task.TASK-01.idleDays").value()).isEqualTo("4");
        assertThat(snapshot.evidence().get("flow.overdueReviewCount").value()).isEqualTo("1");
        assertThat(snapshot.evidence()).doesNotContainKeys(
                "task.TASK-01.reopenCount", "task.TASK-01.assigneeChangeCount");
    }

    @Test
    void publishesServerComputedMemberGradeAndRank() {
        Fixture fixture = fixture();
        LocalDate weekStart = LocalDate.of(2026, 7, 15);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Task task = new Task(fixture.group(), fixture.leader(), "등급 산출 대상",
                null, Task.Priority.NORMAL, weekStart.plusDays(4).atTime(18, 0));
        task.assign(fixture.leader());
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(9, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(2).atTime(9, 0));
        ReflectionTestUtils.setField(task, "status", Task.Status.COMPLETED);
        ReflectionTestUtils.setField(task, "completedAt", weekStart.plusDays(2).atTime(16, 0));
        tasks.saveAndFlush(task);
        events.save(new TaskActivityEvent(task, fixture.leader(),
                TaskActivityEvent.Type.STATUS_CHANGED,
                weekStart.plusDays(2).atTime(16, 0).atZone(zone).toInstant(), 4, 4, true));

        ReportPeriod period = ReportPeriod.completedWeek(weekStart, "Asia/Seoul",
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), zone));
        var snapshot = snapshots.capture(fixture.group().getId(), period);

        // 완료율 100 · 기한 준수율 100 · 체크리스트 100, 감점 없음 → 만점 A, 유일한 평가 대상
        assertThat(snapshot.evidence().get("member.MEMBER-01.grade").value()).isEqualTo("A");
        assertThat(snapshot.evidence().get("member.MEMBER-01.score").value()).isEqualTo("100");
        assertThat(snapshot.evidence().get("member.MEMBER-01.rank").value()).isEqualTo("1/1");
        assertThat(snapshot.evidence().get("member.MEMBER-01.completionRate").value())
                .isEqualTo("100%");
        assertThat(snapshot.evidence().get("members.ratedCount").value()).isEqualTo("1");
        assertThat(snapshot.evidence().get("members.topGrade").value()).isEqualTo("A");
        // 완료된 업무의 무활동 일수는 정체가 아니므로 근거로 발급하지 않는다.
        assertThat(snapshot.evidence()).doesNotContainKey("task.TASK-01.idleDays");
    }

    private Task blockedTask(Fixture fixture, String title, Task.BlockerType blockerType,
            Task.BlockerNextActionType nextActionType, LocalDate weekStart) {
        Task task = new Task(fixture.group(), fixture.leader(), title,
                null, Task.Priority.HIGH, weekStart.plusDays(4).atTime(18, 0));
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(8, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(8, 0));
        task.hold("보고서 enum 변환 검증", blockerType, nextActionType, weekStart.plusDays(3));
        return tasks.save(task);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(new User("metrics_" + suffix, "metrics_" + suffix + "@example.com",
                "hash", "지표 팀장", true));
        Group group = groups.save(Group.team("결정적 지표 팀", null, "Asia/Seoul", user));
        GroupMember leader = members.save(GroupMember.leader(group, user));
        return new Fixture(group, leader);
    }

    private record Fixture(Group group, GroupMember leader) {}
}
