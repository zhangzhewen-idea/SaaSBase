package com.saasbase.tenant.infrastructure.persistence;

import com.saasbase.tenant.domain.Tenant;
import com.saasbase.tenant.domain.gateway.TenantGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
public class TenantPersistenceAdapter implements TenantGateway {
    private final TenantMapper mapper;
    private final Clock clock;

    @Autowired
    public TenantPersistenceAdapter(TenantMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    TenantPersistenceAdapter(TenantMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public boolean existsByCode(String tenantCode) {
        return mapper.existsByCode(tenantCode);
    }

    @Override
    public Tenant insert(Tenant tenant, Long operatorId) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        TenantRecord record = new TenantRecord(null, tenant.tenantCode(), tenant.tenantName(), tenant.status(),
                tenant.sessionVersion(), now, operatorId, now, operatorId, false, 0L);
        mapper.insert(record);
        if (record.id() == null || record.id() < 0) {
            throw new IllegalStateException("Database did not generate a valid tenant id");
        }
        return toDomain(record);
    }

    @Override
    public Optional<Tenant> findById(Long id) {
        return mapper.findById(id).map(this::toDomain);
    }

    @Override
    public Page page(Query query) {
        String namePattern = query.tenantName() == null ? null : "%" + escapeLike(query.tenantName()) + "%";
        long total = mapper.count(query.tenantCode(), namePattern, query.status());
        long offset = safeOffset(query.pageNo(), query.pageSize());
        var items = mapper.page(query.tenantCode(), namePattern, query.status(), offset, query.pageSize())
                .stream().map(this::toDomain).toList();
        return new Page(items, total, query.pageNo(), query.pageSize());
    }

    @Override
    public Optional<Tenant> update(Tenant tenant, Long operatorId) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        TenantRecord record = new TenantRecord(tenant.id(), tenant.tenantCode(), tenant.tenantName(), tenant.status(),
                tenant.sessionVersion(), now, null, now, operatorId, false, tenant.version());
        if (mapper.update(record, operatorId) != 1) {
            return Optional.empty();
        }
        return Optional.of(Tenant.reconstitute(tenant.id(), tenant.tenantCode(), tenant.tenantName(), tenant.status(),
                tenant.sessionVersion(), Math.addExact(tenant.version(), 1)));
    }

    private Tenant toDomain(TenantRecord record) {
        return Tenant.reconstitute(record.id(), record.tenantCode(), record.tenantName(), record.status(),
                record.sessionVersion(), record.version());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static long safeOffset(long pageNo, long pageSize) {
        try {
            return Math.multiplyExact(pageNo - 1, pageSize);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

}
