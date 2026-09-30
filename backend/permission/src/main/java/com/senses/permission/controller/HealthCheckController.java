package com.senses.permission.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/***
 * @ClassName HealthCheckController
 * @Description
 * @Author chenxiwen
 * @Date 1/6/25 5:52 PM
 * @Version 1.0
 */
@RestController
@RequestMapping("/")
@Tag(name = "健康检测 API")
public class HealthCheckController {

    @Value("${server.image-tag}")
    private String tag;
    @GetMapping("/health")
    public String healthCheck(){
        String startTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String hostname = "";
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            hostname = inetAddress.getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        String info = "start_time: " + startTime + "  hostname: " + hostname + "  tag: " + tag;
        return info;
    }

}
