package com.teamproject.admin.domain;

import com.teamproject.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_mfa_credentials")
public class AdminMfaCredential {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false, length = 1000) private String encryptedSecret;
    @Column(columnDefinition = "TEXT") private String recoveryCodeHashes;
    private LocalDateTime enabledAt;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    @Version private long version;
    protected AdminMfaCredential() {}
    public AdminMfaCredential(User user, String encryptedSecret) {
        this.user = user; this.encryptedSecret = encryptedSecret;
        this.createdAt = LocalDateTime.now(); this.updatedAt = createdAt;
    }
    public void replacePendingSecret(String value) {
        if (enabledAt != null) throw new IllegalStateException("MFA is already enabled.");
        encryptedSecret = value; updatedAt = LocalDateTime.now();
    }
    public void enable(String hashes) {
        recoveryCodeHashes = hashes; enabledAt = LocalDateTime.now(); updatedAt = enabledAt;
    }
    public void consumeRecoveryCodes(String hashes) {
        recoveryCodeHashes = hashes; updatedAt = LocalDateTime.now();
    }
    public boolean isEnabled() { return enabledAt != null; }
    public String getEncryptedSecret() { return encryptedSecret; }
    public String getRecoveryCodeHashes() { return recoveryCodeHashes; }
    public LocalDateTime getEnabledAt() { return enabledAt; }
}
