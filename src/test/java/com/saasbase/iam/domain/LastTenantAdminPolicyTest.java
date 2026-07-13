package com.saasbase.iam.domain;

import com.saasbase.common.error.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LastTenantAdminPolicyTest {

    @Test
    void rejects_removing_the_last_tenant_admin() {
        LastTenantAdminPolicy policy = new LastTenantAdminPolicy();

        assertThatThrownBy(() -> policy.ensureAssignable(true, true, 1))
                .isInstanceOf(BizException.class);
    }

    @Test
    void allows_changing_other_roles_or_when_more_than_one_admin_exists() {
        LastTenantAdminPolicy policy = new LastTenantAdminPolicy();

        policy.ensureAssignable(false, true, 1);
        policy.ensureAssignable(true, false, 1);
        policy.ensureAssignable(true, true, 2);
    }
}
