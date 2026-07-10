package com.saasbase.common.tenant;

public final class TenantContextHolder {
    private static final ThreadLocal<TenantContext> HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext context) {
        HOLDER.set(context);
    }

    public static TenantContext require() {
        TenantContext context = HOLDER.get();
        if (context == null) {
            throw new IllegalStateException("tenant context is required");
        }
        return context;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
