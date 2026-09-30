# Spring Boot 2.7.18 → 3.5.14 升级执行清单

项目：chat-server | 创建日期：2026-07-10

---

## 一、环境准备

| 序号 | 事项 | 说明 |
|------|------|------|
| 1 | JDK 17 安装 | `/Library/Java/JavaVirtualMachines/jdk-17.0.19.jdk` |
| 2 | JAVA_HOME 切换 | `~/.zshrc` 指向 JDK 17 |
| 3 | 验证 JDK 版本 | `java -version` 输出 17.0.19 |

---

## 二、pom.xml 依赖改动（10 处）

### 2.1 版本号升级

| 序号 | 位置 | 当前值 | 新值 |
|------|------|------|------|
| 2.1.1 | `java.version` | `1.8` | `17` |
| 2.1.2 | `spring-boot-starter-parent` | `2.7.18` | `3.5.14` |
| 2.1.3 | `spring-cloud.version` | `2021.0.8` | `2025.0.2` |
| 2.1.4 | `spring-cloud-alibaba.version` | `2021.0.5.0` | `2025.0.0.0` |

### 2.2 依赖替换

| 序号 | 当前 GAV | 新 GAV | 原因 |
|------|------|------|------|
| 2.2.1 | `mysql:mysql-connector-java:8.0.33` | `com.mysql:mysql-connector-j:8.0.33` | artifactId/groupId 变更 |
| 2.2.2 | `com.alibaba:druid-spring-boot-starter:1.2.21` | `com.alibaba:druid-spring-boot-3-starter:1.2.28` | Boot 3 专用 starter |
| 2.2.3 | `com.baomidou:mybatis-plus-boot-starter:3.5.5` | `com.baomidou:mybatis-plus-spring-boot3-starter:3.5.5` | Boot 3 专用 starter |
| 2.2.4 | `org.springdoc:springdoc-openapi-ui:1.7.0` | `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17` | artifactId 变更 |
| 2.2.5 | `io.jsonwebtoken:jjwt:0.9.1` | `io.jsonwebtoken:jjwt-api:0.12.5` + `io.jsonwebtoken:jjwt-impl:0.12.5` + `io.jsonwebtoken:jjwt-jackson:0.12.5` | javax → jakarta，需拆成 3 个 |
| 2.2.6 | `io.minio:minio:3.0.10` | `io.minio:minio:8.2.2` | JDK 17 兼容 |

### 2.3 不需要改动的依赖

| 依赖 | 当前版本 | 说明 |
|------|------|------|
| `com.alibaba:fastjson` | 2.0.49 | 不升级，fastjson2 已安全且 JDK 17 兼容 |
| `cn.hutool:hutool-all` | 5.8.26 | 直接兼容 |
| `com.alibaba:easyexcel` | 3.3.4 | 直接兼容 |
| `com.esotericsoftware:kryo` | 5.3.0 | 直接兼容 |
| `org.apache.commons:commons-lang3` | 3.14.0 | 直接兼容 |

---

## 三、javax → jakarta 包替换（16 个文件，30 处）

| 映射规则 | 说明 |
|------|------|
| `javax.annotation.PostConstruct` → `jakarta.annotation.PostConstruct` | |
| `javax.annotation.PreDestroy` → `jakarta.annotation.PreDestroy` | |
| `javax.annotation.Resource` → `jakarta.annotation.Resource` | |
| `javax.servlet.*` → `jakarta.servlet.*` | |
| `javax.validation.*` → `jakarta.validation.*` | |
| `javax.websocket.*` → `jakarta.websocket.*` | |
| `javax.sql.DataSource` | **不动**（JDK 自带，非 Jakarta EE） |

### 涉及文件列表

| 文件 | 处数 | 改动内容 |
|------|------|------|
| `config/WebSocketServer.java` | 5 | PostConstruct、Resource、websocket.* |
| `filter/LoginFilter.java` | 5 | servlet.* |
| `models/DataRequestDTO.java` | 3 | validation.* |
| `config/RedisStreamConsumer.java` | 2 | PostConstruct、Resource |
| `controller/MetricQueryController.java` | 2 | Resource、servlet |
| `filter/RequestLogFilter.java` | 2 | servlet.* |
| `interceptor/RequestLogInterceptor.java` | 2 | servlet |
| `controller/ChatQueryController.java` | 1 | Resource |
| `config/DruidConfig.java` | 1 | DataSource — **不动** |
| `feign/client/QueryServerClient.java` | 1 | validation.Valid |
| `models/DimDTO.java` | 1 | validation |
| `models/OrderByDTO.java` | 1 | validation |
| `models/IndicatrixInfoDTO.java` | 1 | validation |
| `models/DatePeriodDTO.java` | 1 | validation |
| `models/AttributionGetdataDto.java` | 1 | validation |
| `service/impl/OlapDataServiceImpl.java` | 1 | Resource |
| `config/ThreadPoolConfig.java` | 1 | PreDestroy |

---

## 四、代码适配（2 个文件）

### 4.1 JJWT 0.9.1 → 0.12.5 API 重写

**文件**：`utils/JwtTokenUtil.java`

| 0.9.x | 0.12.x |
|------|------|
| `Jwts.parser().setSigningKey(key).parseClaimsJws(token)` | `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` |
| `Jwts.builder().setSubject().setExpiration().signWith(SignatureAlgorithm.HS256, key).compact()` | `Jwts.builder().subject().expiration().signWith(key).compact()` |
| `parser.getBody()` | `parser.getPayload()` |

### 4.2 LoginFilter 白名单路径

**文件**：`filter/LoginFilter.java`

| 当前路径 | 新路径 |
|------|------|
| `/v2/api-docs` | `/v3/api-docs` |

---

## 五、Swagger 路径适配

springdoc 1.7.0 → 2.8.17 后的路径变化：

| 1.x | 2.x |
|------|------|
| `/swagger-ui.html` | `/swagger-ui/index.html` |

---

## 六、MinIO SDK 3.x → 8.x API 迁移

**文件**：`service/impl/MinioService.java`（以及其他使用 MinIO 的文件）

主要 API 变更：
- 客户端构造方式改变
- 方法签名调整

具体按编译报错逐一修复。

---

## 七、配置检查

| 检查项 | 说明 |
|------|------|
| `application.yml` | 无必改项，springdoc 路径如有硬编码需更新 |
| `spring-cloud-starter-bootstrap` | 若 Nacos 配置在 bootstrap.yml 则需引入，当前在 application.yml 则不需要 |
| Logback 配置 | 无需改动 |

---

## 八、编译验证

```bash
mvn clean compile
```

修复所有编译错误，确保零错误通过。

---

## 九、功能回归测试

| 测试项 | 说明 |
|------|------|
| Nacos 服务注册 | 启动后能在 Nacos 控制台看到实例 |
| Feign 远程调用 | 调用下游服务正常 |
| MySQL + MyBatis-Plus | CRUD 正常 |
| Redis 操作 | 读写正常 |
| MinIO 上传/下载 | 文件操作正常 |
| JWT 认证 | 登录/鉴权流程正常 |
| WebSocket | 实时通信正常 |
| EasyExcel 导出 | 导出功能正常 |
| Swagger UI | `/swagger-ui/index.html` 可访问 |

---

## 十、安全漏洞验证

升级后扫描确认以下漏洞已修复：

- CVE-2024-22243（UriComponentsBuilder SSRF）
- CVE-2024-22259（UriComponentsBuilder SSRF）
- CVE-2024-38809（ETags DoS）
- CVE-2024-38816（目录遍历）
- CVE-2024-38819（目录遍历）
