# 数据接入与消费层 — 接口文档

> **Base URL**: `http://127.0.0.1:8080/api`（生产环境替换为实际域名）
>
> **内容类型**: `application/json`
>
> **认证方式**: Header `Authorization: Bearer <token>`
>
> 下文 **URL** 均为完整路径，统一以 `/api/v1` 开头（context-path 为 `/api`），与后端 Controller 一一对应。

---

## 目录

### 一、数据源管理
1. [数据源列表](#11-数据源列表)
2. [数据源详情](#12-数据源详情)
3. [新建数据源](#13-新建数据源)
4. [编辑数据源](#14-编辑数据源)
5. [删除数据源](#15-删除数据源)
6. [测试连接](#16-测试连接)
7. [预览 JDBC URL](#17-预览-jdbc-url)
8. [数据库类型列表](#18-数据库类型列表)
9. [获取 JDBC 前缀](#19-获取-jdbc-前缀)
10. [启动元数据采集](#110-启动元数据采集)
11. [远程库表列表（选表采集）](#111-远程库表列表选表采集)
12. [查询选中表](#112-查询选中表)
13. [元数据表列表](#113-元数据表列表)
14. [元数据字段列表](#114-元数据字段列表)
15. [采集日志列表](#115-采集日志列表)
16. [采集日志详情](#116-采集日志详情)

### 二、数据模型管理
17. [模型列表](#21-模型列表)
19. [模型详情](#22-模型详情)
20. [新建模型](#23-新建模型)
21. [编辑模型](#24-编辑模型)
22. [删除模型](#25-删除模型)
23. [可选主表列表](#26-可选主表列表)
24. [可选关联表及字段](#27-可选关联表及字段)

### 三、指标组合管理
25. [组合列表](#31-组合列表)
26. [组合详情](#32-组合详情)
27. [保存组合](#33-保存组合)
28. [下线组合](#34-下线组合)
29. [上线组合](#35-上线组合)
30. [删除组合](#36-删除组合)
31. [字段候选列表](#37-字段候选列表)

### 四、指标数据预览
32. [维度指标树](#41-维度指标树)
33. [指标维度互滤候选](#411-指标维度互滤候选)
34. [执行预览查询](#42-执行预览查询)
35. [保存报表配置](#43-保存报表配置)
36. [获取报表配置](#44-获取报表配置)
37. [导出预览数据](#45-导出预览数据)

### 附录
38. [通用说明](#通用说明)
39. [枚举字典](#枚举字典)
40. [错误码表](#错误码表)

---

## 通用说明

### 公共请求 Header

| Header | 类型 | 必填 | 说明 |
|---|---|---|---|
| `Authorization` | string | 是 | `Bearer <token>` |
| `Content-Type` | string | 是 | `application/json` |
| `X-Tenant-Id` | string | 是 | 当前租户 ID |

### 公共响应结构

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | 状态码，200 表示成功 |
| `message` | string | 状态描述 |
| `data` | object \| array | 业务数据 |

### 分页请求参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `page` | int | 是 | 页码，从 1 开始 |
| `pageSize` | int | 是 | 每页条数，支持 10 / 20 / 50 / 100 |

### 分页响应结构

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [],
    "total": 100,
    "page": 1,
    "pageSize": 10
  }
}
```

---

# 一、数据源管理

> 对应表：`meta_data_source`、`meta_table`、`meta_column`、`meta_collect_log`

---

## 1.1 数据源列表

获取数据源分页列表，支持名称搜索和状态筛选。

- **URL**: `/api/v1/data-sources`
- **Method**: `GET`

### 请求参数

| 参数 | 类型 | 必填 | 位置 | 说明 |
|---|---|---|---|---|
| `keyword` | string | 否 | query | 按数据源名称模糊匹配 |
| `status` | int | 否 | query | 状态筛选：1-已启用 0-已停用 |
| `page` | int | 是 | query | 页码 |
| `pageSize` | int | 是 | query | 每页条数 |

### 请求示例

```
GET /api/v1/data-sources?keyword=门诊&status=1&page=1&pageSize=10
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "name": "门诊运营库",
        "dbType": "MySQL",
        "host": "ip",
        "port": 3306,
        "defaultDb": "clinic_ops",
        "schemaName": "",
        "displayDbSchema": "clinic_ops",
        "status": 1,
        "statusName": "已启用",
        "updatedAt": "2026-05-20 09:30:00"
      }
    ],
    "total": 8,
    "page": 1,
    "pageSize": 10
  }
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 数据源 ID |
| `name` | string | 数据源名称 |
| `dbType` | string | 数据库类型 |
| `host` | string | 主机地址 |
| `port` | int | 端口 |
| `defaultDb` | string | 数据库/SID |
| `schemaName` | string | Schema（选填） |
| `displayDbSchema` | string | 展示用，如 `clinic_ops` 或 `inpatient_dw / public` |
| `status` | int | 1-启用 0-停用 |
| `statusName` | string | 状态名称 |
| `updatedAt` | datetime | 更新时间 |

---

## 1.2 数据源详情

获取单个数据源完整信息（编辑回显）。

- **URL**: `/api/v1/data-sources/{id}`
- **Method**: `GET`

### 路径参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | long | 数据源 ID |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "门诊运营库",
    "dbType": "MySQL",
    "host": "ip",
    "port": 3306,
    "defaultDb": "clinic_ops",
    "schemaName": "",
    "username": "readonly_user",
    "jdbcUrl": "jdbc:mysql://ip:port/clinic_ops?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai",
    "status": 1,
    "tenantId": 1,
    "createdAt": "2026-05-01 10:00:00",
    "updatedAt": "2026-05-20 09:30:00"
  }
}
```

> 密码不在详情中返回；编辑时如需测试连接，前端展示 `****`，测试接口可通过 `id` 读取后端存储密码。

---

## 1.3 新建数据源

- **URL**: `/api/v1/data-sources`
- **Method**: `POST`

### 请求体

```json
{
  "name": "门诊运营库",
  "dbType": "MySQL",
  "host": "ip",
  "port": 3306,
  "defaultDb": "clinic_ops",
  "schemaName": "",
  "username": "readonly_user",
  "password": "******",
  "jdbcUrl": ""
}
```

### 请求字段说明

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | 是 | 数据源名称，租户内唯一 |
| `dbType` | string | 是 | 数据库类型，见[数据库类型枚举](#数据库类型-dtype) |
| `host` | string | 是 | 主机地址 |
| `port` | int | 是 | 端口，1-65535 |
| `defaultDb` | string | 是 | 数据库/SID/模式名 |
| `schemaName` | string | 否 | Schema（PG/GaussDB/Oracle 选填） |
| `username` | string | 是 | 账号 |
| `password` | string | 是 | 密码 |
| `jdbcUrl` | string | 否 | 留空则后端自动生成 |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "status": 1
  }
}
```

### 错误响应

```json
{
  "code": 400,
  "message": "该数据源名称已存在，请更换",
  "data": null
}
```

---

## 1.4 编辑数据源

- **URL**: `/api/v1/data-sources` 或 `/api/v1/data-sources/{id}`
- **Method**: `PUT`

### 请求体

与[新建数据源](#13-新建数据源)一致，**必须携带 `id`**。

**密码规则：**

- 不传 `password`、传空串、或传 `****` 等掩码占位符 → **保留原密码**
- 传新密码 → 更新为新密码

> 详情接口不返回密码；编辑页密码框请用占位符展示，**不要把掩码原样提交**（现已兼容，但仍建议留空）。

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "status": 1
  }
}
```

---

## 1.5 删除数据源

物理删除数据源，级联清除 `meta_table`、`meta_column` 元数据。

- **URL**: `/api/v1/data-sources/{id}`
- **Method**: `DELETE`

### 业务规则

- 若该数据源下的表已被 `olap_table_pro` 引用，拒绝删除
- 需前端二次确认

### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 错误响应

```json
{
  "code": 403,
  "message": "该数据源下有表已被注册使用，请先解除引用后再删除",
  "data": null
}
```

---

## 1.6 测试连接

保存前验证数据库连通性，无需先保存数据源。

- **URL**: `/api/v1/data-sources/test-connection`
- **Method**: `POST`

### 请求体

```json
{
  "id": 1,
  "dbType": "MySQL",
  "host": "ip",
  "port": 3306,
  "defaultDb": "clinic_ops",
  "schemaName": "",
  "username": "readonly_user",
  "password": "",
  "jdbcUrl": ""
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | long | 否 | **编辑/已保存数据源测试时必传**；未传新密码时从库中读取 |
| `dbType` | string | 是 | 数据库类型 |
| `host` | string | 是 | 主机 |
| `port` | int | 是 | 端口 |
| `defaultDb` | string | 是 | 数据库名 |
| `schemaName` | string | 否 | Schema |
| `username` | string | 是 | 账号 |
| `password` | string | 否 | 新建时必填；编辑时为空/`****` 则使用库中已保存密码 |
| `jdbcUrl` | string | 否 | 可留空由后端生成 |

> **编辑场景测试**：请求体带上 `id`，`password` 可不传（或传空/`****`），后端自动读取已保存密码。

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "message": "连接成功 — 数据库版本 MySQL 8.0.35，当前库 clinic_ops，共 23 张表",
    "dbVersion": "MySQL 8.0.35",
    "databaseName": "clinic_ops",
    "tableCount": 23
  }
}
```

### 失败响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": false,
    "message": "连接失败：Access denied for user"
  }
}
```

---

## 1.7 预览 JDBC URL

根据表单参数生成 JDBC URL（前端只读展示）。

- **URL**: `/api/v1/data-sources/jdbc-url`
- **Method**: `POST`

### 请求体

```json
{
  "dbType": "MySQL",
  "host": "ip",
  "port": 3306,
  "defaultDb": "clinic_ops",
  "schemaName": ""
}
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": "jdbc:mysql://ip:port/clinic_ops?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai"
}
```

---

## 1.8 数据库类型列表

下拉框数据源。

- **URL**: `/api/v1/meta/db-type/list`
- **Method**: `GET`

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "id": 1, "name": "MySQL", "jdbcPrefix": "jdbc:mysql://", "defaultPort": 3306 },
    { "id": 2, "name": "PostgreSQL", "jdbcPrefix": "jdbc:postgresql://", "defaultPort": 5432 },
    { "id": 3, "name": "Oracle", "jdbcPrefix": "jdbc:oracle:thin:@", "defaultPort": 1521 },
    { "id": 4, "name": "达梦 DM", "jdbcPrefix": "jdbc:dm://", "defaultPort": 5236 },
    { "id": 5, "name": "GaussDB", "jdbcPrefix": "jdbc:postgresql://", "defaultPort": 8000 },
    { "id": 6, "name": "ClickHouse", "jdbcPrefix": "jdbc:clickhouse://", "defaultPort": 8123 }
  ]
}
```

---

## 1.9 获取 JDBC 前缀

选择数据库类型后获取连接前缀。

- **URL**: `/api/v1/meta/db-type/jdbc-prefix`
- **Method**: `GET`

### 请求参数

| 参数 | 类型 | 必填 | 位置 | 说明 |
|---|---|---|---|---|
| `id` | int | 是 | query | 数据库类型 ID |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": "jdbc:mysql://"
}
```

---

## 1.10 启动元数据采集

异步采集，接口立即返回任务进行中。

- **URL**: `/api/v1/data-sources/{id}/collect`
- **Method**: `POST`

### 路径参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | long | 数据源 ID |

### 请求体

**全库采集：**

```json
{
  "collectType": "full"
}
```

**选表采集：**

```json
{
  "collectType": "select",
  "tableNames": ["fact_operation_daily", "dim_department"]
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `collectType` | string | 是 | `full`-全库采集 `select`-选表采集 |
| `tableNames` | array\<string\> | 条件 | 选表采集时传入本次勾选的表名；点击「开始采集」时自动写入 `meta_select_table` |

### 业务规则

- 同一数据源同时只能有一个 `RUNNING` 任务
- 数据源 `status=0`（停用）时禁止采集
- 使用 MySQL `GET_LOCK` 按数据源加锁
- **选表采集**：
  - 请求体携带 `tableNames` 时，先全量保存至 `meta_select_table`，再**仅采集这些表**的元数据
  - 未携带 `tableNames` 时，读取 `meta_select_table` 中上次保存的选中表进行采集
  - 采集引擎从远程库拉取全量表清单后，按选中表名过滤，不采集未选中的表

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "collectLogId": 12,
    "sourceId": 1,
    "status": "RUNNING",
    "message": "采集任务进行中"
  }
}
```

### 错误响应

```json
{
  "code": 400,
  "message": "该数据源已有采集任务进行中",
  "data": null
}
```

---

## 1.11 远程库表列表（选表采集）

从目标数据库实时读取 `INFORMATION_SCHEMA` 中的全部物理表，供「选表采集」弹窗展示。逻辑与采集引擎读表一致，支持分页与表名搜索。

- **URL**: `/api/v1/data-sources/remote-tables`
- **Method**: `POST`

### 请求体

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sourceId` | long | 是 | 数据源 ID |
| `tableName` | string | 否 | 表名模糊搜索 |
| `page` | int | 否 | 页码，从 1 开始，默认 1 |
| `pageSize` | int | 否 | 每页条数，默认 10 |

### 请求示例

```
POST /api/v1/data-sources/remote-tables
Content-Type: application/json

{
  "sourceId": 1,
  "tableName": "fact",
  "page": 1,
  "pageSize": 10
}
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "tableName": "fact_operation_daily",
        "tableComment": "运营日流水表",
        "rowCountEstimate": 23000000,
        "rowCountDisplay": "约 2,300 万"
      },
      {
        "tableName": "dim_department",
        "tableComment": "科室维度表",
        "rowCountEstimate": 150,
        "rowCountDisplay": "150"
      }
    ],
    "total": 7,
    "page": 1,
    "pageSize": 10
  }
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `tableName` | string | 物理表名 |
| `tableComment` | string | 表注释 |
| `rowCountEstimate` | long | 预估行数（来自 `TABLE_ROWS`） |
| `rowCountDisplay` | string | 展示用行数，如 `约 2,300 万` |

### 业务说明

- 数据来自远程库，非本地 `meta_table` 缓存
- 当前支持 MySQL；其他库类型需扩展对应引擎
- 连接失败时返回业务错误，如 `读取远程表列表失败：Access denied`

---

## 1.12 查询选中表

查询某数据源在 `meta_select_table` 中已勾选的表名，用于选表采集弹窗回显勾选状态（数据来自上次选表采集时自动保存）。

- **URL**: `/api/v1/meta/select-table/list`
- **Method**: `GET`

### 请求参数

| 参数 | 类型 | 必填 | 位置 | 说明 |
|---|---|---|---|---|
| `datasourceId` | int | 是 | query | 数据源 ID |

### 请求示例

```
GET /api/v1/meta/select-table/list?datasourceId=1
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "datasourceId": 1,
    "tableNames": [
      "fact_operation_daily",
      "dim_department"
    ],
    "selectedCount": 2
  }
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `datasourceId` | int | 数据源 ID |
| `tableNames` | array\<string\> | 已选中的表名列表 |
| `selectedCount` | int | 已选表数量 |

> 选中表无独立保存接口，在点击「开始采集」且 `collectType=select` 时随采集请求一并持久化。

---

## 1.13 元数据表列表

查看已采集的表（元数据弹窗）。

- **URL**: `/api/v1/meta/table/page`
- **Method**: `POST`

### 请求体

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sourceId` | long | 是 | 数据源 ID |
| `tableName` | string | 否 | 表名模糊搜索 |
| `page` | int | 否 | 页码，默认 1 |
| `pageSize` | int | 否 | 每页条数，默认 10 |

### 请求示例

```
POST /api/v1/meta/table/page
Content-Type: application/json

{
  "sourceId": 1,
  "tableName": "fact",
  "page": 1,
  "pageSize": 10
}
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 101,
        "tableName": "fact_inpatient_daily",
        "tableComment": "住院日流水表",
        "columnCount": 38,
        "rowCountEstimate": 5600000,
        "rowCountDisplay": "约 560 万",
        "lastCollectedAt": "2026-05-19 22:00:00"
      }
    ],
    "total": 2,
    "page": 1,
    "pageSize": 10
  }
}
```

---

## 1.14 元数据字段列表

展开表时获取字段列表。

- **URL**: `/api/v1/meta/column/list`
- **Method**: `GET`

### 请求参数

| 参数 | 类型 | 必填 | 位置 | 说明 |
|---|---|---|---|---|
| `tableId` | long | 是 | query | 元数据表 ID（`meta_table.id`） |

### 请求示例

```
GET /api/v1/meta/column/list?tableId=101
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1001,
      "ordinal": 1,
      "columnName": "id",
      "dataType": "bigint",
      "isNullable": 0,
      "isNullableName": "否",
      "isPk": 1,
      "columnComment": "主键"
    },
    {
      "id": 1002,
      "ordinal": 2,
      "columnName": "ward_id",
      "dataType": "varchar(16)",
      "isNullable": 0,
      "isNullableName": "否",
      "isPk": 0,
      "columnComment": "病区ID"
    }
  ]
}
```

---

## 1.15 采集日志列表

- **URL**: `/api/v1/meta/collect-log/page`
- **Method**: `POST`

### 请求体

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sourceId` | long | 是 | 数据源 ID |
| `status` | string | 否 | RUNNING / SUCCESS / FAIL |
| `page` | int | 否 | 页码，默认 1 |
| `pageSize` | int | 否 | 每页条数，默认 10 |

### 请求示例

```
POST /api/v1/meta/collect-log/page
Content-Type: application/json

{
  "sourceId": 1,
  "status": "SUCCESS",
  "page": 1,
  "pageSize": 10
}
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 12,
        "sourceId": 1,
        "collectType": "full",
        "collectTypeLabel": "全库采集",
        "status": "SUCCESS",
        "tableCount": 2,
        "columnCount": 3,
        "diffSummary": "2/3/0",
        "startedAt": "2026-06-04 14:30:25",
        "finishedAt": "2026-06-04 14:31:10",
        "durationSeconds": 45
      }
    ],
    "total": 9,
    "page": 1,
    "pageSize": 10
  }
}
```

| 字段 | 说明 |
|---|---|
| `diffSummary` | 新增表数/新增字段数/删除数，格式如 `2/3/0` |
| `durationSeconds` | 耗时（秒），RUNNING 时为 null |

---

## 1.16 采集日志详情

终端风格步骤日志。

- **URL**: `/api/v1/meta/collect-log/{id}`
- **Method**: `GET`

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 12,
    "sourceId": 1,
    "collectType": "full",
    "collectTypeLabel": "全库采集",
    "status": "SUCCESS",
    "startedAt": "2026-06-04 14:30:25",
    "finishedAt": "2026-06-04 14:31:10",
    "durationSeconds": 45,
    "logLines": [
      { "time": "14:30:25", "level": "INFO", "msg": "开始全库采集 clinic_ops" },
      { "time": "14:30:26", "level": "INFO", "msg": "连接数据源 ip:3306 (MySQL 8.0.35)" },
      { "time": "14:30:28", "level": "INFO", "msg": "读取 INFORMATION_SCHEMA.TABLES，共发现 24 张表" },
      { "time": "14:30:45", "level": "SUCCESS", "msg": "采集完成，耗时 45s" }
    ]
  }
}
```

---

# 二、数据模型管理

> 对应表：`olap_data_model`、`olap_data_model_dimension`、`olap_table_pro`、`meta_data_source`

---

## 2.1 模型列表

- **URL**: `/api/v1/data-models`
- **Method**: `GET`

### 请求参数

| 参数 | 类型 | 必填 | 位置 | 说明 |
|---|---|---|---|---|
| `keyword` | string | 否 | query | 模型名称模糊搜索 |
| `status` | int | 否 | query | 1-启用 0-停用 |
| `page` | int | 是 | query | 页码 |
| `pageSize` | int | 是 | query | 每页条数 |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "name": "门诊运营模型",
        "factTableName": "fact_operation_daily",
        "dimTableCount": 3,
        "sourceId": 1,
        "sourceName": "门诊运营库",
        "status": 1,
        "updatedAt": "2026-05-20 09:30:00"
      }
    ],
    "total": 7,
    "page": 1,
    "pageSize": 10
  }
}
```

---

## 2.2 模型详情

- **URL**: `/api/v1/data-models/{id}`
- **Method**: `GET`

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "门诊运营模型",
    "description": "门诊运营星型模型",
    "sourceId": 1,
    "sourceName": "门诊运营库",
    "factTableId": 10,
    "factTableName": "fact_operation_daily",
    "status": 1,
    "joins": [
      {
        "id": 101,
        "dimTableId": 11,
        "dimTableName": "dim_department",
        "joinType": "LEFT JOIN",
        "factFkColumn": "dept_id",
        "dimPkColumn": "id"
      },
      {
        "id": 102,
        "dimTableId": 12,
        "dimTableName": "dim_date",
        "joinType": "LEFT JOIN",
        "factFkColumn": "date_id",
        "dimPkColumn": "id"
      }
    ],
    "createdAt": "2026-05-01 10:00:00",
    "updatedAt": "2026-05-20 09:30:00"
  }
}
```

---

## 2.3 新建模型

- **URL**: `/api/v1/data-models`
- **Method**: `POST`

### 请求体

```json
{
  "name": "门诊运营模型",
  "description": "门诊运营星型模型",
  "sourceId": 1,
  "factTableId": 10,
  "joins": [
    {
      "dimTableId": 11,
      "joinType": "LEFT JOIN",
      "factFkColumn": "dept_id",
      "dimPkColumn": "id"
    }
  ]
}
```

### 请求字段说明

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | 是 | 模型名称 |
| `description` | string | 否 | 模型说明 |
| `sourceId` | long | 是 | 数据源 ID |
| `factTableId` | long | 是 | 主表 ID（`olap_table_pro.id`） |
| `joins` | array | 是 | 关联表配置，至少 1 条 |
| `joins[].dimTableId` | long | 是 | 关联表 ID |
| `joins[].joinType` | string | 是 | `LEFT JOIN` / `INNER JOIN` / `RIGHT JOIN` |
| `joins[].factFkColumn` | string | 是 | 主表关联字段（物理列名） |
| `joins[].dimPkColumn` | string | 是 | 关联表关联字段（物理列名） |

### 业务规则

- 一个事实表只能属于一个模型
- 所有表必须属于同一数据源，不支持跨数据源 JOIN
- JOIN 字段存物理列名，不引用 `meta_column.id`

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": { "id": 1 }
}
```

---

## 2.4 编辑模型

- **URL**: `/api/v1/data-models/{id}`
- **Method**: `PUT`

请求体与[新建模型](#23-新建模型)一致。

---

## 2.5 删除模型

- **URL**: `/api/v1/data-models/{id}`
- **Method**: `DELETE`

物理删除模型及 `olap_data_model_dimension` 关联关系。

---

## 2.6 可选主表列表

新建模型时，加载当前数据源下可用作主表的事实表。

- **URL**: `/api/v1/data-models/candidate-fact-tables`
- **Method**: `GET`

### 请求参数

| 参数 | 类型 | 必填 | 位置 | 说明 |
|---|---|---|---|---|
| `sourceId` | long | 是 | query | 数据源 ID |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "tableId": 10, "tableName": "fact_operation_daily", "tableComment": "运营日流水表" },
    { "tableId": 13, "tableName": "fact_revenue_summary", "tableComment": "收益汇总表" }
  ]
}
```

> 已配置过模型的事实表不在列表中返回。

---

## 2.7 可选关联表及字段

- **URL**: `/api/v1/data-models/candidate-dim-tables`
- **Method**: `GET`

### 请求参数

| 参数 | 类型 | 必填 | 位置 | 说明 |
|---|---|---|---|---|
| `sourceId` | long | 是 | query | 数据源 ID |
| `factTableId` | long | 否 | query | 主表 ID，用于加载主表字段下拉 |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "dimTables": [
      { "tableId": 11, "tableName": "dim_department", "tableComment": "科室维度表" }
    ],
    "factColumns": [
      { "columnName": "dept_id", "dataType": "varchar(16)" },
      { "columnName": "date_id", "dataType": "varchar(8)" }
    ],
    "dimColumns": {
      "11": [
        { "columnName": "id", "dataType": "bigint" },
        { "columnName": "dept_name", "dataType": "varchar(64)" }
      ]
    }
  }
}
```

---

# 三、指标组合管理

> 对应表：`olap_indicator_group`、`olap_group_item`、`olap_basic_pro`

### 接口一览

| 章节 | 接口（Method + Path） | 说明 |
|---|---|---|
| [3.1 组合列表](#31-组合列表) | `GET /api/v1/indicator-groups/list` | 分页查询 |
| [3.2 组合详情](#32-组合详情) | `GET /api/v1/indicator-groups/{id}/detail` | 按 ID 查询 |
| [3.3 保存组合](#33-保存组合) | `POST /api/v1/indicator-groups/save` | 新建/编辑（`id` 区分） |
| [3.4 下线组合](#34-下线组合) | `POST /api/v1/indicator-groups/{id}/offline` | 已上线 → 已下线 |
| [3.5 上线组合](#35-上线组合) | `POST /api/v1/indicator-groups/{id}/online` | 审批中 → 已上线 |
| [3.6 删除组合](#36-删除组合) | `DELETE /api/v1/indicator-groups/{id}/delete` | 仅草稿可删 |
| [3.7 字段候选列表](#37-字段候选列表) | `GET /api/v1/indicator-groups/candidates` | 新建/编辑候选项 |

---

## 3.1 组合列表

- **接口**: `GET /api/v1/indicator-groups/list`

### 请求参数

| 参数 | 类型 | 必填 | 位置 | 说明 |
|---|---|---|---|---|
| `keyword` | string | 否 | query | 组合名称/编码模糊搜索 |
| `status` | int | 否 | query | 0-草稿 1-审批中 2-已上线 3-已下线 |
| `page` | int | 是 | query | 页码 |
| `pageSize` | int | 是 | query | 每页条数 |

### 请求示例

```
GET /api/v1/indicator-groups/list?keyword=运营&status=2&page=1&pageSize=10
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "groupCode": "op_scorecard",
        "groupName": "运营成绩单",
        "fieldCount": 5,
        "subjectDomain": "运营分析",
        "status": 2,
        "statusName": "已上线",
        "updatedAt": "2026-05-20 09:30:00"
      }
    ],
    "total": 6,
    "page": 1,
    "pageSize": 10
  }
}
```

---

## 3.2 组合详情

- **接口**: `GET /api/v1/indicator-groups/{id}/detail`

### 请求示例

```
GET /api/v1/indicator-groups/1/detail
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "groupCode": "op_scorecard",
    "groupName": "运营成绩单",
    "subjectDomain": "运营分析",
    "description": "门诊运营核心指标组合",
    "status": 2,
    "items": [
      {
        "id": 1001,
        "itemType": "dimension",
        "itemTypeName": "维度",
        "objectId": 201,
        "objectName": "日期",
        "displayName": "日期",
        "displayOrder": 1,
        "isRequired": 1,
        "isDefaultVisible": 1,
        "formatType": null,
        "unit": null,
        "defaultSort": "DESC"
      },
      {
        "id": 1002,
        "itemType": "atomic_metric",
        "itemTypeName": "原子指标",
        "objectId": 301,
        "objectName": "药品费用",
        "displayName": "药品费用",
        "displayOrder": 3,
        "isRequired": 1,
        "isDefaultVisible": 1,
        "formatType": "amount",
        "unit": "元",
        "defaultSort": null
      }
    ],
    "createdAt": "2026-05-01 10:00:00",
    "updatedAt": "2026-05-20 09:30:00"
  }
}
```

---

## 3.3 保存组合

新建、编辑合并为同一接口：通过请求体中的 `id` 区分新建/编辑，保存后直接 **上线**（`status=2`）。

- **接口**: `POST /api/v1/indicator-groups/save`

### 行为说明

| 场景 | 条件 | 结果 |
|---|---|---|
| 新建 | `id` 为空 | 插入组合，`status=2`（已上线） |
| 编辑 | `id` 非空 | 更新组合，`status=2`（已上线） |

> 保存时校验引用的维度/指标/嵌套组合均已上线。不再支持草稿状态，`saveAction` / `status` 字段可忽略。

> 原独立的「编辑组合」「发布组合」接口已合并到此接口，不再单独提供 `PUT /api/v1/indicator-groups/{id}` 与 `POST /api/v1/indicator-groups/{id}/publish`。

### 请求示例

```
POST /api/v1/indicator-groups/save
Content-Type: application/json
```

### 请求体

**新建示例：**

```json
{
  "groupCode": "op_scorecard",
  "groupName": "运营成绩单",
  "subjectDomain": "运营分析",
  "description": "门诊运营核心指标组合",
  "items": [
    {
      "itemType": "dimension",
      "objectId": 201,
      "displayName": "日期",
      "displayOrder": 1,
      "isRequired": 1,
      "isDefaultVisible": 1,
      "defaultSort": "DESC"
    },
    {
      "itemType": "atomic_metric",
      "objectId": 301,
      "displayName": "药品费用",
      "displayOrder": 3,
      "isRequired": 1,
      "isDefaultVisible": 1,
      "formatType": "amount",
      "unit": "元"
    }
  ]
}
```

**编辑示例：**

```json
{
  "id": 1,
  "groupCode": "op_scorecard",
  "groupName": "运营成绩单",
  "subjectDomain": "运营分析",
  "description": "门诊运营核心指标组合",
  "items": []
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | long | 否 | 组合 ID；**为空新建，非空编辑** |
| `groupCode` | string | 是 | 组合编码，租户内唯一 |
| `groupName` | string | 是 | 组合名称 |
| `subjectDomain` | string | 否 | 主题域 |
| `description` | string | 否 | 描述 |
| `items` | array | 是 | 结构配置项 |
| `items[].itemType` | string | 是 | 见[组合项类型](#组合项类型-itemtype) |
| `items[].objectId` | long | 是 | 维度/指标/组合 ID |
| `items[].displayName` | string | 否 | 展示名称 |
| `items[].displayOrder` | int | 是 | 展示顺序 |
| `items[].isRequired` | int | 否 | 是否必选 0/1 |
| `items[].isDefaultVisible` | int | 否 | 是否默认展示 0/1 |
| `items[].formatType` | string | 否 | 格式类型 |
| `items[].unit` | string | 否 | 单位 |
| `items[].defaultSort` | string | 否 | `default` / `ASC` / `DESC` |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": { "id": 1, "status": 2 }
}
```

---

## 3.4 下线组合

- **接口**: `POST /api/v1/indicator-groups/{id}/offline`

### 请求示例

```
POST /api/v1/indicator-groups/1/offline
```

### 错误响应

```json
{
  "code": 403,
  "message": "组合正在被其他组合嵌套引用，无法下线",
  "data": null
}
```

---

## 3.5 上线组合

审批通过后上线，将组合从 **审批中(1)** 变为 **已上线(2)**。

- **接口**: `POST /api/v1/indicator-groups/{id}/online`

### 请求示例

```
POST /api/v1/indicator-groups/1/online
```

### 规则

- 仅 `status=1`（审批中）可上线
- 已上线重复调用返回当前状态（幂等）
- 上线前校验组合项完整，且引用的维度/指标均已上线

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": { "id": 1, "status": 2 }
}
```

### 错误响应

```json
{
  "code": 400,
  "message": "仅审批中状态可上线",
  "data": null
}
```

---

## 3.6 删除组合

仅草稿状态可删除。

- **接口**: `DELETE /api/v1/indicator-groups/{id}/delete`

### 请求示例

```
DELETE /api/v1/indicator-groups/1/delete
```

---

## 3.7 字段候选列表

新建/编辑组合时的三列候选数据源（A 区）。返回结果同时包含结构配置区（B 区）所需的默认值，前端勾选后可直接渲染 B 区表格，无需再拼装默认字段。

- **接口**: `GET /api/v1/indicator-groups/candidates`

### 请求参数

| 参数 | 类型 | 必填 | 位置 | 说明 |
|---|---|---|---|---|
| `keyword` | string | 否 | query | 搜索关键词 |
| `candidateType` | string | 否 | query | `dimension` / `metric` / `group`，不传返回全部 |
| `excludeGroupId` | long | 否 | query | 编辑组合时排除自身，避免嵌套引用自身 |

### 请求示例

```
GET /api/v1/indicator-groups/candidates?keyword=日期&candidateType=dimension&excludeGroupId=1
```

### 响应字段说明

候选项除 A 区展示字段外，还包含 B 区结构配置默认值：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 对象 ID，保存时作为 `objectId` |
| `name` | string | 中文名称（A 区展示） |
| `typeLabel` | string | 类型标签，如 `标准维` / `原子` / `计算` |
| `itemType` | string | 保存组合时使用的类型码 |
| `displayName` | string | B 区默认展示名称，默认等于 `name` |
| `displayOrder` | int | B 区顺序，候选阶段为 `null`，拖入 B 区后由前端赋值 |
| `itemTypeName` | string | B 区类型列，如 `维度` / `原子指标` / `指标组合` |
| `isRequired` | int | 是否必选，维度/指标默认 `1`，组合默认 `0` |
| `isDefaultVisible` | int | 是否默认展示，维度/指标默认 `1`，组合默认 `0` |
| `formatType` | string | 格式：`amount`-金额 / `percent`-百分比，无则为 `null` |
| `unit` | string | 单位，如 `元` / `%` |
| `defaultSort` | string | 默认排序：`default` / `DESC` / `ASC`；时间维度默认 `DESC` |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "dimensions": [
      {
        "id": 201,
        "name": "科室",
        "typeLabel": "标准维",
        "itemType": "dimension",
        "displayName": "科室",
        "displayOrder": null,
        "itemTypeName": "维度",
        "isRequired": 1,
        "isDefaultVisible": 1,
        "formatType": null,
        "unit": null,
        "defaultSort": "default"
      },
      {
        "id": 202,
        "name": "日期",
        "typeLabel": "标准维·时间",
        "itemType": "dimension",
        "displayName": "日期",
        "displayOrder": null,
        "itemTypeName": "维度",
        "isRequired": 1,
        "isDefaultVisible": 1,
        "formatType": null,
        "unit": null,
        "defaultSort": "DESC"
      }
    ],
    "metrics": [
      {
        "id": 301,
        "name": "药品费用",
        "typeLabel": "原子",
        "itemType": "atomic_metric",
        "displayName": "药品费用",
        "displayOrder": null,
        "itemTypeName": "原子指标",
        "isRequired": 1,
        "isDefaultVisible": 1,
        "formatType": "amount",
        "unit": "元",
        "defaultSort": "default"
      },
      {
        "id": 302,
        "name": "收益率",
        "typeLabel": "计算",
        "itemType": "calculated_metric",
        "displayName": "收益率",
        "displayOrder": null,
        "itemTypeName": "计算指标",
        "isRequired": 1,
        "isDefaultVisible": 1,
        "formatType": "percent",
        "unit": "%",
        "defaultSort": "default"
      }
    ],
    "groups": [
      {
        "id": 401,
        "name": "科室经营分析",
        "typeLabel": "指标组合",
        "itemType": "indicator_group",
        "displayName": "科室经营分析",
        "displayOrder": null,
        "itemTypeName": "指标组合",
        "isRequired": 0,
        "isDefaultVisible": 0,
        "formatType": null,
        "unit": null,
        "defaultSort": "default"
      }
    ]
  }
}
```

> 前端将 A 区勾选项追加到 B 区时：复制上述 B 区字段，并按当前行数设置 `displayOrder`（1、2、3…）即可。

---

# 四、指标数据预览

> BI 报表设计器：拖拽维度/指标、过滤、排序、TopN、实时预览、保存/导出

---

## 4.1 维度指标树

左侧数据源树，供拖拽投放。

- **URL**: `/api/v1/report-preview/field-tree`
- **Method**: `POST`

> 空字符串 `key` 会被忽略，等同未传。

### 请求体

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `keyword` | string | 否 | 中文名称模糊搜索 |
| `key` | string | 否 | 维度/指标英文名（`english_name`）模糊搜索 |

### 请求示例

```
POST /api/v1/report-preview/field-tree
Content-Type: application/json

{
  "keyword": "日期",
  "key": ""
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `dimensions[].id` | long | 维度 ID |
| `dimensions[].name` | string | 中文名称 |
| `dimensions[].key` | string | 英文名（`english_name`） |
| `dimensions[].fixedFirst` | boolean | 日期维度固定排第一 |
| `metrics[].name` | string | 指标分组名（`olap_label_name`，缺省为「通用」） |
| `metrics[].items[].id` | long | 指标 ID |
| `metrics[].items[].name` | string | 中文名称 |
| `metrics[].items[].key` | string | 英文名（`english_name`） |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "dimensions": [
      { "id": 201, "name": "日期", "key": "ptdate", "fixedFirst": true },
      { "id": 202, "name": "机构", "key": "org_name" },
      { "id": 203, "name": "部门", "key": "dept_name" }
    ],
    "metrics": [
      {
        "name": "通用",
        "items": [
          { "id": 301, "name": "存款余额", "key": "deposit_balance" },
          { "id": 302, "name": "放款金额", "key": "loan_amount" }
        ]
      }
    ]
  }
}
```

> `fixedFirst=true` 表示日期维度固定排在维度列表第一行。

---

## 4.1.1 指标维度互滤候选

报表设计器或组合编辑场景中，根据已选指标过滤可用维度，或根据已选维度过滤可用指标。基于已上线决策表的字段映射与数据模型 JOIN 关系计算兼容性。

- **URL**: `/api/v1/report-preview/compatible-fields`
- **Method**: `GET`

### 请求参数

| 参数 | 类型 | 必填 | 位置 | 说明 |
|---|---|---|---|---|
| `metricIds` | long[] | 否 | query | 已选指标 ID；传入后返回与之间容的维度 |
| `dimensionIds` | long[] | 否 | query | 已选维度 ID；传入后返回与之兼容的指标 |
| `keyword` | string | 否 | query | 名称模糊搜索 |

### 互滤规则

1. **选指标 → 滤维度**：找到指标所在事实表，返回该事实表上及关联维度表上的可用维度。
2. **选维度 → 滤指标**：反查维度关联的事实表，返回该事实表上的可用指标。
3. **同时传入**：取事实表交集后再双向过滤。
4. **均不传**：返回全部已上线且已映射的维度/指标。
5. 多选时取**交集**，保证结果与所有已选项同时兼容。
6. 已选项始终保留在结果中，避免过滤后选中项消失。

### 响应字段说明

除互滤结果外，每项同样携带 B 区结构配置默认值（字段含义同 [3.7 字段候选列表](#37-字段候选列表)）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 标准指标/维度 ID |
| `code` | string | 唯一 key |
| `name` | string | 中文名称 |
| `englishName` | string | 英文名称 |
| `typeLabel` | string | 类型标签 |
| `itemType` | string | 对象类型码 |
| `fixedFirst` | bool | 时间维度是否固定排第一 |
| `displayName` | string | B 区默认展示名称 |
| `displayOrder` | int | B 区顺序，候选阶段为 `null` |
| `itemTypeName` | string | B 区类型列 |
| `isRequired` | int | 是否必选 |
| `isDefaultVisible` | int | 是否默认展示 |
| `formatType` | string | 格式类型 |
| `unit` | string | 单位 |
| `defaultSort` | string | 默认排序 |
| `factTableIds` | long[] | （响应根级）当前解析出的事实表上下文 |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "factTableIds": [1],
    "dimensions": [
      {
        "id": 7,
        "code": "dim_date",
        "name": "日期",
        "englishName": "ptdate",
        "typeLabel": "标准维·时间",
        "itemType": "dimension",
        "fixedFirst": true,
        "displayName": "日期",
        "displayOrder": null,
        "itemTypeName": "维度",
        "isRequired": 1,
        "isDefaultVisible": 1,
        "formatType": null,
        "unit": null,
        "defaultSort": "DESC"
      }
    ],
    "metrics": [
      {
        "id": 11,
        "code": "metric_power",
        "name": "发电量",
        "englishName": "fadianliang",
        "typeLabel": "原子",
        "itemType": "atomic_metric",
        "fixedFirst": false,
        "displayName": "发电量",
        "displayOrder": null,
        "itemTypeName": "原子指标",
        "isRequired": 1,
        "isDefaultVisible": 1,
        "formatType": null,
        "unit": null,
        "defaultSort": "default"
      }
    ]
  }
}
```

### 调用示例

```http
# 初始：返回全部已上线字段
GET /api/v1/report-preview/compatible-fields

# 选了指标 → 过滤可用维度
GET /api/v1/report-preview/compatible-fields?metricIds=11

# 选了维度 → 过滤可用指标
GET /api/v1/report-preview/compatible-fields?dimensionIds=7

# 同时选择 → 双向过滤
GET /api/v1/report-preview/compatible-fields?metricIds=11&dimensionIds=7
```

---

## 4.2 执行预览查询

> **待实现**

根据投放区配置实时查询数据。

- **URL**: `/api/v1/report-preview/query`
- **Method**: `POST`

### 请求体

```json
{
  "dimensions": [
    { "objectId": 201, "objectType": "dimension", "displayName": "日期" },
    { "objectId": 202, "objectType": "dimension", "displayName": "部门" }
  ],
  "metrics": [
    { "objectId": 301, "objectType": "atomic_metric", "displayName": "存款余额" }
  ],
  "filters": [
    { "fieldObjectId": 301, "fieldType": "metric", "operator": ">", "value": "1000" }
  ],
  "sorts": [
    { "fieldObjectId": 201, "fieldType": "dimension", "direction": "DESC" }
  ],
  "topN": {
    "sortFieldObjectId": 301,
    "limit": 10,
    "direction": "DESC",
    "groupByObjectId": 202
  },
  "dateRange": {
    "startDate": "2026-01-01",
    "endDate": "2026-05-31",
    "granularity": "month"
  },
  "visibleFields": [201, 202, 301],
  "page": 1,
  "pageSize": 20
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `dimensions` | array | 否 | 投放的维度 |
| `metrics` | array | 否 | 投放的指标 |
| `filters` | array | 否 | 过滤条件，`operator` 支持 `>` `<` `=` `between` |
| `sorts` | array | 否 | 排序规则 |
| `topN` | object | 否 | TopN 配置；`groupByObjectId` 选填，填则为分组 TopN |
| `dateRange.granularity` | string | 否 | `day` / `week` / `month` / `quarter` / `year` |
| `visibleFields` | array | 否 | 字段配置弹窗勾选的列 |
| `page` / `pageSize` | int | 是 | 分页 |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "columns": [
      { "field": "date", "title": "日期" },
      { "field": "dept", "title": "部门" },
      { "field": "deposit_balance", "title": "存款余额" }
    ],
    "rows": [
      { "date": "2026-05-21", "dept": "内科", "deposit_balance": 1285000 }
    ],
    "total": 100,
    "page": 1,
    "pageSize": 20
  }
}
```

---

## 4.3 保存报表配置

> **待实现**

- **URL**: `/api/v1/report-preview/configs`
- **Method**: `POST`

### 请求体

与[执行预览查询](#42-执行预览查询)请求体相同，额外增加：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `configName` | string | 是 | 报表配置名称 |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": { "configId": "cfg_20260521_001" }
}
```

---

## 4.4 获取报表配置

> **待实现**

- **URL**: `/api/v1/report-preview/configs/{configId}`
- **Method**: `GET`

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "configId": "cfg_20260521_001",
    "configName": "存款余额预览",
    "dimensions": [],
    "metrics": [],
    "filters": [],
    "sorts": [],
    "topN": null,
    "dateRange": {},
    "visibleFields": []
  }
}
```

---

## 4.5 导出预览数据

> **待实现**

- **URL**: `/api/v1/report-preview/export`
- **Method**: `POST`

### 请求体

与[执行预览查询](#42-执行预览查询)一致，增加：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `exportFormat` | string | 是 | `xlsx` / `csv` |

### 响应

返回文件流，`Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` 或 `text/csv`。

---

## 枚举字典

### 数据库类型 (`dbType`)

| 值 | 默认端口 | JDBC 前缀 |
|---|---|---|
| MySQL | 3306 | `jdbc:mysql://` |
| PostgreSQL | 5432 | `jdbc:postgresql://` |
| Oracle | 1521 | `jdbc:oracle:thin:@` |
| 达梦 DM | 5236 | `jdbc:dm://` |
| GaussDB | 8000 | `jdbc:postgresql://` |
| ClickHouse | 8123 | `jdbc:clickhouse://` |

### 数据源状态 (`status`)

| 值 | 说明 |
|---|---|
| 1 | 已启用 |
| 0 | 已停用 |

### 采集方式 (`collectType`)

| 值 | 说明 |
|---|---|
| `full` | 全库采集 |
| `select` | 选表采集 |

### 采集状态 (`collectStatus`)

| 值 | 说明 | 颜色 |
|---|---|---|
| RUNNING | 执行中 | 蓝色 |
| SUCCESS | 成功 | 绿色 |
| FAIL | 失败 | 红色 |

### 模型 JOIN 类型 (`joinType`)

| 值 | 说明 |
|---|---|
| LEFT JOIN | 左连接 |
| INNER JOIN | 内连接 |
| RIGHT JOIN | 右连接 |

### 组合项类型 (`itemType`)

| 值 | 说明 |
|---|---|
| `dimension` | 维度 |
| `atomic_metric` | 原子指标 |
| `calculated_metric` | 计算指标 |
| `derived_metric` | 派生指标 |
| `indicator_group` | 指标组合 |

### 组合/指标状态 (`status`)

| 值 | 说明 |
|---|---|
| 0 | 草稿 |
| 1 | 审批中 |
| 2 | 已上线 |
| 3 | 已下线 |

### 时间粒度 (`granularity`)

| 值 | 说明 |
|---|---|
| `day` | 日期（默认） |
| `week` | 自然周 |
| `month` | 月份 |
| `quarter` | 季度 |
| `year` | 年 |

---

## 错误码表

| code | 说明 |
|---|---|
| 200 | 成功 |
| 400 | 参数错误 / 名称或编码重复 / 采集任务冲突 |
| 401 | 未认证 |
| 403 | 无权限 / 被引用无法删除或下线 / 数据源停用 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 504 | 连接超时（测试连接场景） |

---

## 附录：Controller 路径索引

> 各模块完整接口（含 Method）见对应章节；指标组合详见 [§3 接口一览](#接口一览)。

| 模块 | `@RequestMapping` 前缀 | 典型接口示例 |
|---|---|---|
| 数据源 | `/api/v1/data-sources` | `GET` 列表 · `POST` 保存 · `POST /{id}/collect` 采集 |
| 数据库类型 | `/api/v1/meta/db-type` | `GET /list` · `GET /jdbc-prefix` |
| 选表采集 | `/api/v1/meta/select-table` | `GET /list` |
| 元数据表 | `/api/v1/meta/table` | `POST /page` |
| 元数据字段 | `/api/v1/meta/column` | `GET /list` |
| 采集日志 | `/api/v1/meta/collect-log` | `POST /page` · `GET /{id}` |
| 数据模型 | `/api/v1/data-models` | `GET` 列表 · `POST` 保存 · `GET /{id}` 详情 |
| 指标组合 | `/api/v1/indicator-groups` | `GET /list` · `GET /{id}/detail` · `POST /save` · `POST /{id}/online` · `POST /{id}/offline` · `DELETE /{id}/delete` · `GET /candidates` |
| 字段映射 | `/api/v1/field-mappings` | — |
| 指标管理 | `/api/v1/metrics` | — |
| 维度管理 | `/api/v1/dimensions` | — |
| 数据预览 | `/api/v1/report-preview` | `GET /field-tree` · `POST /query` |
| AI 智能体 | `/api/v1/ai-bodies` | — |
| 对话 | `/api/v1/chat` | — |

> §4.2 ~ §4.5（预览查询 / 报表配置 / 导出）尚未实现，路径为预留设计。
