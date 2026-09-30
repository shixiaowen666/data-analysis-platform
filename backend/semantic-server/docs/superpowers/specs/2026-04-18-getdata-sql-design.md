# GetData SQL 接口设计

## 1. 接口定义

- **路径**: `/api/getdata/sql`
- **方法**: `POST`
- **Content-Type**: `application/json`

### 1.1 请求参数

```json
{
  "dimensionIds": [1, 2, 3],
  "indicatorIds": [101, 102, 103],
  "timeRange": {
    "start": "2026-01-01",
    "end": "2026-01-31"
  },
  "filters": [
    {"dimensionId": 1, "values": ["广东", "江苏"]}
  ],
  "paging": {
    "page": 1,
    "pageSize": 1000
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| dimensionIds | Long[] | 是 | 维度ID列表（olap_basic_pro.id） |
| indicatorIds | Long[] | 是 | 指标ID列表（olap_basic_pro.id） |
| timeRange | Object | 否 | 时间范围 |
| timeRange.start | String | 是 | 开始日期 YYYY-MM-dd |
| timeRange.end | String | 是 | 结束日期 YYYY-MM-dd |
| filters | Object[] | 否 | 过滤条件 |
| filters[].dimensionId | Long | 是 | 维度ID |
| filters[].values | String[] | 是 | 维度值列表 |
| paging | Object | 否 | 分页 |
| paging.page | Integer | 是 | 页码，默认1 |
| paging.pageSize | Integer | 是 | 每页数量，默认1000 |

### 1.2 响应结构

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sql": "select ...",
    "columns": ["alias_region_name", "alias_hydro_td_gen"],
    "records": [...],
    "total": 10000,
    "paging": {
      "page": 1,
      "pageSize": 1000,
      "totalPages": 10
    }
  }
}
```

## 2. 核心流程

### 2.1 流程图

```
┌─────────────┐
│ 接收请求   │
└─────┬───────┘
      │
      ▼
┌─────────────┐
│ 解析维度   │ ──→ olap_basic_pro(id) → olap_basic_pro_dimension
└─────┬───────┘
      │
      ▼
┌─────────────┐
│ 解析指标   │ ──→ olap_basic_pro(id) → olap_basic_pro_indicator
└─────┬───────┘
      │
      ▼
┌─────────────┐
│ 找候选表   │ ──→ olap_table_pro + olap_src_table_field_mapping
└─────┬───────┘
      │
      ▼
┌─────────────────────┐
│ 表选择决策           │
│ 1. 包含所有维度    │
│ 2. 包含所有指标    │
│ 3. 字段数最少      │
└─────┬───────────────┘
      │
      ▼
┌─────────────┐
│ SQL拼接    │ ──→ 字段映射 → SELECT → WHERE → GROUP BY
└─────┬───────┘
      │
      ▼
┌─────────────┐
│ 执行SQL    │
└─────┬───────┘
      │
      ▼
┌─────────────┐
│ 返回结果  │
└─────────────┘
```

### 2.2 表选择决策算法

```
输入: dimensionIds, indicatorIds
输出: selectedTable

Step1: 获取维度信息
  dimensions = []
  FOR each dimId IN dimensionIds:
    dimInfo = SELECT * FROM olap_basic_pro WHERE id = dimId AND category = 1
    dimFields = SELECT * FROM olap_basic_pro_dimension WHERE olap_basic_pro_id = dimId
    dimensions.add({dimInfo, dimFields})

Step2: 获取指标信息
  indicators = []
  FOR each indId IN indicatorIds:
    indInfo = SELECT * FROM olap_basic_pro WHERE id = indId AND category = 2
    indFields = SELECT * FROM olap_basic_pro_indicator WHERE olap_basic_pro_id = indId
    indicators.add({indInfo, indFields})

Step3: 收集维度关联的表
  dimTableIds = []  // 维度涉及的表ID集合
  FOR each dimField IN dimensions.fields:
    tableFields = SELECT * FROM olap_src_table_field_mapping WHERE basic_id = dimField.id
    dimTableIds.add(tableFields.tableId)
  dimTableIds = dimTableIds.distinct()

Step4: 筛选包含所有维度的候选表
  candidateTables = []
  FOR each tableId IN dimTableIds:
    tableFields = SELECT * FROM olap_src_table_field_mapping WHERE table_id = tableId
    tableFieldBasicIds = tableFields.map(f -> f.basic_id)
    // 检查是否包含所有维度字段
    if all dimField.id IN tableFieldBasicIds:
      candidateTables.add(tableId)

  IF candidateTables.isEmpty():
    ERROR: "维度之间不可关联"

Step5: 筛选包含所有指标的表
  indicatorFieldIds = indicators.fields.map(f -> f.id)
  validTables = []
  FOR each tableId IN candidateTables:
    tableFields = SELECT * FROM olap_src_table_field_mapping WHERE table_id = tableId
    tableFieldBasicIds = tableFields.map(f -> f.basic_id)
    IF all indicatorFieldId IN tableFieldBasicIds:
      validTables.add(tableId)

  IF validTables.isEmpty():
    ERROR: "指标和维度之间不可关联"

Step6: 如果有多张表，选择字段数最少的
  FOR each tableId IN validTables:
    fieldCount = SELECT COUNT(*) FROM olap_src_table_field_mapping WHERE table_id = tableId
    keep (tableId, fieldCount)

  selectedTable = validTables.sort(fieldCount).first()
  RETURN selectedTable
```

## 3. SQL拼接规则

### 3.1 子查询结构

```sql
SELECT
  {维度字段映射} as alias_{维度名},
  {指标聚合} as alias_{指标名}
FROM (
  SELECT
    {时间字段} as ptdate,
    {维度字段} as {维度别名},
    {指标字段} as {指标别名},
    ...
  FROM {选择的表} mainsrc
) f
WHERE 1 = 1
  {时间范围条件}
  {维度过滤条件}
GROUP BY {维度字段}
LIMIT {pageSize} OFFSET {(page-1)*pageSize}
```

### 3.2 字段映射规则

- **时间字段**: 转换为 `TO_CHAR(stat_date, 'YYYY-MM-dd')`
- **维度字段**: 直接映射，使用 `column_key`
- **指标字段**: 使用 `SUM(字段)` 聚合

### 3.3 别名规则

```
维度: alias_{standard_name}
指标: alias_{standard_name}
```

## 4. 错误码

| code | message | 说明 |
|------|---------|------|
| 400 | dimensionIds不能为空 | 维度ID为空 |
| 400 | indicatorIds不能为空 | 指标ID为空 |
| 400 | 维度之间不可关联 | 维度不在同一张表 |
| 400 | 指标和维度之间不可关联 | 指标和维度不在同一张表 |
| 500 | 系统错误 | 内部异常 |

## 5. 待确认问题

1. **分页方式**: 目前使用LIMIT/OFFSET，是否需要支持游标分页？
2. **数据权限**: 是否需要根据用户权限过滤region数据？
3. **缓存**: 是否需要对SQL结果缓存？
4. **慢查询**: 是否需要对查询超时做限制？