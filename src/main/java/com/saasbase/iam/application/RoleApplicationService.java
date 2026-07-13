package com.saasbase.iam.application;

import com.saasbase.common.api.PageResponse;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.iam.application.dto.RoleCommands.CreateRoleCommand;
import com.saasbase.iam.application.dto.RoleCommands.RolePageQuery;
import com.saasbase.iam.application.dto.RoleCommands.UpdateRoleCommand;
import com.saasbase.iam.application.dto.RoleView;
import com.saasbase.iam.domain.Role;
import com.saasbase.iam.domain.RoleStatus;
import com.saasbase.iam.domain.RoleType;
import com.saasbase.iam.domain.gateway.RoleGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RoleApplicationService {
    private final RoleGateway roleGateway;

    public RoleApplicationService(RoleGateway roleGateway) {
        this.roleGateway = roleGateway;
    }

    @Transactional
    public RoleView create(long tenantId, CreateRoleCommand command) {
        if (roleGateway.existsByCode(tenantId, command.roleCode())) {
            throw new BizException(ErrorCode.IAM_ROLE_CODE_CONFLICT);
        }
        Role role = Role.create(nextId(), tenantId, command.roleCode(), command.roleName(), command.dataScope());
        roleGateway.insert(role);
        return toView(role);
    }

    @Transactional
    public RoleView update(long tenantId, long roleId, long expectedVersion, UpdateRoleCommand command) {
        Role role = roleGateway.findById(tenantId, roleId)
                .orElseThrow(() -> new BizException(ErrorCode.IAM_ROLE_NOT_FOUND));
        if (command.roleName() != null) {
            role.rename(command.roleName());
        }
        if (command.dataScope() != null) {
            role.changeDataScope(command.dataScope());
        }
        if (command.status() == RoleStatus.ACTIVE) {
            role.enable();
        } else if (command.status() == RoleStatus.DISABLED) {
            role.disable();
        }
        if (!roleGateway.update(role, expectedVersion)) {
            throw new BizException(ErrorCode.IAM_OPTIMISTIC_LOCK_CONFLICT);
        }
        return toView(role);
    }

    @Transactional
    public void delete(long tenantId, long roleId, long operatorId) {
        Role role = roleGateway.findById(tenantId, roleId)
                .orElseThrow(() -> new BizException(ErrorCode.IAM_ROLE_NOT_FOUND));
        role.delete();
        roleGateway.deleteRelationsAndSoftDelete(tenantId, roleId, operatorId);
    }

    public PageResponse<RoleView> page(long tenantId, RolePageQuery query) {
        PageResponse<Role> response = roleGateway.page(tenantId, query.keyword(), query.status(), query.type(),
                query.pageNo(), query.pageSize());
        return new PageResponse<>(response.items().stream().map(this::toView).toList(),
                response.total(), response.pageNo(), response.pageSize());
    }

    private RoleView toView(Role role) {
        return new RoleView(role.id(), role.tenantId(), role.roleCode(), role.roleName(), role.roleType(),
                role.status(), role.dataScope(), role.version(), role.deleted());
    }

    private long nextId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
