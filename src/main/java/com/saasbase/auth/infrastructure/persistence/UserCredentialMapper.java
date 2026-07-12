package com.saasbase.auth.infrastructure.persistence;

import com.saasbase.auth.domain.UserCredential;
import com.saasbase.auth.domain.gateway.UserCredentialGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public class UserCredentialMapper implements UserCredentialGateway {
    private final JdbcTemplate jdbcTemplate;

    public UserCredentialMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserCredential> findByTenantCodeAndUsername(String tenantCode, String username) {
        String sql = """
                SELECT u.id, u.tenant_id, u.username, u.password_hash,
                       COALESCE(GROUP_CONCAT(p.permission_code ORDER BY p.permission_code SEPARATOR ','), '')
                  FROM tenant t
                  JOIN iam_user u ON u.tenant_id = t.id AND u.deleted = 0
                  LEFT JOIN iam_user_role ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.id
                  LEFT JOIN iam_role_permission rp ON rp.tenant_id = ur.tenant_id AND rp.role_id = ur.role_id
                  LEFT JOIN iam_permission p ON p.id = rp.permission_id
                 WHERE t.tenant_code = ? AND u.username = ? AND t.status = 'ACTIVE'
                 GROUP BY u.id, u.tenant_id, u.username, u.password_hash
                """;
        return jdbcTemplate.query(sql, resultSet -> {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            String permissions = resultSet.getString(5);
            Set<String> permissionSet = permissions == null || permissions.isBlank()
                    ? Set.of()
                    : Set.of(permissions.split(","));
            return Optional.of(new UserCredential(
                    resultSet.getLong(1),
                    resultSet.getLong(2),
                    resultSet.getString(3),
                    resultSet.getString(4),
                    permissionSet));
        }, tenantCode, username);
    }
}
