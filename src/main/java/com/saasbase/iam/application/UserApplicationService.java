package com.saasbase.iam.application;

import com.saasbase.audit.domain.AdminOperationAuditEvent;
import com.saasbase.audit.domain.SecurityAuditEvent;
import com.saasbase.audit.domain.gateway.AuditGateway;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.common.tenant.TenantContext;
import com.saasbase.common.tenant.TenantContextHolder;
import com.saasbase.iam.application.dto.UserCommands.ChangePasswordCommand;
import com.saasbase.iam.application.dto.UserCommands.CreateUserCommand;
import com.saasbase.iam.application.dto.UserCommands.ToggleUserCommand;
import com.saasbase.iam.application.dto.UserCommands.UpdateUserCommand;
import com.saasbase.iam.application.dto.UserView;
import com.saasbase.iam.domain.IamUser;
import com.saasbase.iam.domain.UserAuthState;
import com.saasbase.common.api.PageResponse;
import com.saasbase.iam.domain.UserPageQuery;
import com.saasbase.iam.domain.UserStatus;
import com.saasbase.iam.domain.gateway.DepartmentReferenceGateway;
import com.saasbase.iam.domain.gateway.UserGateway;
import com.saasbase.iam.domain.gateway.UserRoleAssignmentGateway;
import com.saasbase.iam.domain.gateway.UserSessionGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Set;

@Service
public class UserApplicationService {
    private final UserGateway userGateway;
    private final DepartmentReferenceGateway departmentReferenceGateway;
    private final UserRoleAssignmentGateway userRoleAssignmentGateway;
    private final UserSessionGateway userSessionGateway;
    private final AuditGateway auditGateway;

    public UserApplicationService(UserGateway userGateway,
                                  @Qualifier("departmentPersistenceAdapter") DepartmentReferenceGateway departmentReferenceGateway,
                                  UserRoleAssignmentGateway userRoleAssignmentGateway,
                                  UserSessionGateway userSessionGateway,
                                  AuditGateway auditGateway) {
        this.userGateway = userGateway;
        this.departmentReferenceGateway = departmentReferenceGateway;
        this.userRoleAssignmentGateway = userRoleAssignmentGateway;
        this.userSessionGateway = userSessionGateway;
        this.auditGateway = auditGateway;
    }

    @Transactional
    public UserView create(long tenantId, long operatorId, CreateUserCommand command) {
        if (userGateway.existsByUsername(tenantId, command.username())) {
            throw new BizException(ErrorCode.IAM_USERNAME_CONFLICT);
        }
        validateDepartment(tenantId, command.primaryDepartmentId());
        command.roleIds().forEach(roleId -> userRoleAssignmentGateway.assertRoleActive(tenantId, roleId));
        IamUser user = new IamUser(nextUserId(), tenantId, command.username(), command.primaryDepartmentId(),
                encode(command.initialPassword()), UserStatus.ACTIVE, false, 0L);
        userGateway.insert(user);
        userRoleAssignmentGateway.replaceRoles(tenantId, user.id(), command.roleIds());
        appendAudit(tenantId, operatorId, "CREATE", user.id());
        publishSessionAfterCommit(user);
        return toView(user, command.roleIds());
    }

    @Transactional
    public UserView update(long tenantId, long operatorId, UpdateUserCommand command) {
        IamUser user = userGateway.findById(tenantId, command.userId())
                .orElseThrow(() -> new BizException(ErrorCode.IAM_USER_NOT_FOUND));
        assertVersion(user, command.version());
        validateDepartment(tenantId, command.primaryDepartmentId());
        command.roleIds().forEach(roleId -> userRoleAssignmentGateway.assertRoleActive(tenantId, roleId));
        user.changePrimaryDepartment(command.primaryDepartmentId());
        if (!userGateway.update(user)) {
            throw new BizException(ErrorCode.IAM_USER_CONCURRENT_MODIFICATION);
        }
        userRoleAssignmentGateway.replaceRoles(tenantId, user.id(), command.roleIds());
        appendAudit(tenantId, operatorId, "UPDATE", user.id());
        publishSessionAfterCommit(user);
        return toView(user, command.roleIds());
    }

    @Transactional(readOnly = true)
    public PageResponse<UserView> page(long tenantId, UserPageQuery query) {
        PageResponse<IamUser> page = userGateway.page(tenantId, query);
        return new PageResponse<>(
                page.items().stream().map(user -> toView(user, userRoleAssignmentGateway.findRoleIds(tenantId, user.id()))).toList(),
                page.total(),
                page.pageNo(),
                page.pageSize());
    }

    @Transactional(readOnly = true)
    public UserView get(long tenantId, long userId) {
        IamUser user = loadUser(tenantId, userId);
        return toView(user, userRoleAssignmentGateway.findRoleIds(tenantId, user.id()));
    }

    @Transactional
    public UserView disable(long tenantId, long operatorId, long targetUserId, long version) {
        assertNotSelf(operatorId, targetUserId, ErrorCode.IAM_SELF_OPERATION_FORBIDDEN);
        IamUser user = loadUser(tenantId, targetUserId);
        assertVersion(user, version);
        if (user.status() != UserStatus.ACTIVE) {
            throw new BizException(ErrorCode.IAM_USER_STATUS_CONFLICT);
        }
        assertLastAdminProtected(tenantId, targetUserId);
        user.disable();
        if (!userGateway.update(user)) {
            throw new BizException(ErrorCode.IAM_USER_CONCURRENT_MODIFICATION);
        }
        appendAudit(tenantId, operatorId, "DISABLE", user.id());
        publishSessionAfterCommit(user);
        return toView(user, userRoleAssignmentGateway.findRoleIds(tenantId, user.id()));
    }

    @Transactional
    public UserView enable(long tenantId, long operatorId, long targetUserId, long version) {
        IamUser user = loadUser(tenantId, targetUserId);
        assertVersion(user, version);
        user.enable();
        if (!userGateway.update(user)) {
            throw new BizException(ErrorCode.IAM_USER_CONCURRENT_MODIFICATION);
        }
        appendAudit(tenantId, operatorId, "ENABLE", user.id());
        publishSessionAfterCommit(user);
        return toView(user, userRoleAssignmentGateway.findRoleIds(tenantId, user.id()));
    }

    @Transactional
    public UserView resetPassword(long tenantId, long operatorId, long targetUserId, ChangePasswordCommand command) {
        assertNotSelf(operatorId, targetUserId, ErrorCode.IAM_SELF_OPERATION_FORBIDDEN);
        IamUser user = loadUser(tenantId, targetUserId);
        assertVersion(user, command.version());
        user.resetPassword(encode(command.newPassword()));
        if (!userGateway.update(user)) {
            throw new BizException(ErrorCode.IAM_USER_CONCURRENT_MODIFICATION);
        }
        auditGateway.appendSecurityAudit(new SecurityAuditEvent(tenantId, targetUserId, user.username(),
                "RESET_PASSWORD", "SUCCESS", null, Instant.now()));
        appendAudit(tenantId, operatorId, "RESET_PASSWORD", user.id());
        publishSessionAfterCommit(user);
        return toView(user, userRoleAssignmentGateway.findRoleIds(tenantId, user.id()));
    }

    private void publishSessionAfterCommit(IamUser user) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                userSessionGateway.put(UserAuthState.from(user));
            } catch (RuntimeException ignored) {
                // ignore cache failures; the database write is already committed
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    userSessionGateway.put(UserAuthState.from(user));
                } catch (RuntimeException ex) {
                    // ignore cache failures; the database write is already committed
                }
            }
        });
    }

    private void validateDepartment(long tenantId, Long departmentId) {
        if (departmentId != null) {
            departmentReferenceGateway.assertDepartmentActive(tenantId, departmentId);
        }
    }

    private void appendAudit(long tenantId, long operatorId, String operation, long resourceId) {
        auditGateway.appendAdminOperationAudit(new AdminOperationAuditEvent(
                tenantId, operatorId, operation, "IAM_USER", String.valueOf(resourceId), null, Instant.now()));
    }

    private IamUser loadUser(long tenantId, long userId) {
        return userGateway.findById(tenantId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.IAM_USER_NOT_FOUND));
    }

    private void assertNotSelf(long operatorId, long targetUserId, ErrorCode errorCode) {
        if (operatorId == targetUserId) {
            throw new BizException(errorCode);
        }
    }

    private void assertVersion(IamUser user, long expectedVersion) {
        if (user.sessionVersion() != expectedVersion) {
            throw new BizException(ErrorCode.IAM_USER_CONCURRENT_MODIFICATION);
        }
    }

    private void assertLastAdminProtected(long tenantId, long targetUserId) {
        userRoleAssignmentGateway.lockTenantAdminRole(tenantId);
        long otherAdmins = userRoleAssignmentGateway.countActiveAdministratorsExcludingUser(tenantId, targetUserId);
        if (otherAdmins == 0) {
            throw new BizException(ErrorCode.IAM_LAST_TENANT_ADMIN_PROTECTED);
        }
    }

    private UserView toView(IamUser user, Set<Long> roleIds) {
        return new UserView(user.id(), user.username(), user.username(), null, user.primaryDepartmentId(), user.status(), user.sessionVersion(),
                user.mustChangePassword(), roleIds);
    }

    private long nextUserId() {
        return System.currentTimeMillis();
    }

    private String encode(String rawPassword) {
        return "{noop}" + rawPassword;
    }
}
