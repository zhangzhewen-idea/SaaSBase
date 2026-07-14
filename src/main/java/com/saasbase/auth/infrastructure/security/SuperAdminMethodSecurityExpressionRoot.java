package com.saasbase.auth.infrastructure.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

class SuperAdminMethodSecurityExpressionRoot implements MethodSecurityExpressionOperations {
    private final Authentication authentication;
    private Object filterObject;
    private Object returnObject;

    SuperAdminMethodSecurityExpressionRoot(Authentication authentication) {
        this.authentication = authentication;
    }

    public boolean hasAuthority(String authority) {
        return isSuperAdmin() || authorities().stream().anyMatch(authority::equals);
    }

    public boolean hasAnyAuthority(String... authorities) {
        return isSuperAdmin() || Arrays.stream(authorities).anyMatch(this::hasAuthority);
    }

    public boolean hasRole(String role) {
        return hasAuthority(role);
    }

    public boolean hasAnyRole(String... roles) {
        return hasAnyAuthority(roles);
    }

    public boolean isAuthenticated() {
        return authentication != null && authentication.isAuthenticated();
    }

    public boolean isFullyAuthenticated() {
        return isAuthenticated();
    }

    public boolean isAnonymous() {
        return authentication == null;
    }

    public boolean isRememberMe() {
        return false;
    }

    public boolean permitAll() {
        return true;
    }

    public boolean denyAll() {
        return false;
    }

    public Object getPrincipal() {
        return authentication == null ? null : authentication.getPrincipal();
    }

    @Override
    public Object getFilterObject() {
        return filterObject;
    }

    @Override
    public void setFilterObject(Object filterObject) {
        this.filterObject = filterObject;
    }

    @Override
    public Object getReturnObject() {
        return returnObject;
    }

    @Override
    public void setReturnObject(Object returnObject) {
        this.returnObject = returnObject;
    }

    @Override
    public Object getThis() {
        return this;
    }

    @Override
    public Authentication getAuthentication() {
        return authentication;
    }

    @Override
    public boolean hasPermission(Object target, Object permission) {
        return false;
    }

    @Override
    public boolean hasPermission(Object targetId, String targetType, Object permission) {
        return false;
    }

    private Collection<? extends GrantedAuthority> authorities() {
        return authentication == null ? java.util.List.of() : authentication.getAuthorities();
    }

    private boolean isSuperAdmin() {
        return authorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .anyMatch("SUPER_ADMIN"::equals);
    }
}
