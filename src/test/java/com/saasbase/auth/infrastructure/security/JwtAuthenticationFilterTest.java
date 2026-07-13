package com.saasbase.auth.infrastructure.security;

import com.saasbase.auth.domain.UserPrincipal;
import com.saasbase.auth.domain.gateway.TokenGateway;
import com.saasbase.auth.domain.gateway.TokenRevocationStore;
import com.saasbase.tenant.domain.TenantAuthState;
import com.saasbase.tenant.domain.TenantStatus;
import com.saasbase.tenant.domain.gateway.TenantAuthStateGateway;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        TestableFilter filter = new TestableFilter(tokenGateway, revocationStore, activeTenantState(1L, 1L));
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
        TestableFilter filter = new TestableFilter(tokenGateway, revocationStore, activeTenantState(1L, 1L));
        MockHttpServletRequest request = requestWithBearer("revoked-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.apply(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_INVALID_TOKEN");
    }

    @Test
    void returns_401_when_tenant_is_disabled() throws Exception {
        TokenGateway tokenGateway = mock(TokenGateway.class);
        when(tokenGateway.parseTokenId("token")).thenReturn("jti-1");
        when(tokenGateway.parseAccessToken("token")).thenReturn(new UserPrincipal(1L, 2L, "admin", java.util.Set.of("tenant:profile:read"), 3L));
        TokenRevocationStore revocationStore = tokenId -> false;
        TestableFilter filter = new TestableFilter(tokenGateway, revocationStore, disabledTenantState(2L, 3L));
        MockHttpServletRequest request = requestWithBearer("token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.apply(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_TENANT_SESSION_EXPIRED");
    }

    @Test
    void returns_401_when_session_version_mismatches() throws Exception {
        TokenGateway tokenGateway = mock(TokenGateway.class);
        when(tokenGateway.parseTokenId("token")).thenReturn("jti-1");
        when(tokenGateway.parseAccessToken("token")).thenReturn(new UserPrincipal(1L, 2L, "admin", java.util.Set.of("tenant:profile:read"), 3L));
        TokenRevocationStore revocationStore = tokenId -> false;
        TestableFilter filter = new TestableFilter(tokenGateway, revocationStore, activeTenantState(2L, 4L));
        MockHttpServletRequest request = requestWithBearer("token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.apply(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_TENANT_SESSION_EXPIRED");
    }

    @Test
    void sets_platform_context_only_for_platform_routes_with_platform_permissions() throws Exception {
        TokenGateway tokenGateway = mock(TokenGateway.class);
        when(tokenGateway.parseTokenId("token")).thenReturn("jti-1");
        when(tokenGateway.parseAccessToken("token")).thenReturn(new UserPrincipal(1L, 2L, "admin", java.util.Set.of("platform:tenant:read"), 3L));
        TokenRevocationStore revocationStore = tokenId -> false;
        TestableFilter filter = new TestableFilter(tokenGateway, revocationStore, activeTenantState(2L, 3L));
        MockHttpServletRequest request = requestWithBearer("token");
        request.setRequestURI("/api/v1/platform/tenants");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.apply(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void non_platform_routes_do_not_get_platform_context() throws Exception {
        TokenGateway tokenGateway = mock(TokenGateway.class);
        when(tokenGateway.parseTokenId("token")).thenReturn("jti-1");
        when(tokenGateway.parseAccessToken("token")).thenReturn(new UserPrincipal(1L, 2L, "admin", java.util.Set.of("platform:tenant:read"), 3L));
        TokenRevocationStore revocationStore = tokenId -> false;
        TestableFilter filter = new TestableFilter(tokenGateway, revocationStore, activeTenantState(2L, 3L));
        MockHttpServletRequest request = requestWithBearer("token");
        request.setRequestURI("/api/v1/tenants");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.apply(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static final class TestableFilter extends JwtAuthenticationFilter {
        private TestableFilter(
                TokenGateway tokenGateway,
                TokenRevocationStore revocationStore,
                TenantAuthStateGateway tenantAuthStateGateway) {
            super(tokenGateway, revocationStore, tenantAuthStateGateway);
        }

        private void apply(MockHttpServletRequest request, MockHttpServletResponse response, FilterChain chain)
                throws Exception {
                doFilterInternal(request, response, chain);
        }
    }

    private static TenantAuthStateGateway activeTenantState(long tenantId, long sessionVersion) {
        return new TenantAuthStateGateway() {
            @Override
            public TenantAuthState requireCurrent(Long currentTenantId) {
                return new TenantAuthState(currentTenantId, TenantStatus.ACTIVE, sessionVersion);
            }

            @Override
            public void cache(TenantAuthState tenantAuthState) {
            }
        };
    }

    private static TenantAuthStateGateway disabledTenantState(long tenantId, long sessionVersion) {
        return new TenantAuthStateGateway() {
            @Override
            public TenantAuthState requireCurrent(Long currentTenantId) {
                return new TenantAuthState(currentTenantId, TenantStatus.DISABLED, sessionVersion);
            }

            @Override
            public void cache(TenantAuthState tenantAuthState) {
            }
        };
    }
}
