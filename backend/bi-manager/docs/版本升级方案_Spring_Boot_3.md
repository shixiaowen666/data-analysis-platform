# bi-manager 版本升级方案：Spring Boot 2.7.18 → 3.5.14

## 一、项目现状

| 属性 | 当前值 |
|------|--------|
| 项目结构 | 单模块 Maven，`com.bi:bi-backend:1.0.0-SNAPSHOT` |
| Spring Boot | 2.7.18 |
| Java | 1.8 |
| Spring Cloud | 2021.0.8 |
| Spring Cloud Alibaba | 2021.0.5.0 |
| 主要中间件 | MyBatis-Plus、MySQL、Druid、Knife4j、MinIO、Nacos、OpenFeign |
| Java 文件数 | ~130 个 |

## 二、Git 分支策略

```
master（当前）
  └── upgrade/spring-boot-3  （新拉分支）
```

每个阶段一个 commit，便于 review 和回滚：

1. `upgrade: pom dependencies — version bumps + artifactId changes + cleanup unused deps`
2. `upgrade: javax.* → jakarta.* global replace`
3. `upgrade: jjwt 0.9.1 → 0.12.5 API rewrite (JwtTokenUtil)`
4. `upgrade: MinIO 3.0.10 → 8.2.2 API rewrite (MinioService)`
5. `upgrade: Druid package migration to spring-boot-3-starter`
6. `upgrade: application.yml Spring Boot 3 compatibility`

## 三、pom.xml 变更明细

### 3.1 版本号变更

| # | 位置 | 当前值 | 目标值 |
|---|------|--------|--------|
| 1 | parent | `spring-boot-starter-parent:2.7.18` | `3.5.14` |
| 2 | properties/java.version | `1.8` | `17` |
| 3 | properties/spring-cloud.version | `2021.0.8` | `2025.0.2` |
| 4 | properties/spring-cloud-alibaba.version | `2021.0.5.0` | `2025.0.0.0` |
| 5 | properties/spring-framework.version | `5.3.39` | **删除整行**（由 Boot parent 管理） |
| 6 | properties/okhttp.version | `4.12.0` | **删除整行**（未被引用） |
| 7 | properties/druid.version | `1.2.21` | `1.2.28` |
| 8 | properties/knife4j.version | `4.4.0` | `4.5.0`（可选，4.4.0 已支持 Boot 3） |

### 3.2 artifactId / groupId 变更

| # | 依赖 | 变更内容 |
|---|------|---------|
| 1 | MyBatis-Plus | `mybatis-plus-boot-starter` → `mybatis-plus-spring-boot3-starter` |
| 2 | MySQL Driver | `mysql:mysql-connector-java` → `com.mysql:mysql-connector-j` |
| 3 | Druid | `druid-spring-boot-starter` → `druid-spring-boot-3-starter` |
| 4 | jjwt | `jjwt:0.9.1`（1个）→ `jjwt-api:0.12.5` + `jjwt-impl:0.12.5`（runtime）+ `jjwt-jackson:0.12.5`（runtime） |
| 5 | MinIO | `minio:3.0.10` → `minio:8.2.2` |
| 6 | fastjson | `2.0.14` → `2.0.49` |

### 3.3 一并删除的未使用依赖

以下依赖在代码中没有任何 import 或引用，本次一并移除以减少漏洞扫描面：

- `guava` 33.3.1-jre
- `okhttp` 4.12.0
- `logging-interceptor` 4.12.0
- `okio` 3.6.0
- `juniversalchardet` 1.0.3
- `protobuf-java` 3.21.7
- `protostuff-runtime` 1.4.0

## 四、Java 代码改造

### 4.1 javax.* → jakarta.*（影响 ~30 个文件）

**注意：`javax.sql.DataSource` 属于 JDK，不在此变更范围内。**

| 类别 | 搜索关键词 | 替换为 | 影响文件 |
|------|-----------|--------|---------|
| Servlet | `javax.servlet.*` | `jakarta.servlet.*` | 4 个：LoginFilter、RequestLogInterceptor、ChatController、AiBodyController |
| 校验注解 | `javax.validation.constraints.*` | `jakarta.validation.constraints.*` | ~22 个 DTO |
| 校验 | `javax.validation.Valid` | `jakarta.validation.Valid` | 7 个 Controller |

### 4.2 JwtTokenUtil 重写（jjwt 0.9 → 0.12 API）

**涉及的 API 变化：**

- `Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody()` → `Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()`
- `Jwts.builder().signWith(SignatureAlgorithm.HS512, secret)` → `Jwts.builder().signWith(key)`
- 密钥使用 `Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))` 预生成
- `io.jsonwebtoken.Clock` / `DefaultClock` 不再暴露，用 `new Date()` 替代

**已知坑点：**

1. **Claims 不可变**：`refreshToken()` 中 `claims.setIssuedAt()` / `claims.setExpiration()` 会抛 `UnsupportedOperationException`，必须先 `new HashMap<>(claims)` 拷贝再操作
2. **JWT 密钥长度**：jjwt 0.12.x 要求 HMAC-SHA 密钥 ≥ 256 bits（32 字节），当前配置 `mySecret` 只有 8 字节，**启动会直接抛异常**，需要在 `application.yml` 中将 `jwt.secret` 改为 ≥ 32 字符的字符串

### 4.3 MinioService 重写（MinIO 3.x → 8.x API）

**MinIO 业务场景：** 智能问数（ChatBI）对话 JSON 数据存储。`ChatModelQA` 和 `ChatRecord` 表中 `minio_file_path` 字段存放 MinIO 对象路径，读取时通过 `MinioService.getObject()` 获取文件流反序列化。

**实际外部调用情况（重要）：** 在当前代码库中，仅 ChatServiceImpl 调用了 MinioService 的 **1 个方法**：`minioService.getObject(bucketName, fileName)`。MinioService 其余 public 方法（upload、uploadKryoFile、downloadIo、deleteFile、putFileForJson 等）无外部调用点，但属于 MinioService 自身提供的完整能力，仍需迁移。

**需要迁移的 API 清单：**

| 方法 | 旧 API | 新 API |
|------|--------|--------|
| 初始化 | `new MinioClient(endpoint, ak, sk)` | `MinioClient.builder().endpoint(endpoint).credentials(ak, sk).build()` |
| 检查桶 | `client.bucketExists(name)` | `client.bucketExists(BucketExistsArgs.builder().bucket(name).build())` |
| 创建桶 | `client.makeBucket(name)` | `client.makeBucket(MakeBucketArgs.builder().bucket(name).build())` |
| 上传 | `client.putObject(bkt, obj, stream, size, ct)` | `client.putObject(PutObjectArgs.builder().bucket(bkt).object(obj).stream(stream, size, -1).contentType(ct).build())` |
| 下载到文件 | `client.getObject(bkt, obj, file)` | `client.downloadObject(DownloadObjectArgs.builder().bucket(bkt).object(obj).filename(file).build())` |
| 获取流 | `client.getObject(bkt, obj)` | `client.getObject(GetObjectArgs.builder().bucket(bkt).object(obj).build())` |
| 检查对象 | `client.statObject(bkt, obj)` | `client.statObject(StatObjectArgs.builder().bucket(bkt).object(obj).build())` |
| 列表 | `client.listObjects(bkt, prefix)` | `client.listObjects(ListObjectsArgs.builder().bucket(bkt).prefix(prefix).build())` |
| 删除 | `client.removeObject(bkt, obj)` | `client.removeObject(RemoveObjectArgs.builder().bucket(bkt).object(obj).build())` |
| 异常 | `InvalidBucketNameException` | 已移除，统一用 general Exception 处理 |

### 4.4 DruidConfig 包名变更

```java
// import 变更
- import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
+ import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceBuilder;
```

### 4.5 OpenApiConfig（Knife4j）

当前已使用 `knife4j-openapi3-spring-boot-starter:4.4.0`（已支持 Boot 3），注解为 OpenAPI 3 风格（`io.swagger.v3.oas.*`），**无需代码变更**。版本升到 4.5.0 仅更新 pom 版本号。

## 五、application.yml 变更

| # | 变更 | 说明 |
|---|------|------|
| 1 | 删除 `spring.mvc.pathmatch.matching-strategy: ant_path_matcher` | Spring Boot 3 已移除该配置，默认使用 PathPatternParser |

> 本项目未使用 Redis，无需 `spring.redis` → `spring.data.redis` 迁移。

## 六、已验证无风险的项

- **MyBatis-Plus 3.5.5 + Boot 3.5.14**：chat-server 已验证，完全兼容
- **Spring Cloud Alibaba 2025.0.0.0**：Nacos 配置键名无变化，chat-server 已验证
- **Knife4j**：4.4.0 已支持 Boot 3，无需额外处理

## 七、不受影响的依赖

- kryo 5.3.0 — 独立库，MinioService 使用，无需变动
- hutool 5.8.26 — 独立库，无需变动
- commons-lang3 3.14.0 — 独立库，无需变动
- gsjdbc4 / huaweicloud-dws-jdbc — JDBC 驱动，无需变动
- mybatis-plus 3.5.5 — 仅换 artifactId，版本不变
- postgresql — pom 中已注释，无需变动

## 八、Commit 执行计划

| 步骤 | Commit 内容 | 涉及文件 |
|------|------------|---------|
| 1 | pom 版本号 + artifactId 变更 + 删除未使用依赖 | pom.xml |
| 2 | javax → jakarta 全局替换 | ~30 个 Java 文件 |
| 3 | JwtTokenUtil jjwt API 重写 | JwtTokenUtil.java |
| 4 | MinioService API 重写 | MinioService.java |
| 5 | DruidConfig 包名迁移 | DruidConfig.java |
| 6 | application.yml 兼容 | application.yml |
