package com.saasbase.auth.infrastructure.security;

import com.saasbase.auth.domain.gateway.TokenGateway;
import com.saasbase.auth.domain.gateway.TokenRevocationStore;
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
        TestableFilter filter = new TestableFilter(tokenGateway, revocationStore);
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
        TestableFilter filter = new TestableFilter(tokenGateway, revocationStore);
        MockHttpServletRequest request = requestWithBearer("revoked-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.apply(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_INVALID_TOKEN");
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static final class TestableFilter extends JwtAuthenticationFilter {
        private TestableFilter(TokenGateway tokenGateway, TokenRevocationStore revocationStore) {
            super(tokenGateway, revocationStore);
        }

        private void apply(MockHttpServletRequest request, MockHttpServletResponse response, FilterChain chain)
                throws Exception {
            doFilterInternal(request, response, chain);
        }
    }
}
