package com.chatbi.chat.config;

import com.chatbi.chat.models.SaasUser;
import com.chatbi.chat.utils.UserThreadLocal;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class FeignTenantInterceptor {

    @Value("${jwt.header}")
    private String jwtHeader;

    @Bean
    public RequestInterceptor tenantIdInterceptor() {
        return (RequestTemplate template) -> {
            SaasUser user = UserThreadLocal.get();
            if (user == null) {
                return;
            }
            if (user.getTenantId() != null) {
                log.info("FeignTenantInterceptor is running and 租户id is {}", user.getTenantId());
                template.header("X-Tenant-Id", user.getTenantId().toString());
            }
            if (user.getToken() != null) {
                template.header(jwtHeader, user.getToken());
            }
        };
    }
}
