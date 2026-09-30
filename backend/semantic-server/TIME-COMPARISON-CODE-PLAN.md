# 同环比代码实现方案

## 一、请求参数扩展

### 1. QueryDataRequest 新增字段

```java
/** 时间粒度: day/week/month/quarter/year */
private String dateGranularity;

/** 指标对比类型映射: indicatorId → none/pop/yoy/both */
private Map<Long, String> indicatorComparison;
```

前端报文的 `indexList` 里每个指标带 `comparison`，Controller 或网关层转换成 `indicatorComparison` Map，不改 `indicatorIds` 的 List<Long> 结构。

```json
{
  "dimensionIds": [71],
  "indicatorIds": [72, 74],
  "indicatorComparison": {"72": "both", "74": "pop"},
  "dateGranularity": "day",
  "timeRange": {"start": "2026-08-01", "end": "2026-08-03"}
}
```

### 2. 新增 ComparisonType 枚举

```java
public enum ComparisonType {
    NONE("none"),      // 无对比
    POP("pop"),        // 环比（上一相邻周期，跟随粒度）
    YOY("yoy"),        // 同比（去年同期）
    BOTH("both");      // 同比+环比

    public static ComparisonType from(String v) {
        if (v == null || v.isEmpty()) return NONE;
        for (ComparisonType t : values()) {
            if (t.value.equalsIgnoreCase(v)) return t;
        }
        return NONE;
    }
}
```

## 二、新增文件

### 1. TimeRangeShifter.java

```java
@Component
public class TimeRangeShifter {

    /** 把本期 timeRange 换算成对比期 timeRange */
    public TimeRange shift(TimeRange current, ComparisonType type, String granularity) {
        // pop: 往前推一个周期（周期长度 = end - start + 1 天，或按粒度取上月/上季/去年）
        // yoy: 往前推 1 年
        // both 场景调用两次
    }
}
```

偏移规则：

| granularity | pop 偏移 | yoy 偏移 |
|---|---|---|
| day | 往前推 `(end-start+1)` 天 | 往前推 1 年 |
| week | 往前推 7 天 | 往前推 1 年 |
| month | 往前推 1 个月 | 往前推 1 年 |
| quarter | 往前推 3 个月 | 往前推 1 年 |
| year | 往前推 1 年 | 往前推 1 年 |

### 2. TimeComparisonMerger.java

```java
@Component
public class TimeComparisonMerger {

    /**
     * 把本期 rows 和对比期 rows 合并。
     *
     * @param currentRows 本期查询结果
     * @param prevRows    pop 对比期查询结果（可为 null）
     * @param yoyRows     yoy 对比期查询结果（可为 null）
     * @param dateFieldKey ptdate 对应的列 key（有日期维度时非空）
     * @param dimensionKeys 非日期维度列 key 列表
     * @param comparedIndicators 需要对比的指标 key 集合（如 {"num_in"}）
     */
    public List<Map<String, Object>> merge(
            List<Map<String, Object>> currentRows,
            List<Map<String, Object>> prevRows,
            List<Map<String, Object>> yoyRows,
            String dateFieldKey,
            List<String> dimensionKeys,
            Set<String> comparedIndicators,
            long popOffsetDays,
            ComparisonType type) {

        // 1. 建 prevMap / yoyMap
        //    key = dateOffset(对比期日期) + "|" + dim值1 + "|" + dim值2
        //    无日期维度时 key = dim值1 + "|" + dim值2

        // 2. 遍历 currentRows:
        //    - 从 prevMap 取上期值 → row.put(key + "_prev", prevVal)
        //    - 计算 ratio: (cur - prev) / prev * 100 → row.put(key + "_ratio", ratio)
        //    - yoy 同理 → key + "_yoy", key + "_yoy_ratio"

        // 3. 返回合并后的 currentRows
    }
}
```

日期偏移规则：
- 有日期维度：对比期日期 + popOffsetDays → 本期日期（yoy 则 +365/366 天）
- 无日期维度：不偏移，直接维度 key 匹配

## 三、SqlGenerationServiceImpl 主流程

### generateAndExecute 分流

```java
public GetDataSqlResponse generateAndExecute(QueryDataRequest request) {
    validateRequest(request);

    ComparisonType comparison = detectComparison(request);  // 是否有指标设了 comparison

    if (comparison == ComparisonType.NONE) {
        return executeNormal(request);          // 现有流程原封不动
    }
    return executeWithComparison(request, comparison);  // 新流程
}
```

### executeNormal（现有逻辑抽方法）

把现有 generateAndExecute 主体抽成 `executeNormal(request)`，一行不改。

### executeWithComparison

```java
private GetDataSqlResponse executeWithComparison(QueryDataRequest request, ComparisonType type) {
    // 校验: dateGranularity 必传、timeRange 必传，否则报错

    // 1. 时间范围换算
    TimeRange currentRange = request.getTimeRange();
    TimeRange popRange  = (type==POP||type==BOTH) ? shifter.shift(currentRange, POP, granularity) : null;
    TimeRange yoyRange  = (type==YOY||type==BOTH) ? shifter.shift(currentRange, YOY, granularity) : null;

    // 2. 并发执行 2-3 条 SQL
    //    ExecutorService 提交:
    //      task-1: clone(request).timeRange=currentRange → executeNormal → rows
    //      task-2: clone(request).timeRange=popRange    → executeNormal → rows (仅POP/BOTH)
    //      task-3: clone(request).timeRange=yoyRange    → executeNormal → rows (仅YOY/BOTH)
    //    全部 future.get()

    // 3. Merge
    List<Map<String,Object>> mergedRows = merger.merge(currentRows, popRows, yoyRows, ...);

    // 4. buildColumnMetas 展开对比列
    List<ColumnMeta> columns = buildColumnMetas(request, ..., comparisonMap);

    // 5. 组装 response（sql 字段取本期 SQL）
}
```

注意：`clone(request)` 深拷贝，每条 SQL 走完整现有链路（含计算指标预处理、衍生指标、数据源选择、SQL生成、执行）。SQL Builder 零改动。

### buildColumnMetas 展开

```java
// 指标列循环里:
for (Long indId : indicatorIds) {
    ComparisonType ct = comparisonMap.getOrDefault(indId, NONE);

    // 本期列（原有逻辑）
    columns.add(buildColumn(indId, 原名 + "-本期"));

    if (ct == POP || ct == BOTH) {
        columns.add(buildColumn(key + "_prev",  原名 + "-上期"));
        columns.add(buildColumn(key + "_ratio", 原名 + "-环比", unit="%", precision=1));
    }
    if (ct == YOY || ct == BOTH) {
        columns.add(buildColumn(key + "_yoy",       原名 + "-同期"));
        columns.add(buildColumn(key + "_yoy_ratio", 原名 + "-同比", unit="%", precision=1));
    }
}
```

## 四、文件清单

```
新增:
  common/enums/ComparisonType.java
  support/time/TimeRangeShifter.java
  support/time/TimeComparisonMerger.java

修改:
  dto/QueryDataRequest.java              +dateGranularity +indicatorComparison
  service/impl/SqlGenerationServiceImpl.java
    generateAndExecute 分流
    现有主体抽成 executeNormal
    新增 executeWithComparison
    buildColumnMetas 展开对比列

不改:
  TableSelectionServiceImpl
  SegmentSqlBuilder / OuterSqlWrapper / BaseSqlBuilder / UnionSqlBuilder
  SqlBuildContext / IndicatorMeta
  计算指标/衍生指标预处理（对比 SQL 内部照常走）
```

## 五、边界

| 场景 | 处理 |
|---|---|
| comparison 非 none 但无 dateGranularity | 抛 BizException |
| comparison 非 none 但无 timeRange | 抛 BizException |
| 对比期该维度组合无数据 | prev/yoy 列 null |
| 除零 | ratio 列 null |
| 并发异常 | future.get 异常上抛，整体失败 |
| 无日期维度 | 不偏移日期，直接维度 key 匹配，对比期汇总值 |
