package com.teamproject.authentication.application;

import com.teamproject.authentication.application.dto.SessionDtos.TokenResponse;
import com.teamproject.authentication.application.token.RefreshTokenService;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.jwt.JwtService;
import com.teamproject.user.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.teamproject.authentication.domain.token.RefreshToken.ClientMode;
import com.teamproject.authentication.domain.token.SessionDevice;

@Component
public class AccessSessionIssuer {
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;

    public AccessSessionIssuer(JwtService jwt, RefreshTokenService refreshTokens) {
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
    }

    public IssuedTokens issue(User user) { return issue(user, ClientMode.WEB); }

    public IssuedTokens issue(User user, ClientMode mode) {
        return issue(user, mode, SessionDevice.unknown());
    }

    public IssuedTokens issue(User user, ClientMode mode, SessionDevice device) {
        return issue(user, mode, device, false);
    }
    public IssuedTokens issue(User user, ClientMode mode, SessionDevice device, boolean mfaVerified) {
        if (!user.isActive()) {
            throw new ApplicationException("ACCOUNT_INACTIVE", HttpStatus.FORBIDDEN, "사용할 수 없는 계정입니다.");
        }
        var refresh = refreshTokens.issue(user, mode, device);
        return tokens(user, refresh, mfaVerified);
    }

    public IssuedTokens refresh(String rawRefreshToken) {
        return refresh(rawRefreshToken, null);
    }

    public IssuedTokens refresh(String rawRefreshToken, ClientMode requestedMode) {
        return refresh(rawRefreshToken, requestedMode, null);
    }

    public IssuedTokens refresh(String rawRefreshToken, ClientMode requestedMode, SessionDevice device) {
        var rotated = refreshTokens.rotate(rawRefreshToken, requestedMode, device);
        if (!rotated.user().isActive()) {
            throw new ApplicationException("ACCOUNT_INACTIVE", HttpStatus.FORBIDDEN, "사용할 수 없는 계정입니다.");
        }
        return tokens(rotated.user(), rotated.refreshToken(), false);
    }

    private IssuedTokens tokens(User user, RefreshTokenService.IssuedRefreshToken refresh, boolean mfaVerified) {
        return new IssuedTokens(new TokenResponse(jwt.create(user, refresh.sessionId(), mfaVerified), "Bearer", jwt.accessSeconds()),
                refresh.rawToken(), refresh.cookieMaxAgeSeconds());
    }
}
