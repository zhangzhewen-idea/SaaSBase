package com.saasbase.auth.infrastructure.security;

import com.saasbase.auth.domain.UserPrincipal;
import com.saasbase.auth.domain.gateway.TokenGateway;
import com.saasbase.auth.domain.gateway.TokenRevocationStore;
import com.saasbase.iam.domain.UserAuthState;
import com.saasbase.iam.domain.UserStatus;
import com.saasbase.iam.domain.gateway.UserSessionGateway;
import com.saasbase.common.tenant.TenantContext;
import com.saasbase.common.tenant.TenantContextHolder;
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
    private static final Set<String> PASSWORD_CHANGE_WHITELIST = Set.of(
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout",
            "/api/v1/auth/me");

    private final TokenGateway tokenGateway;
    private final TokenRevocationStore tokenRevocationStore;
    private final UserSessionGateway userSessionGateway;

    public JwtAuthenticationFilter(TokenGateway tokenGateway, TokenRevocationStore tokenRevocationStore,
                                   UserSessionGateway userSessionGateway) {
        this.tokenGateway = tokenGateway;
        this.tokenRevocationStore = tokenRevocationStore;
        this.userSessionGateway = userSessionGateway;
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
                UserAuthState state = userSessionGateway.getOrLoad(principal.tenantId(), principal.userId(),
                        () -> new UserAuthState(principal.tenantId(), principal.userId(), UserStatus.ACTIVE,
                                principal.sessionVersion(), principal.mustChangePassword()));
                if (state.status() == UserStatus.DISABLED) {
                    throw new IllegalArgumentException("User disabled");
                }
                if (state.sessionVersion() != principal.sessionVersion()) {
                    throw new IllegalArgumentException("User session expired");
                }
                if (principal.mustChangePassword() && !PASSWORD_CHANGE_WHITELIST.contains(request.getRequestURI())) {
                    throw new IllegalArgumentException("Password change required");
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
                        .anyMatch(PLATFORM_PERMISSIONS::contains)
                        && request.getRequestURI().startsWith("/api/v1/platform/");
                TenantContextHolder.set(new TenantContext(principal.tenantId(), principal.userId(), platformRequest));
            }
            filterChain.doFilter(request, response);
        } catch (RuntimeException exception) {
            int status = HttpServletResponse.SC_UNAUTHORIZED;
            String code = "AUTH_INVALID_TOKEN";
            String message = "登录状态已失效";
            if (exception.getMessage() != null && exception.getMessage().contains("disabled")) {
                status = HttpServletResponse.SC_FORBIDDEN;
                code = "AUTH_USER_DISABLED";
                message = "用户已禁用";
            } else if (exception.getMessage() != null && exception.getMessage().contains("Password change required")) {
                status = HttpServletResponse.SC_FORBIDDEN;
                code = "AUTH_PASSWORD_CHANGE_REQUIRED";
                message = "需要修改密码";
            } else if (exception.getMessage() != null && exception.getMessage().contains("expired")) {
                code = "AUTH_USER_SESSION_EXPIRED";
                message = "用户会话已过期";
            }
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
        } finally {
            TenantContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }
}
