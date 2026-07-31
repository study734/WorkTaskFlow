package com.teamproject.report.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.report.application.AiWeeklyReportEvidenceQuery.CalendarWindow;
import com.teamproject.report.application.AiWeeklyReportEvidenceQuery.TaskCollaborationCounts;
import com.teamproject.report.application.AiWeeklyReportSafeLabelFactory.TaskLabelFacts;
import com.teamproject.report.application.dto.AiWeeklyReportDtos;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.*;
import com.teamproject.task.application.TaskReportDataQuery;
import com.teamproject.task.application.TaskReportDataQuery.ActivityEvent;
import com.teamproject.task.application.TaskReportDataQuery.TaskSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 확정된 그룹 업무 사실을 v7-2 OpenAI 입력 Snapshot으로 조립한다.
 *
 * <p>여기서는 사실만 담는다. 위험 후보 선정과 신호·허용 코드 산출은 policy engine(M3)이
 * 담당하므로 {@code signalCodes}, {@code allowed*Codes}, {@code riskCandidates}는 빈 배열로
 * 둔다. Schema는 빈 배열을 허용한다.
 *
 * <p>개인정보 경계: 표시 문자열은 {@link AiWeeklyReportSafeLabelFactory}가 만든 비식별
 * 라벨뿐이다. 제목·설명·댓글 원문·실명은 어떤 경로로도 들어오지 않는다.
 *
 * <p>기존 {@code TaskMetricsSnapshotSource}는 건드리지 않는다. 같은
 * {@link TaskReportDataQuery}를 쓰되 별도 경로로 조립한다.
 */
@Component
public class AiWeeklyReportSnapshotAssembler {
    /** 활동 이력 스냅샷 계약 버전. TaskMetricsSnapshotSource와 같은 값을 본다. */
    private static final int CONTEXT_SNAPSHOT_VERSION = 2;
    /** 기간 종료 후 이 기간 안에 마감이면 임박으로 본다. */
    private static final int DUE_SOON_DAYS = 3;
    private static final int MAX_MEMBERS = 100;
    private static final int MAX_TASKS = 100;
    private static final int MAX_CALENDAR_CONSTRAINTS = 30;

    private final TaskReportDataQuery taskData;
    private final AiWeeklyReportEvidenceQuery evidence;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final AiWeeklyReportSafeLabelFactory labels;
    private final Clock clock;

    public AiWeeklyReportSnapshotAssembler(TaskReportDataQuery taskData,
            AiWeeklyReportEvidenceQuery evidence, GroupRepository groups,
            GroupMemberRepository members, AiWeeklyReportSafeLabelFactory labels, Clock clock) {
        this.taskData = taskData;
        this.evidence = evidence;
        this.groups = groups;
        this.members = members;
        this.labels = labels;
        this.clock = clock;
    }

    public AiWeeklyReportSnapshotV1 assemble(Long groupId, LocalDate from,
            LocalDate toExclusive, Language language, String promptVersion) {
        Group group = groups.findById(groupId).orElseThrow(() -> new ApplicationException(
                "GROUP_NOT_FOUND", HttpStatus.NOT_FOUND, "그룹을 찾을 수 없습니다."));
        ZoneId zone = ZoneId.of(group.getTimezone());
        requireCompletedPeriod(from, toExclusive, zone);

        TaskReportDataQuery.PeriodData currentPeriod =
                periodData(groupId, from, toExclusive, zone);
        List<TaskSnapshot> current = snapshotsOf(currentPeriod);
        List<TaskSnapshot> previous = snapshotsOf(
                periodData(groupId, previousFrom(from, toExclusive), from, zone));
        List<ActivityEvent> events = currentPeriod.activityEvents();

        List<TaskSnapshot> tasks = current.stream()
                .sorted(Comparator.comparing(TaskSnapshot::taskId))
                .limit(MAX_TASKS)
                .toList();
        Map<Long, String> taskRefs = refs(tasks.stream().map(TaskSnapshot::taskId).toList(), "TASK");

        List<GroupMember> activeMembers = members
                .findAllByGroupIdAndStatusOrderByRoleAscJoinedAtAsc(groupId, GroupMember.Status.ACTIVE)
                .stream()
                .sorted(Comparator.comparing(GroupMember::getId))
                .limit(MAX_MEMBERS)
                .toList();
        Map<Long, String> memberRefs = refs(
                activeMembers.stream().map(GroupMember::getId).toList(), "MEMBER");

        List<CalendarWindow> windows = evidence.loadCalendarWindows(groupId,
                from.atStartOfDay(), toExclusive.plusDays(DUE_SOON_DAYS).atStartOfDay());
        List<CalendarWindow> constraints = windows.stream()
                .limit(MAX_CALENDAR_CONSTRAINTS).toList();
        Map<Long, String> eventRefs = refs(
                constraints.stream().map(CalendarWindow::eventId).toList(), "EVENT");

        Map<Long, TaskCollaborationCounts> collaboration = evidence.loadCollaborationCounts(
                tasks.stream().map(TaskSnapshot::taskId).toList());

        return new AiWeeklyReportSnapshotV1(
                AiWeeklyReportDtos.SNAPSHOT_SCHEMA_VERSION,
                new ReportContext("GROUP-" + groupId,
                        new SnapshotPeriod(from.toString(), toExclusive.toString(),
                                zone.getId()),
                        clock.instant().toString(), language, promptVersion),
                // 수치는 기간 전체(current)로 낸다. MAX_TASKS는 OpenAI에 보낼 배열 크기를 막는
                // 장치이지 집계 범위가 아니다. 잘린 목록으로 재면 101건인 기간이 100건으로
                // 보고되고, 직전 기간은 잘리지 않아 증감이 서로 다른 모수로 계산된다.
                metrics(current, toExclusive, zone),
                comparison(current, previous, from, toExclusive),
                workflow(current),
                memberViews(activeMembers, memberRefs, current, constraints, toExclusive, zone),
                taskViews(tasks, taskRefs, memberRefs, eventRefs, constraints, collaboration,
                        events, language, toExclusive, zone),
                calendarViews(constraints, eventRefs, tasks, taskRefs, language),
                // 위험 후보 생성은 policy engine(M3) 범위다.
                List.of());
    }

    // ---------- 기간 ----------

    private void requireCompletedPeriod(LocalDate from, LocalDate toExclusive, ZoneId zone) {
        if (from == null || toExclusive == null || !from.isBefore(toExclusive)) {
            throw new ApplicationException("AI_REPORT_WEEK_INVALID", HttpStatus.BAD_REQUEST,
                    "기간이 올바르지 않습니다.");
        }
        if (!toExclusive.isAfter(LocalDate.now(clock.withZone(zone)))) return;
        throw new ApplicationException("AI_REPORT_WEEK_INCOMPLETE", HttpStatus.BAD_REQUEST,
                "완료된 기간만 AI 리포트를 생성할 수 있습니다.");
    }

    /**
     * 기간에 속한 업무를 taskId 기준으로 합친다.
     *
     * <p>활동 이력이 있는 업무는 latest 스냅샷이 정본이다. 기간 안에 이력이 전혀 없는 기존
     * 업무는 latest에 나타나지 않으므로 legacy 스냅샷으로 보완한다. 예전에는 둘 중 하나만
     * 골랐기 때문에, 일부 업무에만 이력이 있으면 나머지 업무가 통째로 사라졌다.
     */
    private List<TaskSnapshot> snapshotsOf(TaskReportDataQuery.PeriodData data) {
        Map<Long, TaskSnapshot> merged = new LinkedHashMap<>();
        for (TaskSnapshot snapshot : data.latestSnapshots()) {
            merged.putIfAbsent(snapshot.taskId(), snapshot);
        }
        for (TaskSnapshot snapshot : data.legacySnapshots()) {
            merged.putIfAbsent(snapshot.taskId(), snapshot);
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(TaskSnapshot::taskId))
                .toList();
    }

    private TaskReportDataQuery.PeriodData periodData(Long groupId, LocalDate from,
            LocalDate toExclusive, ZoneId zone) {
        return taskData.loadPeriod(groupId,
                from.atStartOfDay(zone).toInstant(),
                toExclusive.atStartOfDay(zone).toInstant(),
                from.atStartOfDay(),
                toExclusive.atStartOfDay(),
                CONTEXT_SNAPSHOT_VERSION);
    }

    // ---------- 지표 ----------

    private SnapshotMetrics metrics(List<TaskSnapshot> tasks, LocalDate toExclusive, ZoneId zone) {
        int total = tasks.size();
        long completed = tasks.stream().filter(this::isCompleted).count();
        long onTime = tasks.stream().filter(task -> dueState(task, toExclusive, zone)
                == DueState.COMPLETED_ON_TIME).count();
        long delayed = tasks.stream().filter(task -> dueState(task, toExclusive, zone)
                == DueState.OVERDUE).count();
        List<Long> hours = tasks.stream()
                .filter(task -> task.completedAt() != null && task.taskCreatedAt() != null)
                .map(task -> Duration.between(task.taskCreatedAt(), task.completedAt()).toHours())
                .filter(value -> value >= 0)
                .toList();
        return new SnapshotMetrics(total,
                percent(completed, total),
                completed == 0 ? null : percent(onTime, completed),
                Math.toIntExact(delayed),
                hours.isEmpty() ? null
                        : Math.toIntExact(Math.round(
                                hours.stream().mapToLong(Long::longValue).average().orElse(0))));
    }

    /**
     * 직전 비교 기간은 선택 기간과 같은 길이로 바로 앞에 붙인다. 기간 길이가 7일로 고정돼
     * 있지 않으므로 월간·연간·잘린 마지막 주차도 자기 길이만큼 거슬러 올라간다.
     */
    private LocalDate previousFrom(LocalDate from, LocalDate toExclusive) {
        return from.minusDays(ChronoUnit.DAYS.between(from, toExclusive));
    }

    /** 이전 기간에 업무가 하나도 없으면 비교 자체가 성립하지 않는다. */
    private SnapshotComparison comparison(List<TaskSnapshot> current, List<TaskSnapshot> previous,
            LocalDate from, LocalDate toExclusive) {
        if (previous.isEmpty()) return SnapshotComparison.noBaseline();
        LocalDate previousFrom = previousFrom(from, toExclusive);
        SnapshotMetrics now = metrics(current, toExclusive, ZoneOffset.UTC);
        SnapshotMetrics before = metrics(previous, from, ZoneOffset.UTC);
        return new SnapshotComparison(ComparisonStatus.AVAILABLE,
                previousFrom.toString(), from.toString(),
                now.periodTaskCount() - before.periodTaskCount(),
                delta(now.completionRatePercent(), before.completionRatePercent()),
                delta(now.onTimeRatePercent(), before.onTimeRatePercent()),
                now.delayedCount() - before.delayedCount());
    }

    private SnapshotWorkflow workflow(List<TaskSnapshot> tasks) {
        return new SnapshotWorkflow(
                count(tasks, TaskReportDataQuery.Status.REQUESTED, null),
                count(tasks, TaskReportDataQuery.Status.TODO, Boolean.FALSE),
                count(tasks, TaskReportDataQuery.Status.TODO, Boolean.TRUE),
                count(tasks, TaskReportDataQuery.Status.IN_PROGRESS, null),
                count(tasks, TaskReportDataQuery.Status.ON_HOLD, null),
                count(tasks, TaskReportDataQuery.Status.COMPLETED, null));
    }

    private int count(List<TaskSnapshot> tasks, TaskReportDataQuery.Status status,
            Boolean assigned) {
        return Math.toIntExact(tasks.stream()
                .filter(task -> task.status() == status)
                .filter(task -> assigned == null
                        || assigned == (task.assigneeMemberId() != null))
                .count());
    }

    // ---------- 팀원 ----------

    private List<SnapshotMember> memberViews(List<GroupMember> activeMembers,
            Map<Long, String> memberRefs, List<TaskSnapshot> tasks,
            List<CalendarWindow> windows, LocalDate toExclusive, ZoneId zone) {
        LocalDateTime upcomingFrom = toExclusive.atStartOfDay();
        return activeMembers.stream().map(member -> {
            List<TaskSnapshot> owned = tasks.stream()
                    .filter(task -> member.getId().equals(task.assigneeMemberId())).toList();
            long completed = owned.stream().filter(this::isCompleted).count();
            long onTime = owned.stream().filter(task -> dueState(task, toExclusive, zone)
                    == DueState.COMPLETED_ON_TIME).count();
            long delayed = owned.stream().filter(task -> dueState(task, toExclusive, zone)
                    == DueState.OVERDUE).count();
            long active = owned.stream().filter(task ->
                    task.status() == TaskReportDataQuery.Status.IN_PROGRESS
                            || task.status() == TaskReportDataQuery.Status.ON_HOLD).count();
            long upcoming = windows.stream()
                    .filter(window -> member.getId().equals(window.ownerMemberId()))
                    .filter(window -> !window.startAtUtc().isBefore(upcomingFrom)).count();
            return new SnapshotMember(memberRefs.get(member.getId()),
                    member.getRole().name(),
                    owned.size(), Math.toIntExact(active), Math.toIntExact(completed),
                    Math.toIntExact(delayed),
                    completed == 0 ? null : percent(onTime, completed),
                    Math.toIntExact(upcoming));
        }).toList();
    }

    // ---------- 업무 ----------

    private List<SnapshotTask> taskViews(List<TaskSnapshot> tasks, Map<Long, String> taskRefs,
            Map<Long, String> memberRefs, Map<Long, String> eventRefs,
            List<CalendarWindow> windows, Map<Long, TaskCollaborationCounts> collaboration,
            List<ActivityEvent> events, Language language, LocalDate toExclusive, ZoneId zone) {
        return tasks.stream().map(task -> {
            TaskCollaborationCounts counts = collaboration.getOrDefault(
                    task.taskId(), TaskCollaborationCounts.NONE);
            DueState due = dueState(task, toExclusive, zone);
            HoldReasonCategory hold = holdReason(task);
            String label = labels.taskLabel(new TaskLabelFacts(
                    status(task), due, task.assigneeMemberId() != null,
                    task.checklistCompleted(), task.checklistTotal(),
                    counts.unresolvedMentionCount(), hold), language);
            return new SnapshotTask(
                    taskRefs.get(task.taskId()), label, status(task),
                    task.priority() == null ? null : task.priority().name(),
                    memberRefs.get(task.assigneeMemberId()),
                    iso(task.taskCreatedAt()), iso(task.dueAt()), iso(task.completedAt()), due,
                    new TaskChecklist(task.checklistCompleted(), task.checklistTotal()),
                    new TaskCollaboration(counts.commentCount(),
                            counts.unresolvedMentionCount(), counts.resourceLinkCount()),
                    new TaskHistory(lastTransition(task, events), hold, reopened(task, events)),
                    relatedEvents(task, windows, eventRefs),
                    // 신호와 허용 코드 산출은 policy engine(M3) 범위다.
                    List.of(), List.of(), List.of(), List.of());
        }).toList();
    }

    private List<CalendarConstraint> calendarViews(List<CalendarWindow> windows,
            Map<Long, String> eventRefs, List<TaskSnapshot> tasks, Map<Long, String> taskRefs,
            Language language) {
        return windows.stream().map(window -> new CalendarConstraint(
                eventRefs.get(window.eventId()),
                window.eventType() == null ? "OTHER" : window.eventType(),
                labels.eventLabel(window.eventType(), window.allDay(), language),
                iso(window.startAtUtc()), iso(window.endAtUtc()),
                tasks.stream()
                        .filter(task -> overlaps(task, window))
                        .map(task -> taskRefs.get(task.taskId()))
                        .toList())).toList();
    }

    /** 업무 마감이 일정 구간 안에 들어오면 그 일정이 마감에 영향을 준다고 본다. */
    private boolean overlaps(TaskSnapshot task, CalendarWindow window) {
        LocalDateTime due = task.dueAt();
        return due != null && !due.isBefore(window.startAtUtc()) && !due.isAfter(window.endAtUtc());
    }

    private List<String> relatedEvents(TaskSnapshot task, List<CalendarWindow> windows,
            Map<Long, String> eventRefs) {
        return windows.stream().filter(window -> overlaps(task, window))
                .map(window -> eventRefs.get(window.eventId())).toList();
    }

    // ---------- 파생값 ----------

    private TaskStatus status(TaskSnapshot task) {
        return TaskStatus.valueOf(task.status().name());
    }

    private boolean isCompleted(TaskSnapshot task) {
        return task.status() == TaskReportDataQuery.Status.COMPLETED;
    }

    private DueState dueState(TaskSnapshot task, LocalDate toExclusive, ZoneId zone) {
        LocalDateTime due = task.dueAt();
        if (task.completedAt() != null) {
            if (due == null) return DueState.COMPLETED_ON_TIME;
            return task.completedAt().isAfter(due)
                    ? DueState.COMPLETED_LATE : DueState.COMPLETED_ON_TIME;
        }
        if (due == null) return DueState.NO_DUE;
        LocalDateTime boundary = toExclusive.atStartOfDay();
        if (due.isBefore(boundary)) return DueState.OVERDUE;
        return due.isBefore(boundary.plusDays(DUE_SOON_DAYS))
                ? DueState.DUE_SOON : DueState.UPCOMING;
    }

    /** 자유 입력 사유는 보지 않는다. 구조화 blocker type만 category로 옮긴다. */
    private HoldReasonCategory holdReason(TaskSnapshot task) {
        if (task.blockerType() == null) {
            return task.status() == TaskReportDataQuery.Status.ON_HOLD
                    ? HoldReasonCategory.UNKNOWN : HoldReasonCategory.NONE;
        }
        return switch (task.blockerType()) {
            case EXTERNAL -> HoldReasonCategory.EXTERNAL_FEEDBACK;
            case DEPENDENCY -> HoldReasonCategory.DEPENDENCY;
            case RESOURCE -> HoldReasonCategory.RESOURCE_SHORTAGE;
            case DECISION, ACCESS, TECHNICAL, OTHER -> HoldReasonCategory.OTHER;
        };
    }

    private String lastTransition(TaskSnapshot task, List<ActivityEvent> events) {
        return events.stream()
                .filter(event -> task.taskId().equals(event.taskId()))
                .reduce((first, second) -> second)
                .map(event -> event.eventType().name())
                .orElse(null);
    }

    /** 완료 이후 다시 완료가 아닌 상태로 돌아간 횟수. */
    private int reopened(TaskSnapshot task, List<ActivityEvent> events) {
        int count = 0;
        boolean wasCompleted = false;
        for (ActivityEvent event : events) {
            if (!task.taskId().equals(event.taskId())) continue;
            boolean completed = event.taskStatus() == TaskReportDataQuery.Status.COMPLETED;
            if (wasCompleted && !completed) count++;
            wasCompleted = completed;
        }
        return count;
    }

    // ---------- 유틸 ----------

    /**
     * ref는 엔티티 식별자를 그대로 담는다. 순번을 매기면 저장된 Snapshot만으로는 어떤 업무를
     * 가리키는지 되찾을 수 없어, 표시 단계에서 실제 제목·이름을 다시 붙일 수 없다.
     * 식별자는 원문(제목·댓글·실명)이 아니므로 개인정보 경계를 넘지 않는다.
     */
    private Map<Long, String> refs(List<Long> ids, String prefix) {
        Map<Long, String> refs = new LinkedHashMap<>();
        List<Long> sorted = new ArrayList<>(ids);
        sorted.sort(Comparator.nullsLast(Comparator.naturalOrder()));
        for (Long id : sorted) {
            if (id != null) refs.putIfAbsent(id, prefix + "-" + id);
        }
        return refs;
    }

    private Integer percent(long numerator, long denominator) {
        return denominator == 0 ? null : Math.toIntExact(Math.round(
                numerator * 100.0 / denominator));
    }

    private Integer delta(Integer now, Integer before) {
        return now == null || before == null ? null : now - before;
    }

    private String iso(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }
}
