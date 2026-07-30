package com.teamproject.report.domain;

import com.teamproject.group.domain.Group;
import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_schedules")
public class ReportSchedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "group_id") private Group group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recipient_user_id") private User recipient;
    @Column(nullable = false, length = 255) private String recipientEmail;
    @Column(nullable = false) private boolean weeklyEnabled;
    @Enumerated(EnumType.STRING) @Column(length = 20) private DayOfWeek weeklyDay;
    @Column(nullable = false) private boolean monthlyEnabled;
    @Column(columnDefinition = "tinyint") private Integer monthlyDay;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Language language;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected ReportSchedule() {}
    public ReportSchedule(Group group, User recipient) {
        this.group = group; this.recipient = recipient; this.recipientEmail = recipient.getEmail();
        this.weeklyEnabled = false; this.monthlyEnabled = false; this.language = Language.KO;
        this.active = true; this.createdAt = LocalDateTime.now(); this.updatedAt = createdAt;
    }
    public void update(String email, boolean weeklyEnabled, DayOfWeek weeklyDay,
            boolean monthlyEnabled, Integer monthlyDay, Language language) {
        this.recipientEmail = email; this.weeklyEnabled = weeklyEnabled;
        this.weeklyDay = weeklyEnabled ? weeklyDay : null;
        this.monthlyEnabled = monthlyEnabled; this.monthlyDay = monthlyEnabled ? monthlyDay : null;
        this.language = language; this.active = weeklyEnabled || monthlyEnabled; this.updatedAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public User getRecipient() { return recipient; }
    public String getRecipientEmail() { return recipientEmail; }
    public boolean isWeeklyEnabled() { return weeklyEnabled; }
    public DayOfWeek getWeeklyDay() { return weeklyDay; }
    public boolean isMonthlyEnabled() { return monthlyEnabled; }
    public Integer getMonthlyDay() { return monthlyDay; }
    public Language getLanguage() { return language; }
    public boolean isActive() { return active; }
    public enum Language { KO, EN, BOTH }
}
