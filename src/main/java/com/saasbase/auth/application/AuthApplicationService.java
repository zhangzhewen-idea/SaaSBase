package com.saasbase.auth.application;

import com.saasbase.auth.application.dto.LoginRequest;
import com.saasbase.auth.application.dto.LoginResponse;
import com.saasbase.auth.domain.UserCredential;
import com.saasbase.auth.domain.UserPrincipal;
import com.saasbase.auth.domain.gateway.RefreshTokenStore;
import com.saasbase.auth.domain.gateway.TokenGateway;
import com.saasbase.auth.domain.gateway.UserCredentialGateway;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthApplicationService {
    private final UserCredentialGateway userCredentialGateway;
    private final TokenGateway tokenGateway;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;

    public AuthApplicationService(
            UserCredentialGateway userCredentialGateway,
            TokenGateway tokenGateway,
            PasswordEncoder passwordEncoder,
            RefreshTokenStore refreshTokenStore) {
        this.userCredentialGateway = userCredentialGateway;
        this.tokenGateway = tokenGateway;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenStore = refreshTokenStore;
    }

    public LoginResponse login(LoginRequest request) {
        UserCredential credential = userCredentialGateway.findByTenantCodeAndUsername(request.tenantCode(), request.username())
                .orElseThrow(() -> new BizException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), credential.passwordHash())) {
            throw new BizException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        String accessToken = tokenGateway.issueAccessToken(new UserPrincipal(
                credential.userId(),
                credential.tenantId(),
                credential.username(),
                credential.permissions()));
        String refreshToken = UUID.randomUUID().toString();
        refreshTokenStore.save(refreshToken, refreshToken, Instant.now().plusSeconds(7 * 24 * 3600).getEpochSecond());
        return new LoginResponse("Bearer", accessToken, refreshToken, 900);
    }

    public LoginResponse refresh(RefreshRequest request) {
        if (!refreshTokenStore.exists(request.refreshToken())) {
            throw new BizException(ErrorCode.AUTH_TOKEN_REVOKED);
        }
        return new LoginResponse("Bearer", "refreshed-access-token", request.refreshToken(), 900);
    }

    public void logout(LogoutRequest request) {
        refreshTokenStore.revoke(request.refreshToken());
    }
}
