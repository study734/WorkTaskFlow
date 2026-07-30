package com.teamproject.jwt;

import com.teamproject.user.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {
    private final SecretKey key;
    private final long accessSeconds;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.access-seconds}") long accessSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessSeconds = accessSeconds;
    }
    public String create(User user) {
        return create(user, null);
    }
    public String create(User user, String sessionId) {
        return create(user, sessionId, false);
    }
    public String create(User user, String sessionId, boolean mfaVerified) {
        Instant now = Instant.now();
        var builder = Jwts.builder().subject(user.getId().toString())
                .claim("username", user.getUsername()).claim("role", user.getSystemRole().name())
                .claim("authVersion", user.getAuthVersion())
                .claim("mfaVerified", mfaVerified)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(accessSeconds)));
        if (sessionId != null) builder.claim("sessionId", sessionId);
        return builder.signWith(key).compact();
    }
    public Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
    public long accessSeconds() { return accessSeconds; }
}
