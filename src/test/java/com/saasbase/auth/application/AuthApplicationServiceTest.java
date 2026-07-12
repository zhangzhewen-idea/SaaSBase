package com.saasbase.auth.application;

import com.saasbase.auth.application.dto.LoginRequest;
import com.saasbase.auth.domain.UserCredential;
import com.saasbase.auth.domain.UserPrincipal;
import com.saasbase.auth.domain.gateway.RefreshTokenStore;
import com.saasbase.auth.domain.gateway.TokenGateway;
import com.saasbase.auth.domain.gateway.UserCredentialGateway;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthApplicationServiceTest {

    @Test
    void login_returns_access_and_refresh_token_when_password_matches() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        UserCredentialGateway userGateway = (tenantCode, username) -> Optional.of(
                new UserCredential(1001L, 2001L, "alice", encoder.encode("pass123"), Set.of("tenant:user:read")));
        RefreshTokenStore refreshTokenStore = new InMemoryRefreshTokenStore();
        TokenGateway tokenGateway = new TokenGateway() {
            @Override
            public String issueAccessToken(UserPrincipal principal) {
                return "access-token";
            }

            @Override
            public UserPrincipal parseAccessToken(String token) {
                return new UserPrincipal(1001L, 2001L, "alice", Set.of("tenant:user:read"));
            }
        };
        AuthApplicationService service = new AuthApplicationService(userGateway, tokenGateway, encoder, refreshTokenStore);

        var response = service.login(new LoginRequest("tenant-a", "alice", "pass123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(refreshTokenStore.exists(response.refreshToken())).isTrue();

        var refreshed = service.refresh(new RefreshRequest(response.refreshToken()));
        assertThat(refreshed.accessToken()).isEqualTo("access-token");
        assertThat(refreshed.refreshToken()).isNotEqualTo(response.refreshToken());
    }

    @Test
    void refresh_returns_new_access_token_when_refresh_token_exists() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        UserCredentialGateway userGateway = (tenantCode, username) -> Optional.empty();
        InMemoryRefreshTokenStore refreshTokenStore = new InMemoryRefreshTokenStore();
        refreshTokenStore.save("refresh-1", "{\"userId\":1001,\"tenantId\":2001,\"username\":\"alice\",\"permissions\":[\"tenant:user:read\"]}", Long.MAX_VALUE);
        TokenGateway tokenGateway = new TokenGateway() {
            @Override
            public String issueAccessToken(UserPrincipal principal) {
                return "access-token";
            }

            @Override
            public UserPrincipal parseAccessToken(String token) {
                return new UserPrincipal(1001L, 2001L, "alice", Set.of("tenant:user:read"));
            }
        };
        AuthApplicationService service = new AuthApplicationService(userGateway, tokenGateway, encoder, refreshTokenStore);

        var response = service.refresh(new RefreshRequest("refresh-1"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotEqualTo("refresh-1");
        assertThat(refreshTokenStore.exists("refresh-1")).isFalse();
    }

    @Test
    void logout_revokes_refresh_token() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        UserCredentialGateway userGateway = (tenantCode, username) -> Optional.empty();
        InMemoryRefreshTokenStore refreshTokenStore = new InMemoryRefreshTokenStore();
        refreshTokenStore.save("refresh-2", "refresh-2", Long.MAX_VALUE);
        TokenGateway tokenGateway = new TokenGateway() {
            @Override
            public String issueAccessToken(UserPrincipal principal) {
                return "access-token";
            }

            @Override
            public UserPrincipal parseAccessToken(String token) {
                return new UserPrincipal(1001L, 2001L, "alice", Set.of("tenant:user:read"));
            }
        };
        AuthApplicationService service = new AuthApplicationService(userGateway, tokenGateway, encoder, refreshTokenStore);

        service.logout(new LogoutRequest("refresh-2"));

        assertThat(refreshTokenStore.exists("refresh-2")).isFalse();
    }

    private static final class InMemoryRefreshTokenStore implements RefreshTokenStore {
        private final java.util.Map<String, String> tokens = new java.util.HashMap<>();

        @Override
        public void save(String tokenId, String tokenValue, long expiresAtEpochSecond) {
            tokens.put(tokenId, tokenValue);
        }

        @Override
        public boolean exists(String tokenId) {
            return tokens.containsKey(tokenId);
        }

        @Override
        public String find(String tokenId) {
            return tokens.get(tokenId);
        }

        @Override
        public void revoke(String tokenId) {
            tokens.remove(tokenId);
        }
    }
}
