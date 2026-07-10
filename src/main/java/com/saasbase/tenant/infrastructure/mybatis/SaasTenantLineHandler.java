package com.saasbase.tenant.infrastructure.mybatis;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.saasbase.common.tenant.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.Set;

public class SaasTenantLineHandler implements TenantLineHandler {
    private static final Set<String> IGNORE_TABLES = Set.of(
            "tenant",
            "iam_permission",
            "security_audit_log",
            "admin_operation_audit_log",
            "system_config");

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContextHolder.require().tenantId());
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return IGNORE_TABLES.contains(tableName);
    }
}
