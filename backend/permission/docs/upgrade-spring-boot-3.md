# Permission 项目 Spring Boot 3 升级方案 (v2)

## 目标

| 配置项 | 当前 | 目标 |
|--------|------|------|
| java.version | 1.8 | 17 |
| spring-boot.version | 2.0.9.RELEASE | 3.5.14 |
| spring-cloud.version | Finchley.SR3 | 2025.0.2 |
| 新增 spring-cloud-alibaba | - | 2025.0.0.0 |

---

## 影响范围总览

| 变更项 | 涉及文件数 | 风险 |
|--------|-----------|------|
| pom.xml 依赖 | 1 | 低 |
| javax → jakarta（精确替换） | 42 个文件 | 低 |
| Swagger → Springdoc | 14 Controller + entity 注解 | 中 |
| JwtTokenUtil 重写 | 1 util + 2 调用方 | 中 |
| SecurityConfig 适配 | 1 | 低 |
| MyBatis Plus 分页插件 | 1 config | 低 |

---

## 分支管理

```bash
git checkout master
git pull origin master
git checkout -b upgrade/spring-boot-3
git push -u origin upgrade/spring-boot-3
```

在此分支上按以下顺序执行，每步一个独立 commit：

```
upgrade/spring-boot-3
  ├── commit-1: pom.xml 依赖变更
  ├── commit-2: javax → jakarta 精确替换
  ├── commit-3: Swagger → Springdoc 迁移 + SecurityConfig 白名单更新
  ├── commit-4: JwtTokenUtil 重写 + jwt.secret 配置更新
  ├── commit-5: SecurityConfig API 适配 + MyBatis Plus 分页插件适配
  └── commit-6: 编译修复 & 验证
```

> 每个 commit 必须编译通过，方便出问题时 `git bisect` 定位。

---

## 详细执行步骤

### Step 1: pom.xml 依赖变更

#### 1.1 基础版本

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.14</version>
</parent>

<properties>
    <java.version>17</java.version>
    <!-- 删除 maven.compiler.source 和 maven.compiler.target，有 java.version 就够了 -->
</properties>
```

删除 `<dependencyManagement>` 中的 `spring-boot-dependencies` BOM（parent 已自带）。

`spring-cloud-dependencies` → `2025.0.2`，新增 `spring-cloud-alibaba-dependencies`：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-dependencies</artifactId>
    <version>2025.0.0.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

#### 1.2 MyBatis-Plus

```xml
<!-- 旧 -->
<artifactId>mybatis-plus-boot-starter</artifactId>
<version>3.1.1</version>

<!-- 新 -->
<artifactId>mybatis-plus-spring-boot3-starter</artifactId>
<version>3.5.5</version>
<!-- mybatis-plus-extension 同步升到 3.5.5 -->
```

#### 1.3 MySQL 驱动

```xml
<!-- 旧 -->
<groupId>mysql</groupId>
<artifactId>mysql-connector-java</artifactId>

<!-- 新 -->
<groupId>com.mysql</groupId>
<artifactId>mysql-connector-j</artifactId>
```

#### 1.4 JWT

```xml
<!-- 旧 -->
<artifactId>jjwt</artifactId>
<version>0.9.1</version>

<!-- 新（拆成 3 个） -->
<artifactId>jjwt-api</artifactId>
<version>0.12.5</version>
<!-- jjwt-impl 0.12.5 (runtime) -->
<!-- jjwt-jackson 0.12.5 (runtime) -->
```

> **跨项目约束：** chat-server 等所有验证 token 的项目也必须升到 jjwt 0.12.5，且密钥处理方式统一用
> `Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))`，不做 Base64 编解码。

#### 1.5 Nacos 注册发现

```xml
<!-- 旧（已废弃） -->
<groupId>org.springframework.cloud</groupId>
<artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>

<!-- 新 -->
<groupId>com.alibaba.cloud</groupId>
<artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
<!-- 版本由上面 spring-cloud-alibaba-dependencies BOM 管理 -->
```

#### 1.6 Ribbon → LoadBalancer

```xml
<!-- 删除 spring-cloud-starter-netflix-ribbon -->

<!-- 替换为 -->
<groupId>org.springframework.cloud</groupId>
<artifactId>spring-cloud-starter-loadbalancer</artifactId>
```

#### 1.7 Swagger → Springdoc

```xml
<!-- 删除全部 5 个旧依赖 -->
<!-- springfox-swagger-ui / springfox-swagger2 / swagger-models / swagger-annotations / swagger-bootstrap-ui -->

<!-- 替换为 -->
<groupId>org.springdoc</groupId>
<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
<version>2.8.17</version>
```

#### 1.8 PageHelper

```xml
<!-- 旧的 3 个 pagehelper 依赖全部删除，合并为 1 个 -->
<groupId>com.github.pagehelper</groupId>
<artifactId>pagehelper-spring-boot-starter</artifactId>
<version>1.4.7</version>
```

#### 1.9 Fastjson

```xml
<version>2.0.49</version>
```

---

### Step 2: javax → jakarta 精确替换（42 个文件）

**原则：只替换 Jakarta EE 相关包，不动 JDK 核心包。**

```bash
# 逐个精确替换，不做全局 sed
find src -name "*.java" -exec sed -i '' \
  -e 's/import javax\.servlet\./import jakarta.servlet./g' \
  -e 's/import javax\.annotation\./import jakarta.annotation./g' \
  -e 's/import javax\.validation\./import jakarta.validation./g' \
  {} +
```

**保护清单（这些不动）：**
- `javax.management.*`（JDK 核心包，User.java 中有 `RoleInfo`）
- `javax.sql.*`
- `javax.crypto.*`
- `javax.xml.*`
- `javax.net.*`

> 注：本项目只用到 `javax.management.relation.RoleInfo` 一个 JDK 核心包（User.java:14），但按此规则执行可确保安全。

---

### Step 3: Swagger → Springdoc 迁移（14 Controller + entity 注解）

#### 3.1 注解对照表

| 旧注解（Springfox） | 新注解（Springdoc） | 备注 |
|---------------------|--------------------|------|
| `@Api(tags = "xxx")` | `@Tag(name = "xxx")` | import 换 `io.swagger.v3.oas.annotations.tags.Tag` |
| `@ApiOperation(value = "xxx")` | `@Operation(summary = "xxx")` | import 换 `io.swagger.v3.oas.annotations.Operation` |
| `@ApiParam(value = "xxx")` | `@Parameter(description = "xxx")` | import 换 `io.swagger.v3.oas.annotations.Parameter` |
| `@ApiModel(value = "xxx")` | `@Schema(description = "xxx")` | import 换 `io.swagger.v3.oas.annotations.media.Schema` |
| `@ApiModelProperty(value = "xxx")` | `@Schema(description = "xxx")` | |
| `@ApiIgnore` | `@Hidden` | import 换 `io.swagger.v3.oas.annotations.Hidden` |

#### 3.2 Config 类处理

**删除 `Swagger2Config.java`**（Springdoc 自动扫描，无需手动配置 Docket）。

#### 3.3 application.yml 配置

```yaml
springdoc:
  swagger-ui:
    path: /doc.html          # 保留原路径，兼容前端
  api-docs:
    path: /v3/api-docs
```

#### 3.4 SecurityConfig 白名单更新

```java
// 新增 Springdoc 路径
.requestMatchers(
    "/doc.html",
    "/swagger-ui/**",        // Springdoc 静态资源
    "/v3/api-docs/**",        // Springdoc OpenAPI spec
    "/webjars/**",
    "/actuator"
).authenticated()
```

---

### Step 4: JwtTokenUtil 重写 + jwt.secret 配置更新（跨项目联动）

> **关键约束：** permission 是 token 签发方（`/upc/user/login`），chat-server 等其他项目是验证方。
> 两边的 `jwt.secret`、密钥处理方式、jjwt 版本必须完全一致，否则验证方解析不出 token。

#### 4.1 密钥配置（与 chat-server 保持一致）

```yaml
# application.yml（所有环境）
jwt:
  secret: my-super-secret-jwt-key-for-chatbi-2026  # 明文，≥ 32 字符
```

> jjwt 0.12 会根据密钥长度自动选择算法：>=32 字符 → HS256，>=48 字符 → HS384，>=64 字符 → HS512。
> 关键是两边完全一致，算法由 `Keys.hmacShaKeyFor()` 自动匹配。

#### 4.2 两边对齐的代码模板

**permission（签发方）：**

```java
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
}

// 解析 token
private Claims getAllClaimsFromToken(String token) {
    return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
}

// 签发 token（/login 中使用）
String token = Jwts.builder()
        .claims(claims)
        .subject(username)
        .issuedAt(new Date())
        .expiration(expirationDate)
        .signWith(getSigningKey())
        .compact();
```

**chat-server（验证方）：**

```java
// 同样的 getSigningKey() 实现

Claims claims = Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
```

#### 4.3 API 变更速查

| 旧 (jjwt 0.9) | 新 (jjwt 0.12) |
|---------------|-----------------|
| `Jwts.parser().setSigningKey(str)` | `Jwts.parser().verifyWith(key).build()` |
| `.parseClaimsJws(token).getBody()` | `.parseSignedClaims(token).getPayload()` |
| `.signWith(SignatureAlgorithm.HS512, str)` | `.signWith(key)` |
| `.setClaims(claims)` | `.claims(claims)` |
| `.setSubject(subject)` | `.subject(subject)` |
| `.setIssuedAt(date)` | `.issuedAt(date)` |
| `.setExpiration(date)` | `.expiration(date)` |

#### 4.4 调用方无需修改

`LoginFilter.java` 和 `UserController.java` 调用的 `jwtTokenUtil` 方法签名不变，无需改动。

#### 4.5 对齐检查清单

- [ ] `jwt.secret` 配置值与 chat-server 完全一致
- [ ] 两边 jjwt 版本均为 `0.12.5`
- [ ] 两边都用 `secret.getBytes(StandardCharsets.UTF_8)`，**不做 Base64 编解码**
- [ ] 升级后联调：permission 签发 token → chat-server 验证通过

#### 4.1 修改配置文件

当前密钥 `mySecret`（8 字节）太短，jjwt 0.12 要求 HS512 密钥 >= 512 bits (64 bytes)。

```yaml
# application.yml
jwt:
  secret: rNy97rZZQqBLfmqxJPSJlquw1KNXoNg8AbCdEfGhIjKlMnOpQrStUvWxYz1234  # >= 64 字符
```

#### 4.2 JwtTokenUtil 重写

```java
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class JwtTokenUtil implements Serializable {

    @Value("${jwt.secret}")
    private String secret;

    // ... 其他字段不变

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // 解析 token
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 生成 token
    private String doGenerateToken(Map<String, Object> claims, String subject) {
        final Date createdDate = clock.now();
        final Date expirationDate = calculateExpirationDate(createdDate);
        return Jwts.builder()
                .claims(claims)               // .setClaims() → .claims()
                .subject(subject)             // .setSubject() → .subject()
                .issuedAt(createdDate)        // .setIssuedAt() → .issuedAt()
                .expiration(expirationDate)   // .setExpiration() → .expiration()
                .signWith(getSigningKey())    // .signWith(SignatureAlgorithm, String) → .signWith(SecretKey)
                .compact();
    }

    // refreshToken 同理更新
}
```

**API 变更速查：**

| 旧 (jjwt 0.9) | 新 (jjwt 0.12) |
|---------------|-----------------|
| `Jwts.parser().setSigningKey(str)` | `Jwts.parser().verifyWith(key).build()` |
| `.parseClaimsJws(token).getBody()` | `.parseSignedClaims(token).getPayload()` |
| `.signWith(SignatureAlgorithm.HS512, str)` | `.signWith(key)` |
| `.setClaims(claims)` | `.claims(claims)` |
| `.setSubject(subject)` | `.subject(subject)` |
| `.setIssuedAt(date)` | `.issuedAt(date)` |
| `.setExpiration(date)` | `.expiration(date)` |

#### 4.3 调用方无需修改

`LoginFilter.java` 和 `UserController.java` 调用的 `jwtTokenUtil` 方法签名不变，无需改动。

---

### Step 5: SecurityConfig 适配 + MyBatis Plus 分页插件适配

#### 5.1 SecurityConfig（Boot 3 API）

`WebSecurityConfigurerAdapter` 已废弃，改为声明式 Bean：

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/doc.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/webjars/**",
                    "/actuator"
                ).authenticated()
                .anyRequest().permitAll()
            )
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(
            User.withUsername("admin")
                .password("{noop}Shanjing@0322")
                .roles("USER")
                .build()
        );
    }
}
```

**API 变更：**
- 不再继承 `WebSecurityConfigurerAdapter`
- `antMatchers()` → `requestMatchers()`
- `AuthenticationManagerBuilder` → `InMemoryUserDetailsManager` Bean
- 链式写法 → Lambda 写法

#### 5.2 MyBatis Plus 分页插件

旧版 `PaginationInterceptor` 在 3.5.x 已废弃：

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    return interceptor;
}
```

---

### Step 6: 配置变更

#### 6.1 YAML 配置

```yaml
# server.image.tag → server.image-tag（Boot 3 下划线 key 不支持）
server:
  image-tag: 1.0.1
```

> 注：本项目不使用 Redis / Druid，无相关配置变更。

#### 6.2 启动类

`@EnableFeignClients` 中的 `basePackages` 属性确认路径不变，其余注解正常兼容。

---

## 编译验证

```bash
# Step 1-6 全部完成后
./mvnw clean compile

# 预期：0 errors
```

---

## 测试清单

| # | 测试项 | 验证方法 |
|---|--------|---------|
| 1 | 编译通过 | `./mvnw clean compile` |
| 2 | 单元测试 | `./mvnw test` |
| 3 | 启动无报错 | `./mvnw spring-boot:run -Dspring-boot.active=dev` |
| 4 | Swagger 可访问 | 打开 `/doc.html`，确认文档渲染正常 |
| 5 | JWT 鉴权链路（本服务） | 调 `POST /upc/user/login` 获取 token → 用 token 调 `GET /upc/user/info` → 200 |
| 6 | JWT 跨项目联调 | permission login 签发 token → chat-server 用同一 `jwt.secret` 解析成功 |
| 7 | 白名单接口 | 无 token 调 `GET /upc/user/all` → 200 |
| 8 | 审批流程 | 提一个角色变更，确认 Feign 调 service-approve 正常 |
| 9 | 分页功能 | 调带分页的接口，确认 pagehelper + MyBatis Plus 分页都正常 |

---

## 合并准则

1. 全部在 `upgrade/spring-boot-3` 分支完成，不直接在 master 改
2. 6 个 commit 独立，失败时方便 `git bisect` 定位
3. 编译 + 本地启动 + 核心接口冒烟全通过 → 提 MR 合入 master
4. 建议先在 test 环境部署验证，确认无回归后再合 master
