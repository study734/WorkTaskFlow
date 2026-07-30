package com.teamproject.authentication.domain.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findLockedByTokenHash(@Param("tokenHash") String tokenHash);
    List<RefreshToken> findAllByUserId(Long userId);
    List<RefreshToken> findAllBySessionId(String sessionId);
    boolean existsByUserId(Long userId);
    boolean existsByUserIdAndDeviceId(Long userId, String deviceId);
    boolean existsByUserIdAndDeviceIdNot(Long userId, String deviceId);
    boolean existsBySessionIdAndRevokedAtIsNullAndExpiresAtAfterAndAbsoluteExpiresAtAfter(
            String sessionId, LocalDateTime idleExpiry, LocalDateTime absoluteExpiry);
    @Query("select token from RefreshToken token where token.user.id = :userId and token.revokedAt is null "
            + "and token.expiresAt > :now and token.absoluteExpiresAt > :now order by token.lastUsedAt desc")
    List<RefreshToken> findActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
    @org.springframework.data.jpa.repository.Modifying
    long deleteByAbsoluteExpiresAtBefore(LocalDateTime cutoff);
}
