package com.wm.semantic.common.context;

/**
 * 租户ID线程上下文，基于 ThreadLocal 实现。
 *
 * 由 {@link com.wm.semantic.common.interceptor.TenantFilter} 在请求进入时设置，
 * 请求结束时自动清理。
 */
public final class TenantContextHolder {

    private static final ThreadLocal<Long> TENANT_HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void set(Long tenantId) {
        TENANT_HOLDER.set(tenantId);
    }

    public static Long get() {
        return TENANT_HOLDER.get();
    }

    public static void clear() {
        TENANT_HOLDER.remove();
    }
}
