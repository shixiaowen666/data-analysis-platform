package com.senses.permission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableDiscoveryClient
@ServletComponentScan
@EnableFeignClients(basePackages = {"com.senses.permission.service.client"})
@EnableAsync(proxyTargetClass = true)
public class PermissionApplication {
	public static void main(String[] args) {
		SpringApplication.run(PermissionApplication.class, args);
	}

}
