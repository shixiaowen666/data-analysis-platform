# 问答质量管理 / 自动调优 — 本地开发与自测说明

对应需求：`requirements/01`（错误定位 + 用户反馈）、`requirements/03`（自动调优）。

## 涉及模块

| 层 | 模块 | 关键改动 |
|---|---|---|
| 算法 | `algorithm/analyze-streaming/system_b` | 调优模式（`X-Tuning-Task` 头 / `tuning-` request_id 前缀：跳过召回、不清上下文）、`prompt_overrides` 线程级提示词版本覆盖、结构化返回字段（steps/used_tables/resolved_metrics/resolved_dims/failed_steps/prompt_version/elapsed_ms）、`/api/v1/prompts/version/create|diff`、`/api/v1/prompts/lint`、`/api/v1/logs/by-request`、`user_operation_log.request_id` |
| 后端 | `backend/chat-server` | `chat_analysis_trace` / `chat_step_trace` 落库；`GET /api/chat-server/metadata/agent?code=` |
| 后端 | `backend/bi-manager` `com.quality` | 反馈 / 诊断 / 规则建议引擎 R1–R12 / 调优任务状态机 / 验证 / 审批 / 发布 / 回滚 / 回归集 / 观察期调度 / 配置 |
| 前端 | `frontend/chatbi-new` | 应用端 👍👎 `feedbackBar`；管理端「问答质量管理」7 个 Tab + 诊断抽屉 + 任务抽屉 + 全屏提示词编辑器 |

## DDL

- `backend/bi-manager/src/main/resources/db/quality.sql` — 15 张表 + 种子（错误类型字典、规则、GLOBAL 验证配置）。**请以 utf8mb4 连接执行**。
- `backend/bi-manager/src/main/resources/db/dev_minimal.sql` — 仅沙箱/本地缺表时使用。

## 本地启动（沙箱配置）

```bash
# bi-manager（sandbox profile：本地 MariaDB、禁用 nacos、端口 8591）
cd backend/bi-manager && mvn -q -DskipTests package
QUALITY_SYSTEM_B_URL=http://127.0.0.1:5055 QUALITY_CHAT_SERVER_URL=http://127.0.0.1:5055 QUALITY_RECALL_URL=http://127.0.0.1:5055 \
  java -Xmx400m -jar target/bi-backend-1.0.0-SNAPSHOT.jar --spring.profiles.active=sandbox

# 没有真实 System B / chat-server 时，用 mock 代替（Flask，:5055）
python3 docs/dev/mock_system_b.py

# 前端：低内存环境用 development 模式构建，再用静态代理服务
cd frontend/chatbi-new && NODE_OPTIONS=--max-old-space-size=1400 npx vue-cli-service build --mode development --no-module --dest /tmp/fe_dist
python3 docs/dev/fe_static_proxy.py   # :8600，/api → 8591，/webapp → 5055
```

## 自测

| 层 | 命令 | 结果 |
|---|---|---|
| System B | `cd algorithm/analyze-streaming && python3 -m pytest tests/test_tuning_support.py -q` | 13 passed |
| bi-manager 单测 | `mvn test -Dtest=SuggestionEngineTest,VerifyJudgeTest` | 11 passed |
| chat-server | `mvn -q compile -DskipTests` | OK |
| UI E2E 主流程 | `python3 docs/dev/e2e_quality_ui.py` | 反馈 → 诊断 → 建议 → 任务 → 执行验证 PASS → 提交审批 → 超管通过发布 → 线上复验 → 各 Tab；截图 `docs/prototype/screenshots/impl/01–13` |
| UI E2E 反馈条 + 提示词编辑器 | `python3 docs/dev/e2e_quality_ui_prompt.py` / `... c` | 👎 弹窗提交；R9 → 编辑器（diff / lint / 快速验证 / 保存草稿 v1.0.6）→ 执行并验证 PASS；截图 `00a/00b/04b/14–17` |

E2E 脚本依赖：`pip install playwright && playwright install chromium`，并把一个有效 JWT 写到 `/home/user/token`（HS256，`user_id/username`）；
脚本通过 `add_init_script` 预写 `Admin-User`，否则 `TopNav` 在 mounted 时读不到用户、不会显示「管理端」。

## 已知限制

- 生产模式 `vue-cli-service build` 在 2 GB 内存沙箱会 OOM；正式环境无此问题。
- `RECALL_CONFIG` 类资产 Phase 1 仅记录不自动下发。
- 观察期告警规则：24h 内同类 👎 ≥ 2 条触发通知（`tuning_verify_config.alert_window_hours`）。
