# 推荐问题管理

## 接口信息

| 项目 | 说明 |
|------|------|
| 请求前缀 | `/v1/recommend/question` |
| 权限 | 租户隔离，从 `UserThreadLocal` 获取当前租户 |

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/v1/recommend/question/page` | 分页查询推荐问题 |
| `POST` | `/v1/recommend/question/save` | 新增/编辑推荐问题 |
| `POST` | `/v1/recommend/question/status` | 更新启用/禁用状态 |
| `GET` | `/v1/recommend/question/del/{id}` | 删除推荐问题 |
| `GET` | `/v1/recommend/tag/list` | 标签列表 |
| `POST` | `/v1/recommend/tag/save` | 新增/编辑标签 |
| `GET` | `/v1/recommend/tag/del/{id}` | 删除标签 |

---

## 1. 分页查询

```
GET /v1/recommend/question/page
```

### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `aiBodyCode` | String | 是 | 智能体编码 |
| `keyword` | String | 否 | 模糊搜索问题内容 |
| `tagId` | Long | 否 | 按标签筛选，传入标签ID |
| `page` | Integer | 否 | 页码，默认 1 |
| `pageSize` | Integer | 否 | 每页条数，默认 20 |

### 返回结果

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "aiBodyCode": "agent_power",
        "question": "请按日展示龙华供电局2026年6月光伏用户最大负荷同比变化趋势",
        "description": "展示龙华供电局光伏用户最大负荷的同比变化趋势",
        "sortOrder": 0,
        "status": 1,
        "tags": [
          { "id": 1, "name": "同比环比" }
        ],
        "createdAt": "2026-07-20T10:00:00"
      }
    ],
    "total": 6,
    "page": 1,
    "pageSize": 20
  }
}
```

### 内部逻辑

1. 校验 `aiBodyCode` 非空
2. 如果传了 `tagId`，先查 `chat_recommend_question_tag` 获取匹配的 `question_id` 集合，再 `IN` 过滤
3. 根据 `aiBodyCode`、`keyword`、`tenantId` 动态拼接查询条件
4. 按 `sort_order` 升序分页
5. 批量查标签关联表 + 标签表，组装 `tagNames` 到 VO

---

## 2. 新增/编辑

```
POST /v1/recommend/question/save
```

### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `id` | Long | 否 | 传入时为编辑，不传为新增 |
| `aiBodyCode` | String | 是 | 智能体编码 |
| `question` | String | 是 | 问题内容，最长 500 |
| `description` | String | 否 | 问题描述 |
| `sortOrder` | Integer | 否 | 排序，默认 0 |
| `tagIds` | Long[] | 否 | 关联标签 ID 列表 |

### 请求示例

```json
{
  "id": 1,
  "aiBodyCode": "agent_power",
  "question": "请按日展示龙华供电局2026年6月光伏用户最大负荷同比变化趋势",
  "description": "展示龙华供电局光伏用户最大负荷的同比变化趋势",
  "sortOrder": 0,
  "tagIds": [1]
}
```

### 返回结果

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 内部逻辑

1. 如果 `id` 非空，`updateById` 更新问题，然后删除旧标签关联，再插入新的
2. 如果 `id` 为空，`insert` 新增问题（`status` 默认 1），再插入标签关联
3. 整个过程 `@Transactional` 事务保护

---

## 3. 更新状态

```
POST /v1/recommend/question/status?id=1&status=0
```

### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `id` | Long | 是 | 问题 ID |
| `status` | Integer | 是 | 0-禁用 1-启用 |

### 返回结果

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
  "code": 404,
  "message": "推荐问题不存在",
  "data": null
}
```

---

## 4. 删除

```
GET /v1/recommend/question/del/{id}
```

### 内部逻辑

1. `selectById` 校验记录存在且属于当前租户
2. 先删除 `chat_recommend_question_tag` 中该问题的所有关联
3. 再删除问题本身
4. `@Transactional` 事务保护

### 错误响应

- 不存在或非本租户 → `404 "推荐问题不存在"`

---


# 标签管理

## 接口信息

| 项目 | 说明 |
|------|------|
| 请求前缀 | `/v1/recommend/tag` |
| 权限 | 租户隔离，从 `UserThreadLocal` 获取当前租户 |

---

## 1. 标签列表

```
GET /v1/recommend/tag/list
```

### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `keyword` | String | 否 | 模糊搜索标签名称 |

### 返回结果

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "id": 1, "name": "同比环比" },
    { "id": 2, "name": "异常诊断" },
    { "id": 3, "name": "明细下钻" },
    { "id": 4, "name": "趋势预测" },
    { "id": 5, "name": "归因分析" }
  ]
}
```

---

## 2. 新增/编辑

```
POST /v1/recommend/tag/save
```

### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `id` | Long | 否 | 传入时为编辑，不传为新增 |
| `name` | String | 是 | 标签名称，最长 100 |

### 请求示例

```json
{
  "name": "同比环比"
}
```

### 内部逻辑

1. 校验同租户下标签名称唯一（`uk_tenant_name` 索引兜底）
2. `id` 非空则 `updateById`，否则 `insert`

### 错误响应

```json
{
  "code": 400,
  "message": "标签名称已存在",
  "data": null
}
```

---

## 3. 删除

```
GET /v1/recommend/tag/del/{id}
```

### 内部逻辑

1. `selectById` 校验记录存在且属于当前租户
2. 检查 `chat_recommend_question_tag` 中是否有记录引用该标签
3. 被引用时拦截，未引用则删除

### 错误响应

```json
{
  "code": 400,
  "message": "标签已被问题引用，无法删除",
  "data": null
}
```

```json
{
  "code": 404,
  "message": "标签不存在",
  "data": null
}
```
