package com.teamproject.task.domain;

import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
public class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_member_id", nullable = false)
    private GroupMember requester;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_member_id")
    private GroupMember approver;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_member_id")
    private GroupMember assignee;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Priority priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    private LocalDateTime startAt;
    private LocalDateTime dueAt;
    private LocalDateTime completedAt;
    @Column(length = 500)
    private String holdReason;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private BlockerType blockerType;
    @Enumerated(EnumType.STRING)
    @Column(name = "blocker_next_action_type", length = 30)
    private BlockerNextActionType blockerNextActionType;
    private LocalDate blockerReviewDate;
    @Column(length = 500)
    private String stopReason;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Version
    private long version;

    protected Task() {}

    public Task(Group group, GroupMember requester, String title, String description,
            Priority priority, LocalDateTime dueAt) {
        this.group = group;
        this.requester = requester;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.dueAt = dueAt;
        this.status = group.getType() == Group.Type.PERSONAL ? Status.TODO : Status.REQUESTED;
        this.assignee = group.getType() == Group.Type.PERSONAL ? requester : null;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

    public boolean isDelayed(LocalDateTime now) {
        return dueAt != null && dueAt.isBefore(now)
                && status != Status.COMPLETED && status != Status.REJECTED && status != Status.CANCELLED;
    }

    public void accept(GroupMember approver) {
        this.approver = approver;
        changeStatus(Status.TODO);
    }

    public void reject(GroupMember approver, String reason) {
        this.approver = approver;
        this.stopReason = reason;
        changeStatus(Status.REJECTED);
    }

    public void assign(GroupMember assignee) {
        this.assignee = assignee;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateDetails(String title, String description, Priority priority, LocalDateTime dueAt) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.dueAt = dueAt;
        this.updatedAt = LocalDateTime.now();
    }

    public void start() {
        if (startAt == null) startAt = LocalDateTime.now();
        changeStatus(Status.IN_PROGRESS);
    }

    public void hold(String reason, BlockerType blockerType,
            BlockerNextActionType nextActionType, LocalDate reviewDate) {
        this.holdReason = reason;
        this.blockerType = blockerType;
        this.blockerNextActionType = nextActionType;
        this.blockerReviewDate = reviewDate;
        changeStatus(Status.ON_HOLD);
    }

    public void resume() {
        clearBlocker();
        changeStatus(Status.IN_PROGRESS);
    }

    public void complete() {
        this.completedAt = LocalDateTime.now();
        changeStatus(Status.COMPLETED);
    }

    public void reopen() {
        this.completedAt = null;
        changeStatus(Status.IN_PROGRESS);
    }

    public void cancel(String reason) {
        this.stopReason = reason;
        changeStatus(Status.CANCELLED);
    }

    private void clearBlocker() {
        this.holdReason = null;
        this.blockerType = null;
        this.blockerNextActionType = null;
        this.blockerReviewDate = null;
    }

    private void changeStatus(Status status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public GroupMember getRequester() { return requester; }
    public GroupMember getApprover() { return approver; }
    public GroupMember getAssignee() { return assignee; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Priority getPriority() { return priority; }
    public Status getStatus() { return status; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getDueAt() { return dueAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public String getHoldReason() { return holdReason; }
    public BlockerType getBlockerType() { return blockerType; }
    public BlockerNextActionType getBlockerNextActionType() { return blockerNextActionType; }
    public LocalDate getBlockerReviewDate() { return blockerReviewDate; }
    public String getStopReason() { return stopReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public enum Priority { LOW, NORMAL, HIGH, URGENT }
    public enum Status { REQUESTED, TODO, IN_PROGRESS, ON_HOLD, COMPLETED, REJECTED, CANCELLED }
    public enum BlockerType {
        DEPENDENCY, DECISION, ACCESS, RESOURCE, TECHNICAL, EXTERNAL, OTHER
    }
    public enum BlockerNextActionType {
        FOLLOW_UP, ESCALATE, DECIDE, UNBLOCK_ACCESS, REPLAN, WAIT_EXTERNAL, OTHER
    }
}
