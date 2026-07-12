package com.saasbase.auth.application;

import com.saasbase.auth.application.dto.LoginRequest;
import com.saasbase.auth.application.dto.LoginResponse;
import com.saasbase.auth.domain.UserCredential;
import com.saasbase.auth.domain.UserPrincipal;
import com.saasbase.auth.domain.gateway.RefreshTokenStore;
import com.saasbase.auth.domain.gateway.TokenGateway;
import com.saasbase.auth.domain.gateway.TokenRevocationStore;
import com.saasbase.auth.domain.gateway.UserCredentialGateway;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthApplicationService {
    private final UserCredentialGateway userCredentialGateway;
    private final TokenGateway tokenGateway;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenRevocationStore tokenRevocationStore;

    @Autowired
    public AuthApplicationService(
            UserCredentialGateway userCredentialGateway,
            TokenGateway tokenGateway,
            PasswordEncoder passwordEncoder,
            RefreshTokenStore refreshTokenStore,
            TokenRevocationStore tokenRevocationStore) {
        this.userCredentialGateway = userCredentialGateway;
        this.tokenGateway = tokenGateway;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenRevocationStore = tokenRevocationStore;
    }

    public AuthApplicationService(
            UserCredentialGateway userCredentialGateway,
            TokenGateway tokenGateway,
            PasswordEncoder passwordEncoder,
            RefreshTokenStore refreshTokenStore) {
        this(userCredentialGateway, tokenGateway, passwordEncoder, refreshTokenStore, (tokenId) -> false);
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
        String value = refreshTokenStore.find(request.refreshToken());
        if (value == null) {
            throw new BizException(ErrorCode.AUTH_TOKEN_REVOKED);
        }
        UserPrincipal principal = parseRefreshValue(value);
        refreshTokenStore.revoke(request.refreshToken());
        String accessToken = tokenGateway.issueAccessToken(principal);
        String nextRefreshToken = UUID.randomUUID().toString();
        refreshTokenStore.save(nextRefreshToken, value, Instant.now().plusSeconds(7 * 24 * 3600).getEpochSecond());
        return new LoginResponse("Bearer", accessToken, nextRefreshToken, 900);
    }

    public void logout(LogoutRequest request, String accessToken) {
        refreshTokenStore.revoke(request.refreshToken());
        if (accessToken != null && !accessToken.isBlank()) {
            String tokenId = tokenGateway.parseTokenId(accessToken);
            tokenRevocationStore.revoke(tokenId, Instant.now().plusSeconds(900).getEpochSecond());
        }
    }

    public void logout(LogoutRequest request) {
        logout(request, null);
    }

    private UserPrincipal parseRefreshValue(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length != 4) {
            throw new BizException(ErrorCode.AUTH_TOKEN_REVOKED);
        }
        Set<String> permissions = parts[3].isBlank() ? Set.of() : Set.of(parts[3].split(","));
        try {
            return new UserPrincipal(Long.valueOf(parts[0]), Long.valueOf(parts[1]), parts[2], permissions);
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.AUTH_TOKEN_REVOKED);
        }
    }
}
