package com.chatbi.chat.config;

import com.chatbi.chat.filter.LoginFilter;
import com.chatbi.chat.filter.RequestLogFilter;
import com.chatbi.chat.utils.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<LoginFilter> loginFilter(@Qualifier("jwtTokenUtil") JwtTokenUtil jwtTokenUtil,
                                                            @Value("${jwt.header}") String tokenHeader,
                                                            @Value("${jwt.accesstoken}") String jwtAccesstoken) {
        FilterRegistrationBean<LoginFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new LoginFilter(jwtTokenUtil, tokenHeader, jwtAccesstoken));
        registration.addUrlPatterns("/*");
        registration.setOrder(0);
        registration.setName("loginFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestLogFilter> requestLogFilter() {
        FilterRegistrationBean<RequestLogFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestLogFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("requestLogFilter");
        return registration;
    }
}
