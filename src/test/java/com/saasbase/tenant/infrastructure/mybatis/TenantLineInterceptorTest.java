package com.saasbase.tenant.infrastructure.mybatis;

import com.saasbase.common.tenant.TenantContext;
import com.saasbase.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantLineInterceptorTest {

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void supplies_current_tenant_id_to_mybatis_plus_handler() {
        TenantContextHolder.set(new TenantContext(2001L, 1001L, false));
        SaasTenantLineHandler handler = new SaasTenantLineHandler();

        assertThat(handler.getTenantId().toString()).isEqualTo("2001");
    }

    @Test
    void ignores_platform_tables() {
        SaasTenantLineHandler handler = new SaasTenantLineHandler();

        assertThat(handler.ignoreTable("tenant")).isTrue();
        assertThat(handler.ignoreTable("iam_user")).isFalse();
    }
}
