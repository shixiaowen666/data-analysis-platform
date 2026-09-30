# BI Backend - 项目脚手架

基于 **Java 8 + Spring Boot 2.7 + MyBatis-Plus + MySQL + Nacos(注册中心) + OpenFeign** 的项目脚手架。

## 技术栈

| 技术 | 版本 |
|------|------|
| Java | 1.8 |
| Spring Boot | 2.7.18 |
| Spring Cloud | 2021.0.8 |
| Spring Cloud Alibaba | 2021.0.5.0 |
| MyBatis-Plus | 3.5.5 |
| MySQL | 8.0.33 |
| Druid | 1.2.21 |
| OpenFeign | 内置 (Spring Cloud OpenFeign) |
| LoadBalancer | 内置 (Spring Cloud LoadBalancer) |
| Lombok | - |
| Hutool | 5.8.26 |

## 项目结构

```
src/main/java/com/bi/
├── BiApplication.java                    # 启动类 (EnableDiscoveryClient + EnableFeignClients)
├── common/
│   ├── result/
│   │   ├── R.java                        # 统一返回结果
│   │   └── PageResult.java               # 分页结果封装
│   ├── exception/
│   │   ├── BizException.java             # 业务异常
│   │   └── GlobalExceptionHandler        # 全局异常处理
│   └── enums/ResultCode.java             # 结果码枚举
├── config/
│   ├── MyBatisPlusConfig.java            # MyBatis-Plus 配置
│   ├── DruidConfig.java                  # Druid 数据源配置
│   ├── WebMvcConfig.java                 # Web MVC 配置
│   ├── AutoFillHandler.java              # 字段自动填充
│   └── FilterConfig.java                 # Filter 配置
├── feign/
│   ├── client/
│   │   └── BizClient.java                # 示例 Feign Client
│   ├── config/
│   │   └── FeignConfig.java              # Feign 配置 (日志/重试/拦截器)
│   ├── dto/
│   │   └── FeignResult.java              # Feign 通用返回 DTO
│   └── factory/
│       ├── BizClientFallbackFactory.java # Feign 降级工厂
│       └── FeignErrorFactory.java        # 错误处理工厂
├── controller/
│   ├── HealthController.java             # 健康检查
│   ├── SysUserController.java           # 用户控制器（示例）
│   └── DemoController.java              # Feign 调用示例
├── service/
│   ├── IBaseService.java                 # 通用 Service 接口
│   └── impl/
│       ├── BaseServiceImpl.java          # 通用 Service 实现
│       └── SysUserServiceImpl.java
├── mapper/
│   ├── BaseMapper.java                   # 通用 Mapper 接口
│   └── SysUserMapper.java               # 用户 Mapper（示例）
├── entity/
│   ├── BaseEntity.java                   # 实体基类
│   └── SysUser.java                      # 用户实体（示例）
├── interceptor/
│   └── RequestLogInterceptor.java        # 请求日志拦截器
├── filter/
│   └── RequestLogFilter.java             # 请求日志 Filter
└── util/
    └── BeanUtils.java                    # Bean 拷贝工具
```

## 快速开始

### 1. 初始化数据库

```bash
mysql -u root -p < src/main/resources/db/init.sql
```

### 2. 启动 Nacos

确保 Nacos 服务已启动（仅作为注册中心），默认地址 `127.0.0.1:8848`：

```bash
# Nacos 2.x
./bin/startup.sh -m standalone

# Nacos 1.x
./startup.sh -m standalone
```

### 3. 修改数据库连接

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/bi_backend?...
    username: root
    password: your_password
```

或通过环境变量指定 Nacos 地址：

```bash
export NACOS_ADDR=127.0.0.1:8848
```

### 4. 启动项目

```bash
mvn spring-boot:run
```

或打包后运行：

```bash
mvn clean package -DskipTests
java -jar target/bi-backend-1.0.0-SNAPSHOT.jar
```

### 5. 访问接口

- 健康检查：`GET http://localhost:8080/api/health`
- 用户分页：`GET http://localhost:8080/api/user/page?pageNum=1&pageSize=10`
- Feign 调用示例：`GET http://localhost:8080/api/v1/demo/user/1`

## 使用 Feign 调用其他服务

### 1. 创建 Feign Client

```java
@FeignClient(
    name = "other-service",     // Nacos 服务名
    path = "/api",              // 前缀路径（可选）
    fallbackFactory = OtherClientFallbackFactory.class  // 降级工厂
)
public interface OtherClient {
    @GetMapping("/user/{id}")
    R<Object> getUserById(@PathVariable("id") Long id);
}
```

### 2. 创建降级工厂

```java
@Component
public class OtherClientFallbackFactory implements FallbackFactory<OtherClient> {
    @Override
    public OtherClient create(Throwable cause) {
        log.error("Feign fallback: {}", cause.getMessage());
        return new OtherClient() {
            @Override
            public R<Object> getUserById(Long id) {
                return R.fail("服务不可用");
            }
        };
    }
}
```

### 3. 注入使用

```java
@RestController
@RequiredArgsConstructor
public class DemoController {
    private final OtherClient otherClient;

    @GetMapping("/demo/user/{id}")
    public R<Object> getUser(@PathVariable Long id) {
        return otherClient.getUserById(id);
    }
}
```

## 核心特性

- **统一返回**：所有接口返回 `R<T>` 格式
- **分页查询**：内置 MyBatis-Plus 分页插件
- **逻辑删除**：实体基类内置 `deleted` 字段
- **自动填充**：`createTime/updateTime/createBy/updateBy` 自动填充
- **全局异常**：统一捕获处理异常
- **请求日志**：Interceptor + Filter 双维度日志
- **连接池**：Druid 数据源
- **服务注册**：Nacos Discovery
- **服务调用**：OpenFeign + LoadBalancer（内置负载均衡）
- **服务降级**：Feign Hystrix 兼容降级
- **服务重试**：Feign 请求自动重试
