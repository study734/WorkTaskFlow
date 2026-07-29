package com.teamproject.task.domain;

import com.teamproject.group.domain.Group;
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
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "weekly_objectives", uniqueConstraints = @UniqueConstraint(
        name = "uk_weekly_objectives_group_week_position",
        columnNames = {"group_id", "week_start", "position"}))
public class WeeklyObjective {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false)
    private int position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_member_id", nullable = false)
    private GroupMember createdBy;

    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected WeeklyObjective() {}

    public WeeklyObjective(Group group, LocalDate weekStart, String title, int position,
            GroupMember createdBy, LocalDateTime now) {
        this.group = group;
        this.weekStart = weekStart;
        this.title = title;
        this.position = position;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String title, int position, LocalDateTime now) {
        this.title = title;
        this.position = position;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public LocalDate getWeekStart() { return weekStart; }
    public String getTitle() { return title; }
    public int getPosition() { return position; }
    public long getVersion() { return version; }
}
