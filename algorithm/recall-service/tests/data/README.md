# 测试数据

| 文件 | 说明 |
|---|---|
| `测试问题_llm.xlsx` | 批量测试用例（223 条），6 列模板：题号 / 问题 / 期望指标 / 期望表 / 期望维度 / 期望维度值。可直接在控制台「批量测试」页上传，或调用 `POST /api/batch-test/import`。 |
| `database_meta.json.txt` | 与 `webapp/data/database_meta.json` 内容一致的元数据快照（272 指标 / 52 维度 / 70 表 / 11 条业务知识），作为测试基线保留。 |
