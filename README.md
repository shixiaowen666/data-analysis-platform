# Data Analysis Platform

数据智能分析平台。仓库按模块类型划分目录，后端服务位于 `backend/`。

> 说明：本仓库的 `main` 分支由 Genspark AI 按模块分批次推送。第一批为 **后端（backend）** 模块，后续批次（如算法模块）将以同级目录形式追加。

## 目录结构

```
data-analysis-platform/
├── backend/                 # 后端服务（Java / Spring Boot）
│   ├── bi-manager/          # BI 管理后端（指标体系、问数管理、视图/维度管理、报表分组等）
│   ├── permission/          # 权限与用户中心（用户、角色、组织、数据权限、审批等）
│   ├── chat-server/         # 对话问数服务（ChatBI 会话、AI 请求编排、查询脚本等）
│   └── semantic-server/     # 语义建模服务（语义模型、指标计算、快照、时序对比等）
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

## 本地构建

进入具体模块目录后使用 Maven 构建，例如：

```bash
cd backend/bi-manager
mvn clean package -DskipTests
```

## 配置说明

各模块的运行配置位于 `src/main/resources/**/application.yml`。仓库内 `application.yml` 为开发/示例配置，生产环境的敏感配置（数据库口令、密钥、对象存储 AK/SK 等）请通过环境变量或配置中心注入，不要提交到本仓库。
