package com.saasbase.auth.infrastructure.security;

import com.saasbase.auth.domain.UserPrincipal;
import com.saasbase.auth.domain.gateway.TokenGateway;
import com.saasbase.auth.domain.gateway.TokenRevocationStore;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.common.tenant.TenantContext;
import com.saasbase.common.tenant.TenantContextHolder;
import com.saasbase.tenant.domain.TenantAuthState;
import com.saasbase.tenant.domain.TenantStatus;
import com.saasbase.tenant.domain.gateway.TenantAuthStateGateway;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> PLATFORM_PERMISSIONS = Set.of("platform:tenant:read", "platform:tenant:write");

    private final TokenGateway tokenGateway;
    private final TokenRevocationStore tokenRevocationStore;
    private final TenantAuthStateGateway tenantAuthStateGateway;

    public JwtAuthenticationFilter(
            TokenGateway tokenGateway,
            TokenRevocationStore tokenRevocationStore,
            TenantAuthStateGateway tenantAuthStateGateway) {
        this.tokenGateway = tokenGateway;
        this.tokenRevocationStore = tokenRevocationStore;
        this.tenantAuthStateGateway = tenantAuthStateGateway;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveBearerToken(request);
        try {
            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String tokenId = tokenGateway.parseTokenId(token);
                if (tokenRevocationStore.isRevoked(tokenId)) {
                    throw new IllegalArgumentException("Token revoked");
                }
                UserPrincipal principal = tokenGateway.parseAccessToken(token);
                TenantAuthState current = tenantAuthStateGateway.requireCurrent(principal.tenantId());
                if (current.status() != TenantStatus.ACTIVE || current.sessionVersion() != principal.sessionVersion()) {
                    throw new BizException(ErrorCode.AUTH_TENANT_SESSION_EXPIRED);
                }
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        token,
                        principal.permissions().stream()
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toUnmodifiableList()));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                boolean platformRequest = principal.permissions().stream()
                        .anyMatch(permission -> permission.startsWith("platform:tenant:"))
                        && request.getRequestURI().startsWith("/api/v1/platform/");
                TenantContextHolder.set(new TenantContext(principal.tenantId(), principal.userId(), platformRequest));
            }
            filterChain.doFilter(request, response);
        } catch (RuntimeException exception) {
            writeUnauthorized(response, exception);
        } finally {
            TenantContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, RuntimeException exception) throws IOException {
        ErrorCode errorCode = exception instanceof BizException bizException ? bizException.errorCode() : ErrorCode.AUTH_INVALID_TOKEN;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"code\":\"" + errorCode.name() + "\",\"message\":\""
                + errorCode.message() + "\"}");
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }
}
