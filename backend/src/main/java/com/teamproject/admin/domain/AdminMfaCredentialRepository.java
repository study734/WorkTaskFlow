package com.teamproject.admin.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdminMfaCredentialRepository extends JpaRepository<AdminMfaCredential, Long> {
    Optional<AdminMfaCredential> findByUserId(Long userId);
}
