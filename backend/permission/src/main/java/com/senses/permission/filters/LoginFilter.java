package com.senses.permission.filters;

import com.senses.permission.model.LoginUser;
import com.senses.permission.model.UserThreadLocal;
import com.senses.permission.util.JwtTokenUtil;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
        WHITELIST_URI.add("/approve/callback");
        WHITELIST_URI.add("/approve/cancelWorksheet");
        WHITELIST_URI.add("/upc/sso/user");
        WHITELIST_URI.add("/upc/user/login");
        WHITELIST_URI.add("/upc/user/getToken");
        WHITELIST_URI.add("/upc/user/getPermanentToken");
        WHITELIST_URI.add("/upc/user/all");
        WHITELIST_URI.add("/upc/user/list");
        WHITELIST_URI.add("/upc/user/getInfo");
        WHITELIST_URI.add("/upc/user/appendApplyDataPermission1");
        WHITELIST_URI.add("/upc/user/appendApplyDataPermission2");
        WHITELIST_URI.add("/upc/user/myListByPageDataRolePermission");
        WHITELIST_URI.add("/upc/user/getInfoByIds");
        WHITELIST_URI.add("/upc/user/getInfoByUsername");
        WHITELIST_URI.add("/upc/user/validateToken");
        WHITELIST_URI.add("/upc/user/getInfoByUsernames");
        WHITELIST_URI.add("/upc/user/initUserDataRole");
        WHITELIST_URI.add("/upc/user/listByDeptsOrGroupsOrName");
        WHITELIST_URI.add("/upc/user/loginPreUpdatePassword");
        WHITELIST_URI.add("/upc/application/list");
        WHITELIST_URI.add("/upc/application/getInfoByCode");
        WHITELIST_URI.add("/upc/group/list");
        WHITELIST_URI.add("/upc/group/getUsers");
        WHITELIST_URI.add("/upc/group/getGroupsByUsernames");
        WHITELIST_URI.add("/upc/group/getGroupUsersTree");
        WHITELIST_URI.add("/upc/dept/treeList");
        WHITELIST_URI.add("/upc/dept/getDeptsAndUsers");
        WHITELIST_URI.add("/upc/dept/getDeptUsers");
        WHITELIST_URI.add("/upc/dept/getDeptsByIds");
        WHITELIST_URI.add("/upc/dept/deptUserTreeList");
        WHITELIST_URI.add("/upc/role/treeList");
        WHITELIST_URI.add("/upc/dataPermission/checkDataPermission");
        WHITELIST_URI.add("/upc/dataPermission/deleteDataPermissionByDsId");
        WHITELIST_URI.add("/upc/dept/getInfo");
        WHITELIST_URI.add("/upc/dept/getInfoByCode");
        WHITELIST_URI.add("/upc/user/listByDeptsOrStatusOrName");
        WHITELIST_URI.add("/upc/user/loginByToken");
        WHITELIST_URI.add("/upc/dataPermission/getUserDatarolePermission");
        WHITELIST_URI.add("/upc/permission/initPermission");
        WHITELIST_URI.add("/upc/resource/management/saveOrUpdateBatch");
        WHITELIST_URI.add("/upc/resource/management/listAll");
        WHITELIST_URI.add("/upc/resource/management/deleteBatchByObjectId");
        WHITELIST_URI.add("/upc/resource/management/getResourcePermissionByUserId");
        WHITELIST_URI.add("/upc/resource/management/getResourcePermissionByParam");
        WHITELIST_URI.add("/upc/application/getAppAdmin");
        WHITELIST_URI.add("/upc/api/tag/value/getByUserId");
        WHITELIST_URI.add("/upc/user/checkUserAppAdmin");
        WHITELIST_URI.add("/upc/api/table/relation/getByTableId");
        WHITELIST_URI.add("/upc/api/tag/value/getByUserId");
        WHITELIST_URI.add("/health");

    }

    public LoginFilter(@Qualifier("jwtTokenUtil") JwtTokenUtil jwtTokenUtil, @Value("${jwt.header}") String tokenHeader,@Value("${jwt.accesstoken}")String jwtAccesstoken) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.tokenHeader = tokenHeader;
        this.jwtAccesstoken = jwtAccesstoken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

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
            if(uri.equals(uriString) || uri.startsWith("/webjars/")|| uri.startsWith("/swagger-resources/")){
                filterChain.doFilter(request, response);
                return;
            }
        }

        String requestHeader = request.getHeader(this.tokenHeader);
        if(StringUtils.isBlank(requestHeader)){
            log.info("this.tokenHeader no token");
            requestHeader = request.getHeader(this.jwtAccesstoken);
        }
        log.info("LoginFilter token====={}", requestHeader);
        String username = null;
        String authToken = null;
        if (requestHeader != null) {
            authToken = requestHeader.startsWith("Bearer ") ? requestHeader.substring(7) : requestHeader;
            if (jwtTokenUtil.isTokenExpired(authToken)) {
                setResponseResult(response, "token无效，非法登陆", HttpStatus.UNAUTHORIZED.value());
                return;
            }

            try {
                username = jwtTokenUtil.getUsernameFromToken(authToken);
            } catch (ExpiredJwtException e) {
                log.error(e.getMessage());
            }
        }
        //增加获取租户id：请求头优先，缺失时回退到token里的tenant_id
        String tenantid = request.getHeader("tenantid");
        //把用户写到当前的threadloacl里面
        if (StringUtils.isNotBlank(username)) {
            LoginUser user = new LoginUser();
            user.setUsername(username);
            if (StringUtils.isNotBlank(tenantid)) {
                user.setTenantId(Long.parseLong(tenantid));
            } else if (authToken != null) {
                Long tokenTenantId = jwtTokenUtil.getTenantIdFromToken(authToken);
                if (tokenTenantId != null) {
                    user.setTenantId(tokenTenantId);
                }
            }
            UserThreadLocal.set(user);
            filterChain.doFilter(request, response);
            UserThreadLocal.remove();
        } else {
            setResponseResult(response, "token无效，非法登陆", HttpStatus.UNAUTHORIZED.value());
        }
        return;
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
