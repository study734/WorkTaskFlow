package com.teamproject.subscription.domain;

import com.teamproject.group.domain.Group;
import com.teamproject.payment.domain.PaymentMethod;
import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_subscriptions")
public class GroupSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "group_id") private Group group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "subscriber_user_id") private User subscriber;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_method_id") private PaymentMethod paymentMethod;
    @Column(nullable = false, length = 40) private String planCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Status status;
    @Column(nullable = false) private long amount;
    @Column(nullable = false, length = 3, columnDefinition = "char(3)") private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ConversionChoice conversionChoice;
    private LocalDateTime rolloutNoticeAt;
    private LocalDateTime decisionDeadline;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime nextBillingAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime lastBillingAttemptAt;
    private LocalDateTime pastDueSince;
    @Column(nullable = false) private int consecutiveFailures;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    @Version private long version;

    protected GroupSubscription() {}
    public GroupSubscription(Group group, User subscriber, String planCode, long amount) {
        this.group = group; this.subscriber = subscriber; this.planCode = planCode; this.amount = amount;
        this.currency = "KRW"; this.status = Status.FREE; this.conversionChoice = ConversionChoice.UNDECIDED;
        this.consecutiveFailures = 0;
        this.createdAt = LocalDateTime.now(); this.updatedAt = createdAt;
    }
    public void startTrial(LocalDateTime now, int days) {
        status = Status.TRIALING; conversionChoice = ConversionChoice.UNDECIDED;
        currentPeriodStart = now; currentPeriodEnd = now.plusDays(days); nextBillingAt = null;
        cancelledAt = null; pastDueSince = null; consecutiveFailures = 0; updatedAt = now;
    }
    public void choose(ConversionChoice choice, LocalDateTime now) {
        conversionChoice = choice; updatedAt = now;
        if (choice == ConversionChoice.KEEP_FREE) cancelToFree(now);
    }
    public void announce(LocalDateTime now, LocalDateTime deadline) {
        rolloutNoticeAt = now; decisionDeadline = deadline; conversionChoice = ConversionChoice.UNDECIDED; updatedAt = now;
    }
    public void activate(PaymentMethod method, LocalDateTime now) {
        paymentMethod = method; status = Status.ACTIVE; conversionChoice = ConversionChoice.CONTINUE_PAID;
        currentPeriodStart = now; currentPeriodEnd = now.plusMonths(1); nextBillingAt = currentPeriodEnd;
        cancelledAt = null; pastDueSince = null; consecutiveFailures = 0; lastBillingAttemptAt = now; updatedAt = now;
    }
    public void cancelAtPeriodEnd(LocalDateTime now) { status = Status.CANCEL_AT_PERIOD_END; cancelledAt = now; updatedAt = now; }
    public void cancelToFree(LocalDateTime now) {
        status = Status.FREE; paymentMethod = null; currentPeriodStart = null; currentPeriodEnd = null;
        nextBillingAt = null; cancelledAt = now; pastDueSince = null; consecutiveFailures = 0; updatedAt = now;
    }
    public void markPastDue(LocalDateTime now) {
        status = Status.PAST_DUE;
        if (pastDueSince == null) pastDueSince = now;
        consecutiveFailures++;
        lastBillingAttemptAt = now;
        nextBillingAt = now.plusDays(consecutiveFailures >= 3 ? 3 : 1);
        updatedAt = now;
    }
    public void renew(LocalDateTime now) {
        status = Status.ACTIVE;
        currentPeriodStart = now;
        currentPeriodEnd = now.plusMonths(1);
        nextBillingAt = currentPeriodEnd;
        lastBillingAttemptAt = now;
        pastDueSince = null;
        consecutiveFailures = 0;
        updatedAt = now;
    }
    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public User getSubscriber() { return subscriber; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getPlanCode() { return planCode; }
    public Status getStatus() { return status; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public ConversionChoice getConversionChoice() { return conversionChoice; }
    public LocalDateTime getRolloutNoticeAt() { return rolloutNoticeAt; }
    public LocalDateTime getDecisionDeadline() { return decisionDeadline; }
    public LocalDateTime getCurrentPeriodStart() { return currentPeriodStart; }
    public LocalDateTime getCurrentPeriodEnd() { return currentPeriodEnd; }
    public LocalDateTime getNextBillingAt() { return nextBillingAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public LocalDateTime getLastBillingAttemptAt() { return lastBillingAttemptAt; }
    public LocalDateTime getPastDueSince() { return pastDueSince; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public enum Status { FREE, TRIALING, ACTIVE, PAST_DUE, CANCEL_AT_PERIOD_END, CANCELLED }
    public enum ConversionChoice { UNDECIDED, KEEP_FREE, CONTINUE_PAID }
}
