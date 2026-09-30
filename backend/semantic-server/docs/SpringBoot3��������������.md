# Spring Boot 2.7.18 → 3.5.14 升级执行清单

项目：semantic-server | 日期：2026-07-10

> 本文档回答：**改什么、目标是什么**。（不改代码，只记录变更项）

---

## 一、pom.xml

### 1.1 版本号

| 项 | 当前值 | 目标值 |
|------|------|------|
| `java.version` | `1.8` | `17` |
| `spring-boot-starter-parent` | `2.7.18` | `3.5.14` |
| `lombok.version` | `1.16.22` | `1.18.34` |
| `druid.version` | `1.2.23` | `1.2.28` |

### 1.2 要删除的内容

- 被注释的 `<spring-framework.version>5.3.40</spring-framework.version>` 行
- 整个 `<dependencyManagement>` 块（`spring-framework-bom`）

### 1.3 artifactId 替换

| 当前 GAV | 目标 GAV |
|------|------|
| `com.baomidou:mybatis-plus-boot-starter:3.5.5` | `com.baomidou:mybatis-plus-spring-boot3-starter:3.5.5` |
| `com.alibaba:druid-spring-boot-starter:1.2.28` | `com.alibaba:druid-spring-boot-3-starter:1.2.28` |
| `io.springfox:springfox-boot-starter:3.0.0` | `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17` |

### 1.4 maven-compiler-plugin

| 项 | 当前值 | 目标值 |
|------|------|------|
| `source` | `1.8` | `17` |
| `target` | `1.8` | `17` |

### 1.5 不需要改的依赖

| 依赖 | 原因 |
|------|------|
| `com.mysql:mysql-connector-j` | 已是新 GAV，版本由 Boot 管理 |
| `com.alibaba:fastjson:2.0.49` | fastjson2 兼容 JDK 17 |
| `com.huawei.gaussdb:gsjdbc4:8.8.8` | 需验证 JDK 17 兼容性 |
| `com.huaweicloud.dws:huaweicloud-dws-jdbc:8.8.8` | 同上 |
| 所有 `spring-boot-starter-*` | 版本由 Boot 管理 |

---

## 二、Dockerfile

| 项 | 当前值 | 目标值 |
|------|------|------|
| 基础镜像 | `openjdk:8u342-jre` | `openjdk:17-jre` |
| apt sources | `bullseye` | `bookworm`（新镜像 Debian 版本变更） |

---

## 三、javax → jakarta

### 3.1 映射规则

| javax | jakarta | 说明 |
|-------|---------|------|
| `javax.annotation.Resource` | `jakarta.annotation.Resource` | Jakarta EE |
| `javax.servlet.*` | `jakarta.servlet.*` | Jakarta Servlet |
| `javax.validation.Valid` | `jakarta.validation.Valid` | Jakarta Validation |
| `javax.crypto.*` | **不动** | JDK 自带 |
| `javax.sql.DataSource` | **不动** | JDK 自带 |

### 3.2 涉及文件（6 个 Java 文件，7 处 import）

| 文件 | 改什么 |
|------|------|
| `common/config/DataSourceManager.java` | `javax.annotation.Resource` → `jakarta.annotation.Resource` |
| `common/interceptor/TenantFilter.java` | 4 个 `javax.servlet.*` → `jakarta.servlet.*` |
| `controller/GetDataController.java` | `javax.annotation.Resource` + `javax.validation.Valid` |
| `service/impl/SqlGenerationServiceImpl.java` | `javax.annotation.Resource` |
| `service/impl/TableSelectionServiceImpl.java` | `javax.annotation.Resource` |
| `support/sql/builder/SegmentSqlBuilder.java` | `javax.annotation.Resource` |
| `support/sql/builder/SqlGeneratorService.java` | `javax.annotation.Resource` |

---

## 四、application.yml

| 项 | 当前值 | 目标值 |
|------|------|------|
| `springfox.documentation.enabled: false` | `springdoc.api-docs.enabled: false` |

Java 代码中无 springfox 注解，无需额外代码改动。

---

## 五、不需要改的文件

- `bootstrap.yml` — Nacos 已禁用
- `logback-spring.xml` — 无需改
- MyBatis Mapper XML — 无需改
- `PasswordUtil.java` — `javax.crypto.*` 不动

---

## 六、与本项目无关的依赖（无需关注）

Spring Cloud、Spring Cloud Alibaba、Nacos Client、Redis、MinIO、Feign、WebSocket、jjwt、Hutool、EasyExcel、commons-lang3、kryo

---

## 七、本次升级修复的 CVE

| CVE | 修复版本 | 修复方式 |
|------|------|------|
| CVE-2024-22243 | Spring Framework ≥ 5.3.33 | Boot 3.5.14 → Framework 6.2.x 自动覆盖 |
| CVE-2024-22259 | Spring Framework ≥ 5.3.34 | 同上 |
| CVE-2024-38809 | Spring Framework ≥ 5.3.39 | 同上 |
| CVE-2024-38816 | Spring Framework ≥ 5.3.39 | 同上 |
| CVE-2024-38819 | Spring Framework ≥ 5.3.39 | 同上 |
