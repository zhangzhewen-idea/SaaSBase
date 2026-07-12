package com.saasbase.tenant.domain;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantTest {

    @Test
    void creates_active_tenant_with_initial_versions() {
        Tenant tenant = Tenant.create("acme", "Acme");

        assertThat(tenant.id()).isNull();
        assertThat(tenant.tenantCode()).isEqualTo("acme");
        assertThat(tenant.tenantName()).isEqualTo("Acme");
        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.sessionVersion()).isZero();
        assertThat(tenant.version()).isZero();
    }

    @Test
    void rejects_invalid_creation_input() {
        assertThatThrownBy(() -> Tenant.create(" ", "Acme"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tenant.create("acme", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tenant.create("acme", "a".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitutes_persisted_tenant_and_rejects_invalid_state() {
        Tenant tenant = Tenant.reconstitute(1L, "acme", "Acme", TenantStatus.DISABLED, 2, 3);

        assertThat(tenant.id()).isEqualTo(1L);
        assertThat(tenant.authState()).isEqualTo(new TenantAuthState(1L, TenantStatus.DISABLED, 2));
        assertThat(tenant.version()).isEqualTo(3);
        assertThatThrownBy(() -> Tenant.reconstitute(null, "acme", "Acme", TenantStatus.ACTIVE, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tenant.reconstitute(1L, "acme", "Acme", TenantStatus.ACTIVE, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tenant.reconstitute(1L, "acme", "Acme", TenantStatus.ACTIVE, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renames_tenant_after_trimming_name() {
        Tenant tenant = Tenant.create("acme", "Acme");

        tenant.rename("  Acme China  ");

        assertThat(tenant.tenantName()).isEqualTo("Acme China");
        assertThatThrownBy(() -> tenant.rename(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tenant.rename("a".repeat(129))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disables_active_tenant_and_invalidates_existing_sessions_once() {
        Tenant tenant = Tenant.reconstitute(1L, "acme", "Acme", TenantStatus.ACTIVE, 4, 2);

        tenant.disable();

        assertThat(tenant.status()).isEqualTo(TenantStatus.DISABLED);
        assertThat(tenant.sessionVersion()).isEqualTo(5);
        assertThatThrownBy(tenant::disable)
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.TENANT_STATUS_CONFLICT);
        assertThat(tenant.sessionVersion()).isEqualTo(5);
    }

    @Test
    void enables_disabled_tenant_without_changing_session_version() {
        Tenant tenant = Tenant.reconstitute(1L, "acme", "Acme", TenantStatus.DISABLED, 5, 2);

        tenant.enable();

        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.sessionVersion()).isEqualTo(5);
        assertThatThrownBy(tenant::enable)
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).errorCode())
                .isEqualTo(ErrorCode.TENANT_STATUS_CONFLICT);
    }
}
