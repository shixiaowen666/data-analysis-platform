# Data Analysis Platform

数据智能分析平台。仓库按模块类型划分目录：后端服务位于 `backend/`，算法服务位于 `algorithm/`，前端工程位于 `frontend/`，需求与设计文档位于 `docs/`。

> 说明：本仓库的 `main` 分支由 Genspark AI 按模块分批次推送。第一批为 **后端（backend）** 模块，第二批为 **算法（algorithm）** 模块，第三批为 **前端（frontend）** 模块。

## 目录结构

```
data-analysis-platform/
├── backend/                 # 后端服务（Java / Spring Boot）
│   ├── bi-manager/          # BI 管理后端（指标体系、问数管理、视图/维度管理、报表分组等）
│   ├── permission/          # 权限与用户中心（用户、角色、组织、数据权限、审批等）
│   ├── chat-server/         # 对话问数服务（ChatBI 会话、AI 请求编排、查询脚本等）
│   └── semantic-server/     # 语义建模服务（语义模型、指标计算、快照、时序对比等）
├── algorithm/               # 算法服务（Python）
│   ├── analyze-streaming/   # 智能问数分析引擎 System B（问题拆解 / DAG 执行 / 流式分析、附 mock System A）
│   └── recall-service/      # 智能问数召回服务（Embedding + FAISS 四路并行召回 + LLM 精判）
├── frontend/                # 前端工程（Vue）
│   └── chatbi-new/          # 深圳项目前端（ChatBI 问数 + BI 管理后台：数据源/模型/指标/维度/提示词管理等）
├── docs/                    # 需求与设计文档
│   ├── requirements/        # 01 问答质量管理方案 · 02 原型说明 · 03 自动调优设计
│   └── prototype/           # 可点击交互原型（Vue2 + Element UI，双击 index.html 打开）
└── README.md
```

## 需求文档

| 编号 | 文档 | 说明 |
| --- | --- | --- |
| 01 | [问答质量管理方案](docs/requirements/01-问答质量管理方案.md) | 用户反馈（7 类错误 + 描述）、管理端错误定位（链路追踪 · 5 类主因）、数据模型、接口、页面 |
| 02 | [原型说明](docs/requirements/02-原型说明.md) | `docs/prototype/` 12 个页面与交互说明 |
| 03 | [自动调优设计](docs/requirements/03-自动调优设计.md) | 定位后 → 调优建议 → 草稿验证 → 超级管理员审批发布 / 回退；提示词编辑器；验证配置；观察期告警 |

详见 [docs/requirements/README.md](docs/requirements/README.md)。

## 后端模块一览

| 模块 | 目录 | 技术栈 | 说明 |
| --- | --- | --- | --- |
| bi-manager | `backend/bi-manager` | Java + Spring Boot + MyBatis-Plus + MySQL + Nacos + OpenFeign | BI 管理后端主体服务 |
| permission | `backend/permission` | Java + Spring Boot + MyBatis | 权限 / 用户 / 组织与数据权限中心 |
| chat-server | `backend/chat-server` | Java + Spring Boot + MyBatis | 智能对话问数（ChatBI）服务 |
| semantic-server | `backend/semantic-server` | Java + Spring Boot + MyBatis | 语义建模与指标计算服务 |

每个模块均为独立的 Maven 工程，包含各自的 `pom.xml`、`Dockerfile`、`startup.sh`、`src/` 与模块级 `.gitignore`。

## 算法模块一览

| 模块 | 目录 | 技术栈 | 说明 |
| --- | --- | --- | --- |
| analyze-streaming | `algorithm/analyze-streaming` | Python 3.11 + Flask + OpenAI SDK(DashScope) + Redis + MySQL | 智能问数分析引擎（System B）：问题拆解、DAG 并行执行、流式分析与总结、测试中心；`mock_system_a/` 为联调用的 System A 模拟服务 |
| recall-service | `algorithm/recall-service` | Python + FastAPI + FAISS + DashScope text-embedding-v4 / qwen3 + MySQL(SQLite 兜底) | 智能问数召回服务：表 / 主题 / 全局 / 派生指标四路并行召回，合并去重后可选 LLM 精判 |

两个算法模块均为独立的 Python 工程，各自包含 `requirements.txt`（或 README 中的依赖清单）、`README.md`/`docs_public/`、`scripts/`、`tests/` 与模块级 `.gitignore`。`analyze-streaming` 另附 `Dockerfile` 与 `startup.sh`。

## 前端模块一览

| 模块 | 目录 | 技术栈 | 说明 |
| --- | --- | --- | --- |
| chatbi-new | `frontend/chatbi-new` | Vue 2.6 + Vue CLI 5 + Element UI 2 + Vuex 3 + Vue Router 3 + Axios + ECharts 6 + Vant 2 | 深圳项目前端：智能问数（newBI / newBI-web 移动端）、数据源 / 数据模型 / 指标 / 维度 / 字段映射 / 组合 / 提示词 / AI 日志管理、统计报表、任务配置等页面 |

前端工程包含 `package.json` / `package-lock.json`、`vue.config.js`（开发代理）、`.env.development` / `.env.production`、`public/`、`src/`（`api/`、`views/`、`components/`、`router/`、`utils/`、`styles/` 等）与模块级 `.gitignore`。不包含 `node_modules/` 与 `dist/`。

## 本地构建 / 运行

### 后端（Maven）

进入具体模块目录后使用 Maven 构建，例如：

```bash
cd backend/bi-manager
mvn clean package -DskipTests
```

### 算法（Python）

```bash
# 分析引擎 System B（默认端口 5000，可通过 .env 中 SYSTEM_B_PORT 调整）
cd algorithm/analyze-streaming
pip install -r requirements.txt
python -m system_b.app            # 或 python run_services.py 同时拉起 mock System A + System B

# 召回服务（默认端口 5003）
cd algorithm/recall-service
pip install fastapi uvicorn pymysql sqlalchemy faiss-cpu numpy openai requests pydantic python-dotenv aiofiles tenacity
python -m uvicorn app.main:app --host 0.0.0.0 --port 5003
```

详细说明见各模块目录下的 `README.md`（recall-service）与 `docs_public/`。

### 前端（Node / Vue CLI）

```bash
cd frontend/chatbi-new
npm install
npm run serve     # 开发模式，默认端口 8000，接口代理目标见 vue.config.js
npm run build     # 生产构建，产物输出到 dist/
```

## 配置说明

* **后端**：各模块的运行配置位于 `src/main/resources/**/application.yml`。仓库内 `application.yml` 为开发/示例配置。
* **算法**：`analyze-streaming` 通过项目根目录 `.env` 读取配置（System A 地址、LLM Key、MySQL / Redis、JWT 密钥等）；`recall-service` 通过 `app/config.py` + 环境变量读取（`LLM_API_KEY`、`MYSQL_*`、`ADMIN_API_KEY` 等）。
* **前端**：`chatbi-new` 通过 `.env.development` / `.env.production` 读取 `VUE_APP_BASE_API`（接口前缀）与 `VUE_APP_VERSION`；开发环境后端代理地址在 `vue.config.js` 的 `devServer.proxy` 中配置。

生产环境的敏感配置（数据库口令、密钥、对象存储 AK/SK 等）请通过环境变量或配置中心注入，不要提交到本仓库。
