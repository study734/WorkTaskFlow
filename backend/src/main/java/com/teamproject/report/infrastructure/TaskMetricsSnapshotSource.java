package com.teamproject.report.infrastructure;

import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.report.application.MetricsSnapshotSource;
import com.teamproject.report.application.ReportContracts.AiReportContext;
import com.teamproject.report.application.ReportContracts.ChecklistMetrics;
import com.teamproject.report.application.ReportContracts.ComparisonMetrics;
import com.teamproject.report.application.ReportContracts.DailyMetric;
import com.teamproject.report.application.ReportContracts.EvidenceValue;
import com.teamproject.report.application.ReportContracts.HistoryCoverage;
import com.teamproject.report.application.ReportContracts.HistoryCoverageStatus;
import com.teamproject.report.application.ReportContracts.LocalReference;
import com.teamproject.report.application.ReportContracts.MemberMetric;
import com.teamproject.report.application.ReportContracts.MetricsSnapshot;
import com.teamproject.report.application.ReportContracts.ObjectiveContext;
import com.teamproject.report.application.ReportContracts.ReferenceIndex;
import com.teamproject.report.application.ReportContracts.ReportSnapshot;
import com.teamproject.report.application.ReportContracts.RiskSignal;
import com.teamproject.report.application.ReportContracts.StatusMetrics;
import com.teamproject.report.application.ReportContracts.TaskContext;
import com.teamproject.report.application.ReportPeriod;
import com.teamproject.task.application.TaskReportDataQuery;
import com.teamproject.task.application.TaskReportDataQuery.ActivityEvent;
import com.teamproject.task.application.TaskReportDataQuery.BlockerNextActionType;
import com.teamproject.task.application.TaskReportDataQuery.BlockerType;
import com.teamproject.task.application.TaskReportDataQuery.EventType;
import com.teamproject.task.application.TaskReportDataQuery.ObjectiveReference;
import com.teamproject.task.application.TaskReportDataQuery.Priority;
import com.teamproject.task.application.TaskReportDataQuery.Status;
import com.teamproject.task.application.TaskReportDataQuery.TaskReference;
import com.teamproject.task.application.TaskReportDataQuery.TaskSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 활동 이벤트를 frozen evidence로 변환하되, 실제 제목·구성원 이름을 담는 reference index는
 * 로컬 렌더링 경계에만 둔다.
 */
@Component
public class TaskMetricsSnapshotSource implements MetricsSnapshotSource {
    private static final int CONTEXT_SNAPSHOT_VERSION = 2;
    private static final Set<Status> TERMINAL =
            Set.of(Status.COMPLETED, Status.REJECTED, Status.CANCELLED);

    private final TaskReportDataQuery taskData;
    private final GroupMemberRepository members;

    public TaskMetricsSnapshotSource(TaskReportDataQuery taskData,
            GroupMemberRepository members) {
        this.taskData = taskData;
        this.members = members;
    }

    @Override
    @Transactional(readOnly = true)
    public ReportSnapshot capture(Long groupId, ReportPeriod period) {
        List<GroupMember> activeMembers =
                members.findAllByGroupIdAndStatusOrderByRoleAscJoinedAtAsc(
                        groupId, GroupMember.Status.ACTIVE);
        List<Long> activeMemberIds = activeMembers.stream()
                .map(GroupMember::getId).sorted().toList();
        PeriodData current = includeMembers(
                periodData(groupId, period), period, activeMemberIds);
        PeriodData previous = includeMembers(
                periodData(groupId, period.previous()), period.previous(), activeMemberIds);
        ComparisonMetrics comparison = compare(current.metrics(), previous.metrics());

        Map<Long, String> taskAliases = aliases(
                current.snapshots().stream().map(ActivitySnapshot::taskId).toList(), "TASK");
        Map<Long, String> memberAliases = aliases(activeMemberIds, "MEMBER");
        Map<Long, String> objectiveAliases = aliases(current.snapshots().stream()
                .map(ActivitySnapshot::weeklyObjectiveId).filter(value -> value != null).toList(),
                "GOAL");

        List<TaskContext> taskContexts = current.snapshots().stream()
                .sorted(Comparator.comparing(ActivitySnapshot::taskId))
                .map(snapshot -> taskContext(snapshot, current.activityEvents(), period,
                        taskAliases, memberAliases, objectiveAliases))
                .toList();
        List<ObjectiveContext> objectiveContexts =
                objectiveContexts(current.snapshots(), period, objectiveAliases);
        Map<String, EvidenceValue> evidence =
                evidenceValues(current.metrics(), comparison, current.snapshots(),
                        taskAliases, objectiveContexts);
        AiReportContext aiContext = new AiReportContext(current.metrics(), comparison,
                taskContexts, objectiveContexts, evidence.keySet());
        ReferenceIndex referenceIndex = references(
                groupId, period, current.snapshots(), taskAliases, memberAliases,
                objectiveAliases, activeMembers);
        return new ReportSnapshot(current.metrics(), comparison, aiContext,
                referenceIndex, evidence);
    }

    private PeriodData periodData(Long groupId, ReportPeriod period) {
        TaskReportDataQuery.PeriodData data = taskData.loadPeriod(
                groupId,
                period.fromInclusive(),
                period.toExclusive(),
                period.start().atStartOfDay(),
                period.end().plusDays(1).atStartOfDay(),
                CONTEXT_SNAPSHOT_VERSION);
        Instant trackingStartedAt = data.trackingStartedAt();
        List<ActivityEvent> activity = data.activityEvents();
        if (activity.isEmpty()) {
            boolean trackedPeriod = trackingStartedAt != null
                    && !period.fromInclusive().isBefore(trackingStartedAt);
            if (trackedPeriod) {
                HistoryCoverage coverage =
                        new HistoryCoverage(HistoryCoverageStatus.COMPLETE, trackingStartedAt);
                return new PeriodData(calculate(period, List.of(), coverage),
                        List.of(), List.of());
            }
            List<ActivitySnapshot> legacy = data.legacySnapshots().stream()
                    .map(ActivitySnapshot::from)
                    .toList();
            HistoryCoverage coverage =
                    new HistoryCoverage(HistoryCoverageStatus.PARTIAL, trackingStartedAt);
            return new PeriodData(calculate(period, legacy, coverage), legacy, List.of());
        }

        List<ActivitySnapshot> snapshots = data.latestSnapshots().stream()
                .map(ActivitySnapshot::from)
                .toList();
        boolean partial = snapshots.stream().anyMatch(snapshot ->
                !snapshot.historyComplete()
                        || snapshot.snapshotVersion() < CONTEXT_SNAPSHOT_VERSION)
                || trackingStartedAt == null
                || period.fromInclusive().isBefore(trackingStartedAt);
        HistoryCoverage coverage = new HistoryCoverage(partial
                ? HistoryCoverageStatus.PARTIAL
                : HistoryCoverageStatus.COMPLETE, trackingStartedAt);
        return new PeriodData(calculate(period, snapshots, coverage), snapshots, activity);
    }

    private MetricsSnapshot calculate(ReportPeriod period,
            List<ActivitySnapshot> values, HistoryCoverage coverage) {
        LocalDateTime overdueAt = period.end().plusDays(1).atStartOfDay().minusNanos(1);
        long delayed = values.stream().filter(value -> value.delayedAt(overdueAt)).count();
        StatusMetrics statuses = new StatusMetrics(
                count(values, Status.REQUESTED),
                count(values, Status.TODO),
                count(values, Status.IN_PROGRESS),
                count(values, Status.ON_HOLD),
                count(values, Status.COMPLETED),
                count(values, Status.REJECTED),
                count(values, Status.CANCELLED),
                delayed);
        long completed = statuses.completed();
        Integer completionRate = values.isEmpty() ? null : percent(completed, values.size());
        List<ActivitySnapshot> completedWithDue = values.stream()
                .filter(value -> value.status() == Status.COMPLETED)
                .filter(value -> value.dueAt() != null && value.completedAt() != null)
                .toList();
        long onTime = completedWithDue.stream()
                .filter(value -> !value.completedAt().isAfter(value.dueAt()))
                .count();
        Integer onTimeRate = completedWithDue.isEmpty()
                ? null : percent(onTime, completedWithDue.size());
        List<ActivitySnapshot> completedWithTimes = values.stream()
                .filter(value -> value.completedAt() != null)
                .toList();
        Long averageHours = completedWithTimes.isEmpty() ? null
                : Math.round(completedWithTimes.stream()
                        .mapToLong(value -> Duration.between(
                                value.taskCreatedAt(), value.completedAt()).toMinutes())
                        .average().orElse(0) / 60.0);

        List<DailyMetric> daily = period.start().datesUntil(period.end().plusDays(1))
                .map(date -> new DailyMetric(date,
                        values.stream().filter(value -> sameDate(value.taskCreatedAt(), date)).count(),
                        values.stream().filter(value -> sameDate(value.completedAt(), date)).count()))
                .toList();
        Map<Long, List<ActivitySnapshot>> byAssignee = values.stream()
                .filter(value -> value.assigneeMemberId() != null)
                .collect(Collectors.groupingBy(ActivitySnapshot::assigneeMemberId));
        List<MemberMetric> memberMetrics = memberMetrics(byAssignee, overdueAt);

        long checklistTotal = values.stream().mapToLong(ActivitySnapshot::checklistTotal).sum();
        long checklistCompleted = values.stream().mapToLong(ActivitySnapshot::checklistCompleted).sum();
        ChecklistMetrics checklist = new ChecklistMetrics(checklistTotal, checklistCompleted,
                checklistTotal == 0 ? null : percent(checklistCompleted, checklistTotal));
        int highPriority = (int) values.stream()
                .filter(value -> value.priority() == Priority.HIGH
                        || value.priority() == Priority.URGENT)
                .count();
        Map<String, Integer> metricEvidence = metricEvidence(values.size(), statuses, delayed,
                highPriority, completionRate, onTimeRate, checklist, coverage);
        List<RiskSignal> risks = risks(statuses, delayed, highPriority);
        return new MetricsSnapshot(period.start(), period.end(), values.size(), statuses,
                completionRate, onTimeRate, averageHours, coverage, checklist,
                daily, memberMetrics, risks, metricEvidence);
    }

    private PeriodData includeMembers(
            PeriodData data, ReportPeriod period, List<Long> memberIds) {
        LocalDateTime overdueAt =
                period.end().plusDays(1).atStartOfDay().minusNanos(1);
        Map<Long, List<ActivitySnapshot>> byAssignee = data.snapshots().stream()
                .filter(value -> value.assigneeMemberId() != null)
                .collect(Collectors.groupingBy(ActivitySnapshot::assigneeMemberId));
        MetricsSnapshot value = data.metrics();
        MetricsSnapshot updated = new MetricsSnapshot(
                value.periodStart(), value.periodEnd(), value.totalTasks(), value.statuses(),
                value.completionRatePercent(), value.onTimeRatePercent(),
                value.averageCompletionHours(), value.historyCoverage(), value.checklist(),
                value.daily(), memberMetrics(memberIds, byAssignee, overdueAt),
                value.riskSignals(), value.evidence());
        return new PeriodData(updated, data.snapshots(), data.activityEvents());
    }

    private ComparisonMetrics compare(MetricsSnapshot current, MetricsSnapshot previous) {
        boolean available = previous.totalTasks() > 0 || !previous.historyCoverage().partial();
        if (!available) {
            // BASELINE은 변화량 0이 아니라 비교할 이전 이력이 충분하지 않다는 뜻이다.
            return new ComparisonMetrics(false, null, null, null, null, null, null);
        }
        return new ComparisonMetrics(true,
                Math.toIntExact(current.totalTasks() - previous.totalTasks()),
                Math.toIntExact(current.statuses().completed() - previous.statuses().completed()),
                Math.toIntExact(current.statuses().delayed() - previous.statuses().delayed()),
                Math.toIntExact(current.statuses().onHold() - previous.statuses().onHold()),
                delta(current.completionRatePercent(), previous.completionRatePercent()),
                delta(current.checklist().completionRatePercent(),
                        previous.checklist().completionRatePercent()));
    }

    private TaskContext taskContext(ActivitySnapshot snapshot,
            List<ActivityEvent> activityEvents, ReportPeriod period,
            Map<Long, String> taskAliases, Map<Long, String> memberAliases,
            Map<Long, String> objectiveAliases) {
        List<ActivityEvent> taskEvents = activityEvents.stream()
                .filter(event -> event.taskId().equals(snapshot.taskId()))
                .toList();
        return new TaskContext(taskAliases.get(snapshot.taskId()),
                snapshot.status().name(), snapshot.priority().name(),
                dueState(snapshot, period),
                snapshot.checklistTotal(), snapshot.checklistCompleted(),
                name(snapshot.blockerType()), name(snapshot.blockerNextActionType()),
                blockerReviewWindow(snapshot.blockerReviewDate(), period),
                objectiveAliases.get(snapshot.weeklyObjectiveId()),
                memberAliases.get(snapshot.assigneeMemberId()),
                changes(taskEvents));
    }

    private List<String> changes(List<ActivityEvent> taskEvents) {
        LinkedHashSet<String> changes = new LinkedHashSet<>();
        Status previousStatus = null;
        for (ActivityEvent event : taskEvents) {
            switch (event.eventType()) {
                case TASK_CREATED -> changes.add("CREATED");
                case DETAILS_CHANGED -> changes.add("DETAILS_CHANGED");
                case ASSIGNEE_CHANGED -> changes.add("ASSIGNEE_CHANGED");
                case CHECKLIST_CHANGED -> changes.add("CHECKLIST_PROGRESS");
                case BLOCKER_CHANGED -> changes.add("BLOCKER_CHANGED");
                case OBJECTIVE_CHANGED -> changes.add("OBJECTIVE_CHANGED");
                default -> { }
            }
            Status current = event.taskStatus();
            if (current == Status.ON_HOLD) changes.add("BLOCKED");
            if (current == Status.COMPLETED) changes.add("COMPLETED");
            if (previousStatus == Status.ON_HOLD && current == Status.IN_PROGRESS) {
                changes.add("RESUMED");
            }
            if (previousStatus == Status.COMPLETED && current == Status.IN_PROGRESS) {
                changes.add("REOPENED");
            }
            previousStatus = current;
        }
        return List.copyOf(changes);
    }

    private String dueState(ActivitySnapshot value, ReportPeriod period) {
        if (value.dueAt() == null) return "NONE";
        if (value.status() == Status.COMPLETED && value.completedAt() != null) {
            return value.completedAt().isAfter(value.dueAt())
                    ? "COMPLETED_LATE" : "COMPLETED_ON_TIME";
        }
        LocalDateTime end = period.end().plusDays(1).atStartOfDay();
        if (value.dueAt().isBefore(end) && !TERMINAL.contains(value.status())) return "OVERDUE";
        if (!value.dueAt().toLocalDate().isAfter(period.end())) return "DUE_WITHIN_WEEK";
        return "LATER";
    }

    private String blockerReviewWindow(LocalDate reviewDate, ReportPeriod period) {
        if (reviewDate == null) return null;
        if (!reviewDate.isAfter(period.end())) return "DUE_OR_OVERDUE";
        if (!reviewDate.isAfter(period.end().plusWeeks(1))) return "NEXT_WEEK";
        return "LATER";
    }

    private List<ObjectiveContext> objectiveContexts(List<ActivitySnapshot> snapshots,
            ReportPeriod period, Map<Long, String> objectiveAliases) {
        LocalDateTime overdueAt = period.end().plusDays(1).atStartOfDay().minusNanos(1);
        return snapshots.stream()
                .filter(value -> value.weeklyObjectiveId() != null)
                .collect(Collectors.groupingBy(ActivitySnapshot::weeklyObjectiveId))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ObjectiveContext(objectiveAliases.get(entry.getKey()),
                        entry.getValue().size(),
                        Math.toIntExact(count(entry.getValue(), Status.COMPLETED)),
                        Math.toIntExact(count(entry.getValue(), Status.ON_HOLD)),
                        Math.toIntExact(entry.getValue().stream()
                                .filter(value -> value.delayedAt(overdueAt)).count())))
                .toList();
    }

    private ReferenceIndex references(Long groupId, ReportPeriod period,
            List<ActivitySnapshot> snapshots, Map<Long, String> taskAliases,
            Map<Long, String> memberAliases, Map<Long, String> objectiveAliases,
            List<GroupMember> activeMembers) {
        Map<Long, TaskReference> taskById = taskData.taskReferences(taskAliases.keySet());
        Map<Long, ObjectiveReference> objectiveById =
                taskData.objectiveReferences(objectiveAliases.keySet());
        List<LocalReference> result = new ArrayList<>();
        taskAliases.forEach((taskId, alias) -> {
            TaskReference task = taskById.get(taskId);
            if (task != null) {
                result.add(new LocalReference(alias, "TASK", task.title(),
                        "/tasks/" + taskId, task.assigneeName()));
            }
        });
        objectiveAliases.forEach((objectiveId, alias) -> {
            ObjectiveReference objective = objectiveById.get(objectiveId);
            if (objective != null) {
                result.add(new LocalReference(alias, "OBJECTIVE", objective.title(),
                        "/groups/" + groupId + "/dashboard", null));
            }
        });
        Map<Long, String> memberNames = activeMembers.stream()
                .collect(Collectors.toMap(GroupMember::getId,
                        member -> member.getUser().getNickname()));
        taskById.values().forEach(task -> {
            if (task.assigneeMemberId() != null && task.assigneeName() != null) {
                memberNames.put(task.assigneeMemberId(), task.assigneeName());
            }
        });
        memberAliases.forEach((memberId, alias) -> result.add(new LocalReference(
                alias, "MEMBER", memberNames.getOrDefault(memberId, alias), null, null)));
        return new ReferenceIndex(List.copyOf(result));
    }

    private Map<String, EvidenceValue> evidenceValues(MetricsSnapshot metrics,
            ComparisonMetrics comparison, List<ActivitySnapshot> snapshots,
            Map<Long, String> taskAliases, List<ObjectiveContext> objectiveContexts) {
        Map<String, EvidenceValue> result = new LinkedHashMap<>();
        metrics.evidence().forEach((key, value) -> result.put(key,
                evidence(key, label(key), formatMetric(key, value), kind(key))));
        if (comparison.available()) {
            putDelta(result, "comparison.tasksTotalDelta", "지난주 대비 전체 업무",
                    comparison.totalTasksDelta());
            putDelta(result, "comparison.completedDelta", "지난주 대비 완료 업무",
                    comparison.completedTasksDelta());
            putDelta(result, "comparison.delayedDelta", "지난주 대비 지연 업무",
                    comparison.delayedTasksDelta());
            putDelta(result, "comparison.onHoldDelta", "지난주 대비 보류 업무",
                    comparison.onHoldTasksDelta());
            putPercentDelta(result, "comparison.completionRateDelta",
                    "지난주 대비 완료율", comparison.completionRateDeltaPercent());
            putPercentDelta(result, "comparison.checklistRateDelta",
                    "지난주 대비 체크리스트 완료율",
                    comparison.checklistCompletionRateDeltaPercent());
        }
        for (ActivitySnapshot snapshot : snapshots) {
            String alias = taskAliases.get(snapshot.taskId());
            if (snapshot.dueAt() != null) {
                String key = "task." + alias + ".dueDate";
                result.put(key, evidence(key, alias + " 마감일",
                        snapshot.dueAt().toLocalDate().toString(), "DATE"));
            }
            if (snapshot.blockerReviewDate() != null) {
                String key = "task." + alias + ".blockerReviewDate";
                result.put(key, evidence(key, alias + " 보류 확인일",
                        snapshot.blockerReviewDate().toString(), "DATE"));
            }
        }
        for (ObjectiveContext objective : objectiveContexts) {
            String prefix = "objective." + objective.objectiveRef();
            result.put(prefix + ".tasks", evidence(prefix + ".tasks",
                    objective.objectiveRef() + " 연결 업무",
                    Integer.toString(objective.taskTotal()), "COUNT"));
            result.put(prefix + ".completed", evidence(prefix + ".completed",
                    objective.objectiveRef() + " 완료 업무",
                    Integer.toString(objective.completed()), "COUNT"));
            result.put(prefix + ".onHold", evidence(prefix + ".onHold",
                    objective.objectiveRef() + " 보류 업무",
                    Integer.toString(objective.onHold()), "COUNT"));
        }
        return result;
    }

    private List<MemberMetric> memberMetrics(
            Map<Long, List<ActivitySnapshot>> byAssignee, LocalDateTime overdueAt) {
        return memberMetrics(byAssignee.keySet().stream().sorted().toList(),
                byAssignee, overdueAt);
    }

    private List<MemberMetric> memberMetrics(List<Long> memberIds,
            Map<Long, List<ActivitySnapshot>> byAssignee, LocalDateTime overdueAt) {
        List<MemberMetric> result = new ArrayList<>();
        for (int index = 0; index < memberIds.size(); index++) {
            List<ActivitySnapshot> assigned =
                    byAssignee.getOrDefault(memberIds.get(index), List.of());
            List<ActivitySnapshot> completedWithDue = assigned.stream()
                    .filter(value -> value.status() == Status.COMPLETED)
                    .filter(value -> value.dueAt() != null && value.completedAt() != null)
                    .toList();
            long memberOnTime = completedWithDue.stream()
                    .filter(value -> !value.completedAt().isAfter(value.dueAt()))
                    .count();
            result.add(new MemberMetric(String.format("MEMBER-%02d", index + 1), assigned.size(),
                    assigned.stream().filter(value -> !TERMINAL.contains(value.status())).count(),
                    count(assigned, Status.COMPLETED),
                    assigned.stream().filter(value -> value.delayedAt(overdueAt)).count(),
                    completedWithDue.isEmpty() ? null
                            : percent(memberOnTime, completedWithDue.size())));
        }
        return result;
    }

    private Map<String, Integer> metricEvidence(int total, StatusMetrics statuses, long delayed,
            int highPriority, Integer completionRate, Integer onTimeRate,
            ChecklistMetrics checklist, HistoryCoverage coverage) {
        Map<String, Integer> evidence = new LinkedHashMap<>();
        evidence.put("tasks.total", total);
        evidence.put("tasks.completed", Math.toIntExact(statuses.completed()));
        evidence.put("tasks.active", Math.toIntExact(statuses.inProgress() + statuses.onHold()));
        evidence.put("tasks.onHold", Math.toIntExact(statuses.onHold()));
        evidence.put("tasks.delayed", Math.toIntExact(delayed));
        evidence.put("tasks.highPriority", highPriority);
        evidence.put("checklist.total", Math.toIntExact(checklist.total()));
        evidence.put("checklist.completed", Math.toIntExact(checklist.completed()));
        if (completionRate != null) evidence.put("rates.completion", completionRate);
        if (onTimeRate != null) evidence.put("rates.onTime", onTimeRate);
        if (checklist.completionRatePercent() != null) {
            evidence.put("rates.checklistCompletion", checklist.completionRatePercent());
        }
        if (coverage.partial()) evidence.put("coverage.partial", 1);
        return evidence;
    }

    private List<RiskSignal> risks(StatusMetrics statuses, long delayed, int highPriority) {
        List<RiskSignal> risks = new ArrayList<>();
        if (delayed > 0) risks.add(new RiskSignal(
                "OVERDUE_PRESENT", "HIGH", List.of("tasks.delayed")));
        if (statuses.onHold() > 0) risks.add(new RiskSignal(
                "ON_HOLD_PRESENT", "MEDIUM", List.of("tasks.onHold")));
        if (highPriority > 0) risks.add(new RiskSignal(
                "HIGH_PRIORITY_PRESENT", "MEDIUM", List.of("tasks.highPriority")));
        return risks;
    }

    private Map<Long, String> aliases(List<Long> values, String prefix) {
        List<Long> distinct = values.stream().distinct().sorted().toList();
        Map<Long, String> result = new LinkedHashMap<>();
        for (int index = 0; index < distinct.size(); index++) {
            result.put(distinct.get(index), String.format("%s-%02d", prefix, index + 1));
        }
        return result;
    }

    private EvidenceValue evidence(String key, String label, String value, String kind) {
        return new EvidenceValue(key, label, value, kind);
    }

    private void putDelta(Map<String, EvidenceValue> target,
            String key, String label, Integer value) {
        if (value != null) target.put(key, evidence(key, label, signed(value), "COUNT_DELTA"));
    }

    private void putPercentDelta(Map<String, EvidenceValue> target,
            String key, String label, Integer value) {
        if (value != null) target.put(key,
                evidence(key, label, signed(value) + "%p", "PERCENTAGE_POINT_DELTA"));
    }

    private String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private String formatMetric(String key, int value) {
        return key.startsWith("rates.") ? value + "%" : Integer.toString(value);
    }

    private String kind(String key) {
        if (key.startsWith("rates.")) return "PERCENT";
        if ("coverage.partial".equals(key)) return "FLAG";
        return "COUNT";
    }

    private String label(String key) {
        return switch (key) {
            case "tasks.total" -> "전체 업무";
            case "tasks.completed" -> "완료 업무";
            case "tasks.active" -> "진행·보류 업무";
            case "tasks.onHold" -> "보류 업무";
            case "tasks.delayed" -> "지연 업무";
            case "tasks.highPriority" -> "높은 우선순위 업무";
            case "checklist.total" -> "체크리스트 전체";
            case "checklist.completed" -> "체크리스트 완료";
            case "rates.completion" -> "업무 완료율";
            case "rates.onTime" -> "기한 준수율";
            case "rates.checklistCompletion" -> "체크리스트 완료율";
            case "coverage.partial" -> "부분 이력";
            default -> key;
        };
    }

    private int percent(long numerator, long denominator) {
        return (int) Math.round(numerator * 100.0 / denominator);
    }

    private Integer delta(Integer current, Integer previous) {
        return current == null || previous == null ? null : current - previous;
    }

    private long count(List<ActivitySnapshot> values, Status status) {
        return values.stream().filter(value -> value.status() == status).count();
    }

    private boolean inRange(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        return value != null && !value.isBefore(from) && value.isBefore(to);
    }

    private LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private boolean sameDate(LocalDateTime value, LocalDate date) {
        return value != null && value.toLocalDate().equals(date);
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private record PeriodData(
            MetricsSnapshot metrics,
            List<ActivitySnapshot> snapshots,
            List<ActivityEvent> activityEvents) {}

    private record ActivitySnapshot(
            Long taskId,
            Status status,
            Priority priority,
            Long assigneeMemberId,
            LocalDateTime taskCreatedAt,
            LocalDateTime dueAt,
            LocalDateTime completedAt,
            int checklistTotal,
            int checklistCompleted,
            BlockerType blockerType,
            BlockerNextActionType blockerNextActionType,
            LocalDate blockerReviewDate,
            Long weeklyObjectiveId,
            int snapshotVersion,
            boolean historyComplete) {

        static ActivitySnapshot from(TaskSnapshot value) {
            return new ActivitySnapshot(value.taskId(), value.status(), value.priority(),
                    value.assigneeMemberId(), value.taskCreatedAt(), value.dueAt(),
                    value.completedAt(), value.checklistTotal(), value.checklistCompleted(),
                    value.blockerType(), value.blockerNextActionType(),
                    value.blockerReviewDate(), value.weeklyObjectiveId(),
                    value.snapshotVersion(), value.historyComplete());
        }

        boolean delayedAt(LocalDateTime time) {
            return dueAt != null && dueAt.isBefore(time) && !TERMINAL.contains(status);
        }
    }
}
