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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(service.disable(1L, 99L, 12L, 3L).status()).isEqualTo(UserStatus.DISABLED);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
            synchronization.afterCommit();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(auditGateway).appendAdminOperationAudit(any());
        verify(userSessionGateway).put(any());
    }

    @Test
    void resetPasswordAppendsSecurityAudit() {
        IamUser user = new IamUser(12L, 1L, "alice", "hash", UserStatus.ACTIVE, false, 3L);
        when(userGateway.findById(1L, 12L)).thenReturn(Optional.of(user));
        when(userGateway.update(user)).thenReturn(true);

        service.resetPassword(1L, 99L, 12L, new ChangePasswordCommand(12L, "new-pass", 3L));

        ArgumentCaptor<com.saasbase.audit.domain.SecurityAuditEvent> captor = ArgumentCaptor.forClass(com.saasbase.audit.domain.SecurityAuditEvent.class);
        verify(auditGateway).appendSecurityAudit(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("alice");
        verify(auditGateway).appendAdminOperationAudit(any());
        verify(userGateway).update(user);
        assertThat(user.passwordHash()).isEqualTo("{noop}new-pass");
    }

    @Test
    void createPublishesSessionStateAfterCommit() {
        when(userGateway.existsByUsername(1L, "alice")).thenReturn(false);
        when(userRoleAssignmentGateway.countActiveAdministratorsExcludingUser(anyLong(), anyLong())).thenReturn(1L);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.create(1L, 99L, createCommand());
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(userSessionGateway).put(any());
        verify(auditGateway).appendAdminOperationAudit(any());
    }

    @Test
    void disableRejectsStaleVersion() {
        IamUser user = new IamUser(12L, 1L, "alice", "hash", UserStatus.ACTIVE, false, 3L);
        when(userGateway.findById(1L, 12L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.disable(1L, 99L, 12L, 2L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void disableRejectsLastAdmin() {
        IamUser user = new IamUser(12L, 1L, "alice", "hash", UserStatus.ACTIVE, false, 3L);
        when(userGateway.findById(1L, 12L)).thenReturn(Optional.of(user));
        when(userRoleAssignmentGateway.countActiveAdministratorsExcludingUser(1L, 12L)).thenReturn(0L);

        assertThatThrownBy(() -> service.disable(1L, 99L, 12L, 3L))
                .isInstanceOf(BizException.class);
    }

    private CreateUserCommand createCommand() {
        return new CreateUserCommand("alice", "secret", "Alice", null, 101L, Set.of());
    }
}
