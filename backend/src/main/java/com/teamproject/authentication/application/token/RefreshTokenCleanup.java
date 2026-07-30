package com.teamproject.authentication.application.token;

import com.teamproject.authentication.domain.token.RefreshTokenRepository;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RefreshTokenCleanup {
    private final RefreshTokenRepository tokens;

    public RefreshTokenCleanup(RefreshTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Scheduled(fixedDelayString = "${app.jwt.refresh-cleanup-ms:86400000}")
    @Transactional
    public void deleteExpiredSessions() {
        tokens.deleteByAbsoluteExpiresAtBefore(LocalDateTime.now());
    }
}
