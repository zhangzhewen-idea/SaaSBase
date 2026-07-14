package com.saasbase.auth.infrastructure.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private static final Logger log = LoggerFactory.getLogger(SecurityErrorHandler.class);

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.AuthenticationException authException)
            throws IOException {
        log.warn("认证失败: {} {}", request.getMethod(), request.getRequestURI(), authException);
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        log.warn("权限拒绝: {} {}", request.getMethod(), request.getRequestURI(), accessDeniedException);
        writeError(response, HttpServletResponse.SC_FORBIDDEN);
    }

    private void writeError(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String code = status == HttpServletResponse.SC_UNAUTHORIZED ? "AUTH_UNAUTHORIZED" : "AUTH_FORBIDDEN";
        String message = status == HttpServletResponse.SC_UNAUTHORIZED ? "未登录或登录状态已失效" : "权限不足";
        response.getWriter().write("{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
