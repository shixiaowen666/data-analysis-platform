# AGENTS.md

## 1. 项目背景

- 公司：**wm**
- 项目名称：**semantic-server**
- 项目类型：**纯后端单体服务**
- 当前阶段：**单体工程开发，不拆分为多 Maven 子模块**
- 后续演进：项目未来可以作为父工程中的一个子 module 引入，但当前阶段保持独立单工程即可。

本文件用于指导 Codex / 开发代理在本项目中的开发工作，要求所有新增代码、目录设计、配置方式、命名风格、日志规范、接口规范，都优先遵循本文件。

---

## 2. 项目定位

`semantic-server` 不是一个简单的 DAO 服务，也不是仅服务于监控口径的 metrics 服务。

它的职责是：

1. 接收查询请求
2. 解析语义层查询条件
3. 组装查询参数/SQL
4. 执行数据查询
5. 返回统一结构结果
6. 作为企业内部统一的语义查询入口
7. 提供标准 REST API 供其他服务通过 Feign 调用
8. 接入 Nacos，支持服务注册与配置管理

### 命名原则

项目统一命名为：**semantic-server**

原因：
- 当前职责不只围绕 metric，后续会覆盖指标、维度、过滤条件、语义映射、SQL 生成等能力
- `metrics-server` 语义过窄，容易误导为单纯“指标服务”
- `semantic-server` 更适合后续扩展

---

## 3. 技术选型

本项目必须使用以下技术栈：

- JDK：**8** (本地JDK版本已自动升级到26，项目配置保持1.8兼容模式)
- Spring Boot：**2.7.x**
- Web：**spring-boot-starter-web**
- 注册/配置中心：**Nacos**
- 服务调用：**OpenFeign**
- 数据库：**MySQL 8.0**
- 连接池：**Alibaba Druid**
- ORM：**MyBatis-Plus**
- 接口文档：**Swagger2 (Springfox)**
- 构建工具：**Maven**
- 日志：**Logback + Slf4j**
- 辅助：**Lombok**
- 参数校验：**spring-boot-starter-validation**
- 可选监控：**spring-boot-starter-actuator**

### 强约束

1. 所有日志类统一使用 `@Slf4j`
2. 所有数据库访问统一走 MyBatis-Plus / Mapper，不允许直接在业务代码中裸写 JDBC
3. 所有对外能力统一通过 REST API 暴露
4. 其他服务调用本服务时，应通过 Feign 调用 REST API
5. 当前阶段不得为了“看起来高级”而拆成多 Maven 子模块

---

## 4. 工程形态要求

### 当前阶段

- 使用 **单体工程、单 Maven 模块**
- 在一个工程内做清晰的逻辑分层
- 禁止过早拆分为：
  - `semantic-server-api`
  - `semantic-server-core`
  - `semantic-server-dao`
  - `semantic-server-client`
  - `semantic-server-common`

### 原因

当前服务的核心目标是：
- 快速完成从查询请求到数据返回的闭环
- 先把语义查询的主流程跑通
- 保持启动简单、调试高效、修改集中

### 后续演进

未来如需纳入父工程，可将当前工程作为子 module 引入，例如：

```text
wm-parent
├── semantic-server
├── metadata-server
├── auth-server
└── common-core
```

但当前不要为了未来演进而提前增加复杂度。

---

## 5. 推荐目录结构

所有开发都应尽量遵循以下结构：

```text
semantic-server
├── src/main/java/com/wm/semantic
│   ├── SemanticServerApplication.java
│   ├── common
│   │   ├── config
│   │   ├── constant
│   │   ├── context
│   │   ├── exception
│   │   ├── interceptor
│   │   ├── response
│   │   └── util
│   ├── controller
│   ├── service
│   │   └── impl
│   ├── manager
│   ├── mapper
│   ├── entity
│   ├── dto
│   ├── vo
│   ├── feign
│   │   └── fallback
│   └── support
│       └── sql
│           ├── builder
│           ├── validator
│           ├── executor
│           └── model
├── src/main/resources
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-test.yml
│   ├── application-prod.yml
│   ├── bootstrap.yml
│   ├── mapper
│   └── logback-spring.xml
└── pom.xml
```

### 目录职责说明

#### `controller`
- 只负责接收请求、参数校验、调用 service、返回统一响应
- 不允许在 controller 中拼接 SQL
- 不允许在 controller 中写复杂业务逻辑

#### `service`
- 负责业务编排
- 调用语义查询相关组件
- 控制主流程

#### `manager`
- 负责跨组件组合调用
- 用于承载比 service 更接近“领域编排”的逻辑
- 不是必须，但用于隔离复杂流程时优先使用

#### `mapper`
- 负责数据库访问
- 查询配置表、元数据表、数据源配置表等

#### `support/sql`
这是本项目最重要的能力区，所有 SQL 相关逻辑尽量沉淀在这里，包括：
- SQL 构建
- SQL 校验
- 查询执行
- 条件解析
- 排序解析
- 分页处理

#### `common`
存放公共能力：
- 全局异常
- 统一响应
- traceId / MDC
- 配置类
- 常量
- 工具类

#### `feign`
- 放置调用其他服务的 Feign Client
- 不在这里定义“别人调用本服务的接口”
- 本服务被调用方能力统一体现在 `controller`

---

## 6. 核心编码原则

### 原则 1：先跑通，再抽象
优先确保主流程可用，再考虑抽象、封装和通用化。

### 原则 2：禁止过度设计
当前阶段不追求“平台级架构美感”，以可运行、可维护、可演进为优先。

### 原则 3：职责清晰
- Controller 不写业务
- Service 不直接拼大 SQL
- SQL 能力集中到 `support/sql`
- Mapper 不承载业务逻辑

### 原则 4：统一风格
- 统一返回结构
- 统一异常处理
- 统一日志风格
- 统一配置命名
- 统一包名与类命名

### 原则 5：对未来扩展留边界，但不提前拆模块
当前先逻辑分层，不做物理拆分。

### 原则 6：关键方法必须添加注释
所有关键业务方法必须添加方法级注释，包括：
- 方法功能描述
- 核心逻辑说明
- 参数说明
- 返回值说明

注释规范示例：
```java
/**
 * 方法功能描述
 *
 * 核心逻辑：简短的实现要点
 *
 * @param 参数说明
 * @return 返回值说明
 */
```

---

## 7. 包名、类名、命名规范

### 基础包名

统一使用：

```java
com.wm.semantic
```

### 类命名

#### Controller
以 `Controller` 结尾，例如：
- `QueryController`
- `HealthController`

#### Service
接口以 `Service` 结尾，实现类以 `ServiceImpl` 结尾，例如：
- `QueryService`
- `QueryServiceImpl`

#### Manager
以 `Manager` 结尾，例如：
- `QueryManager`

#### Mapper
以 `Mapper` 结尾，例如：
- `QueryConfigMapper`
- `DataSourceConfigMapper`

#### 实体类
数据库实体统一使用 `DO` 后缀，例如：
- `QueryConfigDO`
- `DataSourceConfigDO`

**重要：每个实体类必须添加 `@TableName` 注解**，指定对应的数据库表名：
```java
@Data
@TableName("olap_basic_pro")
public class OlapBasicProDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    // ...
}
```

#### DTO
请求/传输对象统一使用 `DTO` 或明确业务名，例如：
- `QueryRequest`
- `QueryConditionDTO`

#### 返回对象
前端/调用方返回对象统一使用 `VO` 或 `Response`，例如：
- `QueryResultVO`
- `QueryResponse`

#### 异常类
- `BizException`
- `GlobalExceptionHandler`

#### Spring 依赖注入
- 统一优先使用 `@Resource` 进行依赖注入
- 非特殊场景下，不使用 `@Autowired`
- 非特殊场景下，不使用构造器注入统一 Spring Bean

---

## 8. 接口设计规范

### 统一返回结构
所有接口必须返回统一结构，例如：
- `code`
- `message`
- `data`

不得直接返回裸对象、裸列表或字符串。

### REST 风格
统一使用 REST 风格接口，例如：
- `POST /api/query/execute`
- `GET /api/query/config/{code}`
- `GET /api/health/check`

### 版本与前缀
默认接口前缀：

```text
/api
```

查询能力推荐：

```text
/api/query/*
```

### 参数校验
所有请求入参对象必须使用校验注解，结合 `@Valid` 使用。

---

## 9. 日志规范

### 日志框架
统一使用：
- Slf4j
- Lombok `@Slf4j`
- Logback

### 日志要求
1. 所有类日志统一通过 `@Slf4j` 注入
2. 不允许手工 new logger
3. Controller、Service、异常处理都要有必要日志
4. 日志需可排查、可追踪，不要刷屏

### 日志级别建议
- `info`：关键流程节点、请求进入、执行完成、关键耗时
- `warn`：业务异常、可恢复错误、降级场景
- `error`：系统异常、不可预期错误
- `debug`：仅用于本地排查，不作为默认依赖信息

### traceId 规范
日志中统一使用 MDC 字段：

```text
globalSerial
```

要求：
- 从请求头中优先获取 `globalSerial`
- 若无则自动生成
- 写入 MDC
- 响应头回传 `globalSerial`

### Logback 约束
项目必须使用 `logback-spring.xml`，并保持以下输出分类：
- 控制台日志
- 项目业务日志
- 错误日志
- SQL 日志

日志格式核心模式：

```text
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{globalSerial}] %logger{50} - %msg%n
```

### SQL 日志要求
- SQL 日志允许单独文件输出
- 慢 SQL 要可识别
- 不要在普通业务日志中大量打印完整结果集

### 详细日志要求
所有关键代码位置必须添加日志，确保可排查性：

1. **方法入口日志**：每个public方法开始时记录，表明方法作用和入参
2. **查询结果日志**：每次数据库查询后记录查询结果数量，如 `查询到XX记录 count=N`
3. **步骤节点日志**：流程中的关键步骤用 `[StepX-步骤名]` 格式标记
4. **决策点日志**：选择/判断逻辑处记录决策依据和结果
5. **异常日志**：异常前记录关键上下文信息

日志模板示例：
```java
log.info("[表选择] Step1-获取维度字段, dimensionIds={}, count={}", dimensionIds, result.size());
log.info("[SQL生成] 构建SQL完成, sql={}", sql);
log.info("[数据查询] 查询结果 count={}", records.size());
```

---

## 10. 数据访问规范

### 数据库
统一使用 **MySQL 8.0**。

### 连接池
统一使用 **Druid**。

### ORM
统一使用 **MyBatis-Plus**。

### 数据访问约束
1. Mapper 层负责数据库访问
2. 不允许在 service 中直接拼接大量 JDBC 逻辑
3. XML SQL 与代码生成 SQL 必须保持清晰可维护
4. 对于复杂动态查询，优先在 `support/sql` 中做结构化处理，再执行

### MyBatis XML 约束
**涉及 List 类型入参时，必须判断非空**：
```xml
<if test="paramList != null and !paramList.isEmpty()">
    AND column IN
    <foreach collection="paramList" open="(" separator="," close=")">
        #{item}
    </foreach>
</if>
```
- 禁止直接使用 `collection="paramList"` 而不判空
- 必须同时检查 `!= null` 和 `!isEmpty()`

---

## 11. SQL 能力开发规范

由于本项目核心是“语义查询 + 拼接查询”，SQL 相关能力必须集中建设。

### 推荐拆分

#### `builder`
负责 SQL 组装，例如：
- select 子句
- where 子句
- group by 子句
- order by 子句
- limit 子句

#### `validator`
负责 SQL 安全校验，例如：
- 禁止非 SELECT
- 禁止危险关键字
- 限制多语句
- 控制 limit 上限

#### `executor`
负责执行查询、封装执行结果

#### `model`
承载 SQL 构建的中间对象，例如：
- QueryContext
- ConditionNode
- SortRule
- PageInfo

### 安全原则
必须默认认为动态 SQL 存在风险，因此：
- 只允许查询类操作
- 参数必须结构化处理
- 严禁把外部输入直接拼成 SQL 片段
- 必须做白名单或校验策略

---

## 12. Nacos 规范

项目必须具备接入 Nacos 的能力。

### 用途
1. 服务注册与发现
2. 配置中心

### 基本要求
- 服务名统一为：`semantic-server`
- 启动后可注册到 Nacos
- 支持通过 Nacos 获取环境配置

### 配置建议
- `bootstrap.yml` 中配置 Nacos 基础连接
- 环境变量或 profile 区分 dev / test / prod
- 不要把生产敏感配置硬编码到代码中

---

## 13. Feign 规范

### 定位
本服务需要具备两类能力：

#### 1. 调用其他服务
通过 `feign` 包中的 Feign Client 实现。

#### 2. 被其他服务调用
通过本服务暴露的 REST API 实现。

### 重要说明
“提供 Feign 接口供其他服务调用”的本质，不是在本服务中定义某种特殊 Feign 接口，而是：
- 本服务对外暴露标准 HTTP API
- 其他服务自行编写 Feign Client 调用 `semantic-server`

### Feign 要求
- 必须配置合理的 connect/read timeout
- 需要预留 fallback 或降级扩展能力
- Feign 调用异常要有日志

---

## 14. 异常处理规范

项目必须有统一全局异常处理。

### 要求
1. 业务异常使用 `BizException`
2. 全局异常统一由 `GlobalExceptionHandler` 处理
3. 返回统一错误结构
4. 业务异常通常记 `warn`
5. 系统异常必须记 `error`

禁止：
- 在 controller 中大量 try-catch 吞异常
- 直接把堆栈信息返回给调用方

---

## 15. 配置管理规范

### 配置文件建议

- `bootstrap.yml`：Nacos 基础配置
- `application.yml`：通用配置
- `application-dev.yml`：开发环境
- `application-test.yml`：测试环境
- `application-prod.yml`：生产环境

### 配置原则
1. 环境配置分离
2. 敏感信息外置
3. 超时、开关、限流等配置尽量可调
4. 不要把路径、地址、账号写死在代码里

---

## 16. Swagger 规范

项目应默认启用 Swagger2 / Springfox。

### 要求
- 需要有清晰的接口分组和描述
- 请求对象和返回对象需要有基础说明
- 健康检查、查询接口、配置接口都应能在文档中查看

---

## 17. 开发优先级建议

Codex 在开发本项目时，优先级如下：

### 第一优先级
先保证最小闭环可运行：
- 项目可启动
- 数据库可连通
- Nacos 可接入
- 基础接口可访问
- Swagger 可打开
- 日志可正常输出

### 第二优先级
补齐基础能力：
- 统一返回体
- 全局异常
- traceId
- Feign 基础配置
- MyBatis-Plus 基础配置

### 第三优先级
完善查询能力：
- QueryRequest
- QueryService
- SQL Builder
- SQL Validator
- QueryExecutor

### 第四优先级
再考虑增强能力：
- 查询模板化
- 多数据源
- 查询审计
- 慢查询治理
- 缓存和限流

---

## 18. 当前阶段不建议做的事

1. 不要提前拆多 Maven 模块
2. 不要提前做复杂插件化架构
3. 不要为了抽象而抽象
4. 不要把简单流程过度领域化
5. 不要把本项目命名成 `metrics-server`
6. 不要把所有公共代码一开始就抽成独立公共 jar

---

## 19. Codex 开发行为准则

Codex 在本项目中生成代码时，应遵循以下规则：

1. 优先生成可编译、可启动、可运行代码
2. 优先遵守现有目录结构，不随意新增奇怪层级
3. 优先复用已有公共能力
4. 不随意引入未经确认的新技术栈
5. 不为了“架构完整性”引入当前无必要的复杂模块
6. 对关键设计选择，优先贴合本文件约束
7. 所有新增类都要考虑命名是否符合本文件规范
8. 所有新增接口都应返回统一响应结构
9. 所有新增日志都应遵守 traceId 与日志级别约定
10. 对 SQL 相关代码，优先考虑安全性与可维护性

---

## 20. 一句话总结

`semantic-server` 是 wm 公司内部一个基于 Spring Boot 2.7.x 的纯后端单体语义查询服务。当前阶段采用单工程单模块开发，重点建设语义查询主链路、SQL 拼接与校验能力、统一日志与异常处理、Nacos 接入能力，以及对外 REST / 对内 Feign 兼容能力。所有开发都应遵循“先跑通、后抽象，先分层、后拆模块”的原则。
