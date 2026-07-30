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
    private final WeeklyObjectiveRepository objectives;

    public TaskReportDataQueryService(TaskActivityEventRepository events, TaskRepository tasks,
            WeeklyObjectiveRepository objectives) {
        this.events = events;
        this.tasks = tasks;
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
        List<TaskSnapshot> legacy = activity.isEmpty()
                ? legacySnapshots(groupId, legacyFrom, legacyTo)
                : List.of();
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
        return tasks.findAllByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .filter(task -> inRange(task.getCreatedAt(), from, to)
                        || inRange(task.getStartAt(), from, to)
                        || inRange(task.getDueAt(), from, to)
                        || inRange(task.getCompletedAt(), from, to)
                        || inRange(task.getUpdatedAt(), from, to))
                .map(this::snapshot)
                .toList();
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

    private TaskSnapshot snapshot(Task value) {
        return new TaskSnapshot(value.getId(),
                Status.valueOf(value.getStatus().name()),
                Priority.valueOf(value.getPriority().name()),
                value.getAssignee() == null ? null : value.getAssignee().getId(),
                value.getCreatedAt(),
                value.getDueAt(),
                value.getCompletedAt(),
                0,
                0,
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
