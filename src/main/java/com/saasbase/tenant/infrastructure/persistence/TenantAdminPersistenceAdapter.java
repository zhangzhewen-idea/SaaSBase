package com.saasbase.tenant.infrastructure.persistence;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.tenant.domain.gateway.TenantAdminInitializer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Component
public class TenantAdminPersistenceAdapter implements TenantAdminInitializer {
    private final TenantAdminMapper mapper;
    private final PasswordEncoder encoder;
    private final Clock clock;

    @Autowired
    public TenantAdminPersistenceAdapter(TenantAdminMapper mapper, PasswordEncoder encoder) {
        this(mapper, encoder, Clock.systemUTC());
    }

    TenantAdminPersistenceAdapter(TenantAdminMapper mapper, PasswordEncoder encoder, Clock clock) {
        this.mapper = mapper; this.encoder = encoder; this.clock = clock;
    }

    @Override
    public void initialize(Long tenantId, String username, String displayName, String rawPassword, Long operatorId) {
        Objects.requireNonNull(tenantId, "tenantId"); Objects.requireNonNull(operatorId, "operatorId");
        Objects.requireNonNull(rawPassword, "rawPassword");
        String normalizedUsername = requireText(username, 64, "username");
        String normalizedDisplayName = requireText(displayName, 128, "displayName");
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        var user = new TenantAdminRecord(tenantId, normalizedUsername, normalizedDisplayName, encoder.encode(rawPassword), now, operatorId);
        try { mapper.insertUser(user); }
        catch (DuplicateKeyException error) { throw new BizException(ErrorCode.IAM_USERNAME_CONFLICT); }
        requireGeneratedId(user);
        var role = new TenantAdminRecord(tenantId, "TENANT_ADMIN", "租户管理员", null, now, operatorId);
        mapper.insertRole(role);
        requireGeneratedId(role);
        mapper.insertUserRole(tenantId, user.getId(), role.getId());
        var permissionIds = mapper.findTenantPermissionIds();
        if (permissionIds.isEmpty() || !mapper.hasPermissionCode("tenant:profile:read")) {
            throw new BizException(ErrorCode.IAM_PERMISSION_TEMPLATE_MISSING);
        }
        mapper.insertRolePermissions(tenantId, role.getId(), permissionIds);
    }

    private static String requireText(String value, int max, String name) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > max) throw new IllegalArgumentException(name);
        return value.trim();
    }
    private static void requireGeneratedId(TenantAdminRecord record) {
        if (record.getId() == null || record.getId() < 1) throw new IllegalStateException("Database did not generate id");
    }
}
