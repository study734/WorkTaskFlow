package com.teamproject.task.application;

import com.teamproject.task.application.TaskReportDataQuery.ActivityEvent;
import com.teamproject.task.application.TaskReportDataQuery.BlockerNextActionType;
import com.teamproject.task.application.TaskReportDataQuery.BlockerType;
import com.teamproject.task.application.TaskReportDataQuery.EventType;
import com.teamproject.task.application.TaskReportDataQuery.ObjectiveReference;
import com.teamproject.task.application.TaskReportDataQuery.PeriodData;
import com.teamproject.task.application.TaskReportDataQuery.Priority;
import com.teamproject.task.application.TaskReportDataQuery.Status;
import com.teamproject.task.application.TaskReportDataQuery.TaskReference;
import com.teamproject.task.application.TaskReportDataQuery.TaskSnapshot;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskActivityEvent;
import com.teamproject.task.domain.TaskActivityEventRepository;
import com.teamproject.task.domain.TaskChecklistItemRepository;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.task.domain.WeeklyObjective;
import com.teamproject.task.domain.WeeklyObjectiveRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskReportDataQueryService implements TaskReportDataQuery {
    private final TaskActivityEventRepository events;
    private final TaskRepository tasks;
    private final TaskChecklistItemRepository checklistItems;
    private final WeeklyObjectiveRepository objectives;

    public TaskReportDataQueryService(TaskActivityEventRepository events, TaskRepository tasks,
            TaskChecklistItemRepository checklistItems, WeeklyObjectiveRepository objectives) {
        this.events = events;
        this.tasks = tasks;
        this.checklistItems = checklistItems;
        this.objectives = objectives;
    }

    @Override
    @Transactional(readOnly = true)
    public PeriodData loadPeriod(Long groupId, Instant fromInclusive, Instant toExclusive,
            LocalDateTime legacyFrom, LocalDateTime legacyTo, int contextSnapshotVersion) {
        Instant trackingStartedAt = events
                .findFirstByGroupIdAndSnapshotVersionOrderByOccurredAtAscIdAsc(
                        groupId, contextSnapshotVersion)
                .map(TaskActivityEvent::getOccurredAt)
                .orElse(null);
        List<ActivityEvent> periodEvents = events
                .findAllByGroupIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
                        groupId,
                        LocalDateTime.ofInstant(fromInclusive, ZoneOffset.UTC),
                        LocalDateTime.ofInstant(toExclusive, ZoneOffset.UTC))
                .stream()
                .map(this::event)
                .toList();
        List<ActivityEvent> activity = periodEvents.stream()
                .filter(value -> value.eventType() != EventType.BASELINE)
                .toList();
        // 이 한 번의 조회로 최신 스냅샷과 전체 이력을 모두 만든다. 이전에는 마지막 1건만 쓰고
        // 나머지를 버렸다 — 체류 시간 계산이 그 버려진 구간을 필요로 한다.
        List<TaskActivityEvent> history = taskHistory(activity, toExclusive);
        List<TaskSnapshot> latest = history.stream()
                .collect(Collectors.toMap(event -> event.getTask().getId(), Function.identity(),
                        (left, right) -> right, LinkedHashMap::new))
                .values().stream()
                .map(this::snapshot)
                .toList();
        // 활동 이력이 있든 없든 기간에 걸친 업무는 모두 보고해야 한다. 이력이 일부 업무에만
        // 있을 때 나머지 업무를 잃지 않도록 legacy 스냅샷은 항상 계산한다. 어느 쪽을 쓸지는
        // 호출자가 taskId 기준으로 합쳐 정한다.
        List<TaskSnapshot> legacy = legacySnapshots(groupId, legacyFrom, legacyTo);
        return new PeriodData(trackingStartedAt, activity, latest, legacy,
                history.stream().map(this::event).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, TaskReference> taskReferences(Collection<Long> taskIds) {
        return tasks.findAllById(taskIds).stream().collect(Collectors.toMap(
                Task::getId,
                task -> new TaskReference(
                        task.getId(),
                        task.getTitle(),
                        task.getAssignee() == null ? null : task.getAssignee().getId(),
                        task.getAssignee() == null ? null
                                : task.getAssignee().getUser().getNickname()),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ObjectiveReference> objectiveReferences(Collection<Long> objectiveIds) {
        return objectives.findAllById(objectiveIds).stream().collect(Collectors.toMap(
                WeeklyObjective::getId,
                objective -> new ObjectiveReference(objective.getId(), objective.getTitle()),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    private List<TaskActivityEvent> taskHistory(List<ActivityEvent> activity, Instant toExclusive) {
        if (activity.isEmpty()) return List.of();
        List<Long> taskIds = activity.stream().map(ActivityEvent::taskId).distinct().toList();
        return events.findAllByTaskIdInAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
                taskIds, LocalDateTime.ofInstant(toExclusive, ZoneOffset.UTC));
    }

    private List<TaskSnapshot> legacySnapshots(Long groupId, LocalDateTime from,
            LocalDateTime to) {
        List<Task> selected = tasks.findAllByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .filter(task -> inRange(task.getCreatedAt(), from, to)
                        || inRange(task.getStartAt(), from, to)
                        || inRange(task.getDueAt(), from, to)
                        || inRange(task.getCompletedAt(), from, to)
                        || inRange(task.getUpdatedAt(), from, to))
                .toList();
        // 활동 이력이 없는 업무다. 체크리스트는 이력이 아니라 현재 상태로도 셀 수 있고,
        // 세지 않으면 리포트가 "체크리스트 0/0"이라고 말한다. 이력 테이블이 생기기 전에
        // 만들어진 업무가 전부 이 경로를 타므로, 기존 팀은 협업 근거가 통째로 비어 보인다.
        Map<Long, int[]> counts = checklistCounts(selected.stream().map(Task::getId).toList());
        return selected.stream().map(task -> snapshot(task, counts)).toList();
    }

    private Map<Long, int[]> checklistCounts(List<Long> taskIds) {
        if (taskIds.isEmpty()) return Map.of();
        Map<Long, int[]> counts = new LinkedHashMap<>();
        for (Object[] row : checklistItems.countByTaskIds(taskIds)) {
            counts.put(((Number) row[0]).longValue(),
                    new int[] {((Number) row[1]).intValue(), ((Number) row[2]).intValue()});
        }
        return counts;
    }

    private ActivityEvent event(TaskActivityEvent value) {
        return new ActivityEvent(value.getTask().getId(),
                EventType.valueOf(value.getEventType().name()),
                Status.valueOf(value.getTaskStatus().name()),
                snapshot(value),
                value.getOccurredAt());
    }

    private TaskSnapshot snapshot(TaskActivityEvent value) {
        return new TaskSnapshot(value.getTask().getId(),
                Status.valueOf(value.getTaskStatus().name()),
                Priority.valueOf(value.getTaskPriority().name()),
                value.getAssigneeMemberId(),
                value.getTaskCreatedAt(),
                value.getDueAt(),
                value.getCompletedAt(),
                value.getChecklistTotal(),
                value.getChecklistCompleted(),
                name(value.getBlockerType(), BlockerType.class),
                name(value.getBlockerNextActionType(), BlockerNextActionType.class),
                value.getBlockerReviewDate(),
                value.getWeeklyObjectiveId(),
                value.getSnapshotVersion(),
                value.isHistoryComplete());
    }

    private TaskSnapshot snapshot(Task value, Map<Long, int[]> checklistCounts) {
        int[] checklist = checklistCounts.getOrDefault(value.getId(), new int[] {0, 0});
        return new TaskSnapshot(value.getId(),
                Status.valueOf(value.getStatus().name()),
                Priority.valueOf(value.getPriority().name()),
                value.getAssignee() == null ? null : value.getAssignee().getId(),
                value.getCreatedAt(),
                value.getDueAt(),
                value.getCompletedAt(),
                checklist[0],
                checklist[1],
                name(value.getBlockerType(), BlockerType.class),
                name(value.getBlockerNextActionType(), BlockerNextActionType.class),
                value.getBlockerReviewDate(),
                null,
                1,
                false);
    }

    private <T extends Enum<T>> T name(Enum<?> value, Class<T> type) {
        return value == null ? null : Enum.valueOf(type, value.name());
    }

    private boolean inRange(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        return value != null && !value.isBefore(from) && value.isBefore(to);
    }
}
