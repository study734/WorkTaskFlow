package com.teamproject.report.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_deliveries")
public class ReportDelivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "schedule_id") private ReportSchedule schedule;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PeriodType periodType;
    @Column(nullable = false) private LocalDate periodStart;
    @Column(nullable = false) private LocalDate periodEnd;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Language language;
    @Column(nullable = false, length = 160, unique = true) private String eventKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private int retryCount;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime nextRetryAt;
    @Column(length = 100) private String errorCode;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    private LocalDateTime sentAt;

    protected ReportDelivery() {}
    public ReportDelivery(ReportSchedule schedule, PeriodType type, LocalDate from, LocalDate to,
            Language language, String eventKey) {
        this.schedule = schedule; this.periodType = type; this.periodStart = from; this.periodEnd = to;
        this.language = language; this.eventKey = eventKey; this.status = Status.PENDING;
        this.retryCount = 0; this.createdAt = LocalDateTime.now();
    }
    public void sent() {
        status = Status.SENT; sentAt = LocalDateTime.now(); lastAttemptAt = sentAt;
        nextRetryAt = null; errorCode = null;
    }
    public void failed(String code) {
        status = Status.FAILED; errorCode = code; retryCount++;
        lastAttemptAt = LocalDateTime.now();
        nextRetryAt = retryCount >= 3 ? null : lastAttemptAt.plusHours(retryCount);
    }
    public void abandon(String code) { status = Status.FAILED; errorCode = code; nextRetryAt = null; }
    public Long getId() { return id; }
    public ReportSchedule getSchedule() { return schedule; }
    public PeriodType getPeriodType() { return periodType; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public Language getLanguage() { return language; }
    public Status getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public LocalDateTime getLastAttemptAt() { return lastAttemptAt; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public String getErrorCode() { return errorCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public enum PeriodType { WEEKLY, MONTHLY }
    public enum Language { KO, EN }
    public enum Status { PENDING, SENT, FAILED }
}
