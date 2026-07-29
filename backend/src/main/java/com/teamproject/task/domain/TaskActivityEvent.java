package com.teamproject.task.domain;

import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 리포트를 과거 시점 그대로 재현하기 위한 비정규화 스냅샷이다. {@code historyComplete}는 해당
 * 이벤트 이전 이력까지 신뢰할 수 있는지 나타낸다.
 */
@Entity
@Table(name = "task_activity_events")
public class TaskActivityEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_member_id")
    private GroupMember actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private Type eventType;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_status", nullable = false, length = 20)
    private Task.Status taskStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_priority", nullable = false, length = 20)
    private Task.Priority taskPriority;

    @Column(name = "assignee_member_id")
    private Long assigneeMemberId;

    @Column(name = "task_created_at", nullable = false)
    private LocalDateTime taskCreatedAt;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "blocker_type", length = 30)
    private Task.BlockerType blockerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "blocker_next_action_type", length = 30)
    private Task.BlockerNextActionType blockerNextActionType;

    @Column(name = "blocker_review_date")
    private LocalDate blockerReviewDate;

    @Column(name = "weekly_objective_id")
    private Long weeklyObjectiveId;

    @Column(name = "checklist_total", nullable = false)
    private int checklistTotal;

    @Column(name = "checklist_completed", nullable = false)
    private int checklistCompleted;

    @Column(name = "snapshot_version", nullable = false)
    private int snapshotVersion;

    @Column(name = "history_complete", nullable = false)
    private boolean historyComplete;

    protected TaskActivityEvent() {}

    public TaskActivityEvent(Task task, GroupMember actor, Type eventType, Instant occurredAt,
            int checklistTotal, int checklistCompleted, boolean historyComplete) {
        this(task, actor, eventType, occurredAt, checklistTotal, checklistCompleted,
                historyComplete, null);
    }

    public TaskActivityEvent(Task task, GroupMember actor, Type eventType, Instant occurredAt,
            int checklistTotal, int checklistCompleted, boolean historyComplete,
            Long weeklyObjectiveId) {
        this.task = task;
        this.groupId = task.getGroup().getId();
        this.actor = actor;
        this.eventType = eventType;
        this.occurredAt = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
        this.taskStatus = task.getStatus();
        this.taskPriority = task.getPriority();
        this.assigneeMemberId = task.getAssignee() == null ? null : task.getAssignee().getId();
        this.taskCreatedAt = task.getCreatedAt();
        this.dueAt = task.getDueAt();
        this.completedAt = task.getCompletedAt();
        this.blockerType = task.getBlockerType();
        this.blockerNextActionType = task.getBlockerNextActionType();
        this.blockerReviewDate = task.getBlockerReviewDate();
        this.weeklyObjectiveId = weeklyObjectiveId;
        this.checklistTotal = checklistTotal;
        this.checklistCompleted = checklistCompleted;
        this.snapshotVersion = 2;
        this.historyComplete = historyComplete;
    }

    public Long getId() { return id; }
    public Task getTask() { return task; }
    public Long getGroupId() { return groupId; }
    public GroupMember getActor() { return actor; }
    public Type getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt.toInstant(ZoneOffset.UTC); }
    public Task.Status getTaskStatus() { return taskStatus; }
    public Task.Priority getTaskPriority() { return taskPriority; }
    public Long getAssigneeMemberId() { return assigneeMemberId; }
    public LocalDateTime getTaskCreatedAt() { return taskCreatedAt; }
    public LocalDateTime getDueAt() { return dueAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public Task.BlockerType getBlockerType() { return blockerType; }
    public Task.BlockerNextActionType getBlockerNextActionType() { return blockerNextActionType; }
    public LocalDate getBlockerReviewDate() { return blockerReviewDate; }
    public Long getWeeklyObjectiveId() { return weeklyObjectiveId; }
    public int getChecklistTotal() { return checklistTotal; }
    public int getChecklistCompleted() { return checklistCompleted; }
    public int getSnapshotVersion() { return snapshotVersion; }
    public boolean isHistoryComplete() { return historyComplete; }

    public enum Type {
        BASELINE,
        TASK_CREATED,
        STATUS_CHANGED,
        DETAILS_CHANGED,
        ASSIGNEE_CHANGED,
        CHECKLIST_CHANGED,
        BLOCKER_CHANGED,
        OBJECTIVE_CHANGED
    }
}
