# Data Analysis Platform

数据智能分析平台。仓库按模块类型划分目录：后端服务位于 `backend/`，算法服务位于 `algorithm/`。

> 说明：本仓库的 `main` 分支由 Genspark AI 按模块分批次推送。第一批为 **后端（backend）** 模块，第二批为 **算法（algorithm）** 模块。

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
└── README.md
```

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

## 配置说明

* **后端**：各模块的运行配置位于 `src/main/resources/**/application.yml`。仓库内 `application.yml` 为开发/示例配置。
* **算法**：`analyze-streaming` 通过项目根目录 `.env` 读取配置（System A 地址、LLM Key、MySQL / Redis、JWT 密钥等）；`recall-service` 通过 `app/config.py` + 环境变量读取（`LLM_API_KEY`、`MYSQL_*`、`ADMIN_API_KEY` 等）。

生产环境的敏感配置（数据库口令、密钥、对象存储 AK/SK 等）请通过环境变量或配置中心注入，不要提交到本仓库。
