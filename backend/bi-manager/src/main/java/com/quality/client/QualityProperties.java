package com.quality.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "quality")
public class QualityProperties {
    private String systemBUrl = "http://127.0.0.1:5000";
    private String chatServerUrl = "http://127.0.0.1:9099";
    private String recallUrl = "http://127.0.0.1:5003";
    private int verifyPoolSize = 3;
    /** 超级管理员用户名，逗号分隔；与前端 config.js APP_Super_Manager 保持一致 */
    private String superAdmins = "admin";
}
