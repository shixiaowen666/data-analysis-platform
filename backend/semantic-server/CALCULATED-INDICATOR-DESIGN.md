# 计算指标（Calculated Indicator）设计文档

## 1. 背景

### 1.1 问题

比率型指标（如"净管理资产 = 管理资产总额 - 贷款余额"）无法用现有的单一聚合函数（sum/count/avg）表达。跨天场景下，外层 `sum(每天的值)` 再相减可能得到正确结果，但像"次均金额 = 总金额/总次数"这种除法指标，外层 `sum(每天的avg)` 是错误结果，需要 `sum(金额)/sum(次数)` 才能得到正确的加权平均。

### 1.2 现状

`olap_basic_pro_indicator.calculated_production` 字段已在 DDL 中定义，管理端已保存公式数据，Java 代码从未解析。公式指标能力待实现。

## 2. 公式 JSON 格式

### 2.1 选型过程

最初设计：

```json
{
  "formula": "A/B",
  "mapping": [
    {"symbol": "A", "metricId": 1},
    {"symbol": "B", "metricId": 2}
  ]
}
```

管理端实际保存的样例：

```json
{
  "displayFormula": "${A}-${B}",
  "formula": "{374}-{158}",
  "indicatorList": [
    {"id": 374, "letter": "A", "name": "管理资产总额"},
    {"id": 158, "letter": "B", "name": "贷款余额"}
  ]
}
```

对比结论：**管理端的设计更好**，三个理由：

1. **`formula` 用 `{id}` 占位更安全** —— 原设计用字母做变量名（`A/B`），解析时可能歧义（符号 `AB` 和 `A`、`B` 同时存在时不好拆分）。`{374}-{158}` 直接用 ID 占位，正则 `\{(\d+)\}` 一把提取，不存在歧义。

2. **`displayFormula` 和 `formula` 职责分离** —— `formula` 给后端计算用，ID 占位精确高效；`displayFormula` 给前端展示用，`${A}` 格式人类可读。原设计只靠一个 `formula` 兼顾两头，Java 层要反复做符号→ID→符号的来回转换。

3. **`indicatorList` 带 `name` 更好** —— 多了 `name` 字段（"管理资产总额"），前端展示映射关系时不需要再查一次指标名称，减少网络请求。trade-off 是 `displayFormula` 和 `formula` 存在不一致可能，但可通过后端校验兜底。

### 2.2 最终格式

```json
{
  "displayFormula": "${A}-${B}",
  "formula": "{374}-{158}",
  "indicatorList": [
    {"id": 374, "letter": "A", "name": "管理资产总额"},
    {"id": 158, "letter": "B", "name": "贷款余额"}
  ]
}
```

| 字段 | 说明 |
|---|---|
| `displayFormula` | 前端展示用，`${letter}` 占位符，人类可读 |
| `formula` | 后端计算用，`{id}` 占位符，解析无歧义，支持 `+` `-` `*` `/` 和括号 `()` |
| `indicatorList` | 子指标映射表，每个子指标含 `id`（指标 ID）、`letter`（公式符号）、`name`（展示名称） |

- `formula` 通过正则 `\{(\d+)\}` 提取所有子指标 ID，再与 `indicatorList` 建立 ID → letter 的映射
- `displayFormula` 和 `formula` 表达同一表达式结构，后端可做一致性校验
- 子指标必须是物理指标（有 `olap_table_field_mapping` 记录）
- v1 不支持嵌套（子指标不能也是计算指标）

## 3. 核心设计原则

1. **预处理**：解析公式 → 展开子指标 ID → 和普通指标汇合 → 一视同仁走现有流程
2. **原始请求列表保留不动**，最终输出用它。过程中不关心谁是因子谁是计算指标
3. **不改** SegmentSqlBuilder、OuterSqlBuilder、IndicatorMeta、SqlBuildContext
4. **不在 SQL 外包装一层 SELECT**，不在 Java 层算
5. **公式在 SQL 算**：在现有 SQL 的 SELECT 里直接注入公式列

## 4. 整体流程

以请求 `indicatorIds=[100(净管理资产), 374(管理资产总额), 120(贷款金额)]`，维度 `[10(日期)]` 为例。

公式：`100 = {374} - {158}`，其中 158(贷款余额) 用户未请求，需作为计算因子引入。

### Step 1：预处理 → 解析公式 + 展开子指标 ID

```
① 保留原始列表: [100, 374, 120]（最终输出用）

② 查 olap_basic_pro_indicator:
   100 → calculatedProduction 有值 → 计算指标
   374、120 → 无值 → 普通指标

③ 解析 100 的公式: formula="{374}-{158}", 子指标=[374, 158]

④ 展开:
   原始 [100, 374, 120] - 计算指标 100 + 子指标 158 = [374, 120, 158]

⑤ request.indicatorIds 临时替换为 [374, 120, 158]
   记录 calcMetaMap: {100 → formula="{374}-{158}"}
```

### Step 2：现有流程原封不动 → 生成 SQL

```
以 [374, 120, 158] 跑 selectDataSource → buildRequestIndicators → 拼 SQL
三个指标都有 field_mapping，现有流程无感知

fieldKeyMap: {374→"amount_key", 120→"other_key", 158→"loan_key"}

单源 SQL（SegmentSqlBuilder 产出）:
  SELECT f.date_key,
         sum(f.amount_key) as amount_key,
         sum(f.other_key)  as other_key,
         sum(f.loan_key)   as loan_key
  FROM (inner) f GROUP BY f.date_key

多源 SQL（OuterSqlWrapper 产出）:
  SELECT unionTable.date_key,
         sum(unionTable.amount_key) as amount_key,
         sum(unionTable.other_key)  as other_key,
         sum(unionTable.loan_key)   as loan_key
  FROM (UNION ALL) unionTable GROUP BY unionTable.date_key
```

### Step 3：注入公式列 + 执行

```
① 恢复 request.indicatorIds = [100, 374, 120]

② 公式替换（注意不能直接用列别名，SQL 同一 SELECT 子句中无法引用别名）:
   单源  → {374}→sum(f.amount_key), {158}→sum(f.loan_key)
   多源  → {374}→sum(unionTable.amount_key), {158}→sum(unionTable.loan_key)

③ 在 SELECT 末尾注入公式列:

   单源 SQL:
   SELECT f.date_key,
          sum(f.amount_key) as amount_key,
          sum(f.other_key)  as other_key,
          sum(f.loan_key)   as loan_key,
          sum(f.amount_key) - sum(f.loan_key) as calc_100_key  ← 注入
   FROM (inner) f GROUP BY f.date_key

   多源 SQL:
   SELECT unionTable.date_key,
          sum(unionTable.amount_key) as amount_key,
          sum(unionTable.other_key)  as other_key,
          sum(unionTable.loan_key)   as loan_key,
          sum(unionTable.amount_key) - sum(unionTable.loan_key) as calc_100_key  ← 注入
   FROM (UNION ALL) unionTable GROUP BY unionTable.date_key

   loan_key 在 SQL 中存在（子指标聚合列），ResultSet 中也有
   但原始请求列表中不含 158，按列表取值时自然过滤掉

④ {100→"calc_100_key"} 加入 fieldKeyMap，供 buildColumnMetas 和 ORDER BY 使用

⑤ 执行注入后的 SQL，后续 appendOrderBy/appendPaging 正常处理

⑥ buildColumnMetas 按原始列表 [100, 374, 120]:
   374、120 → 普通指标，现有逻辑
   100 → 计算指标，从 olap_basic_pro_indicator 取 unit/decimalPlaces
```

### 最终效果

用户看到：

```
date_key   | amount_key | other_key | calc_100_key
-----------|------------|-----------|-------------
2024-01-01 | 2100       | 500       | 1450
2024-01-02 | 1100       | 300       | 600
```

loan_key 不出现在结果中（用户没请求 158）。

若用户同时也请求了 158，则 loan_key 正常出现在结果中，同时被公式复用（一列两用）。

## 5. 改造范围

| 文件 | 改动说明 |
|---|---|
| **新增** `dto/CalculatedIndicatorMeta.java` | 存储 `formula`、`displayFormula`、`indicatorList`，以及运行时 `{id} → fieldKey` 映射 |
| `service/impl/SqlGenerationServiceImpl.java` | ① 预处理：解析公式 + 展开子指标 + 临时替换 request.indicatorIds ② 注入公式列到 SQL ③ buildColumnMetas 支持计算指标 |

**不改的文件**：

TableSelectionServiceImpl、SegmentSqlBuilder、OuterSqlWrapper、BaseSqlBuilder、UnionSqlBuilder、IndicatorMeta、SqlBuildContext

## 6. 边界与限制（v1）

- 运算符：`+`、`-`、`*`、`/` 和括号 `()`
- 不支持嵌套公式（子指标必须是物理指标）
- 子指标数量：2-5 个
- 除零：依赖数据库行为（返回 NULL）
- countDistinct 作为子指标时，多源外层 sum 的语义问题继承现有限制（参见 LOGIC-ARCHITECTURE.md §13）

---

## 附录A：历史方案对比（已废弃）

以下三个方案是设计过程中讨论过的替代方案，最终选用方案四。

> 下文以公式 `{374}-{158}`（管理资产总额 - 贷款余额 = 净管理资产，ID=100）为例，子指标聚合均为 `sum`。
>
> **原始数据**：
>
> 华北库 orders_1:
> | pt_date | amount | loan |
> |---|---|---|
> | 2024-01-01 | 1000 | 300 |
> | 2024-01-01 | 500  | 200 |
> | 2024-01-02 | 800  | 400 |
>
> 华南库 orders_2:
> | pt_date | amount | loan |
> |---|---|---|
> | 2024-01-01 | 600  | 150 |
> | 2024-01-02 | 300  | 100 |
>
> **期望结果**：
> | pt_date | 净管理资产 |
> |---|---|
> | 2024-01-01 | (1000+500+600) - (300+200+150) = 1450 |
> | 2024-01-02 | (800+300) - (400+100) = 600 |

### A.1 方案一：双路径（Segment 感知展开）

SegmentSqlBuilder 知道自己处于单源还是多源，单源直接展开公式，多源输出子指标中间列交 OuterSqlWrapper 处理。

**单源数据流**（假设华北华南合并为一张大表 orders_all）：

```
BaseSqlBuilder 内层投影:
 amount_key | loan_key | date_key
----------- | -------- | ----------
 1000       | 300      | 2024-01-01
 500        | 200      | 2024-01-01
 800        | 400      | 2024-01-02
 600        | 150      | 2024-01-01
 300        | 100      | 2024-01-02
```

SegmentSqlBuilder 展开公式，一步输出：
```sql
SELECT f.date_key as date_key,
       sum(f.amount_key) - sum(f.loan_key) as calc_100_key
FROM (内层) f
GROUP BY f.date_key
```

```
执行结果（无外层包装，直接返回）:
 date_key    | calc_100_key
-------------|-------------
 2024-01-01  | 2100 - 650 = 1450
 2024-01-02  | 1100 - 500 = 600
```

**多源数据流**（华北 orders_1、华南 orders_2 独立数据源）：

```
Segment1 (orders_1):
SELECT f.date_key,
       sum(f.amount_key) as sub_374_key,  -- internal
       sum(f.loan_key)  as sub_158_key   -- internal
FROM (inner1) f GROUP BY f.date_key

执行结果:
 date_key    | sub_374_key | sub_158_key
-------------|-------------|------------
 2024-01-01  | 1500        | 500
 2024-01-02  | 800         | 400

Segment2 (orders_2):
SELECT f.date_key,
       sum(f.amount_key) as sub_374_key,  -- internal
       sum(f.loan_key)  as sub_158_key   -- internal
FROM (inner2) f GROUP BY f.date_key

执行结果:
 date_key    | sub_374_key | sub_158_key
-------------|-------------|------------
 2024-01-01  | 600         | 150
 2024-01-02  | 300         | 100

UNION ALL 后:
 date_key    | sub_374_key | sub_158_key
-------------|-------------|------------
 2024-01-01  | 1500        | 500
 2024-01-02  | 800         | 400
 2024-01-01  | 600         | 150
 2024-01-02  | 300         | 100

OuterSqlWrapper 套公式 → sum 再聚合:
SELECT unionTable.date_key,
       sum(unionTable.sub_374_key) - sum(unionTable.sub_158_key) as calc_100_key
FROM (UNION ALL) unionTable GROUP BY unionTable.date_key

执行结果:
 date_key    | calc_100_key
-------------|-------------
 2024-01-01  | (1500+600) - (500+150) = 1450
 2024-01-02  | (800+300) - (400+100) = 600
```

**优点**：单源 SQL 最优，无冗余嵌套
**缺点**：两套代码路径，SegmentSqlBuilder 需感知 `multiSource`

---

### A.2 方案二：统一中间列 + 独立公式层

SegmentSqlBuilder **不感知**单源/多源，始终输出子指标中间列。公式统一在新增的 FormulaWrapper 层计算。

**单源数据流**：

```
Segment（行为与多源完全一致）:
SELECT f.date_key,
       sum(f.amount_key) as sub_374_key,  -- internal
       sum(f.loan_key)  as sub_158_key   -- internal
FROM (inner) f GROUP BY f.date_key

执行结果:
 date_key    | sub_374_key | sub_158_key
-------------|-------------|------------
 2024-01-01  | 2100        | 650
 2024-01-02  | 1100        | 500

FormulaWrapper 包一层（单源：不需要 sum + GROUP BY，子指标已是最终值）:
SELECT wrapper.date_key,
       wrapper.sub_374_key - wrapper.sub_158_key as calc_100_key
FROM (segment) wrapper

执行结果:
 date_key    | calc_100_key
-------------|-------------
 2024-01-01  | 2100 - 650 = 1450
 2024-01-02  | 1100 - 500 = 600
```

**多源数据流**：

```
Segment1、Segment2、UNION ALL 输出同方案一:
 date_key    | sub_374_key | sub_158_key
-------------|-------------|------------
 2024-01-01  | 1500        | 500
 2024-01-02  | 800         | 400
 2024-01-01  | 600         | 150
 2024-01-02  | 300         | 100

FormulaWrapper 包一层（多源：需要 sum + GROUP BY）:
SELECT wrapper.date_key,
       sum(wrapper.sub_374_key) - sum(wrapper.sub_158_key) as calc_100_key
FROM (UNION ALL) wrapper GROUP BY wrapper.date_key

执行结果:
 date_key    | calc_100_key
-------------|-------------
 2024-01-01  | (1500+600) - (500+150) = 1450
 2024-01-02  | (800+300) - (400+100) = 600
```

**优点**：SegmentSqlBuilder 逻辑统一（只加 internal 标记），公式逻辑集中在 FormulaWrapper
**缺点**：单源多一层嵌套 SELECT（开销极小，优化器通常内联）

---

### A.3 方案三：延迟绑定（单源伪装成多源）

不修改 SegmentSqlBuilder，不新增组件。单源也走 OuterSqlWrapper，利用现有 `sum()` 再聚合语义。

**单源数据流**：

```
Segment（子指标正常输出）:
SELECT f.date_key,
       sum(f.amount_key) as sub_374_key,
       sum(f.loan_key)  as sub_158_key
FROM (inner) f GROUP BY f.date_key

执行结果:
 date_key    | sub_374_key | sub_158_key
-------------|-------------|------------
 2024-01-01  | 2100        | 650
 2024-01-02  | 1100        | 500

跳过 UnionSqlBuilder → 直送 OuterSqlWrapper:
SELECT wrapper.date_key,
       sum(wrapper.sub_374_key) - sum(wrapper.sub_158_key) as calc_100_key
FROM (segment) wrapper GROUP BY wrapper.date_key

执行过程（数据库视角）:
  wrapper 子查询产出:
    date_key    | sub_374_key | sub_158_key
    ------------|-------------|------------
    2024-01-01  | 2100        | 650        ← 每个key只有一行
    2024-01-02  | 1100        | 500        ← 每个key只有一行

  外层 sum + GROUP BY:
    因为每个 date_key 只有一行，sum(一行) = 该行本身

执行结果（结果正确，但多了一次无意义的 sum+GROUP BY）:
 date_key    | calc_100_key
-------------|-------------
 2024-01-01  | 2100 - 650 = 1450
 2024-01-02  | 1100 - 500 = 600
```

**多源数据流**：同方案一（本来就是 OuterSqlWrapper 的职责）。

**优点**：SegmentSqlBuilder 零改动
**缺点**：单源多一层 `sum()+GROUP BY`，SQL 最冗余；OuterSqlWrapper 职责膨胀

---

### A.4 方案对比总结

```
┌─────────────────────┬────────────────────────┬──────────────────────────┬─────────────────────────┬──────────────────────────┐
│       维度           │    方案一（双路径）     │  方案二（统一中间列）     │  方案三（延迟绑定）       │  方案四（SQL注入公式列）   │
├─────────────────────┼────────────────────────┼──────────────────────────┼─────────────────────────┼──────────────────────────┤
│ SegmentSqlBuilder改动│ 中等（+公式展开分支）   │ 小（+internal标记）       │ 零                      │ 零                       │
├─────────────────────┼────────────────────────┼──────────────────────────┼─────────────────────────┼──────────────────────────┤
│ 公式逻辑位置          │ 两处（Segment+Wrapper）│ 一处（FormulaWrapper）    │ 一处（OuterSqlWrapper）  │ 一处（SqlGenServiceImpl） │
├─────────────────────┼────────────────────────┼──────────────────────────┼─────────────────────────┼──────────────────────────┤
│ 新增组件              │ 1（FormulaEvaluator）  │ 2（+FormulaWrapper）      │ 0                       │ 0                        │
├─────────────────────┼────────────────────────┼──────────────────────────┼─────────────────────────┼──────────────────────────┤
│ SQL 层数变化          │ 无（最优）              │ +1层 SELECT              │ +1层 SELECT+sum+GROUP BY│ 无                       │
├─────────────────────┼────────────────────────┼──────────────────────────┼─────────────────────────┼──────────────────────────┤
│ 方案四核心优势        │                        │                          │                         │ 不改Builder，不增SQL层，   │
│                     │                        │                          │                         │ 不改现有DTO，集中到一个文件 │
└─────────────────────┴────────────────────────┴──────────────────────────┴─────────────────────────┴──────────────────────────┘
```
