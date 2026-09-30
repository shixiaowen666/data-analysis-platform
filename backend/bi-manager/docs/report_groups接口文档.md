# report_groups 接口文档

Base URL：`/api/v1/report_groups`
认证：所有接口需 `Authorization: Bearer {token}` + `tenantid: 1` 请求头
响应统一包装：`{"code":200,"message":"success","data":...,"success":true}`

## 一、接口列表

| # | 接口 | 方法 | 说明 |
|---|---|---|---|
| 1 | `/save` | POST | 保存组合（id 空新建，非空编辑；保存后置为已上线） |
| 2 | `/list` | GET | 分页列表（keyword/status 筛选） |
| 3 | `/{id}` | GET | 详情（含 groupConfig，用于编辑回填） |
| 4 | `/{id}/online` | POST | 上线 |
| 5 | `/{id}/offline` | POST | 下线 |
| 6 | `/{id}/delete` | GET | 删除（仅草稿/审批中/已下线可删） |

**状态枚举**：0-草稿、1-审批中、2-已上线、3-已下线

---

## 二、1. 保存组合

**基本信息**

| 项 | 值 |
|---|---|
| 接口 | `POST /api/v1/report_groups/save` |
| 请求体 | JSON |

**请求参数（Body）**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | Long | 否 | 空=新建；非空=编辑该组合 |
| groupCode | String | 是 | 组合编码（英文名），租户内唯一 |
| groupName | String | 是 | 组合名称（中文名） |
| description | String | 否 | 描述 |
| groupConfig | String | 是 | 组合配置 JSON 字符串（数据预览查询上下文） |

**请求示例**

```json
{
  "id": null,
  "groupCode": "month_report",
  "groupName": "月报",
  "description": "月度运营报表",
  "groupConfig": "{\"timeRange\":{\"start\":\"2026-07-01\",\"end\":\"2026-07-31\"},\"timeGranularity\":\"day\",\"dimensions\":[{\"id\":156,\"key\":\"keshi_name\",\"name\":\"科室\"},{\"id\":17,\"key\":\"ptdate\",\"name\":\"日期\"}],\"indicators\":[{\"id\":11,\"key\":\"wscl_zc\",\"name\":\"卫生材料支出\"}],\"filters\":[],\"sorts\":[],\"topN\":null}"
}
```

**返回示例（成功）**

```json
{
  "code": 200,
  "message": "success",
  "data": 12,
  "success": true
}
```

`data` 为组合 ID。保存后组合状态为**已上线(2)**。

**失败示例**

```json
{"code": 400, "message": "组合编码已存在: month_report", "success": false}
{"code": 400, "message": "组合编码不能为空", "success": false}
{"code": 404, "message": "指标组合不存在", "success": false}
```

---

## 三、2. 分页列表

**基本信息**

| 项 | 值 |
|---|---|
| 接口 | `GET /api/v1/report_groups/list` |
| 参数 | Query |

**请求参数（Query）**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | String | 否 | 模糊搜组合名称/编码 |
| status | Integer | 否 | 0-草稿 1-审批中 2-已上线 3-已下线 |
| page | Integer | 否 | 页码，默认 1 |
| pageSize | Integer | 否 | 每页条数，默认 10 |

**请求示例**

```
GET /api/v1/report_groups/list?keyword=月报&status=2&page=1&pageSize=10
```

**返回示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 12,
        "groupCode": "month_report",
        "groupName": "月报",
        "description": "月度运营报表",
        "groupConfig": "{\"timeRange\":{...}}",
        "status": 2,
        "statusName": "已上线",
        "createdAt": "2026-08-25 16:00:00",
        "updatedAt": "2026-08-25 16:00:00"
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 10
  },
  "success": true
}
```

---

## 四、3. 组合详情

**基本信息**

| 项 | 值 |
|---|---|
| 接口 | `GET /api/v1/report_groups/{id}` |
| 参数 | Path |

**请求示例**

```
GET /api/v1/report_groups/12
```

**返回示例（成功）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 12,
    "groupCode": "month_report",
    "groupName": "月报",
    "description": "月度运营报表",
    "groupConfig": "{\"timeRange\":{\"start\":\"2026-07-01\",\"end\":\"2026-07-31\"},\"timeGranularity\":\"day\",\"dimensions\":[{\"id\":156,\"key\":\"keshi_name\",\"name\":\"科室\"}],\"indicators\":[{\"id\":11,\"key\":\"wscl_zc\",\"name\":\"卫生材料支出\"}],\"filters\":[],\"sorts\":[],\"topN\":null}",
    "status": 2,
    "statusName": "已上线",
    "createdAt": "2026-08-25 16:00:00",
    "updatedAt": "2026-08-25 16:00:00"
  },
  "success": true
}
```

编辑流程：前端拿到 `groupConfig`（JSON 字符串）反序列化后回填数据预览页的维度/指标/时间范围/过滤/排序/TopN。

**失败示例**

```json
{"code": 404, "message": "指标组合不存在", "success": false}
```

---

## 五、4. 上线组合

**基本信息**

| 项 | 值 |
|---|---|
| 接口 | `POST /api/v1/report_groups/{id}/online` |
| 参数 | Path |

**请求示例**

```
POST /api/v1/report_groups/12/online
```

**返回示例（成功）**

```json
{"code": 200, "message": "success", "success": true}
```

已上线状态调用为幂等操作，直接返回成功。

---

## 六、5. 下线组合

**基本信息**

| 项 | 值 |
|---|---|
| 接口 | `POST /api/v1/report_groups/{id}/offline` |
| 参数 | Path |

**请求示例**

```
POST /api/v1/report_groups/12/offline
```

**返回示例（成功）**

```json
{"code": 200, "message": "success", "success": true}
```

已下线状态调用为幂等操作，直接返回成功。

---

## 七、6. 删除组合

**基本信息**

| 项 | 值 |
|---|---|
| 接口 | `GET /api/v1/report_groups/{id}/delete` |
| 参数 | Path |

**请求示例**

```
GET /api/v1/report_groups/12/delete
```

**返回示例（成功）**

```json
{"code": 200, "message": "success", "success": true}
```

**失败示例**

```json
{"code": 403, "message": "已上线状态不可删除，请先下线", "success": false}
{"code": 403, "message": "无权限操作该组合", "success": false}
{"code": 404, "message": "指标组合不存在", "success": false}
```

**删除规则**

| 状态 | 能否删除 |
|---|---|
| 草稿（0） | ✅ |
| 审批中（1） | ✅ |
| 已上线（2） | ❌ 需先调下线接口 |
| 已下线（3） | ✅ |

物理删除，不可恢复。
