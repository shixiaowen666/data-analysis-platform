# Spring Boot 2.7.18 → 3.2.11 升级改动详单

项目：chat-server | 分析日期：2026-07-01

---

## 一、总览

| 类别 | 影响范围 | 预估工作量 |
|------|----------|------------|
| Java 版本 | JDK 8 → 17 | 安装/部署调整 |
| pom.xml 依赖升级 | 父 POM + 9 个依赖 | 0.5 天 |
| javax → jakarta 包替换 | 16 个文件，30 处引用 | 0.5 天 |
| 配置/代码适配 | 少量配置调整 | 0.5 天 |
| 编译验证 + 测试 | 全项目 | 1 天 |
| **合计** | | **2-3 天** |

---

## 二、pom.xml 改动（8 处）

### 2.1 必改项

| 序号 | 当前 | 改为 | 位置 |
|------|------|------|------|
| 1 | `java.version = 1.8` | `java.version = 17` | properties 第 22 行 |
| 2 | `spring-boot-starter-parent: 2.7.18` | `3.2.11` | parent 第 10 行 |
| 3 | `spring-cloud.version: 2021.0.8` | `2023.0.3` | properties 第 24 行 |
| 4 | `spring-cloud-alibaba.version: 2021.0.5.0` | `2023.0.1.0` | properties 第 25 行 |

### 2.2 依赖替换

| 序号 | 当前 | 改为 | 原因 |
|------|------|------|------|
| 5 | `mysql:mysql-connector-java:8.0.33` | `com.mysql:mysql-connector-j:8.0.33` | artifactId 和 groupId 变更 |
| 6 | `io.jsonwebtoken:jjwt:0.9.1` | `io.jsonwebtoken:jjwt-api:0.12.5` + `jjwt-impl:0.12.5` + `jjwt-jackson:0.12.5` | javax→jakarta 迁移，0.9.x 不兼容 |
| 7 | `org.springdoc:springdoc-openapi-ui:1.7.0` | `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0` | artifactId 变更，2.3.0+ 适配 Boot 3.2 |
| 8 | `com.alibaba:druid-spring-boot-starter:1.2.21` | `com.alibaba:druid-spring-boot-3-starter:1.2.23` | artifactId 变更（适配 Boot 3.2） |
| 9 | **`mybatis-plus-boot-starter:3.5.5`** | **`mybatis-plus-spring-boot3-starter:3.5.5`** | 必须用 Boot3 专用 starter，否则 mybatis-spring 2.x → 3.0.x 冲突 |

### 2.3 无需改动的依赖

| 依赖 | 当前版本 | 说明 |
|------|----------|------|
| `easyexcel` | 3.3.4 | 直接兼容 Boot 3 |
| `hutool-all` | 5.8.26 | 直接兼容 |
| `fastjson` | 2.0.49 | 直接兼容 |
| `kryo` | 5.3.0 | 直接兼容 |
| `commons-lang3` | 3.14.0 | 直接兼容 |
| `commons-collections4` | 由 BOM 管理 | 直接兼容 |
| `lombok` | 由 BOM 管理 | 直接兼容 |
| `spring-boot-starter-web` | 由 BOM 管理 | 直接兼容（artifactId 不变） |
| `spring-boot-starter-websocket` | 由 BOM 管理 | 直接兼容 |
| `spring-boot-starter-validation` | 由 BOM 管理 | 直接兼容（Boot 3 已内置 Hibernate Validator 8.x） |
| `spring-boot-starter-data-redis` | 由 BOM 管理 | 直接兼容 |
| `spring-cloud-starter-openfeign` | 由 BOM 管理 | 直接兼容 |

---

## 三、javax → jakarta 包替换（16 个文件，30 处）

### 3.1 映射表

| javax | jakarta |
|-------|---------|
| `javax.annotation.Resource` | `jakarta.annotation.Resource` |
| `javax.annotation.PostConstruct` | `jakarta.annotation.PostConstruct` |
| `javax.servlet.*` | `jakarta.servlet.*` |
| `javax.validation.*` | `jakarta.validation.*` |
| `javax.websocket.*` | `jakarta.websocket.*` |
| `javax.sql.DataSource` | `javax.sql.DataSource`（此包无需改，JDBC 归属 JDK 而非 Java EE） |

### 3.2 各文件改动明细

| 文件 | 处数 | 改动内容 |
|------|------|----------|
| `config/WebSocketServer.java` | 5 | `javax.annotation.PostConstruct`、`javax.annotation.Resource`、`javax.websocket.*` → jakarta |
| `filter/LoginFilter.java` | 5 | `javax.servlet.*` → jakarta |
| `models/DataRequestDTO.java` | 3 | `javax.validation.*` → jakarta |
| `config/RedisStreamConsumer.java` | 2 | `javax.annotation.PostConstruct`、`javax.annotation.Resource` → jakarta |
| `controller/MetricQueryController.java` | 2 | `javax.annotation.Resource`、`javax.servlet.http.HttpServletResponse` → jakarta |
| `filter/RequestLogFilter.java` | 2 | `javax.servlet.*` → jakarta |
| `interceptor/RequestLogInterceptor.java` | 2 | `javax.servlet.*` → jakarta |
| `controller/ChatQueryController.java` | 1 | `javax.annotation.Resource` → jakarta |
| `config/DruidConfig.java` | 1 | `javax.sql.DataSource` — **无需改**（JDK 自带） |
| `feign/client/QueryServerClient.java` | 1 | `javax.validation.Valid` → jakarta |
| `models/DimDTO.java` | 1 | `javax.validation.*` → jakarta |
| `models/OrderByDTO.java` | 1 | `javax.validation.*` → jakarta |
| `models/IndicatrixInfoDTO.java` | 1 | `javax.validation.*` → jakarta |
| `models/DatePeriodDTO.java` | 1 | `javax.validation.*` → jakarta |
| `models/AttributionGetdataDto.java` | 1 | `javax.validation.*` → jakarta |
| `service/impl/OlapDataServiceImpl.java` | 1 | `javax.annotation.Resource` → jakarta |

> 注：`javax.sql.DataSource`（DruidConfig.java）不需要改，属于 JDK 标准库，不受 Jakarta EE 命名空间变更影响。

### 3.3 白名单路径变更（LoginFilter）

`springdoc-openapi` 升级到 2.x 后 Swagger UI 路径有变化：

| 当前 | 改为 |
|------|------|
| `/swagger-ui.html` | `/swagger-ui.html`（仍然有效） |
| `/v2/api-docs` | `/v3/api-docs` |

---

## 四、代码逻辑适配（3 处）

### 4.1 JJWT 0.9.x → 0.12.x API 变更

**文件**：`utils/JwtTokenUtil.java`

0.12.x 的 API 与 0.9.x 差异较大，主要涉及：

| 0.9.x（当前） | 0.12.x |
|---------------|--------|
| `Jwts.parser().setSigningKey(key).parseClaimsJws(token)` | `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` |
| `Jwts.builder().setSubject().setExpiration().signWith(SignatureAlgorithm.HS256, key).compact()` | `Jwts.builder().subject().expiration().signWith(key).compact()` |
| `parser.getBody()` | `parser.getPayload()` |

`JwtTokenUtil.java` 需要重写，约 20-30 行。

### 4.2 Springdoc OpenAPI 配置类

**文件**：如果存在 Springdoc 配置类，`springdoc-openapi-ui` 升级到 `2.2.0` 后包名从 `org.springdoc.webmvc.ui` 变为 `org.springdoc.webmvc.ui`，基本兼容，主要关注点：
- 如果引用了 `org.springdoc.core.GroupedOpenApi`，改为 `org.springdoc.core.models.GroupedOpenApi`
- Swagger UI 路径从 `swagger-ui.html` 变为 `swagger-ui/index.html`

当前项目未发现自定义 Springdoc 配置类，此改动可能不涉及。

### 4.3 Spring Cloud LoadBalancer

Spring Cloud 2022.0.x 默认使用 `RoundRobinLoadBalancer` 替代 Ribbon（Ribbon 已彻底移除），当前项目已使用 LoadBalancer，无需额外改动。

---

## 五、不需要改动的地方（排查确认）

| 项目 | 说明 |
|------|------|
| `spring.factories` | 项目无此文件，无需迁移到 `org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `WebSecurityConfigurerAdapter` | 项目未使用 Spring Security |
| SpringFox / Swagger 2 | 项目已使用 OpenAPI 3.0（springdoc） |
| `@EnableGlobalMethodSecurity` | 未使用 |
| MyBatis-Plus XML Mapper | 兼容，无需改动 |
| `application.yml` | 无明显需要变更的配置项 |

---

## 六、推荐升级路径

选择 **Spring Boot 3.2.11**，理由：

- 内置 Spring Framework 6.1.14，同步修复所有已知 Spring 漏洞（CVE-2024-22243/22259/38809/38816/38819）
- 3.2.x 是成熟稳定版本线，非最新但久经验证
- Spring Cloud 2023.0.1 + Cloud Alibaba 2023.0.1.0 官方验证适配

对应依赖版本组合：

```
Spring Boot:          3.2.11  → Spring Framework 6.1.14
Spring Cloud:         2023.0.1
Spring Cloud Alibaba: 2023.0.1.0
MyBatis-Plus:         3.5.5（mybatis-plus-spring-boot3-starter）
jJWT:                 0.12.5
springdoc:            2.3.0
druid:                1.2.23（druid-spring-boot-3-starter）
```

---

## 七、执行步骤

1. 安装 JDK 17
2. 改 `pom.xml`（8 处）
3. 全局替换 `javax.annotation.Resource` → `jakarta.annotation.Resource`
4. 全局替换 `javax.servlet` → `jakarta.servlet`
5. 全局替换 `javax.validation` → `jakarta.validation`
6. 全局替换 `javax.websocket` → `jakarta.websocket`
7. 改写 `JwtTokenUtil.java`（jJWT API 适配）
8. 更新 `LoginFilter.java` 白名单路径（`/v2/api-docs` → `/v3/api-docs`）
9. `mvn clean compile` 编译验证
10. 功能回归测试
