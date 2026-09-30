# 同环比功能设计文档

> 状态：方案选定，待实现

## 1. 背景

指标查询支持同环比对比，在一条请求中选择对比类型（环比/同比），对应的指标自动展开为 3 列（本期值、上期值、变化率），无需前端多次请求。

## 2. 方案选型

### 2.1 三套方案对比

**方案 A：LAG 窗口函数**

扩展 WHERE 时间范围到基期，在现有 SQL 外层用 `LAG() OVER (PARTITION BY dims ORDER BY date)` 取上期值，单次查询。

```
优点: 单次DB查询，无Java merge
缺点: 依赖日期维度参与GROUP BY，日期不连续会错位，同比需扫描一年数据，需MySQL 8.0+
```

**方案 B：多 SQL 并发**

时间范围换算后并发执行两条 SQL（本期 + 上期），各自走完整现有链路，Java 层按日期偏移 + HashMap 匹配合并。

```
优点: SQL Builder零改动，精准命中时间范围，日期不连续也不错位，无DB版本限制
缺点: 2次DB查询（并发耗时≈1次），需Java merge逻辑
```

**方案 C：UNION ALL 双时间**

在同一条 SQL 里 UNION ALL 两段时间的数据，外层用 CASE WHEN 分组聚合。

```
优点: 单次SQL查询，兼容性好
缺点: SQL Builder大改，与多源场景叠加复杂度爆炸
```

### 2.2 选型结论

```
┌──────────────┬─────────────────────┬──────────────────────┬─────────────────────────┐
│              │  方案 A (LAG)       │  方案 B (多SQL并发)   │  方案 C (UNION ALL双时间) │
├──────────────┼─────────────────────┼──────────────────────┼─────────────────────────┤
│ SQL Builder  │ 需改（加LAG包装层） │ 零改动               │ 大改                    │
│ DB 查询次数  │ 1 次                │ 2 次（并发）         │ 1 次                    │
│ 数据扫描量   │ 扩展覆盖基期        │ 精准命中             │ 精准命中                │
│ 日期不连续   │ 会错位              │ 精确匹配             │ 精确匹配                │
│ 与计算/衍生  │ 需额外处理          │ 天然兼容             │ 需额外处理              │
│ DB 版本要求  │ MySQL 8.0+          │ 无限制               │ 无限制                  │
│ 推荐度       │ ⭐⭐               │ ⭐⭐⭐                │ ⭐                      │
└──────────────┴─────────────────────┴──────────────────────┴─────────────────────────┘
```

选定**方案 B（多 SQL 并发）**。短偏移场景（周/月环比）后期可切方案 A 优化。

## 3. 请求参数设计

在 `indexList` 每个指标上新增 `comparison` 字段，和 Power BI / FineBI 的指标级别对比一致：

```json
{
  "dimList": [
    {"id": 71, "dimKey": "hebing_name", "dimName": "合并科室"}
  ],
  "indexList": [
    {"id": 72, "indKey": "num_in", "indName": "出院人次", "comparison": "both"},
    {"id": 74, "indKey": "material_all_out", "indName": "卫生材料支出", "comparison": "pop"},
    {"id": 76, "indKey": "", "indName": "医院总人次"}
  ],
  "timeRange": {"start": "2026-08-01", "end": "2026-08-03"},
  "dateGranularity": "day",
  "paging": {"page": 1, "pageSize": 10}
}
```

### 3.1 comparison 枚举

| 值 | 含义 |
|---|---|
| `none`（默认） | 无对比，正常 1 列 |
| `yoy` | 同比（Year-On-Year，去年同期） |
| `pop` | 环比（Period-Over-Period，上一相邻周期，跟随粒度） |
| `both` | 同比+环比都输出 |

### 3.2 偏移规则

| 粒度 | pop 对比目标 | yoy 对比目标 |
|---|---|---|
| day | 昨天 | 去年同日 |
| week | 上周 | 去年同周 |
| month | 上个月 | 去年同月 |
| quarter | 上季度 | 去年同季 |
| year | 去年 | 前年 |

- `comparison` 不传或 `none` → 现有流程零改动
- 计算指标 76（公式 `{72}+{73}`）不受 72 的对比影响，照常输出 1 列

## 4. 执行流程

以 `timeRange=[2026-08-01, 2026-08-03]`、`comparison=pop`、日粒度为例。

**Step 1：时间范围换算**

```
本期: [2026-08-01, 2026-08-03]
上期: [2026-07-29, 2026-07-31]  ← 往前推 3 天（周期天数）
```

| 类型 | 偏移规则 |
|---|---|
| `pop` | 往前推 `(end - start + 1)` 天（一个周期） |
| `yoy` | 往前推 1 年（同期月日） |
| `both` | 两条上期 SQL 并发（pop 上期 + yoy 上期），加本期共 3 条 |

**Step 2：并发执行 SQL**

```
SQL-1（本期）:
  request.indicatorIds = [72, 74, 73]  ← 展开后的ID
  request.timeRange = [2026-08-01, 2026-08-03]
  → 走完整现有链路（选数据源 → gen SQL → 执行）
  → 计算指标、衍生指标正常处理

SQL-2（pop 上期）:
  request.timeRange = [2026-07-29, 2026-07-31]
  → 走完整现有链路

SQL-3（yoy 同期）: 仅 comparison 含 yoy 时执行
  request.timeRange = [2025-08-01, 2025-08-03]
  → 走完整现有链路
```

各条 SQL 完全独立，各自走全链路。SQL Builder 零改动。

**Step 3：Java Merge**

```
① 上期 rows 做日期偏移:
   pop 偏移量 = 本期起点 - pop 上期起点 = 3天
   yoy 偏移量 = 1年
   每行 ptdate + 偏移 → 映射到本期日期

② 上期 rows 建 HashMap:
   key   = "映射日期|维度键1|维度键2|..."
   value = 上期行的指标值 Map

③ 遍历本期 rows:
   同 key 从 HashMap 取上期指标值
   匹配到 → 追加 _prev 列，计算 _ratio 列（pop）
            追加 _yoy 列，计算 _yoy_ratio 列（yoy）
   匹配不到 → 对应列为 null
```

Merge 示例（comparison=both）：

```
本期 row:    {ptdate:0801, hebing_name:内科, num_in:100}
pop上期 row: {ptdate:0729, hebing_name:内科, num_in:90}
yoy同期 row: {ptdate:2025-08-01, hebing_name:内科, num_in:85}
             ↓ ptdate+3 → 0801 / ptdate+1年 → 0801
prevMap:     {"0801|内科" → {num_in:90}}
yoyMap:      {"0801|内科" → {num_in:85}}
             ↓ match
最终 row:    {ptdate:0801, hebing_name:内科, num_in:100,
              num_in_prev:90, num_in_ratio:11.1,
              num_in_yoy:85, num_in_yoy_ratio:17.6}
```

## 5. 返回结果

### 5.1 有日期维度（日粒度）

请求 `timeRange=[0801, 0803]`, `dateGranularity=day`, `dimList=[hebing_name]`：

**comparison=pop**（环比）：

```json
{
  "columns": [
    {"key": "ptdate",      "name": "日期",   "type": "dimension"},
    {"key": "hebing_name", "name": "合并科室", "type": "dimension"},
    {"key": "num_in",      "name": "出院人次-本期", "type": "indicator", "unit": "人次"},
    {"key": "num_in_prev", "name": "出院人次-上期", "type": "indicator", "unit": "人次"},
    {"key": "num_in_ratio","name": "出院人次-环比", "type": "indicator", "unit": "%", "precision": 1}
  ],
  "records": [
    {"ptdate": "2026-08-01", "hebing_name": "内科", "num_in": 100, "num_in_prev": 90,  "num_in_ratio": 11.1},
    {"ptdate": "2026-08-02", "hebing_name": "内科", "num_in": 120, "num_in_prev": 110, "num_in_ratio": 9.1},
    {"ptdate": "2026-08-03", "hebing_name": "内科", "num_in": 95,  "num_in_prev": 100, "num_in_ratio": -5.0}
  ]
}
```

**comparison=both**（同比+环比）：

```json
{
  "columns": [
    {"key": "ptdate",      "name": "日期",   "type": "dimension"},
    {"key": "hebing_name", "name": "合并科室", "type": "dimension"},
    {"key": "num_in",        "name": "出院人次-本期", "type": "indicator", "unit": "人次"},
    {"key": "num_in_prev",   "name": "出院人次-上期", "type": "indicator", "unit": "人次"},
    {"key": "num_in_ratio",  "name": "出院人次-环比", "type": "indicator", "unit": "%", "precision": 1},
    {"key": "num_in_yoy",    "name": "出院人次-同期", "type": "indicator", "unit": "人次"},
    {"key": "num_in_yoy_ratio", "name": "出院人次-同比", "type": "indicator", "unit": "%", "precision": 1}
  ],
  "records": [
    {"ptdate": "2026-08-01", "hebing_name": "内科",
     "num_in": 100, "num_in_prev": 90, "num_in_ratio": 11.1,
     "num_in_yoy": 85, "num_in_yoy_ratio": 17.6}
  ]
}
```

### 5.2 无日期维度

只按科室汇总，整个周期对比：

```json
{
  "records": [
    {"hebing_name": "内科", "num_in": 315, "num_in_prev": 300, "num_in_ratio": 5.0},
    {"hebing_name": "外科", "num_in": 600, "num_in_prev": 550, "num_in_ratio": 9.1}
  ]
}
```

Merge 时 HashMap key 只包含非日期维度，行数由本期决定。

## 6. 列名设计

### 6.1 展开规则

设了 `comparison` 的指标，在 `buildColumnMetas` 阶段展开：

**pop**：1 列 → 3 列

| key 格式 | name 格式 | type | unit | precision |
|---|---|---|---|---|
| `{原始key}` | `{原始name}-本期` | indicator | 原始 unit | 原始 precision |
| `{原始key}_prev` | `{原始name}-上期` | indicator | 原始 unit | 原始 precision |
| `{原始key}_ratio` | `{原始name}-环比` | indicator | `%` | 1 |

**yoy**：1 列 → 3 列

| key 格式 | name 格式 | type | unit | precision |
|---|---|---|---|---|
| `{原始key}` | `{原始name}-本期` | indicator | 原始 unit | 原始 precision |
| `{原始key}_yoy` | `{原始name}-同期` | indicator | 原始 unit | 原始 precision |
| `{原始key}_yoy_ratio` | `{原始name}-同比` | indicator | `%` | 1 |

**both**：1 列 → 5 列（pop 3 列 + yoy 后 2 列）

### 6.2 对比类型后缀

| comparison | 对比值后缀 (key) | 对比值后缀 (name) | 比率后缀 (key) | 比率后缀 (name) |
|---|---|---|---|---|
| `pop` | `_prev` | `-上期` | `_ratio` | `-环比` |
| `yoy` | `_yoy` | `-同期` | `_yoy_ratio` | `-同比` |
| `both` | 两组都输出 | 两组都输出 | 两组都输出 | 两组都输出 |

### 6.3 环比计算公式

```java
BigDecimal current = row.get("num_in");
BigDecimal prev = row.get("num_in_prev");
if (prev != null && prev.compareTo(BigDecimal.ZERO) != 0) {
    BigDecimal ratio = current.subtract(prev)
        .multiply(BigDecimal.valueOf(100))
        .divide(prev, 1, RoundingMode.HALF_UP);
    row.put("num_in_ratio", ratio);
} else {
    row.put("num_in_ratio", null);
}
```

## 7. 改造范围

```
新增文件:
  support/time/TimeRangeShifter.java     时间范围换算（pop 上期 / yoy 同期）
  support/time/TimeComparisonMerger.java 日期映射 + HashMap match + 环比/同比计算

修改文件:
  dto/QueryDataRequest.java             +dateGranularity 字段；indicator 元素 +comparison 字段
  service/impl/SqlGenerationServiceImpl.java
    generateAndExecute: 检测 comparison，分流 无→现有 / 有→并发(2-3条SQL)+merge
    buildColumnMetas:   comparison 指标按 pop/yoy/both 展开列 meta

不改文件:
  TableSelectionServiceImpl
  SegmentSqlBuilder / OuterSqlWrapper / BaseSqlBuilder / UnionSqlBuilder
  SqlBuildContext / IndicatorMeta
```

## 8. 边界与限制

| 场景 | 处理 |
|---|---|
| 上期该维度组合无数据 | `_prev=null`, `_ratio=null`（yoy 同理） |
| 上期有、本期无 | 忽略该上期行，不输出 |
| 除零（上期为0） | `_ratio=null`（yoy 同理） |
| 无 dateGranularity + comparison 非 none | 报错提示 |
| 无 timeRange + comparison 非 none | 报错提示 |
| 与计算指标叠加 | 对比的本期 SQL 包含计算指标列，不参与展开 |
| 与衍生指标叠加 | 同上，衍生指标不参与展开 |
| 与排序/分页 | SQL 层原有排序分页；merge 后不再重排序 |
| 与 groupTopN | v1 暂不共存 |
