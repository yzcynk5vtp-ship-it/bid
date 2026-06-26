# 部署报告：第 6 次发布

**发布 ID**: `8ed1ac7dd-api8080`
**部署时间**: 2026-06-26 21:04 CST (13:04 UTC) — JAR 部署；21:26 CST — 修复后成功启动
**部署者**: Trae AI Agent（xiyu-bid-poc 基准区 main 分支）
**部署类型**: 手动 SSH 部署（remote-deploy.sh）

---

## 发布范围

| 项目 | 值 |
|---|---|
| **Commit** | `8ed1ac7dd` — `!1180 fix(db): CO-358 真正根因——business_qualifications.status enum→varchar(32)` |
| **源码变更** | 14 个新 commit（从第 5 次 `8f5497693` 起），涵盖 CO-358 数据库列类型修复、V1027/V1039/V1100 迁移文件补全、Flyway 版本号治理机制 |
| **前端构建** | Vite + Vue 3，生产同源构建（`baseURL=""`） |
| **后端构建** | Maven + Spring Boot 3.2.0，`mvn clean package -DskipTests` |
| **JAR 大小** | ~155 MB（`bid-poc-1.0.3.jar`） |
| **迁移数量** | 165 个（JAR 内） |

### 主要变更内容（14 个 commit）

1. **CO-358 数据库列类型修复**（!1180/!1181/!1182）：`business_qualifications.status` 从 enum 改为 VARCHAR(32)，修复 margin 接口 500 错误
2. **V1027/V1039/V1100 迁移文件补全**（!1175）：补全 3 个历史缺失的迁移文件（源码缺失但生产 DB 已执行），含 forward + rollback 脚本
3. **Flyway 版本号治理机制**（!1175/!1176）：新建 `check-flyway-db-source-sync.sh` 脚本，检查源码迁移文件与生产 DB 一致性；改进 `new-migration.sh` 调用 `next-migration-version.sh` 防止并行开发版本冲突
4. **其他**：CO-358 margin 接口参数绑定修复、agents-bug-lessons-ref 更新

---

## 部署执行摘要

### 1. 发布准备

- **基准区**: `xiyu-bid-poc/main`，已同步到 `origin/main` 最新
- **打包**: `scripts/release/package-release.sh` → `.release/xiyu-bid-release-8ed1ac7dd-api8080.tar.gz`

### 2. Flyway Validate 预检

- **结果**: ✅ 通过
- **已验证迁移**: 165 个
- **所有 checksums 匹配**: 是

### 3. 发布激活

- **remote-deploy.sh** 执行成功
- JAR 复制到 `/opt/xiyu-bid/shared/backend/app.jar`
- 前端复制到 `/srv/www/xiyu-bid`
- backend restart

### 4. ⚠️ 后端启动失败 — Flyway V1039 failed migration

**现象**: 后端重启后 Spring Boot 启动失败，错误：
```
Caused by: org.flywaydb.core.internal.command.DbMigrate$FlywayMigrateException:
Schema `winbid` contains a failed migration to version 1039 !
```

**根因分析**:

生产 DB `flyway_schema_history` 中 V1027/V1039 存在**重复记录**，且 V1039 最后一次执行 failed：

| installed_rank | version | type | success | installed_on | 说明 |
|---|---|---|---|---|---|
| 99 | 1027 | SQL | 1 | 2026-06-05 10:27:49 | 原始成功（checksum=-1006465723） |
| 107 | 1039 | SQL | 1 | 2026-06-05 10:27:51 | 原始成功（checksum=346670299） |
| 162 | 1027 | DELETE | 1 | 2026-06-26 07:19:41 | flyway repair 标记（execution_time=0） |
| 163 | 1039 | DELETE | 1 | 2026-06-26 07:19:41 | flyway repair 标记（execution_time=0） |
| 165 | 1100 | SQL | 1 | 2026-06-26 13:17:15 | V1100 正常 |
| 167 | 1027 | SQL | 1 | 2026-06-26 21:06:21 | 重复执行（幂等成功） |
| 168 | 1039 | SQL | **0** | 2026-06-26 21:06:21 | **FAILED**（ADD COLUMN 列已存在） |

**深层根因**: 第 5 次部署补全 V1039 源码文件时，文件内容与 06-05 原始执行的 V1039 不同（checksum 从 346670299 → 161923702）。06-05 已执行过原始版本（表里已有列），21:06 新 jar 用新内容执行时因 `ADD COLUMN bid_result` 列已存在而失败。

> 注：第一版 `check-flyway-db-source-sync.sh` 的 `get_db_versions()` 查询未过滤 `success=1`，导致 V1039（实际 success=0 failed）被误判为"DB 已执行"，据此添加了内容不一致的 V1039 到源码。

### 5. 修复操作（用户批准后执行）

**步骤 1: 备份 flyway_schema_history**
```
备份文件: /tmp/flyway_schema_history-20260626-212424.sql (25KB)
```

**步骤 2: DELETE 重复 + failed 记录**
```sql
DELETE FROM flyway_schema_history WHERE installed_rank IN (162, 163, 167, 168);
```
- 删除 rank 162, 163（DELETE 类型 flyway repair 标记）
- 删除 rank 167（V1027 重复 SQL 成功记录）
- 删除 rank 168（V1039 failed 记录）
- 保留 rank 99, 107（06-05 原始成功记录）和 rank 165（V1100）

**步骤 3: flyway repair 对齐 checksum**
```bash
sudo bash /tmp/flyway-repair-runner.sh repair
```
- 从 jar 提取 Flyway 9.22.3 + 依赖
- 自动备份 flyway_schema_history → `/opt/xiyu-bid/backups/flyway-history/flyway_schema_history-20260626-212533.sql`
- repair 对齐 V1039 checksum: 346670299 → 161923702
- **repair 后 validate**: ✅ Successfully validated 166 migrations, all checksums match

**步骤 4: 重启后端**
```bash
sudo systemctl restart xiyu-bid-backend
```
- 启动时间: 148 秒
- 健康检查: `{"status":"UP","groups":["liveness","readiness"]}`
- 服务状态: `active (running)`

---

## Smoke 测试结果

| 检查项 | 结果 |
|---|---|
| **Verdict** | ✅ **GO**（服务可用，登录需生产密码确认） |
| 生产前端首页可访问 | ✅ HTTP 200，entry JS `/assets/index-DT_nZybY.js` |
| 生产健康检查返回 UP | ✅ `{"status":"UP","groups":["liveness","readiness"]}` |
| 后端 18080 内部健康检查 | ✅ HTTP 200 |
| nginx 8080 代理健康检查 | ✅ HTTP 200（服务器内部） |
| 登录 API 接口可用 | ✅ 返回 401（密码错误，接口本身正常） |
| /actuator/info 安全策略 | ✅ 返回 403（未授权访问被拒绝，安全配置正常） |
| 完整登录流程 | ⏭️ skipped（生产 admin 密码未确认，非 `XiyuAdmin2026!`） |
| 业务 API 读测试 | ⏭️ skipped（需登录 token） |

**说明**: 本地访问公网 8080 需绕过代理（`--noproxy '*'`），因本地有 HTTP_PROXY 环境变量导致首次测试 502。

---

## 回滚姿态

| 锚点 | 值 |
|---|---|
| **当前版本** | `8ed1ac7dd-api8080`（`8ed1ac7dd`） |
| **前回滚版本** | `8f5497693-api8080`（第 5 次部署） |
| **JAR 备份位置** | `/opt/xiyu-bid/releases/8f5497693/backend/app.jar` |
| **前端备份位置** | `/opt/xiyu-bid/releases/8f5497693/frontend/` |
| **flyway_schema_history 备份** | `/tmp/flyway_schema_history-20260626-212424.sql` + `/opt/xiyu-bid/backups/flyway-history/flyway_schema_history-20260626-212533.sql` |
| **回滚命令** | 参考 `docs/release/ROLLBACK_RUNBOOK.md` |

---

## 环境信息

| 环境 | 值 |
|---|---|
| **目标服务器** | `172.16.38.78`（winbid-01.test） |
| **后端端口** | 18080（内部）/ 8080（nginx proxy） |
| **前端端口** | nginx 80 / 8080 |
| **数据库** | `winbid`（`ea_bid@winbid-01.test.rds.ehsy.com:3306`） |
| **MySQL 版本** | 8.0.43 |
| **Java 版本** | OpenJDK 21 |
| **Spring Boot 版本** | 3.2.0 |
| **Flyway 版本** | 9.22.3 |
| **Profile** | `prod,mysql` |

---

## 待处理事项

### 高优先级

1. **check-flyway-db-source-sync.sh 查询 bug 已修复**: 第一版 `get_db_versions()` 未过滤 `success=1`，导致 failed migration 被误判为"DB 已执行"。已在本次部署中修复（本地源码已更新，需提交 PR 合入 main）
2. **V1039 源码文件内容不一致**: 本次补全的 V1039 文件 checksum（161923702）与 06-05 原始执行的版本（346670299）不同。当前已通过 flyway repair 对齐 DB checksum 到源码版本，但建议确认 V1039 源码内容是否为期望版本

### 中优先级

1. **生产 admin 密码**: 冒烟测试无法完成完整登录流程，需确认生产环境 `ADMIN_PASSWORD` 环境变量设置的密码
2. **本地代理配置**: 本地有 `HTTP_PROXY=http://127.0.0.1:7897`，访问内网 172.16.38.78 时需加 `--noproxy '*'`

---

## 经验教训

1. **flyway_schema_history 查询必须过滤 success=1**: `SELECT version FROM flyway_schema_history WHERE type='SQL' AND success=1`，否则 failed migration 会被误判为"已执行"
2. **补全迁移文件时需确认 checksum 一致**: 源码迁移文件内容必须与生产 DB 已执行版本完全一致，否则会触发 checksum mismatch 或重复执行失败
3. **flyway repair 不能处理重复的 DELETE 类型记录**: flyway repair 会删除 failed 记录和对齐 checksum，但对历史 repair 产生的 DELETE 类型标记和重复 SQL 记录需要手动清理
4. **幂等迁移 vs 非幂等迁移**: V1027（MODIFY COLUMN）是幂等的，重复执行也能成功；V1039（ADD COLUMN/INDEX）是非幂等的，重复执行会因列已存在而失败。编写迁移时应尽量考虑幂等性
