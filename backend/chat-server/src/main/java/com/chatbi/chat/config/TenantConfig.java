package com.chatbi.chat.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.chatbi.chat.models.SaasUser;
import com.chatbi.chat.utils.UserThreadLocal;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantConfig {

    @Bean
    public MybatisPlusInterceptor tenantInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(() -> {
            SaasUser user = UserThreadLocal.get();
            if (user == null || user.getTenantId() == null) {
                throw new IllegalStateException("租户ID缺失，无法执行多租户数据查询");
            }
            return new LongValue(user.getTenantId());
        }));
        return interceptor;
    }
}

