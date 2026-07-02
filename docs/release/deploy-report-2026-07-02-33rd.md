# 第 33 次生产部署报告

**部署日期**：2026-07-02
**Release ID**：`057612930-api8080`
**部署执行人**：Trae Agent
**部署目标**：winbid-01.test (172.16.38.78)
**部署结果**：✅ 成功
**关键变化**：前端 Sentry DSN 首次注入，启用前端错误追踪

---

## 1. 部署概览

| 项目 | 值 |
|---|---|
| 基线 commit | `057612930b5bad86a8fde4637089c1a5b201387a` |
| 上一部署 | `88b99604e-api8080`（第 32 次，2026-07-02 00:08:30 UTC） |
| 增量 commit | 8 个 |
| Flyway 新迁移 | 无 |
| Release archive | 137M |
| 前端入口 | `assets/index-kGBb_RQ1.js` |
| **Sentry 状态** | **`sentryEnabled: true`（首次启用前端 Sentry）** |

### 本次部署关键变化

1. **前端 Sentry DSN 首次注入**：根据交接文档 `docs/release/handoff-sentry-frontend-dsn.md`（PR !1502 / !1512），打包时显式传入 `VITE_SENTRY_DSN` 环境变量，前端 Sentry 正式激活
2. CO-441 修复：审批 CA 借用申请时请求体格式错误
3. 测试修复：KnowledgeResourceAccessSecurityTest 与 TenderControllerPermissionTest
4. agent-locks 清理

---

## 2. 基线信息

### 早操三连

- `source scripts/dev-env.sh`：环境检测通过（trae / 前端 1323 / 后端 18089 / Sidecar 8009 / DB xiyu_bid_main / Redis DB 0）
- `bash scripts/sync-env.sh .`：锚点分支 `agent/trae-init` ff-only 同步至 `origin/main`，门禁 7/7 通过
- `bash scripts/check-git-wrapper.sh`：通过

### 任务分支

- 在持久 worktree `/Users/user/xiyu/worktrees/trae` 内创建 `agent/trae/deploy-33rd-report`（`--in-place` 模式）
- 基线：`origin/main` (`057612930`)

### 仓库状态

- `git status`：干净，无未提交变更
- HEAD：`057612930b5bad86a8fde4637089c1a5b201387a`
- GitHub 镜像同步状态：部署前落后 8 个 commit（部署后已同步）

---

## 3. PR 列表与改动范围

### 增量 commit（88b99604e..057612930）

| Commit | PR | 说明 |
|---|---|---|
| `057612930` | !1517 | fix(test): 修复 KnowledgeResourceAccessSecurityTest 与 TenderControllerPermissionTest 测试失败 |
| `3fe463376` | !1515 | docs(release): 第 32 次部署报告 |
| `b7c4e957a` | !1516 | fix(CA): 修复审批CA借用申请时请求体格式错误 |
| `7926cbf2e` | - | fix(CA): 修复审批CA借用申请时请求体格式错误 |
| `aa2f960bb` | - | fix(test): 修复测试失败与权限遗漏 |
| `2ef3534e8` | - | docs(release): 第 32 次部署报告 |
| `369339f79` | - | chore(locks): clean up expired agent-locks files |
| `128f32849` | - | chore: cleanup orphan agent locks (CO-439/CO-152/CO-441) |

### 改动文件统计

```
 .agent-locks/co439-admin-staff-nav-perm.yml        |  21 ---
 .agent-locks/crm-token-per-user.yml                |  21 ---
 ...to-assign-username-employee-number-mismatch.yml |  15 --
 .../controller/CaCertificateController.java        |   2 +
 .../KnowledgeResourceAccessSecurityTest.java       |   3 +
 .../controller/TenderControllerPermissionTest.java |   6 +-
 docs/release/deploy-report-2026-07-02-32nd.md      | 172 +++++++++++++++++++++
 src/views/Resource/CAManagement.vue                |   4 +-
 8 files changed, 182 insertions(+), 62 deletions(-)
```

### Flyway 迁移文件变更

- **无**（`backend/src/main/resources/db/migration-mysql/` 无 diff）

---

## 4. Flyway 预检 3 步法

### Step 1：服务器 validate

```
10:10:05.027 [main] INFO org.flywaydb.core.internal.command.DbValidate -- Successfully validated 191 migrations (execution time 00:00.068s)
VALIDATE OK - all checksums match
```

### Step 2：DB 已应用版本（最近 5 条）

| version | description | success | installed_on |
|---|---|---|---|
| 1127 | backfill oss user employee number | 1 | 2026-07-01 15:13:50 |
| 1126 | add crm sales no to users | 1 | 2026-07-01 14:14:57 |
| 1125 | fix co 439 admin staff navigation permissions | 1 | 2026-07-01 14:14:57 |
| 1124 | seed platform account create whitelist | 1 | 2026-07-01 14:14:57 |
| 1123 | add qualification manage permission | 1 | 2026-06-30 21:29:36 |

DB 最新版本 V1127，源码无新迁移，无版本差异。

### Step 3：remote-deploy.sh 内置 validate

```
10:11:47.692 [main] INFO org.flywaydb.core.internal.command.DbValidate -- Successfully validated 191 migrations (execution time 00:00.083s)
VALIDATE OK - all checksums match
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

---

## 5. 本地打包

### 打包命令

```bash
RELEASE_ID="057612930-api8080" \
VITE_API_BASE_URL= \
VITE_SENTRY_DSN="https://afe598346bea591afeabcefe91562d9b@o4511652658937856.ingest.us.sentry.io/4511652674076672" \
VITE_SENTRY_ENVIRONMENT=production \
VITE_SENTRY_TRACES_SAMPLE_RATE=0.1 \
bash scripts/release/package-release.sh
```

**关键**：本次首次在打包时传入 `VITE_SENTRY_DSN`，启用前端 Sentry 错误追踪。

### 产物校验

| 校验项 | 结果 |
|---|---|
| Maven build | ✅ BUILD SUCCESS（25.986 s） |
| jar 内 Flyway 迁移版本无重复 | ✅ 190 files |
| apiBaseUrl | `""`（同源构建） |
| **sentryEnabled** | **`true`** ✅ |
| Release archive | 137M |
| 最新迁移文件 | V1127（与 DB 一致） |

`release-metadata.json` 关键字段：
```json
{
  "apiBaseUrl": "",
  "sentryEnabled": true
}
```

---

## 6. 部署执行

### 上传

```bash
scp .release/xiyu-bid-release-057612930-api8080.tar.gz scripts/release/remote-deploy.sh jetty@172.16.38.78:/opt/xiyu-bid/incoming/
```

### 执行 remote-deploy.sh

- DB 备份：完成（`/opt/xiyu-bid/db-backups/winbid-057612930-api8080-*.sql.gz`）
- Flyway validate：通过
- Backend artifact 更新：完成
- 前端切换：完成（`/assets/index-kGBb_RQ1.js` 与 release 一致）
- 后端服务重启：`xiyu-bid-backend.service` active (running) since Thu 2026-07-02 10:13:18 CST

### deployed-release.json

```json
{
  "releaseId": "057612930-api8080",
  "activatedAt": "2026-07-02T02:11:47Z",
  "releaseDir": "/opt/xiyu-bid/releases/057612930-api8080",
  "frontendPublicDir": "/srv/www/xiyu-bid",
  "backendJarPath": "/opt/xiyu-bid/shared/backend/app.jar",
  "backendServiceName": "xiyu-bid-backend",
  "healthcheckUrl": "http://127.0.0.1:8080/actuator/health",
  "packageMetadata": {
    "releaseId": "057612930-api8080",
    "apiBaseUrl": "",
    "jarName": "bid-poc-1.0.3.jar",
    "builtAt": "2026-07-02T02:11:01Z",
    "sentryEnabled": true
  }
}
```

---

## 7. 部署验证

### 7.1 健康检查

```
✅ 健康检查通过 (第 1 次)
```

**所有 9 个组件 UP**（无 Kafka SDK readiness 延迟）：

| 组件 | 状态 |
|---|---|
| aiProvider | UP（doubao / deepseek-v3-2-251201 / apiKeyConfigured） |
| db | UP（MySQL / isValid()） |
| diskSpace | UP（40GB free / 100GB total） |
| jwt | UP（HMAC-SHA256 / 64 bytes / STRONG） |
| livenessState | UP |
| ping | UP |
| readinessState | UP |
| redis | UP（6.2.19） |
| sidecar | UP（http://localhost:8000 reachable） |

### 7.2 Smoke 测试（Mac 绕过 HTTP_PROXY）

| 接口 | HTTP | 期望 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 | UP | ✅ |
| `/actuator/health/readiness` | 200 | UP | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 路由正常 | ✅ |
| `GET /api/projects` | 403 | 需认证 | ✅ |
| `GET /api/integration/crm/health` | 401 | 需认证 | ✅ |
| `GET /`（前端） | 200 | 前端可达 | ✅ |
| `GET /login`（前端） | 200 | 前端可达 | ✅ |

**Smoke 全绿**（admin 密码未授予，按经验使用 400/403/401 替代验证策略）

### 7.3 前端 Sentry 激活验证（本次重点）

```bash
ssh jetty@172.16.38.78 'grep -c "afe598346bea591afeabcefe91562d9b" /srv/www/xiyu-bid/assets/index-*.js'
# 1  → ✅ Sentry DSN 已注入前端 JS
```

- ✅ Sentry DSN 字面量 `afe598346bea591afeabcefe91562d9b` 已注入前端 JS
- ✅ `environment=production` 已注入
- ✅ `tracesSampleRate=0.1` 已注入
- ⚠️ JS 中仍含 `"DSN not configured, Sentry disabled"` 字符串字面量 —— **这是 `src/sentry.js:54` 的 fallback 分支字符串**，由于 DSN 已注入，运行时 `if (!dsn || dsn.trim() === '')` 条件为 false，会跳过该分支执行 `Sentry.init()`。字符串字面量本身不影响功能

**结论**：前端 Sentry 已正确激活，将捕获 Vue 组件渲染错误、未捕获 Promise rejection、JavaScript 运行时错误。

---

## 8. GitHub 镜像同步

部署前 GitHub 镜像落后 8 个 commit，部署后执行同步：

```bash
bash scripts/sync-to-github.sh
```

同步结果：

| 仓库 | HEAD |
|---|---|
| Gitee main | `057612930b5bad86a8fde4637089c1a5b201387a` |
| GitHub main | `057612930b5bad86a8fde4637089c1a5b201387a` |

✅ 完全一致

---

## 9. 配置清理检查

```bash
sudo grep -E "SHOW_DETAILS|DEBUG|TRACE" /etc/xiyu-bid/backend.env
# 结果：MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always
```

`SHOW_DETAILS=always` 保留（沿用第 13/14/15 次决定，运维监控需要）。无新增临时调试配置。

---

## 10. 回滚信息

### 回滚姿态

- **回滚就绪**：不需要回滚，所有验证通过
- **回滚方式**：如需回滚，使用上一 release `88b99604e-api8080` 的 artifacts
- **DB 备份**：`/opt/xiyu-bid/db-backups/winbid-057612930-api8080-*.sql.gz`（本次无迁移，回滚不涉及 DB）
- **回滚步骤**：
  1. 恢复 backend jar：`/opt/xiyu-bid/releases/88b99604e-api8080/backend/app.jar` → `/opt/xiyu-bid/shared/backend/app.jar`
  2. 恢复前端：`/opt/xiyu-bid/releases/88b99604e-api8080/frontend/dist/*` → `/srv/www/xiyu-bid/`
  3. 重启服务：`sudo systemctl restart xiyu-bid-backend`
  4. 验证健康：`curl http://127.0.0.1:8080/actuator/health`

### 注意事项

- **前端 Sentry 回滚影响**：如回滚到上一 release，前端 Sentry 会自动禁用（`sentryEnabled: false`），不影响业务功能
- 后端 Sentry 始终通过 `SENTRY_DSN` 环境变量配置，不受 release 切换影响

---

## 11. 经验沉淀应用情况

本次部署应用了以下经验：

| # | 经验 | 应用情况 |
|---|---|---|
| 1 | Flyway 预检 3 步法 | ✅ 全部执行 |
| 2 | Kafka SDK readiness 延迟 | ✅ 等待循环就绪（本次第 1 次通过，无延迟） |
| 3 | 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空 |
| 4 | Smoke 测试 400/403/401 替代策略 | ✅ admin 密码未授予 |
| 5 | GitHub 镜像同步 | ✅ 部署后同步 |
| 6 | SHOW_DETAILS=always 保留决定 | ✅ 沿用历史决定 |
| 8 | systemctl sudo | ✅ `SYSTEMCTL_SUDO=true` |
| 11 | /tmp/migration-mysql/ 过时 | ✅ 通过 SQL 直查 flyway_schema_history |
| 16 | Mac HTTP_PROXY 502 | ✅ curl 加 `--noproxy '*'` 绕过代理 |

**新增经验**（本次首次应用）：

### 17. 前端 Sentry DSN 打包时注入

PR !1502 接入前端 Sentry 后，需要在打包时显式传入 `VITE_SENTRY_DSN` 环境变量才会激活。**仅打包脚本支持是不够的，部署命令必须显式传参**，否则前端会走 fallback 分支自动禁用。

- **打包命令模板**：
  ```bash
  RELEASE_ID="<commit>-api8080" \
  VITE_API_BASE_URL= \
  VITE_SENTRY_DSN="<dsn>" \
  VITE_SENTRY_ENVIRONMENT=production \
  VITE_SENTRY_TRACES_SAMPLE_RATE=0.1 \
  bash scripts/release/package-release.sh
  ```
- **验证方法**：
  1. 打包后检查 `release-metadata.json` 的 `sentryEnabled` 字段是否为 `true`
  2. 部署后 grep 前端 JS 是否包含 DSN 字面量
- **死代码识别**：JS 中存在 `"DSN not configured, Sentry disabled"` 字符串字面量属于源码 fallback 分支，DSN 已注入时不会执行该分支，不影响功能
- **回滚影响**：回滚到旧 release 会自动禁用前端 Sentry，不影响业务

---

## 12. 风险提示

1. **前端 Sentry 首次激活**：本次为前端 Sentry 首次生产激活，建议观察 Sentry dashboard 24-48 小时，确认错误上报正常、采样率合理
2. **DSN 暴露**：DSN 会作为字符串字面量打包进前端 JS，任何访问前端的用户都能在浏览器 devtools 中看到。这是 Sentry 客户端架构的预期行为（DSN 是 public identifier，权限通过 Sentry 项目配置控制）
3. **`SHOW_DETAILS=always` 保留**：生产环境暴露健康详情，如有安全收紧需求可改为 `never` 并重启后端
4. **Smoke 测试限制**：admin 密码未授予，登录流程未端到端验证

---

## 13. 部署确认清单

- [x] 早操三连通过
- [x] 基线确认（git status 干净，HEAD = origin/main）
- [x] 服务器现状确认（健康 UP，deployed-release.json 已读）
- [x] 增量 commit 检查（8 个 commit）
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（含 VITE_SENTRY_DSN 注入）
- [x] 产物校验通过（sentryEnabled=true）
- [x] 上传 + 部署成功
- [x] 健康检查通过（第 1 次，无 Kafka 延迟）
- [x] Smoke 测试全绿（7 项）
- [x] 前端 Sentry 激活验证通过
- [x] GitHub 镜像同步完成
- [x] 配置清理检查完成
- [x] 部署报告生成

---

## 14. 部署历史延伸

| # | 日期 | Release ID | 新增迁移 | 备注 |
|---|---|---|---|---|
| 30 | 2026-07-01 | `b962dc953` | 无 | CO-447 列表排序，第 30 次部署 |
| 31 | 2026-07-01 | `88b99604e` | V1125, V1126, V1127 | CO-439 admin-staff 导航权限、CO-445 CRM sales no、CO-446 OSS 用户 employee number |
| 32 | 2026-07-02 | `88b99604e` | 无 | CO-441 CA 借用审批修复（重打第 31 次 jar） |
| **33** | **2026-07-02** | **`057612930`** | **无** | **前端 Sentry DSN 首次注入 + CO-441 CA 借用审批修复 + 测试修复** |

---

**部署完成时间**：2026-07-02 10:13:18 CST（健康检查通过）
**报告生成时间**：2026-07-02 10:18 (Asia/Shanghai)
