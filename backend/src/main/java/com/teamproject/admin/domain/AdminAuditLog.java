package com.teamproject.admin.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_user_id") private User actor;
    @Column(nullable = false, length = 10) private String httpMethod;
    @Column(nullable = false, length = 500) private String requestPath;
    @Column(nullable = false) private int httpStatus;
    @Column(nullable = false, length = 20) private String outcome;
    @Column(length = 64) private String ipAddress;
    @Column(length = 500) private String userAgent;
    @Column(length = 80) private String requestId;
    @Column(nullable = false, updatable = false) private LocalDateTime occurredAt;
    protected AdminAuditLog() {}
    public AdminAuditLog(User actor, String method, String path, int status, String ip, String userAgent, String requestId) {
        this.actor = actor; this.httpMethod = cut(method, 10); this.requestPath = cut(path, 500);
        this.httpStatus = status; this.outcome = status < 400 ? "SUCCESS" : "DENIED";
        this.ipAddress = cut(ip, 64); this.userAgent = cut(userAgent, 500);
        this.requestId = cut(requestId, 80); this.occurredAt = LocalDateTime.now();
    }
    private String cut(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
    public Long getId() { return id; }
    public User getActor() { return actor; }
    public String getHttpMethod() { return httpMethod; }
    public String getRequestPath() { return requestPath; }
    public int getHttpStatus() { return httpStatus; }
    public String getOutcome() { return outcome; }
    public String getIpAddress() { return ipAddress; }
    public String getRequestId() { return requestId; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
