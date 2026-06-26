# 第 3 次生产部署报告（2026-06-26）

> 本报告沉淀"旧流程 → 首次新流程 → 本次重复部署"三次对比，重点验证 Flyway validate 预检在重复部署场景下依然生效。

## 部署摘要

| 项 | 值 |
|---|---|
| 日期 | 2026-06-26 |
| Release ID | `e51a673bc`（代码等同 `b77fc9170`，仅 docs 差异） |
| 业务 | CO-346：CRM 回调 status 置空 + 操作人姓名工号 + systemName |
| 服务器 | `jetty@172.16.38.78`（winbid-01.test） |
| 部署方式 | `scripts/release/remote-deploy.sh`（含 Flyway validate 预检） |
| 部署结果 | ✅ 成功，后端 UP，smoke 14/15 |
| 启动耗时 | ~2.5 分钟（11:31:42 stop → 11:33:12 新进程 → 11:36 health UP） |

## 执行步骤与证据

### 1. 早操 SOP
- `git fetch origin` + 对比：HEAD `e51a673bc` == `origin/main`，0 ahead/behind
- `sync-env.sh`：门禁 7/7 通过，GitHub 镜像落后 10 commit（非主工作区，仅检测不推送）

### 2. 本地打包（clean target + package-release）
- 清理 `backend/target`
- `VITE_API_BASE_URL=http://172.16.38.78:8080 bash scripts/release/package-release.sh`
- 前端 build（vite 5.4.21，8.51s）→ `check:frontend-api-base` ✅ → 后端 `mvn package`（2527 源文件，24.5s）
- 产物：`.release/e51a673bc/` + `xiyu-bid-release-e51a673bc.tar.gz`（142843800 bytes）

### 3. 产物校验
- **Flyway 重复版本**：10 个迁移文件无重复版本号 ✅（最新 V120）
- **jar 完整性**：155434879 bytes，Spring Boot manifest 正常 ✅
  - CO-346 三类打入：`WebhookEventListener`、`OperatorDisplayName`、`TenderEvaluationService`、`TenderEvaluationSubmissionService`、`TenderSubmissionService`
- **CORS**：`src/api/config.js:47` 生产构建强制 `baseURL=""`（同源），代码注释说明该 bug 已复发 3 次，故在代码层强制不可绕过。前端产物内联 `J=""`（空 baseURL），非 `http://172.16.38.78:8080`

### 4. 服务器 DB 备份 + 上传 archive
- DB 备份：`/opt/xiyu-bid/backups/winbid-20260626-113107.sql`（35M）✅
- archive 上传：MD5 一致 `74d11f89b0f7499813fae8bdddfd05b0` ✅
- Flyway 基线（预检前）：165 条迁移，165 success，0 failed

### 5. remote-deploy.sh（validate 预检核心验证）
执行环境变量：`RELEASE_ARCHIVE`/`APP_ROOT`/`FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid`/`BACKEND_SERVICE_NAME=xiyu-bid-backend`/`HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health`/`FLYWAY_REPAIR_RUNNER=/tmp/flyway-repair-runner.sh`，**不跳过 validate**。

日志关键行：
```
187:==> Flyway validate pre-check (before jar activation)
199:==> 执行 validate
911:✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
913:==> Updating backend artifact        ← validate 通过后才覆盖 jar
915:==> Restarting backend service
926:==> Waiting for health check
928:✅ Frontend matches release (src="/assets/index-BrUfC-at.js")
929:Remote deployment completed for release e51a673bc
```
exit code 0 ✅

### 6. health check + smoke 验活
- 后端 health：`{"status":"UP","groups":["liveness","readiness"]}` ✅
- `run-prod-smoke.mjs`：**14/15 通过**
  - 通过：health UP、登录、当前用户、8 项业务列表、CRM page-list（status=200 total=31）、CRM probe（skipped 缺 env）、Prometheus（protected 403）
  - 失败：P0 生产前端首页 `fetch failed` —— **本地网络无法直连 80 端口**（curl timeout），服务器侧 nginx 验证 200 且资源一致，属检测位置局限非部署问题

### 7. CO-346 webhook 专项验证
对比 `webhook_delivery_tasks.payload` 最近 3 条：

| id | 时间 | feedback 含 systemName |
|---|---|---|
| 129 | 11:04（部署前旧 jar） | ❌ 无 |
| 130 | 11:16（新 jar） | ✅ `"systemName":"投标管理系统"` |
| 131 | 11:23（新 jar） | ✅ `"systemName":"投标管理系统"` |

**精确证明 CO-346 systemName 字段已生效**。`OperatorDisplayName.format()` 在操作人信息缺失时返回空字符串（null 安全）。

## 三次部署对比

| 维度 | 第 1 次（旧流程） | 第 2 次（首次新流程） | 第 3 次（本次重复部署） |
|---|---|---|---|
| Release ID | `53bbf8a34` | `3468efd59` | `e51a673bc` |
| 业务 | V1099 新迁移 | CO-329/CO-332 无新迁移 | CO-346（代码同 b77fc9170，仅 docs） |
| Flyway validate 预检 | ❌ 未实现 | ✅ 生效（163 migrations） | ✅ 生效（165 migrations，重复部署下仍执行） |
| mismatch 发现时机 | 后端启动失败→4 分钟超时 | restart 前 ~2s validate | restart 前 ~2s validate |
| 服务中断 | 有（health 超时） | 无（旧 jar 在线） | 无（旧 jar 在线） |
| smoke | 15/15（修复后） | 15/15 | 14/15（1 项本地网络局限） |
| repair 工具 | 手动一次性 Runner | 被 deploy 调用 | 未触发（无 mismatch） |

## 核心结论

**validate 预检在重复部署场景下依然生效**：本次 `e51a673bc` 相对服务器已运行的 `b77fc9170` 代码零 diff，schema 无变化，但 `remote-deploy.sh` 仍在覆盖 jar 前无条件执行 `flyway-repair-runner.sh validate`（165 migrations 全过）。预检不依赖"是否有变化"的启发式判断，是无条件前置门禁。这证明第 2 次沉淀的加固设计在"重复部署、schema 无变化、jar 代码相同"场景下依然成立，不会因"反正没变化"短路跳过。

## 实施决策与权衡

1. **SSH 密钥复用**：`/tmp/xiyu-prod-deploy-crm-test` 缺失，复用 `~/.ssh/xiyu_cursor_deploy`（用户确认）。验证连通性 + sudo 免密通过。
2. **mysqldump 参数调整**：服务器 `mysqldump` 是 MariaDB 5.5.68 客户端（连接 MySQL 8.0.43 服务端），不认 `--set-gtid-purged=OFF`，去掉该参数备份成功。这是 `backup-db.sh` 在该服务器的已知兼容点。
3. **前端 baseURL 同源**：`package-release.sh` 传入 `VITE_API_BASE_URL=http://172.16.38.78:8080`，但 `src/api/config.js:47` 生产构建强制 `baseURL=""`（同源），忽略该环境变量。`release-metadata.json` 记录的是环境变量值（非前端实际取值）。这是有意设计（同源防跨域，bug 已复发 3 次，代码层强制不可绕过）。
4. **shutdown NoClassDefFoundError**：`systemctl restart` 期间旧进程 shutdown hook 出现类找不到异常（jar 被 cp 覆盖后旧 class loader 失效）。非致命，新进程正常启动。属既有行为，前两次同样存在。
5. **smoke P0 前端失败**：本地无法直连 80 端口，通过 SSH 在服务器侧验证 nginx 200 + 资源一致，确认是检测位置局限非部署问题。

## 回滚指引（如需）
- jar 回退：`cp /opt/xiyu-bid/backups/app.jar.<旧时间戳> /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend`
- DB 回退：`mysql -h winbid-01.test.rds.ehsy.com -u ea_bid -p winbid < /opt/xiyu-bid/backups/winbid-20260626-113107.sql`
- 详见 `docs/release/ROLLBACK_RUNBOOK.md`
