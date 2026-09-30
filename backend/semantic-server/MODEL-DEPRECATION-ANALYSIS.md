# 模型废弃可行性分析

> 讨论时间：2026-08-09
> 状态：讨论记录，待决策

## 1. 背景

当前数据源有两种类型：

- **Type 1（单表 / 视图）**：`olap_table_pro`，`tb_type_key=0` 为物理表、`tb_type_key=1` 为视图。视图通过 `view_sql` 字段存储 SQL 文本，查询时内联为子查询。
- **Type 2（模型）**：`olap_data_model` + `olap_data_model_dimension`，事实表 JOIN 多张维度表，系统自动按需拼接 JOIN。

视图能力逐步完善后（已支持 `view_sql`、`${start_time}` / `${end_time}` 时间变量替换），提出一个问题：**模型是否可以废弃，全部用视图替代？**

## 2. 能力对比

```
┌───────────┬───────────────────────────────────────┬──────────────────────────────────────────────┐
│   维度    │            模型（Type 2）             │            视图（tb_type_key=1）             │
├───────────┼───────────────────────────────────────┼──────────────────────────────────────────────┤
│ 配置方式  │ 声明式：指定事实表+维度表+JOIN键      │ 命令式：手写完整 SQL                         │
├───────────┼───────────────────────────────────────┼──────────────────────────────────────────────┤
│ JOIN 行为 │ 按需 JOIN——只 JOIN 实际用到的维度表   │ 全量 JOIN——view_sql 写死的所有 JOIN 都会执行 │
├───────────┼───────────────────────────────────────┼──────────────────────────────────────────────┤
│ 字段发现  │ 自动——维度表的字段自动纳入候选        │ 手动——需要在 field_mapping 逐个注册          │
├───────────┼───────────────────────────────────────┼──────────────────────────────────────────────┤
│ 新增维度  │ 加一行 olap_data_model_dimension 记录 │ 改 view_sql + 逐个注册新字段映射             │
├───────────┼───────────────────────────────────────┼──────────────────────────────────────────────┤
│ 灵活性    │ 受限于事实表-维度表的星型模式         │ 无限制：子查询、CTE、窗口函数、跨库都可以    │
└───────────┴───────────────────────────────────────┴──────────────────────────────────────────────┘
```

**结论**：视图在能力集上已覆盖模型。唯一的差异是模型的"按需 JOIN"在视图中变成了"全量 JOIN"。

## 3. 废弃模型的收益

### 3.1 删除的代码

| 文件 | 可删除/简化的部分 |
|---|---|
| `TableSelectionServiceImpl.fillModelSource()` | ~130 行，字段来源解析 + JOIN 关系裁剪 |
| `BaseSqlBuilder.buildModelInner()` | ~50 行，动态 FROM + JOIN 拼接 |
| `OlapDataModelDO` / `OlapDataModelDimensionDO` | 2 个实体类 |
| `OlapDataModelMapper` / `OlapDataModelDimensionMapper` | 2 个 Mapper + XML SQL |
| 数据源选择中的 model 分支 | `findAllCandidates`、`batchFindIndicators` 等 |
| `DataSourceInfo` 中 model 专用字段 | `modelId`、`factTableId`、`joinRelations` 等 |

总计可删除约 **180 行业务逻辑 + 2 张配置表 + 2 个实体 + 2 个 Mapper**。

### 3.2 简化的逻辑

- 数据源选择不需要区分 table 和 model 两个分支
- SQL 生成不需要两套 FROM 子句构建逻辑
- 贪心选择不需要 `objType='table'/'model'` 的优先级判断
- `olap_data_model` 和 `olap_data_model_dimension` 两张表可以清理
- 文档中的 `olap_fact_dim_mapping_pro`（实际并未使用）不再需要

### 3.3 统一的架构

```
当前双路径：

  TableSelectionServiceImpl
  ├── table 候选
  │     └── fillTableSource (20行)
  │           └── 直接取 dbName/tableName 或 view_sql
  │
  └── model 候选
        └── fillModelSource (130行)
              ├── 加载 olap_data_model + olap_data_model_dimension
              ├── 解析维度表 JOIN 关系
              ├── 字段优先级匹配（事实表优先 → 维度表fallback）
              ├── 指标字段强制归属事实表
              └── 裁剪未使用的 JOIN（setJoinRelations）

  BaseSqlBuilder
  ├── buildTableInner → FROM db.table / FROM (view_sql) mainsrc
  └── buildModelInner → FROM fact t0 LEFT JOIN dim1 t1 ON ... LEFT JOIN dim2 t2 ON ...

──────────────────────────────────────────────────────────

废弃后统一路径：

  TableSelectionServiceImpl
  └── 统一候选
        └── fillTableSource (20行)
              └── 直接取 dbName/tableName 或 view_sql

  BaseSqlBuilder
  └── buildTableInner → FROM db.table / FROM (view_sql) mainsrc
```

## 4. 废弃模型的风险与 trade-off

### 4.1 全量 JOIN 性能

模型只 JOIN 实际使用的维度表，视图每次都跑全部 JOIN。

- **维度表数据量小（< 万行）**：影响可忽略，数据库优化器通常能处理
- **维度表多（5 张以上）且量不小**：需要实测。对有此类需求的模型，可拆成多个"瘦视图"（按常见维度组合预置），属于配置层面消化
- **事实表很大但只查事实字段（不查维度）**：视图仍然做了多余的 JOIN，可用"纯事实视图"兜底

### 4.2 字段注册工作量

模型自动发现维度表字段，视图需要手动注册 `olap_src_table_field_mapping`。

- 一次性成本，迁移时可写脚本从现有的 `olap_data_model_dimension` + `olap_table_field_mapping` 批量生成
- 新增维度时多一步操作，但频率不高

### 4.3 迁移成本

已上线的模型需要逐个转成视图：
1. 根据模型的事实表 + 维度表 JOIN 关系写出 view_sql
2. 为视图注册所有字段的 field_mapping（可从现有模型关联的 field_mapping 导出）
3. 创建对应的 `olap_table_pro` 记录（tb_type_key=1）
4. 验证 + 切流

迁移量取决于已上线模型数量。

### 4.4 配置可读性

- 模型：`olap_data_model_dimension` 表里清晰列出 JOIN 关系，一目了然
- 视图：JOIN 关系藏在 view_sql 文本里，不如结构化配置直观

缓解方式：视图命名规范 + view_sql 注释规范。

## 5. 建议

**短期（当前迭代）**：不废弃。两者共存，计算指标需求不依赖模型废弃。

**中期（视实际情况）**：满足以下条件时可启动废弃：
- 已上线模型数量可控（迁移成本低）
- 维度表规模较小或已确认全量 JOIN 无性能问题
- 有自动化脚本降低字段注册迁移成本

**废弃的实质前提**：不是"视图能不能替代模型"，而是"全量 JOIN 在你们的实际数据集上是否可接受"。这是唯一的硬约束，其他都是工作量和偏好问题。

## 6. 相关文件索引

- 模型实体：`entity/OlapDataModelDO.java`、`entity/OlapDataModelDimensionDO.java`
- 模型 SQL 生成：`support/sql/builder/BaseSqlBuilder.java` → `buildModelInner()`
- 模型数据源填充：`service/impl/TableSelectionServiceImpl.java` → `fillModelSource()`
- 模型 DTO 承载：`dto/DataSourceInfo.java` → model 相关字段
- 视图 SQL 生成：`support/sql/builder/BaseSqlBuilder.java` → `buildTableInner()` 中的 `isView` 分支
- 视图时间变量：`service/impl/SqlGenerationServiceImpl.java` → `resolveViewTimeVariables()`
- 配置表 DDL：`ddl/ddl.sql` → `olap_data_model` (L102)、`olap_data_model_dimension` (L119)、`olap_table_pro.view_sql` (L137)
