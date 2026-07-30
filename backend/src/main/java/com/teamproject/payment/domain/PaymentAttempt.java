package com.teamproject.payment.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_method_id") private PaymentMethod method;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private OperationType operationType;
    @Column(nullable = false, length = 100, unique = true) private String idempotencyKey;
    @Column(length = 64) private String orderId;
    private Long amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    private Integer httpStatus;
    @Column(length = 100) private String providerCode;
    @Column(length = 500) private String providerMessage;
    @Column(nullable = false) private int retryCount;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected PaymentAttempt() {}
    public PaymentAttempt(User user, PaymentMethod method, OperationType type, String key, String orderId, Long amount) {
        this.user = user; this.method = method; this.operationType = type; this.idempotencyKey = key;
        this.orderId = orderId; this.amount = amount; this.status = Status.PENDING;
        this.createdAt = LocalDateTime.now(); this.updatedAt = createdAt;
    }
    public void success(int status) { this.status = Status.SUCCESS; this.httpStatus = status; touch(); }
    public void fail(Integer status, String code, String message) {
        this.status = Status.FAILED; this.httpStatus = status; this.providerCode = safe(code, 100);
        this.providerMessage = safe(message, 500); touch();
    }
    public void retrying() { this.status = Status.PENDING; this.retryCount++; touch(); }
    public void attachMethod(PaymentMethod method) { this.method = method; touch(); }
    private void touch() { this.updatedAt = LocalDateTime.now(); }
    private String safe(String value, int max) { return value == null ? null : value.substring(0, Math.min(value.length(), max)); }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public PaymentMethod getMethod() { return method; }
    public OperationType getOperationType() { return operationType; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getOrderId() { return orderId; }
    public Long getAmount() { return amount; }
    public Status getStatus() { return status; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getProviderCode() { return providerCode; }
    public String getProviderMessage() { return providerMessage; }
    public int getRetryCount() { return retryCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public enum OperationType { BILLING_KEY_ISSUE, TEST_CHARGE, SUBSCRIPTION_CHARGE }
    public enum Status { PENDING, SUCCESS, FAILED }
}
