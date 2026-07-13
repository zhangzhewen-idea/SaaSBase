package com.saasbase.tenant.infrastructure.persistence;

import com.saasbase.tenant.domain.Tenant;
import com.saasbase.tenant.domain.gateway.TenantGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

@Component
public class TenantPersistenceAdapter implements TenantGateway {
    private static final AtomicLong IDS = new AtomicLong(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
    private final TenantMapper mapper;
    private final LongSupplier idSupplier;

    @Autowired
    public TenantPersistenceAdapter(TenantMapper mapper) {
        this(mapper, () -> nextId(IDS));
    }

    TenantPersistenceAdapter(TenantMapper mapper, LongSupplier idSupplier) {
        this.mapper = mapper;
        this.idSupplier = idSupplier;
    }

    @Override
    public boolean existsByCode(String tenantCode) {
        return mapper.existsByCode(tenantCode);
    }

    @Override
    public Tenant insert(Tenant tenant, Long operatorId) {
        long id = idSupplier.getAsLong();
        if (id < 0) {
            throw new IllegalStateException("Generated tenant id must not be negative");
        }
        LocalDateTime now = LocalDateTime.now();
        TenantRecord record = new TenantRecord(id, tenant.tenantCode(), tenant.tenantName(), tenant.status(),
                tenant.sessionVersion(), now, operatorId, now, operatorId, false, 0L);
        mapper.insert(record);
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
    public boolean update(Tenant tenant, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        TenantRecord record = new TenantRecord(tenant.id(), tenant.tenantCode(), tenant.tenantName(), tenant.status(),
                tenant.sessionVersion(), now, null, now, operatorId, false, tenant.version());
        return mapper.update(record, operatorId) == 1;
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

    static long nextId(AtomicLong sequence) {
        while (true) {
            long current = sequence.get();
            if (current == Long.MAX_VALUE) {
                throw new IllegalStateException("Tenant id sequence exhausted");
            }
            if (sequence.compareAndSet(current, current + 1)) {
                return current;
            }
        }
    }
}
