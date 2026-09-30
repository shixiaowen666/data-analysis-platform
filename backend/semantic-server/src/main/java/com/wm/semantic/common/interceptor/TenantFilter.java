package com.wm.semantic.common.interceptor;

import com.wm.semantic.common.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 从请求头 X-Tenant-Id 提取租户ID，写入 TenantContextHolder。
 *
 * Feign 调用方通过 RequestInterceptor 统一传递此请求头，无需修改各接口参数。
 */
@Slf4j
@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantIdStr = request.getHeader(HEADER_TENANT_ID);
        log.info("=========       TenantFilter：{}     =========",tenantIdStr);
        if (tenantIdStr == null || tenantIdStr.trim().isEmpty()) {
            throw new ServletException("请求头 X-Tenant-Id 缺失");
        }
        Long tenantId;
        try {
            tenantId = Long.valueOf(tenantIdStr.trim());
        } catch (NumberFormatException e) {
            throw new ServletException("请求头 X-Tenant-Id 格式非法: " + tenantIdStr);
        }
        TenantContextHolder.set(tenantId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
