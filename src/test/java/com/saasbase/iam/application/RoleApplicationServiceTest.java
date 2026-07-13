package com.saasbase.iam.application;

import com.saasbase.common.api.PageResponse;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.iam.application.dto.RoleCommands.CreateRoleCommand;
import com.saasbase.iam.application.dto.RoleCommands.RolePageQuery;
import com.saasbase.iam.application.dto.RoleCommands.UpdateRoleCommand;
import com.saasbase.iam.application.dto.RoleView;
import com.saasbase.iam.domain.DataScope;
import com.saasbase.iam.domain.Role;
import com.saasbase.iam.domain.RoleStatus;
import com.saasbase.iam.domain.RoleType;
import com.saasbase.iam.domain.gateway.RoleGateway;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleApplicationServiceTest {
    private final RoleGateway roleGateway = mock(RoleGateway.class);
    private final RoleApplicationService service = new RoleApplicationService(roleGateway);

    @Test
    void creates_custom_role_in_current_tenant() {
        when(roleGateway.existsByCode(7L, "AUDITOR")).thenReturn(false);

        RoleView result = service.create(7L, new CreateRoleCommand("AUDITOR", "审计员", DataScope.SELF));

        assertThat(result.tenantId()).isEqualTo(7L);
        verify(roleGateway).insert(argThat(role -> role.roleType() == RoleType.CUSTOM && role.tenantId() == 7L));
    }

    @Test
    void rejects_duplicate_role_code() {
        when(roleGateway.existsByCode(7L, "AUDITOR")).thenReturn(true);

        assertThatThrownBy(() -> service.create(7L, new CreateRoleCommand("AUDITOR", "审计员", DataScope.SELF)))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.IAM_ROLE_CODE_CONFLICT));
    }

    @Test
    void update_throws_when_optimistic_lock_fails() {
        when(roleGateway.findById(7L, 71L)).thenReturn(Optional.of(Role.create(71L, 7L, "AUDITOR", "审计员", DataScope.SELF)));
        when(roleGateway.update(any(Role.class), eq(3L))).thenReturn(false);

        assertThatThrownBy(() -> service.update(7L, 71L, 3L, new UpdateRoleCommand("审计负责人", DataScope.ALL, RoleStatus.ACTIVE)))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.IAM_OPTIMISTIC_LOCK_CONFLICT));
    }

    @Test
    void delete_uses_tenant_scoped_lookup() {
        when(roleGateway.findById(7L, 71L)).thenReturn(Optional.of(Role.create(71L, 7L, "AUDITOR", "审计员", DataScope.SELF)));

        service.delete(7L, 71L, 99L);

        verify(roleGateway).deleteRelationsAndSoftDelete(7L, 71L, 99L);
    }

    @Test
    void delete_rejects_built_in_tenant_admin() {
        when(roleGateway.findById(7L, 71L)).thenReturn(Optional.of(Role.restore(71L, 7L, "TENANT_ADMIN", "管理员",
                RoleType.BUILT_IN, RoleStatus.ACTIVE, DataScope.ALL, 0L)));

        assertThatThrownBy(() -> service.delete(7L, 71L, 99L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.IAM_BUILT_IN_ROLE_PROTECTED));
    }

    @Test
    void page_delegates_and_maps_results() {
        Role role = Role.create(71L, 7L, "AUDITOR", "审计员", DataScope.SELF);
        when(roleGateway.page(7L, "审", RoleStatus.ACTIVE, RoleType.CUSTOM, 1L, 20L))
                .thenReturn(new PageResponse<>(java.util.List.of(role), 1L, 1L, 20L));

        PageResponse<RoleView> result = service.page(7L, new RolePageQuery("审", RoleStatus.ACTIVE, RoleType.CUSTOM, 1L, 20L));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).roleCode()).isEqualTo("AUDITOR");
    }
}
