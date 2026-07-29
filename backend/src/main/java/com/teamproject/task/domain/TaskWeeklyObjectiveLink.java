package com.teamproject.task.domain;

import com.teamproject.group.domain.GroupMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_weekly_objective_links", uniqueConstraints = @UniqueConstraint(
        name = "uk_task_weekly_objective_task_week",
        columnNames = {"task_id", "week_start"}))
public class TaskWeeklyObjectiveLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "objective_id", nullable = false)
    private WeeklyObjective objective;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "linked_by_member_id", nullable = false)
    private GroupMember linkedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected TaskWeeklyObjectiveLink() {}

    public TaskWeeklyObjectiveLink(Task task, WeeklyObjective objective, GroupMember linkedBy,
            LocalDateTime now) {
        this.task = task;
        this.objective = objective;
        this.weekStart = objective.getWeekStart();
        this.linkedBy = linkedBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changeObjective(WeeklyObjective objective, GroupMember linkedBy, LocalDateTime now) {
        this.objective = objective;
        this.linkedBy = linkedBy;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Task getTask() { return task; }
    public WeeklyObjective getObjective() { return objective; }
    public LocalDate getWeekStart() { return weekStart; }
}
