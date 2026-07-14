package com.saasbase.iam.infrastructure.persistence;

import com.saasbase.common.api.PageResponse;
import com.saasbase.iam.domain.IamUser;
import com.saasbase.iam.domain.UserPageQuery;
import com.saasbase.iam.domain.gateway.UserGateway;
import com.saasbase.iam.domain.gateway.UserRoleAssignmentGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Repository
public class UserPersistenceAdapter implements UserGateway, UserRoleAssignmentGateway {
    private final UserMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    public UserPersistenceAdapter(UserMapper mapper, JdbcTemplate jdbcTemplate) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsByUsername(long tenantId, String username) {
        return mapper.existsByUsername(tenantId, username);
    }

    @Override
    public Optional<IamUser> findById(long tenantId, long userId) {
        return mapper.findById(tenantId, userId)
                .map(this::toDomain);
    }

    @Override
    public PageResponse<IamUser> page(long tenantId, UserPageQuery query) {
        var items = mapper.listPage(tenantId, query).stream().map(this::toDomain).toList();
        Long total = mapper.countPage(tenantId, query);
        return new PageResponse<>(items, total == null ? 0L : total, query.page(), query.size());
    }

    @Override
    public void insert(IamUser user) {
        jdbcTemplate.update("""
                        INSERT INTO iam_user
                        (id, tenant_id, username, password_hash, display_name, phone, primary_department_id, status,
                         must_change_password, session_version, last_login_at, version, created_at, updated_at, deleted)
                        VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, NULL, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0)
                        """,
                user.id(), user.tenantId(), user.username(), user.passwordHash(), user.username(), user.primaryDepartmentId(),
                user.status().name(), user.mustChangePassword(), user.sessionVersion());
    }

    @Override
    public boolean update(IamUser user) {
        return jdbcTemplate.update("""
                        UPDATE iam_user
                           SET password_hash = ?,
                               primary_department_id = ?,
                               status = ?,
                               must_change_password = ?,
                               session_version = ?,
                               version = version + 1,
                               updated_at = CURRENT_TIMESTAMP(6)
                         WHERE tenant_id = ?
                           AND id = ?
                           AND version = ?
                        """,
                user.passwordHash(), user.primaryDepartmentId(), user.status().name(), user.mustChangePassword(), user.sessionVersion(),
                user.tenantId(), user.id(), Math.max(0L, user.sessionVersion() - 1)) == 1;
    }

    public void assertDepartmentActive(long tenantId, long departmentId) {
        if (mapper.findActiveDepartmentId(tenantId, departmentId).isEmpty()) {
            throw new IllegalArgumentException("department not active");
        }
    }

    @Override
    public void replaceRoles(long tenantId, long userId, Set<Long> roleIds) {
        mapper.deleteRoles(tenantId, userId);
        mapper.insertRoles(tenantId, userId, new LinkedHashSet<>(roleIds));
    }

    @Override
    public Set<Long> findRoleIds(long tenantId, long userId) {
        return mapper.findRoleIds(tenantId, userId);
    }

    @Override
    public long countActiveAdministratorsExcludingUser(long tenantId, long userId) {
        return mapper.countActiveAdministratorsExcludingUser(tenantId, userId);
    }

    @Override
    public void lockTenantAdminRole(long tenantId) {
        mapper.lockTenantAdminRole(tenantId);
    }

    @Override
    public void assertRoleActive(long tenantId, long roleId) {
        if (mapper.findActiveRoleId(tenantId, roleId).isEmpty()) {
            throw new IllegalArgumentException("role not active");
        }
    }

    private IamUser toDomain(UserRecord record) {
        return new IamUser(
                record.id(),
                record.tenantId(),
                record.username(),
                record.primaryDepartmentId(),
                record.passwordHash(),
                com.saasbase.iam.domain.UserStatus.valueOf(record.status()),
                Boolean.TRUE.equals(record.mustChangePassword()),
                record.sessionVersion()
        );
    }

    private UserRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new UserRecord(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                rs.getString("phone"),
                rs.getObject("primary_department_id", Long.class),
                rs.getString("status"),
                rs.getBoolean("must_change_password"),
                rs.getLong("session_version"),
                rs.getObject("last_login_at", java.time.LocalDateTime.class),
                rs.getLong("version"),
                rs.getObject("created_at", java.time.LocalDateTime.class),
                rs.getObject("updated_at", java.time.LocalDateTime.class),
                rs.getBoolean("deleted")
        );
    }
}