package com.teamproject.task.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface TaskReportDataQuery {
    /**
     * 애플리케이션 시간 경계는 {@link Instant}로 유지한다. {@code legacyFrom}/{@code legacyTo}는
     * 활동 이력이 없는 기존 업무를 조회하는 JPA 호환 경계에서만 사용한다.
     */
    PeriodData loadPeriod(Long groupId, Instant fromInclusive, Instant toExclusive,
            LocalDateTime legacyFrom, LocalDateTime legacyTo, int contextSnapshotVersion);

    Map<Long, TaskReference> taskReferences(Collection<Long> taskIds);

    Map<Long, ObjectiveReference> objectiveReferences(Collection<Long> objectiveIds);

    record PeriodData(
            Instant trackingStartedAt,
            List<ActivityEvent> activityEvents,
            List<TaskSnapshot> latestSnapshots,
            List<TaskSnapshot> legacySnapshots) {}

    record ActivityEvent(
            Long taskId,
            EventType eventType,
            Status taskStatus,
            TaskSnapshot snapshot,
            Instant occurredAt) {}

    record TaskSnapshot(
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
            boolean historyComplete) {}

    record TaskReference(
            Long taskId,
            String title,
            Long assigneeMemberId,
            String assigneeName) {}

    record ObjectiveReference(Long objectiveId, String title) {}

    enum EventType {
        BASELINE,
        TASK_CREATED,
        DETAILS_CHANGED,
        ASSIGNEE_CHANGED,
        CHECKLIST_CHANGED,
        BLOCKER_CHANGED,
        OBJECTIVE_CHANGED,
        STATUS_CHANGED
    }

    enum Status {
        REQUESTED, TODO, IN_PROGRESS, ON_HOLD, COMPLETED, REJECTED, CANCELLED
    }

    enum Priority {
        LOW, NORMAL, HIGH, URGENT
    }

    enum BlockerType {
        DEPENDENCY, DECISION, ACCESS, RESOURCE, TECHNICAL, EXTERNAL, OTHER
    }

    enum BlockerNextActionType {
        FOLLOW_UP, ESCALATE, DECIDE, UNBLOCK_ACCESS, REPLAN, WAIT_EXTERNAL, OTHER
    }
}
