# Changelog

## v1.0.0 (2026-04-26)

### Features
- 完整实现《召回服务技术方案(qw-embedding).docx》中的四路并行召回架构（Path A/B/C/D）。
- 集成 DashScope `text-embedding-v4`（1024 维 L2 归一化）和 `qwen3-235b-a22b-instruct-2507`。
- FAISS HNSW 索引（M=32, efConstruction=200, efSearch=64）持久化到 `data/indexes/`。
- 支持 LLMJudge 二次精判（可选 use_llm_judge=true）。
- 元数据涵盖 8 张表 / 99 指标 / 44 维度 / 525 维度值 / 7 业务主题 / 8 派生指标。
- 业务主题及派生指标依赖关系按业务理解补充。
- 所有可配置项（召回数量、阈值、启用、模式）均通过 HTTP API 动态修改并自动热更新。
- MySQL 优先（`mysql+pymysql`），不可用时自动降级 SQLite，元数据均以纯文本字段存储。
- 每路召回独立日志：`logs/path_a.log` / `path_b.log` / `path_c.log` / `path_d.log`。

### Tests
- 59 条测试用例，58 条三类全部命中（98.3% all-hit）。
- 平均查询延迟 ≈ 480 ms。
- 测试报告：`data/test_report.json` / `data/test_report.md`。
