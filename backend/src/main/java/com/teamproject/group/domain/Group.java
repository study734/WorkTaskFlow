package com.teamproject.group.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_groups")
public class Group {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Type type;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(length = 500)
    private String imageUrl;
    @Column(nullable = false, length = 50)
    private String timezone;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private DashboardVisibility dashboardVisibility;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private MembershipPlan membershipPlan;
    private LocalDateTime paidStartedAt;
    private LocalDateTime paidUntil;
    private LocalDateTime nextBillingAt;
    @Column(name = "join_code_hash", length = 64, unique = true)
    private String joinCodeHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Group() {}

    private Group(Type type, String name, String description, String timezone,
            DashboardVisibility dashboardVisibility, MembershipPlan membershipPlan,
            String joinCodeHash, User createdBy) {
        this.type = type;
        this.name = name;
        this.description = description;
        this.timezone = timezone;
        this.dashboardVisibility = dashboardVisibility;
        this.membershipPlan = membershipPlan;
        this.joinCodeHash = joinCodeHash;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

    public static Group personal(User owner) {
        return new Group(Type.PERSONAL, owner.getNickname() + "의 개인 일정", null,
                "Asia/Seoul", DashboardVisibility.MEMBERS, MembershipPlan.FREE, null, owner);
    }

    public static Group team(String name, String description, String timezone, User creator) {
        return team(name, description, timezone, null, creator);
    }

    public static Group team(String name, String description, String timezone, String joinCodeHash, User creator) {
        return new Group(Type.TEAM, name, description, timezone,
                DashboardVisibility.MEMBERS, MembershipPlan.FREE, joinCodeHash, creator);
    }

    public void updateSettings(String name, String description, String timezone,
            DashboardVisibility dashboardVisibility) {
        this.name = name;
        this.description = description;
        this.timezone = timezone;
        this.dashboardVisibility = dashboardVisibility;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateImage(String imageUrl) {
        this.imageUrl = imageUrl;
        this.updatedAt = LocalDateTime.now();
    }

    public void switchTestMembership(MembershipPlan plan, LocalDateTime now) {
        if (type != Type.TEAM) throw new IllegalStateException("Personal groups cannot use paid membership.");
        this.membershipPlan = plan;
        if (plan == MembershipPlan.PAID) {
            this.paidStartedAt = now;
            this.paidUntil = now.plusDays(30);
            this.nextBillingAt = paidUntil;
        } else {
            this.paidStartedAt = null;
            this.paidUntil = null;
            this.nextBillingAt = null;
        }
        this.updatedAt = now;
    }

    public void applySubscription(MembershipPlan plan, LocalDateTime startedAt,
            LocalDateTime paidUntil, LocalDateTime nextBillingAt) {
        if (type != Type.TEAM) throw new IllegalStateException("Personal groups cannot use paid membership.");
        this.membershipPlan = plan;
        this.paidStartedAt = plan == MembershipPlan.PAID ? startedAt : null;
        this.paidUntil = plan == MembershipPlan.PAID ? paidUntil : null;
        this.nextBillingAt = plan == MembershipPlan.PAID ? nextBillingAt : null;
        this.updatedAt = LocalDateTime.now();
    }

    public void issueJoinCodeHash(String joinCodeHash) {
        if (type != Type.TEAM) throw new IllegalStateException("Personal groups cannot have a join code.");
        this.joinCodeHash = joinCodeHash;
        this.updatedAt = LocalDateTime.now();
    }

    public void revokeJoinCode() {
        this.joinCodeHash = null;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Type getType() { return type; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public String getTimezone() { return timezone; }
    public DashboardVisibility getDashboardVisibility() { return dashboardVisibility; }
    public MembershipPlan getMembershipPlan() { return membershipPlan; }
    public LocalDateTime getPaidStartedAt() { return paidStartedAt; }
    public LocalDateTime getPaidUntil() { return paidUntil; }
    public LocalDateTime getNextBillingAt() { return nextBillingAt; }
    public String getJoinCodeHash() { return joinCodeHash; }
    public User getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public enum Type { PERSONAL, TEAM }
    public enum DashboardVisibility { LEADER_ONLY, MEMBERS }
    public enum MembershipPlan { FREE, PAID }
}
