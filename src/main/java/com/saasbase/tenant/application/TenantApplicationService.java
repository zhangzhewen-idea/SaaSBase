package com.saasbase.tenant.application;

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
import com.saasbase.tenant.domain.TenantStatus;
import com.saasbase.tenant.domain.gateway.TenantAdminInitializer;
import com.saasbase.tenant.domain.gateway.TenantAuthStateGateway;
import com.saasbase.tenant.domain.gateway.TenantGateway;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TenantApplicationService {
    private final TenantGateway tenantGateway;
    private final TenantAdminInitializer tenantAdminInitializer;
    private final TenantAuthStateGateway tenantAuthStateGateway;
    private final AuditGateway auditGateway;
    private final Clock clock;

    @Autowired
    public TenantApplicationService(
            TenantGateway tenantGateway,
            TenantAdminInitializer tenantAdminInitializer,
            TenantAuthStateGateway tenantAuthStateGateway,
            AuditGateway auditGateway) {
        this(tenantGateway, tenantAdminInitializer, tenantAuthStateGateway, auditGateway, Clock.systemUTC());
    }

    TenantApplicationService(
            TenantGateway tenantGateway,
            TenantAdminInitializer tenantAdminInitializer,
            TenantAuthStateGateway tenantAuthStateGateway,
            AuditGateway auditGateway,
            Clock clock) {
        this.tenantGateway = tenantGateway;
        this.tenantAdminInitializer = tenantAdminInitializer;
        this.tenantAuthStateGateway = tenantAuthStateGateway;
        this.auditGateway = auditGateway;
        this.clock = clock;
    }

    @Transactional
    public TenantResponse create(CreateTenantRequest request, Long operatorId) {
        requirePositive(operatorId, "operatorId");
        if (tenantGateway.existsByCode(request.tenantCode())) {
            throw new BizException(ErrorCode.TENANT_CODE_CONFLICT);
        }

        Tenant created = tenantGateway.insert(Tenant.create(request.tenantCode(), request.tenantName()), operatorId);
        tenantAdminInitializer.initialize(
                created.id(),
                request.adminUsername(),
                request.adminDisplayName(),
                request.initialPassword(),
                operatorId);
        auditGateway.appendAdminOperationAudit(operation(created.id(), operatorId, "CREATE", "TENANT"));
        afterCommit(() -> tenantAuthStateGateway.cache(created.authState()));
        return TenantResponse.from(created);
    }

    @Transactional
    public TenantResponse update(Long tenantId, UpdateTenantRequest request, Long operatorId) {
        Tenant tenant = requireTenant(tenantId);
        tenant.rename(request.tenantName());
        Tenant saved = tenantGateway.update(tenant, operatorId)
                .orElseThrow(() -> new BizException(ErrorCode.TENANT_CONCURRENT_MODIFICATION));
        auditGateway.appendAdminOperationAudit(operation(saved.id(), operatorId, "UPDATE", "TENANT"));
        afterCommit(() -> tenantAuthStateGateway.cache(saved.authState()));
        return TenantResponse.from(saved);
    }

    @Transactional
    public TenantResponse enable(Long tenantId, Long operatorId) {
        Tenant tenant = requireTenant(tenantId);
        tenant.enable();
        Tenant saved = tenantGateway.update(tenant, operatorId)
                .orElseThrow(() -> new BizException(ErrorCode.TENANT_CONCURRENT_MODIFICATION));
        auditGateway.appendAdminOperationAudit(operation(saved.id(), operatorId, "ENABLE", "TENANT"));
        afterCommit(() -> tenantAuthStateGateway.cache(saved.authState()));
        return TenantResponse.from(saved);
    }

    @Transactional
    public TenantResponse disable(Long tenantId, Long operatorId) {
        Tenant tenant = requireTenant(tenantId);
        tenant.disable();
        Tenant saved = tenantGateway.update(tenant, operatorId)
                .orElseThrow(() -> new BizException(ErrorCode.TENANT_CONCURRENT_MODIFICATION));
        auditGateway.appendAdminOperationAudit(operation(saved.id(), operatorId, "DISABLE", "TENANT"));
        afterCommit(() -> tenantAuthStateGateway.cache(saved.authState()));
        return TenantResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantResponse> page(TenantQuery query) {
        TenantGateway.Page page = tenantGateway.page(query.toGatewayQuery());
        List<TenantResponse> items = page.items().stream().map(TenantResponse::from).toList();
        return new PageResponse<>(items, page.total(), page.pageNo(), page.pageSize());
    }

    @Transactional(readOnly = true)
    public TenantResponse currentProfile(Long tenantId) {
        return TenantResponse.from(requireTenant(tenantId));
    }

    private Tenant requireTenant(Long tenantId) {
        return tenantGateway.findById(tenantId)
                .orElseThrow(() -> new BizException(ErrorCode.TENANT_NOT_FOUND));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private AdminOperationAuditEvent operation(Long tenantId, Long operatorId, String operationType, String resourceType) {
        return new AdminOperationAuditEvent(
                tenantId,
                operatorId,
                operationType,
                resourceType,
                String.valueOf(tenantId),
                null,
                Instant.now(clock));
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
