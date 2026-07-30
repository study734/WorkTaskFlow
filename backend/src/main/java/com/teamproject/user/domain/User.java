package com.teamproject.user.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40)
    private String username;
    @Column(nullable = false, length = 255)
    private String email;
    @Column(length = 255)
    private String passwordHash;
    @Column(nullable = false, length = 60)
    private String name;
    @Column(nullable = false, length = 30)
    private String nickname;
    @Column(length = 20)
    private String phoneNumber;
    @Column(length = 500)
    private String profileImageUrl;
    @Enumerated(EnumType.STRING) @Column(name = "role", nullable = false, length = 20)
    private SystemRole systemRole = SystemRole.USER;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;
    @Column(nullable = false)
    private boolean emailVerified;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    private LocalDateTime lastLoginAt;
    private LocalDateTime withdrawnAt;
    @Column(length = 100, unique = true)
    private String paymentCustomerKey;
    @Column(nullable = false)
    private long authVersion;

    protected User() {}

    public User(String username, String email, String passwordHash, String name, boolean emailVerified) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.nickname = name;
        this.emailVerified = emailVerified;
    }

    public static User social(String username, String email, String name) {
        return new User(username, email, null, name, true);
    }

    public void changePassword(String passwordHash) { this.passwordHash = passwordHash; touch(); }
    public void recordLogin() { this.lastLoginAt = LocalDateTime.now(); touch(); }
    public void updateProfile(String nickname, String phoneNumber, String profileImageUrl) {
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.profileImageUrl = profileImageUrl;
        touch();
    }
    public void suspend() { this.status = Status.SUSPENDED; touch(); }
    public void activate() {
        if (status == Status.WITHDRAWN) throw new IllegalStateException("Withdrawn users cannot be reactivated.");
        this.status = Status.ACTIVE; touch();
    }
    public void invalidateSessions() { this.authVersion++; touch(); }
    public void ensurePaymentCustomerKey(String value) {
        if (this.paymentCustomerKey == null) {
            this.paymentCustomerKey = value;
            touch();
        }
    }
    public void anonymizeAndWithdraw(String anonymizedUsername, String anonymizedEmail) {
        this.username = anonymizedUsername;
        this.email = anonymizedEmail;
        this.passwordHash = null;
        this.name = "탈퇴한 사용자";
        this.nickname = "탈퇴한 사용자";
        this.phoneNumber = null;
        this.profileImageUrl = null;
        this.systemRole = SystemRole.USER;
        this.emailVerified = false;
        this.lastLoginAt = null;
        this.status = Status.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now();
        touch();
    }
    public boolean isActive() { return status == Status.ACTIVE; }
    private void touch() { this.updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public String getNickname() { return nickname; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public SystemRole getSystemRole() { return systemRole; }
    public Status getStatus() { return status; }
    public boolean isEmailVerified() { return emailVerified; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public LocalDateTime getWithdrawnAt() { return withdrawnAt; }
    public String getPaymentCustomerKey() { return paymentCustomerKey; }
    public long getAuthVersion() { return authVersion; }

    public enum SystemRole { USER, ADMIN }
    public enum Status { ACTIVE, SUSPENDED, WITHDRAWN }
}
