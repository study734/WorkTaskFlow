package com.teamproject.group.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_report_downloads")
public class GroupReportDownload {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_member_id", nullable = false)
    private GroupMember requestedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Scope scope;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PeriodType periodType;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected GroupReportDownload() {}

    public GroupReportDownload(Group group, GroupMember requestedBy, Scope scope, PeriodType periodType) {
        this.group = group;
        this.requestedBy = requestedBy;
        this.scope = scope;
        this.periodType = periodType;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public GroupMember getRequestedBy() { return requestedBy; }
    public Scope getScope() { return scope; }
    public PeriodType getPeriodType() { return periodType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public enum Scope { GROUP, MY }
    public enum PeriodType { WEEKLY, MONTHLY, YEARLY }
}
