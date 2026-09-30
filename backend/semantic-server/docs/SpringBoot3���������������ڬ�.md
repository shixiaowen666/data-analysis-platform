# Spring Boot 3 升级操作步骤

项目：semantic-server | 日期：2026-07-10

> 本文档回答：**先做什么、后做什么**。
>
> 每一步要改的具体内容见《SpringBoot3升级执行清单》，分支合并规则见《SpringBoot3升级分支管理方案》。

---

## 步骤 0：前置准备

```bash
# 0.1 确认 JDK 17
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.19.jdk/Contents/Home
java -version   # 确认输出 17.x

# 0.2 确认 master 干净
git checkout master
git pull origin master
git status        # 必须无未提交改动
```

---

## 步骤 1：拉取升级分支

```bash
git checkout -b feature/upgrade-springboot3
git push -u origin feature/upgrade-springboot3
```

---

## 步骤 2：Commit ① — 基础环境升级

参照《升级执行清单》第一、二节，依次修改以下文件：

### 2.1 改 pom.xml

1. `spring-boot-starter-parent` 版本 `2.7.18` → `3.5.14`
2. `<java.version>` `1.8` → `17`
3. `<druid.version>` `1.2.23` → `1.2.28`
4. `<lombok.version>` `1.16.22` → `1.18.34`
5. 删除被注释的 `<spring-framework.version>` 行
6. 删除整个 `<dependencyManagement>` 块
7. `mybatis-plus-boot-starter` → `mybatis-plus-spring-boot3-starter`
8. `druid-spring-boot-starter` → `druid-spring-boot-3-starter`
9. `io.springfox:springfox-boot-starter:3.0.0` → `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17`
10. maven-compiler-plugin `source/target` `1.8` → `17`

### 2.2 改 Dockerfile

1. `FROM openjdk:8u342-jre` → `FROM openjdk:17-jre`
2. apt sources 中 `bullseye` → `bookworm`（3 处）

### 2.3 提交

```bash
git add pom.xml Dockerfile
git commit -m "feat: Spring Boot 3.5.14 升级 - 父 POM、JDK 17、依赖版本与 artifactId 替换"
git push origin feature/upgrade-springboot3
```

---

## 步骤 3：Commit ② — javax → jakarta 包迁移

参照《升级执行清单》第三节，修改以下 6 个 Java 文件（共 7 处 import）：

| 文件 | 改什么 |
|------|------|
| `common/config/DataSourceManager.java` | `javax.annotation.Resource` → `jakarta.annotation.Resource`（第 10 行 `javax.sql.DataSource` 不动） |
| `common/interceptor/TenantFilter.java` | 4 个 `javax.servlet.*` → `jakarta.servlet.*` |
| `controller/GetDataController.java` | `javax.annotation.Resource` + `javax.validation.Valid` |
| `service/impl/SqlGenerationServiceImpl.java` | `javax.annotation.Resource`（`javax.sql.DataSource` 不动） |
| `service/impl/TableSelectionServiceImpl.java` | `javax.annotation.Resource` |
| `support/sql/builder/SegmentSqlBuilder.java` | `javax.annotation.Resource` |
| `support/sql/builder/SqlGeneratorService.java` | `javax.annotation.Resource` |

```bash
git add src/
git commit -m "feat: javax → jakarta 包迁移，适配 Spring Boot 3"
git push origin feature/upgrade-springboot3
```

---

## 步骤 4：Commit ③ — 配置文件 + 编译验证

参照《升级执行清单》第四节：

### 4.1 改 application.yml

`springfox.documentation.enabled: false` → `springdoc.api-docs.enabled: false`

### 4.2 编译验证

```bash
mvn clean compile
```

修复所有编译错误。确认 `BUILD SUCCESS`。

### 4.3 提交

```bash
git add src/main/resources/application.yml
# 如有编译修复，一并 git add
git commit -m "fix: springfox → springdoc 配置替换，通过 mvn clean compile"
git push origin feature/upgrade-springboot3
```

---

## 步骤 5：功能回归测试

| 测试项 | 方式 |
|------|------|
| 启动应用 | `mvn spring-boot:run` 无异常 |
| 健康检查 | `curl /actuator/health` 返回 UP |
| 查询接口 | POST `/api/data-server/getdata` 正常返回 |
| TenantFilter | 带 `X-Tenant-Id` 请求头调用正常 |

---

## 步骤 6：合并回 master

> 确认步骤 5 全部通过后再执行。

```bash
# 确认升级分支已推送
git push origin feature/upgrade-springboot3

# 切到 master 合并
git checkout master
git pull origin master
git merge feature/upgrade-springboot3

# 打 tag
git tag v2.0.0
git push origin master --tags
```

---

## 回退

按《分支管理方案》第五节执行。
