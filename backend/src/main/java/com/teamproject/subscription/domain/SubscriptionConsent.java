package com.teamproject.subscription.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_consents")
public class SubscriptionConsent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "subscription_id")
    private GroupSubscription subscription;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false) private long amount;
    @Column(nullable = false, length = 3, columnDefinition = "char(3)") private String currency;
    @Column(nullable = false, length = 30) private String termsVersion;
    @Column(nullable = false, length = 30) private String refundPolicyVersion;
    @Column(length = 64) private String ipAddress;
    @Column(length = 500) private String userAgent;
    @Column(nullable = false, updatable = false) private LocalDateTime acceptedAt;
    protected SubscriptionConsent() {}
    public SubscriptionConsent(GroupSubscription subscription, User user, long amount, String termsVersion,
            String refundPolicyVersion, String ipAddress, String userAgent, LocalDateTime acceptedAt) {
        this.subscription = subscription; this.user = user; this.amount = amount; this.currency = "KRW";
        this.termsVersion = termsVersion; this.refundPolicyVersion = refundPolicyVersion;
        this.ipAddress = trim(ipAddress, 64); this.userAgent = trim(userAgent, 500); this.acceptedAt = acceptedAt;
    }
    private String trim(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
