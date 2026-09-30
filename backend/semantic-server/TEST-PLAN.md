# 测试计划

> 覆盖范围：计算指标、衍生指标、同环比、快照值、时间派生维度五大新功能 + 存量回归 + 方言兼容

## 1. 计算指标

| 场景 | 验证点 |
|---|---|
| 单源 + 无维度 | 公式内联展开，无 GROUP BY，一行结果 |
| 单源 + 有维度 | 按维度分组的公式值 |
| 多源 | OuterSqlWrapper 层 `sum(unionTable.子指标)` 展开公式 |
| 用户显式请求子指标 `[76, 72]` | 72 正常输出，公式复用 |
| 纯 internal 子指标 | SQL 不输出冗余列（`[76]` 时无 num_in/num_out 列） |
| 两个计算指标同查 | 各自展开，互不影响 |
| 计算 + 普通混查 | 普通指标不受影响 |
| 公式 `*/` 运算 | 多源场景 `sum(A)*sum(B)` 语义正确 |
| english_name 别名 | 输出列名用 english_name 而非 calc_id_key |

## 2. 衍生指标

| 场景 | 验证点 |
|---|---|
| 无维度请求 `[80]` | 无 GROUP BY 语法合法，全表一行 |
| 有普通维度 | 按维度分组，CASE WHEN 值正确 |
| filter 维度也是用户请求维度 | 进 GROUP BY，合法输出 |
| 衍生 + 普通混查 | 普通指标不被 filter 污染 |
| 两个衍生不同 filter | 各自 CASE WHEN 互不影响 |
| 衍生 + 计算混查 | 两套机制正交 |
| 老格式 JSON（indicatorId/dimensionList/symbol） | 解析兼容，baseIndicatorId 非 null |
| 新格式 JSON（baseIndicatorId/filters） | 正常解析 |

## 3. 同环比

| 场景 | 验证点 |
|---|---|
| pop × 5 种粒度 | day/week/month/quarter/year 偏移正确 |
| yoy | 去年同期偏移 |
| both | 5 列全输出 |
| 有日期维度 | 逐日对比 merge |
| 无日期维度 | 整周期汇总对比 |
| 上期无数据 | `_prev=null, _ratio=null` 且报文里字段存在 |
| 上期为 0 | ratio=null 不抛异常 |
| 缺 dateGranularity / timeRange | 报错提示 |
| 并发租户传递 | 3 条 SQL 都带 tenant 条件 |

## 4. 快照值

| 场景 | 验证点 |
|---|---|
| 带 ptdate 维度 | 日粒度 sum = 当天快照 |
| 不带 ptdate | 自动补入，返回多一列日期 |
| 快照 + 普通指标 | 同查正常 |
| 数据源无 ptdate 字段 | 明确报错 |

## 5. 时间派生维度

| 场景 | 验证点 |
|---|---|
| ptweek/ptmonth/ptquarter/ptyear | 四种粒度 SQL 正确 |
| varchar 字段（yyyy-MM-dd 兜底） | 字符串截取，不报 TO_CHAR 错 |
| yyyyMMdd 格式 | 截取位置正确 |
| date 类型字段 | 仍走 TO_CHAR 路径 |
| 季度边界值 | 1/3/4/6/7/9/10/12 月 → Q1/Q1/Q2/Q2/Q3/Q3/Q4/Q4 |
| filter 传派生维度 | 跳过不拼 WHERE，由 timeRange 承担 |

## 6. 回归测试（老功能不受影响）

- 普通指标 + 维度查询（主链路）
- 多源 UNION ALL + 外层二次聚合
- 模型（Type 2）JOIN
- 视图（tb_type_key=1）+ 时间变量替换
- WHERE/HAVING filter 自动分类
- 排序 / 分页 / groupTopN / limit
- 老请求报文（无新字段）零影响

## 7. 方言兼容

| 数据库 | 验证点 |
|---|---|
| PostgreSQL | 当前全部功能 |
| GaussDB/DWS | 刚换的驱动，SQL 语法 + 连接 + 时间函数 |
| MySQL | DATE_FORMAT/DIV 分支 |
| Oracle/DM | SUBSTR/TO_CHAR 分支 |

## 8. 异常与边界

- 公式 JSON 格式错误 → 明确报错
- 子指标无 field_mapping → 数据源选择报错
- 衍生指标 baseIndicatorId 缺失 → 报错非 null 污染
- filter 值与字段类型不匹配（如数字维度传字符串）
- 除零、null 值处理
- 租户 ID 缺失（TenantFilter 已有拦截）

## 执行优先级

1. **P0**：1-5 新功能主链路（核心交付）
2. **P1**：6 回归（保证存量不破坏）
3. **P2**：7 方言兼容（重点 GaussDB，刚换驱动）
4. **P3**：8 异常场景（建议单测覆盖）
