package com;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.common.feign")
@MapperScan({"com.bi.mapper", "com.metadata.mapper", "com.quality.mapper"})
@ServletComponentScan
public class BiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BiApplication.class, args);
        System.out.println("===== BI Backend Started =====");
    }
}
