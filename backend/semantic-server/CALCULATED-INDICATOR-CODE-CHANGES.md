# 计算指标代码修改文档

## 改动总览

```
新增:
  dto/CalculatedIndicatorMeta.java
  dto/SubIndicatorMeta.java

修改:
  dto/IndicatorMeta.java                       +2字段
  service/impl/SqlGenerationServiceImpl.java   预处理 + buildRequestIndicators + buildColumnMetas
  support/sql/builder/SegmentSqlBuilder.java   appendIndicatorSelect 加 calculated 分支
  support/sql/builder/OuterSqlWrapper.java     wrapFinalSql 加 calculated 分支
```

---

## 1. 新增 dto/SubIndicatorMeta.java

```java
package com.wm.semantic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubIndicatorMeta {
    private Long id;
    private String letter;
    private String name;
}
```

## 2. 新增 dto/CalculatedIndicatorMeta.java

```java
package com.wm.semantic.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class CalculatedIndicatorMeta {
    private Long indicatorId;
    private String formula;           // "{374}-{158}"
    private String displayFormula;    // "${A}-${B}"
    private List<SubIndicatorMeta> subIndicators;

    /** 运行时填充：子指标ID → fieldKey（数据源选择后赋值） */
    private Map<Long, String> idToFieldKey;
}
```

## 3. 修改 dto/IndicatorMeta.java

```java
// 新增两个字段:
private boolean calculated;
private CalculatedIndicatorMeta formulaMeta;
```

## 4. 修改 SqlGenerationServiceImpl

### 4.1 generateAndExecute 方法

在 `selectDataSource` 调用之前插入预处理：

```java
// === 新增：预处理计算指标 ===
List<Long> originalIndicatorIds = new ArrayList<>(request.getIndicatorIds());
Map<Long, CalculatedIndicatorMeta> calcMetaMap = preprocessCalculatedIndicators(request);

if (!calcMetaMap.isEmpty()) {
    // 展开ID列表：去掉计算指标，加入子指标
    Set<Long> expandedSet = new LinkedHashSet<>();
    for (Long id : request.getIndicatorIds()) {
        if (!calcMetaMap.containsKey(id)) {
            expandedSet.add(id);
        }
    }
    for (CalculatedIndicatorMeta calcMeta : calcMetaMap.values()) {
        for (SubIndicatorMeta sub : calcMeta.getSubIndicators()) {
            expandedSet.add(sub.getId());
        }
    }
    request.setIndicatorIds(new ArrayList<>(expandedSet));
}
// === 预处理结束 ===

// 继续现有流程：selectDataSource ...
```

在 `buildContexts` 之后、`generateFinalSql` 之前，填充 idToFieldKey：

```java
// === 新增：填充计算指标的子指标ID→fieldKey映射 ===
if (!calcMetaMap.isEmpty()) {
    Map<Long, String> firstFieldKeyMap = contexts.get(0).getFieldKeyMap();
    for (CalculatedIndicatorMeta calcMeta : calcMetaMap.values()) {
        Map<Long, String> idToFieldKey = new LinkedHashMap<>();
        for (SubIndicatorMeta sub : calcMeta.getSubIndicators()) {
            idToFieldKey.put(sub.getId(), firstFieldKeyMap.get(sub.getId()));
        }
        calcMeta.setIdToFieldKey(idToFieldKey);
    }
}
```

执行完成后，恢复原始请求用于输出：

```java
// 恢复原始请求
if (!calcMetaMap.isEmpty()) {
    request.setIndicatorIds(originalIndicatorIds);
}
```

### 4.2 preprocessCalculatedIndicators 新方法

```java
private Map<Long, CalculatedIndicatorMeta> preprocessCalculatedIndicators(QueryDataRequest request) {
    Map<Long, CalculatedIndicatorMeta> result = new LinkedHashMap<>();
    List<Long> ids = request.getIndicatorIds();
    if (CollectionUtils.isEmpty(ids)) return result;

    // 批量查询 indicator 表
    List<OlapBasicProIndicatorDO> indicators = indicatorMapper.selectList(
        new LambdaQueryWrapper<OlapBasicProIndicatorDO>()
            .in(OlapBasicProIndicatorDO::getOlapBasicProId, ids));
    
    for (OlapBasicProIndicatorDO ind : indicators) {
        if (isBlank(ind.getCalculatedProduction())) continue;
        // 解析JSON
        CalculatedIndicatorMeta meta = parseCalculatedProduction(ind.getCalculatedProduction());
        meta.setIndicatorId(ind.getOlapBasicProId());
        result.put(meta.getIndicatorId(), meta);
    }
    return result;
}
```

### 4.3 parseCalculatedProduction 新方法

```java
private CalculatedIndicatorMeta parseCalculatedProduction(String json) {
    // JSON: {"displayFormula":"${A}-${B}","formula":"{374}-{158}",
    //         "indicatorList":[{"id":374,"letter":"A","name":"..."}, ...]}
    // 使用 Jackson ObjectMapper 或 fastjson 解析
    // ...
}
```

### 4.4 buildRequestIndicators 方法

现有逻辑不变，末尾追加计算指标：

```java
// === 新增：追加计算指标到结果列表 ===
if (calcMetaMap != null && !calcMetaMap.isEmpty()) {
    for (CalculatedIndicatorMeta calcMeta : calcMetaMap.values()) {
        IndicatorMeta meta = new IndicatorMeta();
        meta.setId(calcMeta.getIndicatorId());
        meta.setCalculated(true);
        meta.setFormulaMeta(calcMeta);
        meta.setAlias("calc_" + calcMeta.getIndicatorId() + "_key");
        meta.setAggFunc("sum"); // 占位，不会被用到
        result.add(meta);
    }
}
```

注意：`calcMetaMap` 需要从 `generateAndExecute` 方法作用域传递到 `buildRequestIndicators`。可将其设为方法参数或类成员变量。

### 4.5 buildColumnMetas 方法

计算指标的 unit/decimalPlaces 从 `olap_basic_pro_indicator` 表取（现有代码已在批量加载 indicatorMap 时查询）。对 calculated 指标，直接使用 indicatorMap 中的值。

---

## 5. 修改 SegmentSqlBuilder.appendIndicatorSelect

在现有循环内增加分支：

```java
for (IndicatorMeta meta : ctx.getRequestIndicators()) {
    // === 新增：计算指标分支 ===
    if (meta.isCalculated()) {
        CalculatedIndicatorMeta calcMeta = meta.getFormulaMeta();
        String alias = ctx.getFieldKeyMap().get(meta.getId());
        // 判断所有子指标是否都被当前数据源拥有
        boolean allOwned = true;
        for (SubIndicatorMeta sub : calcMeta.getSubIndicators()) {
            if (!ctx.getOwnIndicatorIds().contains(sub.getId())) {
                allOwned = false;
                break;
            }
        }
        if (allOwned) {
            String expr = buildCalculatedExpr(calcMeta, aggFuncByFieldId, dialect);
            selectFields.add(expr + " as " + dialect.quote(alias));
        } else {
            selectFields.add("0 as " + dialect.quote(alias));
        }
        continue;
    }
    // === 新增结束 ===

    // 现有逻辑不变 ...
}
```

新增辅助方法：

```java
private String buildCalculatedExpr(CalculatedIndicatorMeta calcMeta,
                                    Map<Long, String> aggFuncByFieldId,
                                    SqlDialect dialect) {
    String expr = calcMeta.getFormula(); // "{374}-{158}"
    Map<Long, String> idToFieldKey = calcMeta.getIdToFieldKey();
    for (SubIndicatorMeta sub : calcMeta.getSubIndicators()) {
        String fieldKey = idToFieldKey.get(sub.getId());
        String aggFunc = aggFuncByFieldId.getOrDefault(sub.getId(), "sum");
        String aggExpr = buildAggregateExpr(aggFunc, OUTER_ALIAS + "." + dialect.quote(fieldKey));
        expr = expr.replace("{" + sub.getId() + "}", aggExpr);
    }
    return expr;
}
```

## 6. 修改 OuterSqlWrapper.wrapFinalSql

在现有指标循环内增加分支：

```java
if (indicators != null) {
    for (IndicatorMeta meta : indicators) {
        // === 新增：计算指标分支 ===
        if (meta.isCalculated()) {
            CalculatedIndicatorMeta calcMeta = meta.getFormulaMeta();
            String alias = /* 从 fieldKeyMap 或 meta.alias 获取 */;
            String expr = buildCalculatedOuterExpr(calcMeta, dialect);
            selectFields.add(expr + " as " + dialect.quote(alias));
            continue;
        }
        // === 新增结束 ===

        // 现有逻辑不变
        String outerFunc = pickOuterAggFunc(meta.getAggFunc());
        selectFields.add(outerFunc + "(unionTable." + dialect.quote(meta.getAlias()) + ") as " + dialect.quote(meta.getAlias()));
    }
}
```

新增辅助方法：

```java
private String buildCalculatedOuterExpr(CalculatedIndicatorMeta calcMeta, SqlDialect dialect) {
    String expr = calcMeta.getFormula();
    Map<Long, String> idToFieldKey = calcMeta.getIdToFieldKey();
    for (SubIndicatorMeta sub : calcMeta.getSubIndicators()) {
        String fieldKey = idToFieldKey.get(sub.getId());
        expr = expr.replace("{" + sub.getId() + "}", 
                            "sum(unionTable." + dialect.quote(fieldKey) + ")");
    }
    return expr;
}
```

注意：多源场景下计算指标的别名需要通过某种方式获取。可在方法签名中增加 `Map<Long, String> calcAliasMap` 参数，或从 IndicatorMeta.alias 读取。
