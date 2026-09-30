# Spring Boot 3 升级操作步骤

项目：chat-server | 日期：2026-07-10

**前置条件**：JDK 17 已安装，JAVA_HOME 已指向 JDK 17

---

## 阶段一：基线准备（在 master 上）

### 1.1 确认当前状态

```bash
git checkout master
git pull origin master
git status          # 应为 "nothing to commit, working tree clean"
```

### 1.2 提交计划文档

```bash
git add src/main/resources/doc/SpringBoot3升级执行清单.md \
        src/main/resources/doc/SpringBoot3升级分支管理方案.md \
        src/main/resources/doc/SpringBoot3升级操作步骤.md
git commit -m "docs: Spring Boot 3 升级方案文档"
git push origin master
```

---

## 阶段二：拉升级分支

```bash
git checkout -b feature/upgrade-springboot3
git push -u origin feature/upgrade-springboot3
```

> 以下所有代码改动都在 `feature/upgrade-springboot3` 分支上进行。

---

## 阶段三：执行升级（对照《升级执行清单》）

### 3.1 Commit 1：基础环境升级

修改 `pom.xml`：
- `java.version` 1.8 → 17
- `spring-boot-starter-parent` 2.7.18 → 3.5.14
- `spring-cloud.version` 2021.0.8 → 2025.0.2
- `spring-cloud-alibaba.version` 2021.0.5.0 → 2025.0.0.0

```bash
git add pom.xml
git commit -m "feat: Spring Boot 3.5.14 升级 - 父 POM、JDK 17、基础依赖版本"
git push origin feature/upgrade-springboot3
```

### 3.2 Commit 2：javax → jakarta 包迁移

对 16 个 Java 文件做全局替换（30 处）：
- `javax.annotation.*` → `jakarta.annotation.*`
- `javax.servlet.*` → `jakarta.servlet.*`
- `javax.validation.*` → `jakarta.validation.*`
- `javax.websocket.*` → `jakarta.websocket.*`
- `javax.sql.DataSource` **不替换**

```bash
git add src/
git commit -m "feat: javax 包迁移至 jakarta，适配 Spring Boot 3"
git push origin feature/upgrade-springboot3
```

### 3.3 Commit 3：第三方依赖升级

修改 `pom.xml`：
- mysql-connector-java → mysql-connector-j
- druid-spring-boot-starter → druid-spring-boot-3-starter，版本 1.2.21 → 1.2.28
- mybatis-plus-boot-starter → mybatis-plus-spring-boot3-starter
- springdoc-openapi-ui → springdoc-openapi-starter-webmvc-ui，版本 1.7.0 → 2.8.17
- jjwt → jjwt-api + jjwt-impl + jjwt-jackson，版本 0.9.1 → 0.12.5
- minio 3.0.10 → 8.2.2

```bash
git add pom.xml
git commit -m "feat: 升级 jjwt 0.12.5、MinIO 8.2.2、druid 1.2.28、springdoc 2.8.17"
git push origin feature/upgrade-springboot3
```

### 3.4 Commit 4：代码逻辑适配

- `utils/JwtTokenUtil.java`：重写 JJWT API（0.9.x → 0.12.x）
- `service/impl/MinioService.java`：适配 MinIO SDK 8.x API
- `filter/LoginFilter.java`：`/v2/api-docs` → `/v3/api-docs`
- 其他编译错误修复

```bash
git add src/
git commit -m "feat: JwtTokenUtil 重写、MinIO API 迁移、LoginFilter 路径适配"
git push origin feature/upgrade-springboot3
```

### 3.5 Commit 5：编译验证

```bash
mvn clean compile
```

修复所有编译错误后提交：

```bash
git add src/
git commit -m "fix: 编译修复，通过 mvn clean compile"
git push origin feature/upgrade-springboot3
```

---

## 阶段四：功能回归测试

部署到测试环境，逐项验证：

- [ ] Nacos 服务注册/发现正常
- [ ] Feign 远程调用正常
- [ ] MySQL + MyBatis-Plus CRUD 正常
- [ ] Redis 读写正常
- [ ] MinIO 上传/下载正常
- [ ] JWT 登录/鉴权正常
- [ ] WebSocket 通信正常
- [ ] EasyExcel 导出正常
- [ ] Swagger UI 可访问（`/swagger-ui/index.html`）

---

## 阶段五：合并发布

### 5.1 确认升级分支已同步 master 最新代码

```bash
git checkout master
git pull origin master

# 如果 master 有新的 hotfix
git checkout feature/upgrade-springboot3
git merge master
git push origin feature/upgrade-springboot3
```

### 5.2 合并并打 tag

```bash
git checkout master
git merge feature/upgrade-springboot3
git tag v2.0.0
git push origin master --tags
```

---

## 回退预案

```bash
# 废弃升级分支，重新开始
git checkout master
git branch -D feature/upgrade-springboot3
git push origin --delete feature/upgrade-springboot3
```
