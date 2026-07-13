package com.saasbase.tenant.domain.gateway;

import com.saasbase.tenant.domain.Tenant;
import com.saasbase.tenant.domain.TenantStatus;
import java.util.List;
import java.util.Optional;

public interface TenantGateway {
    boolean existsByCode(String tenantCode);

    Tenant insert(Tenant tenant, Long operatorId);

    Optional<Tenant> findById(Long id);

    Page page(Query query);

    Optional<Tenant> update(Tenant tenant, Long operatorId);

    record Query(String tenantCode, String tenantName, TenantStatus status, long pageNo, long pageSize) {
        public Query {
            tenantCode = normalize(tenantCode);
            tenantName = normalize(tenantName);
            validatePagination(pageNo, pageSize);
        }
    }

    record Page(List<Tenant> items, long total, long pageNo, long pageSize) {
        public Page {
            if (items == null) {
                throw new IllegalArgumentException("items must not be null");
            }
            if (total < 0) {
                throw new IllegalArgumentException("total must not be negative");
            }
            validatePagination(pageNo, pageSize);
            items = List.copyOf(items);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void validatePagination(long pageNo, long pageSize) {
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be at least 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        }
    }
}
