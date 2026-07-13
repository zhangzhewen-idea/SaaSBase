package com.saasbase.iam.domain;

import com.saasbase.common.error.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleTest {

    @Test
    void built_in_tenant_admin_allows_rename_and_data_scope_change_but_rejects_code_change_disable_and_delete() {
        Role role = Role.restore(1L, 10L, "TENANT_ADMIN", "管理员", RoleType.BUILT_IN,
                RoleStatus.ACTIVE, DataScope.ALL, 0L);

        role.rename("租户管理员");
        role.changeDataScope(DataScope.DEPT_ONLY);

        assertThat(role.roleName()).isEqualTo("租户管理员");
        assertThat(role.dataScope()).isEqualTo(DataScope.DEPT_ONLY);
        assertThatThrownBy(role::disable)
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(com.saasbase.common.error.ErrorCode.IAM_BUILT_IN_ROLE_PROTECTED);
        assertThatThrownBy(() -> role.changeCode("ADMIN"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(com.saasbase.common.error.ErrorCode.IAM_BUILT_IN_ROLE_PROTECTED);
        assertThatThrownBy(role::delete)
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(com.saasbase.common.error.ErrorCode.IAM_BUILT_IN_ROLE_PROTECTED);
    }

    @Test
    void custom_role_supports_lifecycle_changes() {
        Role role = Role.create(7L, 70L, "AUDITOR", "审计员", DataScope.SELF);

        role.rename("审计角色");
        role.changeCode("AUDIT");
        role.changeDataScope(DataScope.ALL);
        role.disable();
        role.enable();
        role.delete();

        assertThat(role.roleCode()).isEqualTo("AUDIT");
        assertThat(role.roleName()).isEqualTo("审计角色");
        assertThat(role.dataScope()).isEqualTo(DataScope.ALL);
        assertThat(role.status()).isEqualTo(RoleStatus.DISABLED);
        assertThat(role.deleted()).isTrue();
    }

    @Test
    void change_code_allows_same_value_for_built_in_compatibility() {
        Role role = Role.restore(1L, 10L, "TENANT_ADMIN", "管理员", RoleType.BUILT_IN,
                RoleStatus.ACTIVE, DataScope.ALL, 0L);

        role.changeCode("TENANT_ADMIN");

        assertThat(role.roleCode()).isEqualTo("TENANT_ADMIN");
    }

    @Test
    void other_built_in_roles_are_not_over_protected() {
        Role role = Role.restore(1L, 10L, "SYSTEM_AUDITOR", "系统审计员", RoleType.BUILT_IN,
                RoleStatus.ACTIVE, DataScope.ALL, 0L);

        role.disable();
        role.changeCode("SYSTEM_AUDITOR_V2");

        assertThat(role.status()).isEqualTo(RoleStatus.DISABLED);
        assertThat(role.roleCode()).isEqualTo("SYSTEM_AUDITOR_V2");
    }

    @Test
    void disable_does_not_mark_role_deleted() {
        Role role = Role.create(7L, 70L, "AUDITOR", "审计员", DataScope.SELF);

        role.disable();

        assertThat(role.status()).isEqualTo(RoleStatus.DISABLED);
        assertThat(role.deleted()).isFalse();
    }
}
