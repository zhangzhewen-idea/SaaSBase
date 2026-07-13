package com.saasbase.iam.application;

import com.saasbase.audit.domain.gateway.AuditGateway;
import com.saasbase.common.error.BizException;
import com.saasbase.iam.application.dto.UserCommands.ChangePasswordCommand;
import com.saasbase.iam.application.dto.UserCommands.CreateUserCommand;
import com.saasbase.iam.application.dto.UserCommands.UpdateUserCommand;
import com.saasbase.iam.domain.IamUser;
import com.saasbase.iam.domain.UserStatus;
import com.saasbase.iam.domain.gateway.DepartmentReferenceGateway;
import com.saasbase.iam.domain.gateway.UserGateway;
import com.saasbase.iam.domain.gateway.UserRoleAssignmentGateway;
import com.saasbase.iam.domain.gateway.UserSessionGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UserApplicationServiceTest {
    UserGateway userGateway = mock(UserGateway.class);
    DepartmentReferenceGateway departmentReferenceGateway = mock(DepartmentReferenceGateway.class);
    UserRoleAssignmentGateway userRoleAssignmentGateway = mock(UserRoleAssignmentGateway.class);
    UserSessionGateway userSessionGateway = mock(UserSessionGateway.class);
    AuditGateway auditGateway = mock(AuditGateway.class);
    UserApplicationService service = new UserApplicationService(
            userGateway, departmentReferenceGateway, userRoleAssignmentGateway, userSessionGateway, auditGateway);

    @Test
    void createRejectsDuplicateUsername() {
        when(userGateway.existsByUsername(1L, "alice")).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, 99L, createCommand()))
                .isInstanceOf(BizException.class);
    }

    @Test
    void disableRejectsSelfOperation() {
        assertThatThrownBy(() -> service.disable(1L, 11L, 11L, 1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void disableIgnoresCacheFailureAfterCommit() {
        IamUser user = new IamUser(12L, 1L, "alice", "hash", UserStatus.ACTIVE, false, 3L);
        when(userGateway.findById(1L, 12L)).thenReturn(Optional.of(user));
        when(userGateway.update(user)).thenReturn(true);
        when(userRoleAssignmentGateway.countActiveAdministratorsExcludingUser(1L, 12L)).thenReturn(1L);
        doThrow(new DataAccessResourceFailureException("down")).when(userSessionGateway).put(any());

        assertThat(service.disable(1L, 99L, 12L, 3L).status()).isEqualTo(UserStatus.DISABLED);
        verify(auditGateway).appendAdminOperationAudit(any());
    }

    @Test
    void resetPasswordAppendsSecurityAudit() {
        IamUser user = new IamUser(12L, 1L, "alice", "hash", UserStatus.ACTIVE, false, 3L);
        when(userGateway.findById(1L, 12L)).thenReturn(Optional.of(user));
        when(userGateway.update(user)).thenReturn(true);

        service.resetPassword(1L, 99L, 12L, new ChangePasswordCommand(12L, "new-pass", 3L));

        verify(auditGateway).appendSecurityAudit(any());
        verify(auditGateway).appendAdminOperationAudit(any());
    }

    @Test
    void createPublishesSessionStateAfterCommit() {
        when(userGateway.existsByUsername(1L, "alice")).thenReturn(false);
        when(userRoleAssignmentGateway.countActiveAdministratorsExcludingUser(anyLong(), anyLong())).thenReturn(1L);

        service.create(1L, 99L, createCommand());

        verify(userSessionGateway, timeout(1000)).put(any());
    }

    private CreateUserCommand createCommand() {
        return new CreateUserCommand("alice", "secret", "Alice", null, null, Set.of());
    }
}
