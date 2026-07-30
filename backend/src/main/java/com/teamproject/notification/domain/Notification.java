package com.teamproject.notification.domain;

import com.teamproject.comment.domain.TaskComment;
import com.teamproject.group.domain.Group;
import com.teamproject.task.domain.Task;
import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(
        name = "uk_notifications_recipient_event", columnNames = {"recipient_user_id", "event_key"}))
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private TaskComment comment;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private Type type;
    @Column(nullable = false, length = 160)
    private String eventKey;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 500)
    private String message;
    private LocalDateTime readAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {}

    public Notification(User recipient, User actor, Group group, Task task, TaskComment comment,
            Type type, String eventKey, String title, String message) {
        this.recipient = recipient;
        this.actor = actor;
        this.group = group;
        this.task = task;
        this.comment = comment;
        this.type = type;
        this.eventKey = eventKey;
        this.title = title;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public static Notification security(User recipient, Type type, String eventKey, String title, String message) {
        return new Notification(recipient, null, null, null, null, type, eventKey, title, message);
    }

    public void read() { if (readAt == null) readAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public User getRecipient() { return recipient; }
    public User getActor() { return actor; }
    public Group getGroup() { return group; }
    public Task getTask() { return task; }
    public TaskComment getComment() { return comment; }
    public Type getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public LocalDateTime getReadAt() { return readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public enum Type {
        TASK_REQUESTED, TASK_ASSIGNED, TASK_STATUS_CHANGED, TASK_DUE_SOON,
        COMMENT_CREATED, COMMENT_MENTIONED, SECURITY_NEW_DEVICE, SECURITY_SESSION_REUSED,
        SUBSCRIPTION_ROLLOUT_NOTICE
    }
}
