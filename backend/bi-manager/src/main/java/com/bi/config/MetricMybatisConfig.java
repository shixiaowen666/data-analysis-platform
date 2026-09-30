package com.bi.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 指标管理 MyBatis 配置
 * 扫描指标 Mapper XML（存放在 java 目录下以适配沙箱权限）
 */
@Configuration
@MapperScan(basePackages = {"com.bi.mapper"})
public class MetricMybatisConfig {
    // 通过 application.yml 中的 mapper-locations 配置加载 XML
    // 已在 application.yml 中添加 classpath:com/bi/mapper/res/**/*.xml
}
