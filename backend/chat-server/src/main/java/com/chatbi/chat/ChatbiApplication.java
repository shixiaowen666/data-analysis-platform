package com.chatbi.chat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.chatbi.chat.feign.client")
@MapperScan("com.chatbi.chat.mapper")
public class ChatbiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbiApplication.class, args);
        System.out.println("===== ChatBI chat-server Started =====");
    }
}
