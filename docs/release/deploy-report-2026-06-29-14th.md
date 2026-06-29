# 第十四次部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| **部署时间** | 2026-06-29 08:20:08 CST |
| **部署人** | Trae AI Agent |
| **目标服务器** | 172.16.38.78 (winbid-01.test) |
| **应用端口** | 8080 |
| **部署状态** | ✅ 成功 |

## 基线信息

| 项目 | 值 |
|---|---|
| **部署 commit** | `cb6796cef` (!1311) |
| **第 13 次部署基线** | `c577dec2c` (!1301) |
| **新增 commit 数** | 10 个 |
| **新增迁移数** | 1 个（V1107） |

## 第 13 次部署后合入的 PR（9 个）

| PR 号 | 主题 | 分类 |
|---|---|---|
| !1302 | CO-387 任务看板增加删除任务功能 | 功能 |
| !1303 | CO-386 账号借用申请全流程（蓝图 5.2.1.4） | 功能 |
| !1304 | resolve silent transaction rollback when Tender auto-assignment fails | 修复 |
| !1306 | CO-382 修复投标文件删除权限与提交前守卫 | 修复 |
| !1307 | CO-381 region backend validation | 修复 |
| !1308 | CO-361 项目任务看板空白——前端 allSettled 容错 + 后端文档策略放行任务执行人 | 修复 |
| !1309 | CO-382 review 修复删除按钮权限与后端策略对齐 | 修复 |
| !1310 | 第十三次部署报告 | 文档 |
| !1311 | 补充 ExternalUrlGuard/FilePathGuard 单元测试（PR !1298 遗漏） | 测试 |

## 改动范围

- **52 个文件变更**：+2855 行 / -271 行
- **1 个新 Flyway 迁移**：`V1107__account_borrow_application_project_and_comment.sql`
  - 为 `account_borrow_applications` 增加 `project_id` BIGINT NULL + `approval_comment` VARCHAR(500) NULL
  - 将历史 `APPROVED` 状态迁移为 `BORROWED`
  - **非幂等**（标准 Flyway 迁移，靠版本号防重复）

## Flyway 预检与处置

### 预检 3 步法结果

| 步骤 | 检查项 | 结果 |
|---|---|---|
| Step 1 | `flyway-repair-runner.sh validate` | ✅ 170 migrations validated, all checksums match |
| Step 2 | JAR 内迁移版本与 DB 一致性 | ✅ DB 最新 V1106 = jar V1106；V1107 为预期 pending |
| Step 3 | remote-deploy.sh 激活前自动 validate | ✅ 通过（170 migrations validated, execution time 00:00.081s） |

### 关键确认

- ✅ 本次有 1 个新迁移文件（V1107），完整执行 3 步预检
- ✅ JAR 内无重复版本号（package-release.sh 内置校验通过）
- ✅ V1107 在后端启动后 6 秒内成功执行（installed_on: 2026-06-29 08:20:14）

## 部署步骤

### 1. 同步基线

- 早操同步：`git fetch origin --prune && git merge --ff-only origin/main`（锚点分支 ff-only）
- 当前分支：`agent/trae-init`（锚点分支）
- 同步结果：HEAD = `cb6796cef` = origin/main（0/0 完全同步）
- 工作区干净，无未提交变更

### 2. 本地打包

- **打包命令**：`RELEASE_ID="cb6796cef-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh`
- **前端构建**：
  - 生产同源构建模式（`VITE_API_BASE_URL=` 显式设空）
  - `src/api/config.js` 在 `import.meta.env.PROD=true` 时强制 `baseURL=""`（同源）
  - 产物：`index-DGML8Q1G.js`
  - `check:frontend-api-base` 验证通过
- **后端构建**：
  - `mvn clean -DskipTests package`（强制 clean，避免脏 target）
  - 构建耗时：27.232s
  - jar 大小：155548648 bytes (约 155MB)
  - jar 内迁移文件：170 个 V*.sql + 1 个 B73 baseline
  - 重复版本校验：✅ 无重复
- **Archive 大小**：136M

### 3. 代码版本验证

| 验证项 | 结果 | 说明 |
|---|---|---|
| jar 内 V1107 迁移文件 | ✅ 存在 | CO-386 账号借用申请字段已包含 |
| jar 内 migration-mysql/ V 数 | ✅ 170 个 | 与源码一致（13th 是 169，+1 V1107） |
| jar 内 B73 baseline | ✅ 存在 | 与历史一致 |
| 前端 index.html | ✅ index-DGML8Q1G.js | 最新前端构建 |

### 4. 上传与激活

- **上传**：archive (137M) + remote-deploy.sh 到 `/opt/xiyu-bid/incoming/`
- **DB 备份**：`/opt/xiyu-bid/db-backups/winbid-cb6796cef-api8080-20260629081810.sql.gz`（2.8M）
- **remote-deploy.sh 执行流程**：
  1. ✅ 解压 archive 到 `/opt/xiyu-bid/releases/cb6796cef-api8080/`
  2. ✅ 激活前端（atomic swap 到 `/srv/www/xiyu-bid`）
  3. ✅ Flyway validate 预检通过（170 migrations, execution time 00:00.081s）
  4. ✅ 更新后端 jar：`/opt/xiyu-bid/shared/backend/app.jar`
  5. ✅ 写入部署记录：`/opt/xiyu-bid/deployed-release.json`
  6. ✅ 重启后端服务（PID 22793）

### 5. 后端重启与健康检查

- **重启时间**：2026-06-29 08:20:08 CST
- **PID**：22793
- **V1107 应用时间**：2026-06-29 08:20:14（启动后 6 秒）
- **健康检查**：通过（30s 后检查，所有组件 UP）
- **前端一致性校验**：✅ `src="/assets/index-DGML8Q1G.js"` 与 release 一致

## 验证结果

### 后端健康检查

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "MySQL", "validationQuery": "isValid()" } },
    "diskSpace": { "status": "UP", "details": { "total": "105553760256", "free": "47364628480" } },
    "jwt": { "status": "UP", "details": { "algorithm": "HMAC-SHA256", "secretLength": 64, "strength": "STRONG" } },
    "livenessState": { "status": "UP" },
    "readinessState": { "status": "UP" },
    "redis": { "status": "UP", "details": { "version": "6.2.19" } }
  }
}
```

### API Smoke 测试

| 接口 | 结果 | 说明 |
|---|---|---|
| `/actuator/health` | ✅ 200 UP | 所有组件 UP |
| `/actuator/health/readiness` | ✅ 200 UP | Readiness 正常（无 Kafka 延迟） |
| `POST /api/auth/login` | ✅ 400 | 空密码触发验证错误（接口路由正常） |
| `GET /api/projects` | ✅ 403 | 需要认证（接口正常） |
| `/api/integration/crm/health` | ✅ 401 | 需要认证（接口正常） |

### 前端验证

| 端点 | 结果 |
|---|---|
| `/` (首页) | ✅ 200 |
| `/login` | ✅ 200 |
| 前端入口 JS | ✅ `index-DGML8Q1G.js`（与 release 一致） |

### Flyway 迁移应用确认

```sql
SELECT version, description, success, installed_on FROM flyway_schema_history WHERE version IN (1106, 1107);

version  description                                          success  installed_on
1107     account borrow application project and comment       1        2026-06-29 08:20:14
1106     add created by to tasks                              1        2026-06-28 15:44:19
```

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 同步方向 | Gitee main → GitHub main（单向镜像） |
| 同步前差异 | 0 个 commit（两边已一致） |
| 同步后状态 | ✅ 完全一致（两边 HEAD = `cb6796cef`） |
| 同步命令 | `bash scripts/sync-to-github.sh` |

## 回滚信息

| 项目 | 值 |
|---|---|
| **回滚版本** | `c577dec2c-api8080`（第 13 次部署版本） |
| **回滚 jar 路径** | `/opt/xiyu-bid/releases/c577dec2c-api8080/backend/app.jar` |
| **回滚前端路径** | `/opt/xiyu-bid/releases/c577dec2c-api8080/frontend/` |
| **回滚命令** | 恢复旧 jar + 前端 + `sudo systemctl restart xiyu-bid-backend` |
| **DB 备份** | `/opt/xiyu-bid/db-backups/winbid-cb6796cef-api8080-20260629081810.sql.gz` |
| **V1107 回滚注意** | 该迁移加了 2 列 + UPDATE 状态，回滚需手动 DROP COLUMN + 恢复 APPROVED 状态 |

## 经验沉淀应用情况

本次部署应用了 xiyu-server-deploy skill 中沉淀的 7 条关键经验：

| # | 经验 | 应用情况 |
|---|---|---|
| 1 | Flyway 预检 3 步法 | ✅ 完整执行（validate + source-sync + remote-deploy 内置） |
| 2 | Readiness 延迟恢复 | ✅ 本次无延迟（Kafka 启动顺利，30s 内 readiness UP） |
| 3 | 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空，baseURL="" |
| 4 | Smoke 测试限制 | ✅ admin 密码未知，用 400/403/401 验证接口路由 |
| 5 | GitHub 镜像同步 | ✅ 两边已一致（无需推送） |
| 6 | 临时调试配置清理 | ⚠️ `SHOW_DETAILS=always` 保留现状（用户决定，运维监控需要） |
| 7 | 幂等迁移设计 | ⚠️ V1107 非幂等（标准 Flyway 模式，靠版本号防重） |

## 风险提示

1. **MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always**：`/actuator/health` 暴露 DB/Redis/JWT 组件详情。用户连续两次决定保留现状（运维监控需要）。如后续需收紧安全，可改为 `never` 并重启后端。

2. **V1107 非幂等**：迁移使用 `ALTER TABLE ADD COLUMN`（无 IF NOT EXISTS）+ `UPDATE` 状态。如果通过 `flyway repair` 重放可能失败。生产环境靠 Flyway 版本号机制防重复执行，正常流程无风险；仅在 schema_history 被手动篡改时需注意。

3. **V1107 回滚需配套脚本**：当前 V1107 没有对应的 rollback 脚本（`db/rollback/migration-mysql/` 下无 `RV1107__*.sql`）。如需回滚 DB 状态，需手动 DROP COLUMN project_id/approval_comment + UPDATE 状态回 APPROVED。建议后续补充 rollback 脚本。

## 部署确认

- [x] 早操同步 + 工作区干净
- [x] Flyway 预检 3 步法通过（含 V1107 新迁移）
- [x] 本地打包验证（前端同源 + 后端 clean build + 无重复迁移）
- [x] 代码版本验证（V1107 在 jar 内 + 170 V + 1 B73）
- [x] DB 备份完成（2.8M）
- [x] remote-deploy.sh 执行成功（含 Flyway validate 预检）
- [x] 后端重启 + 健康检查 UP
- [x] V1107 迁移已应用（08:20:14）
- [x] API smoke 通过（health + readiness + 3 个接口路由）
- [x] 前端验证通过（页面 200 + JS 一致性）
- [x] GitHub 镜像同步完成（两边一致）
- [x] 部署报告生成
