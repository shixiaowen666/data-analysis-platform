# Spring Boot 3 升级分支管理方案

项目：chat-server | 日期：2026-07-10

---

## 一、分支规划

```
master ─────●───────────────────────────(hotfix)──────●── tag v2.0.0
            │                                           │
            └── feature/upgrade-springboot3 ──●──●──●───┘
                                               ①  ②  ③ (多 commit + 回归)
```

## 二、分支基本信息

| 项目 | 内容 |
|------|------|
| 主分支 | `master` |
| 升级分支 | `feature/upgrade-springboot3` |
| 合并后 tag | `v2.0.0` |

## 三、执行步骤

### 步骤 1：确认基线干净

```bash
git checkout master
git pull origin master
git status          # 确保无未提交改动
```

### 步骤 2：拉出升级分支

```bash
git checkout -b feature/upgrade-springboot3
git push -u origin feature/upgrade-springboot3
```

### 步骤 3：分段提交

按以下顺序分段 commit，便于 review 和回退：

```bash
# Commit 1：基础环境升级
git add pom.xml Dockerfile
git commit -m "feat: Spring Boot 3.5.14 升级 - 父 POM、JDK 17、基础依赖版本"

# Commit 2：javax → jakarta 包迁移
git add src/
git commit -m "feat: javax 包迁移至 jakarta，适配 Spring Boot 3"

# Commit 3：第三方依赖升级
git add pom.xml
git commit -m "feat: 升级 jjwt 0.12.5、MinIO 8.2.2、druid 1.2.28、springdoc 2.8.17"

# Commit 4：代码逻辑适配
git add src/
git commit -m "feat: JwtTokenUtil 重写、MinIO API 迁移、LoginFilter 路径适配"

# Commit 5：编译修复与最终验证
git add src/
git commit -m "fix: 编译修复，通过 mvn clean compile"
```

每次 commit 后推送到远端：

```bash
git push origin feature/upgrade-springboot3
```

### 步骤 4：合并回 master

```bash
# 确认升级分支所有代码已推送
git push origin feature/upgrade-springboot3

# 切换到 master 合并
git checkout master
git pull origin master
git merge feature/upgrade-springboot3

# 打 tag
git tag v2.0.0
git push origin master --tags
```

---

## 四、并行开发规则

升级期间 master 可能接收 hotfix：

```
master 有 hotfix → 合入后同步到 feature/upgrade-springboot3
```

```bash
# master 合入 hotfix 后，同步到升级分支
git checkout feature/upgrade-springboot3
git merge master                    # 或 git rebase master
git push origin feature/upgrade-springboot3
```

## 五、回退预案

如果升级过程中发现阻塞性问题：

```bash
# 方案 1：回退单个 commit
git log --oneline                   # 找到要回退的 commit
git revert <commit-hash>
git push origin feature/upgrade-springboot3

# 方案 2：废弃整个升级分支
git checkout master
git branch -D feature/upgrade-springboot3
git push origin --delete feature/upgrade-springboot3
```

## 六、禁止事项

- 禁止在 master 上直接改升级代码
- 禁止 force push 到 master
- 禁止跳过 `mvn clean compile` 就直接 push
- 禁止不拉 rebase/merge master hotfix 就最终合并
