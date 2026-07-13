package com.saasbase.auth.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.saasbase.audit.domain.SecurityAuditEvent;
import com.saasbase.audit.domain.gateway.AuditGateway;
import com.saasbase.auth.application.dto.LoginRequest;
import com.saasbase.auth.application.dto.LoginResponse;
import com.saasbase.auth.domain.UserCredential;
import com.saasbase.auth.domain.UserPrincipal;
import com.saasbase.iam.domain.gateway.UserSessionGateway;
import com.saasbase.auth.domain.gateway.RefreshTokenStore;
import com.saasbase.auth.domain.gateway.TokenGateway;
import com.saasbase.auth.domain.gateway.TokenRevocationStore;
import com.saasbase.auth.domain.gateway.UserCredentialGateway;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.iam.domain.UserAuthState;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthApplicationService {
    private final UserCredentialGateway userCredentialGateway;
    private final TokenGateway tokenGateway;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenRevocationStore tokenRevocationStore;
    private final ObjectMapper objectMapper;
    private final AuditGateway auditGateway;
    private final UserSessionGateway userSessionGateway;

    @Autowired
    public AuthApplicationService(
            UserCredentialGateway userCredentialGateway,
            TokenGateway tokenGateway,
            PasswordEncoder passwordEncoder,
            RefreshTokenStore refreshTokenStore,
            TokenRevocationStore tokenRevocationStore,
            AuditGateway auditGateway,
            UserSessionGateway userSessionGateway) {
        this.userCredentialGateway = userCredentialGateway;
        this.tokenGateway = tokenGateway;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenRevocationStore = tokenRevocationStore;
        this.objectMapper = new ObjectMapper();
        this.auditGateway = auditGateway;
        this.userSessionGateway = userSessionGateway;
    }

    public AuthApplicationService(
            UserCredentialGateway userCredentialGateway,
            TokenGateway tokenGateway,
            PasswordEncoder passwordEncoder,
            RefreshTokenStore refreshTokenStore) {
        this(userCredentialGateway, tokenGateway, passwordEncoder, refreshTokenStore, (tokenId) -> false,
                new NoopAuditGateway(), new NoopUserSessionGateway());
    }

    public LoginResponse login(LoginRequest request) {
        UserCredential credential = userCredentialGateway.findByTenantCodeAndUsername(request.tenantCode(), request.username())
                .orElseThrow(() -> {
                    auditGateway.appendSecurityAudit(SecurityAuditEvent.loginFailure(null, request.username(), null));
                    return new BizException(ErrorCode.AUTH_INVALID_CREDENTIALS);
                });
        if (!passwordEncoder.matches(request.password(), credential.passwordHash())) {
            auditGateway.appendSecurityAudit(SecurityAuditEvent.loginFailure(credential.tenantId(), request.username(), null));
            throw new BizException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if ("DISABLED".equalsIgnoreCase(credential.status())) {
            auditGateway.appendSecurityAudit(SecurityAuditEvent.loginFailure(credential.tenantId(), request.username(), null));
            throw new BizException(ErrorCode.AUTH_USER_DISABLED);
        }
        auditGateway.appendSecurityAudit(SecurityAuditEvent.loginSuccess(
                credential.tenantId(), credential.userId(), credential.username(), null));
        String accessToken = tokenGateway.issueAccessToken(new UserPrincipal(
                credential.userId(),
                credential.tenantId(),
                credential.username(),
                credential.permissions(),
                credential.sessionVersion(),
                credential.mustChangePassword()));
        String refreshToken = UUID.randomUUID().toString();
        refreshTokenStore.save(refreshToken, serializeRefreshValue(credential), Instant.now().plusSeconds(7 * 24 * 3600).getEpochSecond());
        return new LoginResponse("Bearer", accessToken, refreshToken, 900);
    }

    public LoginResponse refresh(RefreshRequest request) {
        String value = refreshTokenStore.find(request.refreshToken());
        if (value == null) {
            throw new BizException(ErrorCode.AUTH_TOKEN_REVOKED);
        }
        UserPrincipal principal = parseRefreshValue(value);
        String accessToken = tokenGateway.issueAccessToken(principal);
        String nextRefreshToken = UUID.randomUUID().toString();
        boolean rotated = refreshTokenStore.rotate(
                request.refreshToken(),
                value,
                nextRefreshToken,
                value,
                Instant.now().plusSeconds(7 * 24 * 3600).getEpochSecond());
        if (!rotated) {
            throw new BizException(ErrorCode.AUTH_TOKEN_REVOKED);
        }
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
        try {
            Map<String, Object> data = objectMapper.readValue(value, new TypeReference<>() {
            });
            Long userId = Long.valueOf(String.valueOf(data.get("userId")));
            Long tenantId = Long.valueOf(String.valueOf(data.get("tenantId")));
            String username = String.valueOf(data.get("username"));
            Set<String> permissions = objectMapper.convertValue(data.get("permissions"), new TypeReference<>() {
            });
            long sessionVersion = Long.parseLong(String.valueOf(data.get("sessionVersion")));
            boolean mustChangePassword = Boolean.parseBoolean(String.valueOf(data.get("mustChangePassword")));
            return new UserPrincipal(userId, tenantId, username, permissions, sessionVersion, mustChangePassword);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.AUTH_TOKEN_REVOKED);
        }
    }

    private String serializeRefreshValue(UserCredential credential) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "userId", credential.userId(),
                    "tenantId", credential.tenantId(),
                    "username", credential.username(),
                    "permissions", credential.permissions(),
                    "sessionVersion", credential.sessionVersion(),
                    "mustChangePassword", credential.mustChangePassword()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize refresh session", exception);
        }
    }

    private static final class NoopAuditGateway implements AuditGateway {
        @Override
        public void appendSecurityAudit(SecurityAuditEvent event) {
        }

        @Override
        public void appendAdminOperationAudit(com.saasbase.audit.domain.AdminOperationAuditEvent event) {
        }
    }

    private static final class NoopUserSessionGateway implements UserSessionGateway {
        @Override
        public void put(UserAuthState state) {
        }

        @Override
        public java.util.Optional<UserAuthState> get(long tenantId, long userId) {
            return java.util.Optional.empty();
        }

        @Override
        public UserAuthState getOrLoad(long tenantId, long userId, java.util.function.Supplier<UserAuthState> loader) {
            return loader.get();
        }
    }
}
