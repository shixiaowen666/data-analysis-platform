# 快照值设计文档

> 状态：已实现（v1 简化方案）

## 1. 业务语义

快照值：**取某一天时点上的指标值**，不做跨日累计。

与普通流量指标的区别：

| | 流量指标 | 快照指标 |
|---|---|---|
| 语义 | 时间段内累计/平均 | 某天的时点值 |
| 示例 | 卫生材料支出（月累计） | 库存余额（当天余额） |
| 聚合方式 | sum/avg/count | sum（日粒度下 = 当天值） |

## 2. v1 方案：强制 ptdate 维度

### 2.1 核心思路

不做 SQL 包装层。**请求含快照指标时，强制要求维度里包含 ptdate**。

日粒度下，每个分组只有一天的数据，`sum(快照字段)` 就是当天的快照值，现有聚合逻辑天然正确，无需任何 SQL 改造。

### 2.2 实现

```
mapping 表: olap_table_field_mapping.summary = "snapshot"

代码:
  1. TableSelectionServiceImpl.pickAggFunction
     summary="snapshot"/"last_value" → aggFunc="snapshot"（不再兜底成 sum）

  2. SqlGenerationServiceImpl.validateSnapshotConstraints
     请求含快照指标时校验:
       a. 数据源必须有 ptdate 分区字段，否则报错
       b. request.dimensionIds 必须包含 ptdate 的 basicId，否则报错
         "快照指标查询必须包含 ptdate 日期维度"

SQL 效果（日粒度，ptdate 在 GROUP BY）:

  SELECT f.dim_dept, f.ptdate,
         sum(f.material_all_out) as material_all_out,  -- 快照: sum = 当天值
         sum(f.num_in) as num_in
  FROM (inner) f
  WHERE f.ptdate BETWEEN '2026-06-01' AND '2026-06-03'
  GROUP BY f.dim_dept, f.ptdate
```

### 2.3 语义正确性

| 场景 | 行为 |
|---|---|
| 快照指标 + ptdate 在维度里 | sum = 当天快照值，正确 |
| 快照指标 + ptdate 不在维度里 | 请求校验报错，明确提示 |
| 同一天多条明细 | sum 累加当天多条，语义 = 当天快照总量 |
| 多源 | 现有 UNION ALL + 外层 sum 逻辑天然兼容 |

## 3. 历史方案（已废弃）

### 3.1 快照包装层方案

曾设计 SnapshotWrapper：强制 ptdate 进 GROUP BY + `MAX(ptdate) OVER` 标记最新日期 + 外层聚合取最新值，支持无日期维度的月/季汇总场景。

实现后测试发现 PostgreSQL 报错 `aggregate function calls cannot contain window function calls`——`sum(CASE WHEN ... max() OVER ...)` 语法非法，需要拆成两层子查询。

后决定放弃：v1 不做快照值 SQL 兼容，强制要求 ptdate 维度，日粒度下现有逻辑天然正确。月/季汇总的快照语义留待后续有业务需求时再做。

### 3.2 废弃方案的关键教训

- 窗口函数不能嵌套在聚合函数内部（PG/MySQL 语法限制）
- 若未来恢复包装层方案，需拆两层：窗口标记层（单独算 max date）+ 聚合层（sum case when 普通列比较）
- 性能上包装层操作的是聚合后小数据集（分组数×天数），可控

## 4. 边界

- 快照指标必须配合 ptdate 维度查询，否则报错
- 管理端约束：快照值不建议作为计算指标的子指标（后端不做强制校验，但语义上快照子指标在公式里按 sum 处理）
- summary 值支持 `snapshot` 和 `last_value` 两种写法，统一归一化为 snapshot
