package com.saasbase.iam.infrastructure.persistence;

import com.saasbase.common.api.PageResponse;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.iam.domain.Role;
import com.saasbase.iam.domain.RoleStatus;
import com.saasbase.iam.domain.RoleType;
import com.saasbase.iam.domain.gateway.RoleGateway;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

@Repository
public class RolePersistenceAdapter implements RoleGateway {
    private final RoleMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    public RolePersistenceAdapter(RoleMapper mapper, JdbcTemplate jdbcTemplate) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Role> findById(long tenantId, long roleId) {
        return mapper.findById(tenantId, roleId).map(RoleRecord::toDomain);
    }

    @Override
    public boolean existsByCode(long tenantId, String roleCode) {
        return mapper.existsByCode(tenantId, roleCode);
    }

    @Override
    public PageResponse<Role> page(long tenantId, String keyword, RoleStatus status, RoleType type, long pageNo, long pageSize) {
        long offset = Math.max(0, pageNo - 1) * pageSize;
        List<Role> items = mapper.page(tenantId, keyword, status, type, offset, pageSize)
                .stream()
                .map(RoleRecord::toDomain)
                .toList();
        long total = mapper.countPage(tenantId, keyword, status, type);
        return new PageResponse<>(items, total, pageNo, pageSize);
    }

    @Override
    public void insert(Role role) {
        mapper.insert(toRecord(role));
    }

    @Override
    public boolean update(Role role, long expectedVersion) {
        return mapper.update(toRecord(role), expectedVersion) > 0;
    }

    @Override
    public void deleteRelationsAndSoftDelete(long tenantId, long roleId, long operatorId) {
        jdbcTemplate.update("DELETE FROM iam_role_permission WHERE tenant_id = ? AND role_id = ?", tenantId, roleId);
        jdbcTemplate.update("DELETE FROM iam_user_role WHERE tenant_id = ? AND role_id = ?", tenantId, roleId);
        if (mapper.softDelete(tenantId, roleId, operatorId) == 0) {
            throw new BizException(ErrorCode.IAM_OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private RoleRecord toRecord(Role role) {
        return new RoleRecord(role.id(), role.tenantId(), role.roleCode(), role.roleName(), role.roleType().name(),
                role.status().name(), role.dataScope().name(), role.version(), role.deleted(), null, null, null, null, null, null);
    }
}
