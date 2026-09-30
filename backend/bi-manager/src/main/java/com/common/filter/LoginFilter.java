package com.common.filter;

import com.common.base.UserThreadLocal;
import com.common.models.SaasUser;
import com.common.util.JwtTokenUtil;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Order(1)
@WebFilter(filterName = "loginFilter", urlPatterns = {"/*"})
public class LoginFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final String tokenHeader;
    private final String jwtAccesstoken;

    private static List<String> WHITELIST_URI;

    static {
        WHITELIST_URI = new ArrayList<>();
        //登陆接口白名单
        WHITELIST_URI.add("/doc.html");
        WHITELIST_URI.add("/swagger-ui.html");
        WHITELIST_URI.add("/webjars");
        WHITELIST_URI.add("/swagger-resources");
        WHITELIST_URI.add("/v2/api-docs");
        WHITELIST_URI.add("/upc/user/login");
        WHITELIST_URI.add("/health");
    }

    private static final List<String> WHITELIST_PREFIX_URI;

    static {
        WHITELIST_PREFIX_URI = new ArrayList<>();
        WHITELIST_PREFIX_URI.add("/webjars/");
        WHITELIST_PREFIX_URI.add("/swagger-resources/");
        WHITELIST_PREFIX_URI.add("/api/websocket");
    }

    public LoginFilter(@Qualifier("jwtTokenUtil") JwtTokenUtil jwtTokenUtil, @Value("${jwt.header}") String tokenHeader, @Value("${jwt.accesstoken}")String jwtAccesstoken) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.tokenHeader = tokenHeader;
        this.jwtAccesstoken = jwtAccesstoken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            log.info("LoginFilter uri====={}", request.getRequestURI());
            //获取请求的RUi:去除http:localhost:8080这部分剩下的
            String uri = request.getRequestURI();
            String[] uris = uri.split("\\?");
            uri = uris[0];
            if(uri.equals("/upc/user/return401")){
                // setResponseResult(response, "http://dataops-dev.ip.nip.io/#/", HttpStatus.UNAUTHORIZED.value());
                setResponseResult(response, "http://127.0.0.1:8488/#/", HttpStatus.UNAUTHORIZED.value());
                return;
            }
            for(String uriString : WHITELIST_URI){
                if(uri.equals(uriString)){
                    log.info("uri={}走白名单",uri);
                    filterChain.doFilter(request, response);
                    return;
                }
            }
            for(String prefix : WHITELIST_PREFIX_URI){
                if(uri.startsWith(prefix)){
                    log.info("uri={}走白名单(前缀匹配)",uri);
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            String token = request.getHeader(this.tokenHeader);
            if(StringUtils.isBlank(token)){
                log.info("this.{} has no token",this.tokenHeader);
                token = request.getHeader(this.jwtAccesstoken);
            }
            log.info("LoginFilter token====={}", token);
            if (StringUtils.isBlank(token)) {
                setResponseResult(response, "缺少认证token", HttpStatus.UNAUTHORIZED.value());
                return;
            }
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            if (jwtTokenUtil.isTokenExpired(authToken)) {
                setResponseResult(response, "token无效，非法登陆", HttpStatus.UNAUTHORIZED.value());
                return;
            }
            String username;
            try {
                username = jwtTokenUtil.getUsernameFromToken(authToken);
            } catch (ExpiredJwtException e) {
                log.error(e.getMessage());
                setResponseResult(response, "token无效，非法登陆", HttpStatus.UNAUTHORIZED.value());
                return;
            }
            String tenantid = request.getHeader("tenantid");
            if (StringUtils.isBlank(tenantid)) {
                log.warn("uri={} 缺少租户id(tenantid)", uri);
                setResponseResult(response, "缺少租户id(tenantid)，请重新登陆", HttpStatus.NOT_FOUND.value());
                return;
            }
            if (StringUtils.isBlank(username)) {
                setResponseResult(response, "token无效，非法登陆", HttpStatus.UNAUTHORIZED.value());
                return;
            }
            SaasUser user = new SaasUser();
            user.setToken(token);
            user.setUsername(username);
            user.setId(jwtTokenUtil.getUserIdFromToken(authToken));
            user.setTenantId(Long.parseLong(tenantid));
            UserThreadLocal.set(user);
            try {
                filterChain.doFilter(request, response);
            } finally {
                UserThreadLocal.remove();
            }
        } finally {
            MDC.remove("trace_id");
        }
    }

    @Override
    public void destroy() {
        log.info("过滤器销毁");
    }

    private void setResponseResult(HttpServletResponse response, String responseMsg, int code) {
        response.setStatus(code);
        response.setContentType("text/plain");
        response.setCharacterEncoding("utf-8");
        try {
            response.getWriter().print(responseMsg);
        } catch (IOException e) {
            log.error("登陆权限校验异常：{}", e);
        }
    }

}
