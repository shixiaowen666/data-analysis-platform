# Spring Boot 3 升级分支管理方案

项目：semantic-server | 日期：2026-07-10

> 本文档回答：**用哪个分支、怎么提交、怎么合并、出问题怎么回退**。

---

## 一、分支规划

```
master ─────●───────────────────────────(hotfix)──────●── tag v2.0.0
            │                                           │
            └── feature/upgrade-springboot3 ──●──●──●───┘
                                               ①  ②  ③
```

| 分支 | 用途 |
|------|------|
| `master` | 生产基线，禁止直接改动 |
| `feature/upgrade-springboot3` | 本次升级的开发分支 |

---

## 二、Commit 分段

| 序号 | 提交内容 | commit message |
|:--:|------|------|
| ① | pom.xml（全部依赖改动）+ Dockerfile | `feat: Spring Boot 3.5.14 升级 - 父 POM、JDK 17、依赖版本与 artifactId 替换` |
| ② | 6 个 Java 文件 javax → jakarta（7 处 import） | `feat: javax → jakarta 包迁移，适配 Spring Boot 3` |
| ③ | application.yml springfox → springdoc + 编译修复 | `fix: springfox → springdoc 配置替换，通过 mvn clean compile` |

---

## 三、合并流程

```bash
# 1. 确认升级分支已推送
git push origin feature/upgrade-springboot3

# 2. 切回 master 合并
git checkout master
git pull origin master
git merge feature/upgrade-springboot3

# 3. 打 tag
git tag v2.0.0
git push origin master --tags
```

---

## 四、并行开发规则

升级期间 master 如有 hotfix：

```
master 有 hotfix → 合入后同步到 feature/upgrade-springboot3
```

```bash
git checkout feature/upgrade-springboot3
git merge master
git push origin feature/upgrade-springboot3
```

---

## 五、回退预案

```bash
# 回退单个 commit
git log --oneline                   # 找到目标 commit
git revert <commit-hash>
git push origin feature/upgrade-springboot3

# 废弃整个升级分支
git checkout master
git branch -D feature/upgrade-springboot3
git push origin --delete feature/upgrade-springboot3
```

---

## 六、禁止事项

- 禁止在 master 上直接改升级代码
- 禁止 force push 到 master
- 禁止跳过 `mvn clean compile` 就 push
- 禁止不同步 master hotfix 就直接最终合并
