package com.saasbase.auth.infrastructure.security;

import com.saasbase.auth.domain.gateway.TokenGateway;
import com.saasbase.auth.domain.gateway.TokenRevocationStore;
import com.saasbase.iam.domain.gateway.UserSessionGateway;
import com.saasbase.iam.domain.UserAuthState;
import com.saasbase.iam.domain.UserStatus;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returns_401_when_token_parsing_fails() throws Exception {
        TokenGateway tokenGateway = mock(TokenGateway.class);
        when(tokenGateway.parseTokenId("bad-token")).thenThrow(new IllegalArgumentException("bad token"));
        TokenRevocationStore revocationStore = tokenId -> false;
        UserSessionGateway sessionGateway = new UserSessionGateway() {
            @Override
            public void put(com.saasbase.iam.domain.UserAuthState state) {
            }

            @Override
            public java.util.Optional<com.saasbase.iam.domain.UserAuthState> get(long tenantId, long userId) {
                return java.util.Optional.empty();
            }

            @Override
            public com.saasbase.iam.domain.UserAuthState getOrLoad(long tenantId, long userId, java.util.function.Supplier<com.saasbase.iam.domain.UserAuthState> loader) {
                return loader.get();
            }
        };
        TestableFilter filter = new TestableFilter(tokenGateway, revocationStore, sessionGateway);
        MockHttpServletRequest request = requestWithBearer("bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.apply(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_INVALID_TOKEN");
    }

    @Test
    void returns_401_when_token_is_revoked() throws Exception {
        TokenGateway tokenGateway = mock(TokenGateway.class);
        when(tokenGateway.parseTokenId("revoked-token")).thenReturn("jti-1");
        TokenRevocationStore revocationStore = tokenId -> true;
        UserSessionGateway sessionGateway = new UserSessionGateway() {
            @Override
            public void put(com.saasbase.iam.domain.UserAuthState state) {
            }

            @Override
            public java.util.Optional<com.saasbase.iam.domain.UserAuthState> get(long tenantId, long userId) {
                return java.util.Optional.empty();
            }

            @Override
            public com.saasbase.iam.domain.UserAuthState getOrLoad(long tenantId, long userId, java.util.function.Supplier<com.saasbase.iam.domain.UserAuthState> loader) {
                return loader.get();
            }
        };
        TestableFilter filter = new TestableFilter(tokenGateway, revocationStore, sessionGateway);
        MockHttpServletRequest request = requestWithBearer("revoked-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.apply(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_INVALID_TOKEN");
    }

    @Test
    void returns_403_when_password_change_is_required() throws Exception {
        TokenGateway tokenGateway = mock(TokenGateway.class);
        when(tokenGateway.parseTokenId("password-token")).thenReturn("jti-1");
        when(tokenGateway.parseAccessToken("password-token")).thenReturn(
                new com.saasbase.auth.domain.UserPrincipal(1L, 2L, "alice", java.util.Set.of(), 7L, true));
        TokenRevocationStore revocationStore = tokenId -> false;
        UserSessionGateway sessionGateway = new UserSessionGateway() {
            @Override
            public void put(com.saasbase.iam.domain.UserAuthState state) {
            }

            @Override
            public java.util.Optional<com.saasbase.iam.domain.UserAuthState> get(long tenantId, long userId) {
                return java.util.Optional.of(new UserAuthState(2L, 1L, UserStatus.ACTIVE, 7L, true));
            }

            @Override
            public com.saasbase.iam.domain.UserAuthState getOrLoad(long tenantId, long userId, java.util.function.Supplier<com.saasbase.iam.domain.UserAuthState> loader) {
                return loader.get();
            }
        };
        TestableFilter filter = new TestableFilter(tokenGateway, revocationStore, sessionGateway);
        MockHttpServletRequest request = requestWithBearer("password-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.apply(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("AUTH_PASSWORD_CHANGE_REQUIRED");
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static final class TestableFilter extends JwtAuthenticationFilter {
        private TestableFilter(TokenGateway tokenGateway, TokenRevocationStore revocationStore, UserSessionGateway sessionGateway) {
            super(tokenGateway, revocationStore, sessionGateway);
        }

        private void apply(MockHttpServletRequest request, MockHttpServletResponse response, FilterChain chain)
                throws Exception {
            doFilterInternal(request, response, chain);
        }
    }
}
