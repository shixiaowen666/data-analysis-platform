package com.chatbi.chat.filter;

import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * 请求日志 Filter
 */
@Slf4j
public class RequestLogFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        long start = System.currentTimeMillis();
        chain.doFilter(request, response);
        log.debug("Filter: {} {} completed in {}ms",
                httpRequest.getMethod(), httpRequest.getRequestURI(),
                System.currentTimeMillis() - start);
    }
}
