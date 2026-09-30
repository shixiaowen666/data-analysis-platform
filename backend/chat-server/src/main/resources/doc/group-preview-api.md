# GROUP 预览接口

## 基本信息

- **路径**: `/api/chat-server/group/preview`
- **方法**: `POST`

## 请求头

| Header | 类型 | 必填 | 说明 |
|--------|------|------|------|
| Authorization | String | 是 | JWT Token，格式 `Bearer <token>` |
| tenantid | String | 是 | 租户ID |
| Content-Type | String | 是 | `application/json` |

---

## 入参

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| items | Array\<Item\> | 是 | 指标/维度/组合列表 |

### Item

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| itemType | String | 是 | `dimension` / `atomic_metric` / `calculated_metric` / `derived_metric` / `indicator_group` |
| objectId | Long | 是 | 对应 `olap_basic_pro.id` 或 `olap_indicator_group.id` |
| defaultSort | String | 否 | 仅指标有效，`ASC` / `DESC` |
| displayName | String | 否 | 展示名，`indicator_group` 建议填写（表头分组名），叶子可选 |

---

## 返回

### 成功

| 字段 | 类型 | 说明 |
|------|------|------|
| ret | String | `success` |
| code | Integer | `200` |
| data | Object | 见下方 |

### Data

| 字段 | 类型 | 说明 |
|------|------|------|
| total | Long | 总记录数 |
| columns | Array\<ColumnNode\> | 列元数据树，支持多层嵌套 |
| records | Array\<Map\> | 数据行，行内 key=字段标识 value=展示值（已格式化） |
| sql | String | 执行的 SQL 信息（JSON 字符串） |
| page | Integer | 当前页码 |
| pageSize | Integer | 每页条数，固定 `10` |

### ColumnNode（树结构）

| 字段 | 类型 | 说明 |
|------|------|------|
| key | String | **仅叶子**，对应 records 的取值字段 |
| name | String | 列中文名 |
| type | String | **仅叶子**，`dimension` / `indicator` |
| unit | String | **仅叶子**，单位 |
| precision | Integer | **仅叶子**，小数位数 |
| children | Array\<ColumnNode\> | **仅分组**，子节点列表 |

> 叶子有 `key` + `type` + `unit` + `precision`，无 `children`。分组有 `name` + `children`，无 `key`、`type`。

---

## 枚举

### ItemType

| 值 | 说明 |
|------|------|
| `dimension` | 维度 |
| `atomic_metric` | 原子指标 |
| `calculated_metric` | 计算指标 |
| `derived_metric` | 衍生指标 |
| `indicator_group` | 指标组合（展开为子节点） |

### 方向

| 值 | 说明 |
|------|------|
| `ASC` | 升序 |
| `DESC` | 降序 |

### 错误码

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 示例

### 平铺列

**Request**
```json
{
  "items": [
    {"itemType": "atomic_metric", "objectId": 11, "defaultSort": "DESC"},
    {"itemType": "dimension", "objectId": 7}
  ]
}
```

**Response**
```json
{
  "ret": "success",
  "code": 200,
  "data": {
    "total": 29438,
    "columns": [
      {"key": "fadianliang", "name": "发电量", "type": "indicator", "unit": "kwh", "precision": 2},
      {"key": "ptdate", "name": "日期", "type": "dimension"}
    ],
    "records": [
      {"fadianliang": "60.00", "ptdate": "2026-03-13"},
      {"fadianliang": "59.97", "ptdate": "2026-03-24"}
    ],
    "sql": "{...}",
    "page": 1,
    "pageSize": 10
  }
}
```

### 包含 indicator_group

**Request**
```json
{
  "items": [
    {"itemType": "indicator_group", "objectId": 1, "displayName": "基础指标"},
    {"itemType": "atomic_metric", "objectId": 11, "defaultSort": "DESC"},
    {"itemType": "dimension", "objectId": 7}
  ]
}
```

> 假设 group(1) 包含 [供电局(18), 发电量(11)]。

**Response**
```json
{
  "ret": "success",
  "code": 200,
  "data": {
    "total": 29438,
    "columns": [
      {
        "name": "基础指标",
        "children": [
          {"key": "org", "name": "供电局", "type": "dimension"},
          {"key": "fadianliang", "name": "发电量", "type": "indicator", "unit": "kwh", "precision": 2}
        ]
      },
      {"key": "fadianliang", "name": "发电量", "type": "indicator", "unit": "kwh", "precision": 2},
      {"key": "ptdate", "name": "日期", "type": "dimension"}
    ],
    "records": [
      {"org": "福永供电分局", "fadianliang": "60.00", "ptdate": "2026-03-13"},
      {"org": "大鹏局", "fadianliang": "59.97", "ptdate": "2026-03-24"}
    ],
    "sql": "{...}",
    "page": 1,
    "pageSize": 10
  }
}
```

> 注意：`fadianliang` 在 columns 树中出现了两次（group 内一次、顶层一次），但 records 中只有一个条目。前端渲染时两个叶子节点取同一个 key 的值。

### 三层嵌套 indicator_group

**Request**
```json
{
  "items": [
    {"itemType": "indicator_group", "objectId": 2, "displayName": "运营分析"}
  ]
}
```

> 假设 group(2) 包含 [日期, 收入指标(group 3)]，group(3) 包含 [收益, 收益率]

**Response**
```json
{
  "ret": "success",
  "code": 200,
  "data": {
    "total": 100,
    "columns": [
      {
        "name": "运营分析",
        "children": [
          {"key": "ptdate", "name": "日期", "type": "dimension"},
          {
            "name": "收入指标",
            "children": [
              {"key": "revenue", "name": "收益", "type": "indicator", "unit": "元", "precision": 0},
              {"key": "revenue_rate", "name": "收益率", "type": "indicator", "unit": "%", "precision": 2}
            ]
          }
        ]
      }
    ],
    "records": [
      {"ptdate": "2026-05-21", "revenue": "45,200", "revenue_rate": "35.18%"}
    ],
    "sql": "{...}",
    "page": 1,
    "pageSize": 10
  }
}
```

**渲染效果**：
```
|        运营分析         |
|           |  收入指标   |
| 日期      | 收益(元) | 收益率(%) |
|-----------|----------|-----------|
| 2026-05-21 | 45,200   | 35.18%    |
```

---

### 错误返回

**缺少租户ID**
```json
{
  "code": 404,
  "message": "缺少租户id(tenantid)，请重新登陆"
}
```

**Token无效**
```json
{
  "code": 401,
  "message": "token无效，非法登陆"
}
```

---

## 前端渲染指南

### 表头

1. 遍历 `columns` 树，计算每个节点的 `colSpan`（后代叶子数）和 `rowSpan`（`最大深度 - 当前深度 + 1`）
2. 渲染等深度的表格头行，`colSpan` 跨列、`rowSpan` 跨行
3. 叶子节点展示 `name`，指标列可拼接 `unit`

### 数据行

遍历 `records`，按 columns 树深度优先找到叶子，取 `records[row][leaf.key]`。

### 同一 key 多次出现

columns 树中同一 `key` 可多次出现（如 group 内 + 顶层），前端分别用 `key` 去 records 取同一个值即可，records 内不重复。

### 伪代码

```javascript
// 计算 colSpan / rowSpan
function calcLayout(nodes, depth, maxDepth) {
  return nodes.map(node => {
    if (node.children) {
      const children = calcLayout(node.children, depth + 1, maxDepth);
      const leafCount = children.reduce((s, c) => s + c.colSpan, 0);
      return { ...node, colSpan: leafCount, rowSpan: 1, children };
    }
    return { ...node, colSpan: 1, rowSpan: maxDepth - depth + 1 };
  });
}

// 深度优先收集叶子 key 顺序
function getLeafKeys(nodes) {
  return nodes.flatMap(n => n.children ? getLeafKeys(n.children) : [n.key]);
}

// 渲染一行
const keys = getLeafKeys(columns);
records.forEach(row => {
  const cells = keys.map(k => row[k]);
});
```
