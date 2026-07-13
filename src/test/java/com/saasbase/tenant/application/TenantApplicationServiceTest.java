package com.saasbase.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;

import com.saasbase.audit.domain.AdminOperationAuditEvent;
import com.saasbase.audit.domain.gateway.AuditGateway;
import com.saasbase.common.api.PageResponse;
import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.tenant.application.dto.CreateTenantRequest;
import com.saasbase.tenant.application.dto.TenantQuery;
import com.saasbase.tenant.application.dto.TenantResponse;
import com.saasbase.tenant.application.dto.UpdateTenantRequest;
import com.saasbase.tenant.domain.Tenant;
import com.saasbase.tenant.domain.TenantAuthState;
import com.saasbase.tenant.domain.TenantStatus;
import com.saasbase.tenant.domain.gateway.TenantAdminInitializer;
import com.saasbase.tenant.domain.gateway.TenantAuthStateGateway;
import com.saasbase.tenant.domain.gateway.TenantGateway;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TenantApplicationServiceTest {

    @Test
    void create_orders_gateway_initializer_audit_and_cache() {
        TenantGateway tenantGateway = mock(TenantGateway.class);
        TenantAdminInitializer initializer = mock(TenantAdminInitializer.class);
        TenantAuthStateGateway authStateGateway = mock(TenantAuthStateGateway.class);
        AuditGateway auditGateway = mock(AuditGateway.class);
        when(tenantGateway.existsByCode("acme")).thenReturn(false);
        when(tenantGateway.insert(any(Tenant.class), eq(9L)))
                .thenReturn(Tenant.reconstitute(11L, "acme", "Acme", TenantStatus.ACTIVE, 2L, 0L));
        when(authStateGateway.requireCurrent(11L))
                .thenReturn(new TenantAuthState(11L, TenantStatus.ACTIVE, 2L));
        TenantApplicationService service = new TenantApplicationService(
                tenantGateway, initializer, authStateGateway, auditGateway, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        TenantResponse response = service.create(
                new CreateTenantRequest("acme", "Acme", "admin", "管理员", "Secret123456"),
                9L);

        assertThat(response).isEqualTo(TenantResponse.from(Tenant.reconstitute(11L, "acme", "Acme", TenantStatus.ACTIVE, 2L, 0L)));
        var order = inOrder(tenantGateway, initializer, auditGateway, authStateGateway);
        order.verify(tenantGateway).existsByCode("acme");
        order.verify(tenantGateway).insert(any(Tenant.class), eq(9L));
        order.verify(initializer).initialize(11L, "admin", "管理员", "Secret123456", 9L);
        order.verify(auditGateway).appendAdminOperationAudit(any(AdminOperationAuditEvent.class));
        verify(authStateGateway).cache(new TenantAuthState(11L, TenantStatus.ACTIVE, 2L));
    }

    @Test
    void create_rejects_duplicate_tenant_code() {
        TenantGateway tenantGateway = mock(TenantGateway.class);
        TenantAdminInitializer initializer = mock(TenantAdminInitializer.class);
        TenantAuthStateGateway authStateGateway = mock(TenantAuthStateGateway.class);
        AuditGateway auditGateway = mock(AuditGateway.class);
        when(tenantGateway.existsByCode("acme")).thenReturn(true);
        TenantApplicationService service = new TenantApplicationService(tenantGateway, initializer, authStateGateway, auditGateway);

        assertThatThrownBy(() -> service.create(
                new CreateTenantRequest("acme", "Acme", "admin", "管理员", "Secret123456"),
                9L))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(ErrorCode.TENANT_CODE_CONFLICT);
    }

    @Test
    void update_enable_disable_and_current_profile_use_gateway_state() {
        TenantGateway tenantGateway = mock(TenantGateway.class);
        TenantAdminInitializer initializer = mock(TenantAdminInitializer.class);
        TenantAuthStateGateway authStateGateway = mock(TenantAuthStateGateway.class);
        AuditGateway auditGateway = mock(AuditGateway.class);
        Tenant existing = Tenant.reconstitute(11L, "acme", "Acme", TenantStatus.ACTIVE, 2L, 5L);
        when(tenantGateway.findById(11L)).thenReturn(Optional.of(existing));
        when(tenantGateway.update(any(Tenant.class), eq(9L))).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        TenantApplicationService service = new TenantApplicationService(tenantGateway, initializer, authStateGateway, auditGateway);

        TenantResponse updated = service.update(11L, new UpdateTenantRequest("After"), 9L);
        assertThat(updated.tenantName()).isEqualTo("After");
        assertThat(service.currentProfile(11L).tenantCode()).isEqualTo("acme");

        when(tenantGateway.findById(11L)).thenReturn(Optional.of(
                Tenant.reconstitute(11L, "acme", "After", TenantStatus.ACTIVE, 2L, 6L)));
        TenantResponse disabled = service.disable(11L, 9L);
        assertThat(disabled.status()).isEqualTo(TenantStatus.DISABLED);
        when(tenantGateway.findById(11L)).thenReturn(Optional.of(
                Tenant.reconstitute(11L, "acme", "After", TenantStatus.DISABLED, 3L, 7L)));
        TenantResponse enabled = service.enable(11L, 9L);
        assertThat(enabled.status()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void page_maps_gateway_page_to_response() {
        TenantGateway tenantGateway = mock(TenantGateway.class);
        TenantAdminInitializer initializer = mock(TenantAdminInitializer.class);
        TenantAuthStateGateway authStateGateway = mock(TenantAuthStateGateway.class);
        AuditGateway auditGateway = mock(AuditGateway.class);
        Tenant tenant = Tenant.reconstitute(11L, "acme", "Acme", TenantStatus.ACTIVE, 2L, 0L);
        when(tenantGateway.page(new TenantGateway.Query(null, null, TenantStatus.ACTIVE, 1, 10)))
                .thenReturn(new TenantGateway.Page(List.of(tenant), 1, 1, 10));
        TenantApplicationService service = new TenantApplicationService(tenantGateway, initializer, authStateGateway, auditGateway);

        PageResponse<TenantResponse> response = service.page(new TenantQuery(null, null, TenantStatus.ACTIVE, 1, 10));

        assertThat(response.items()).containsExactly(TenantResponse.from(tenant));
        assertThat(response.total()).isEqualTo(1);
    }
}
