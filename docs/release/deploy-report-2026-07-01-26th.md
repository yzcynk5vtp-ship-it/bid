# 第 26 次部署报告

> **部署日期**：2026-07-01
> **Release ID**：`330d3241a-api8080`
> **部署类型**：常规部署（无 Flyway 迁移）
> **部署结果**：✅ 成功

## 一、部署概览

| 项目 | 值 |
|---|---|
| Release ID | `330d3241a-api8080` |
| 激活时间 | 2026-07-01 09:07:48 CST |
| 上一次部署 | `261830696-api8080`（第 25 次，2026-07-01 07:04:42 CST） |
| 增量 commit | 14 个（含 PR !1436-!1441） |
| 新增迁移 | 无 |
| 部署耗时 | 约 6 分钟（含打包 22s + 上传 + 部署 + 验证） |
| 健康检查 | 第 1 次通过（无 Kafka readiness 延迟） |
| 回滚状态 | 未需要 |

## 二、基线信息

| 项目 | 值 |
|---|---|
| Worktree | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 任务分支 | `agent/trae/deploy-26th`（in-place 任务分支） |
| 部署 commit | `330d3241ae74a64cdc9e0e3d8493196afd692d43` |
| GitHub 镜像 | ✅ 已同步（部署后 Gitee/GitHub main 完全一致） |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 后端服务 | `xiyu-bid-backend`（systemd） |
| 后端端口 | 8080 |

## 三、PR 列表

| PR | 描述 |
|---|---|
| !1436 | refactor: 统一 ZIP 打包器文件名处理与去重逻辑 + Sentry 双 bug 修复 |
| !1437 | docs(release): 第 25 次部署报告 |
| !1438 | fix(test): 修复 ca.spec.js 维护声明不完整导致 doc-governance 检查失败 |
| !1439 | fix(ca): 修复 CA 详情页登记归还入口命名不统一问题 (CO-433) |
| !1440 | feat(CO-423): 档案详情抽屉移除中标结果字段 |
| !1441 | test(tender): 补充 TenderDeduplicationPolicy 消息格式化方法的单元测试 |

## 四、改动范围

- **20 个文件变更**（+1015 / -210）
- **无 Flyway 迁移**（纯业务修复 + 重构 + 测试补充）
- 涉及后端 Java、前端 Vue、测试用例、文档

### 重点修复

- **CO-433 CA 详情页登记归还入口命名统一**：将 `CADetailDrawer.vue` 重构为 `CADetailDialog.vue`，统一登记/归还入口命名，修复业务逻辑；新增 `useCaBorrowEligibility` composable 抽取借用资格判断
- **CO-423 档案详情抽屉移除中标结果字段**：从 `ArchiveDetailDrawer.vue` 移除中标结果字段显示
- **ZIP 打包器统一**：统一 `StreamingZipPackager` 文件名处理与去重逻辑，新增 `ZipEntryDeduplicator` 工具类，`CaseExportPolicy` / `PerformanceZipExporter` / `PersonnelZipExporter` 同步适配
- **Sentry 双 bug 修复**：修复 Sentry 上报的两个生产错误
- **测试补充**：补充 `TenderDeduplicationPolicy` 消息格式化方法单元测试；补充 `CABorrowDialog` / `CADetailDialog` / `CAReturnDialog` / `useCaBorrowEligibility` 测试

## 五、Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - 187 migrations, all checksums match |
| Step 2: DB 已应用版本 | 最新 V1123（add qualification manage permission，2026-06-30 21:29:36 应用） |
| Step 3: remote-deploy 内置 validate | ✅ 通过（仅 pending 新迁移为预期状态，本次无新迁移） |

## 六、部署步骤

1. 早操三连（`source dev-env.sh` + `sync-env.sh` + `check-git-wrapper.sh`）— 锚点分支 ff-only 同步
2. 创建任务分支 `agent/trae/deploy-26th`
3. 确认基线：`git status` 干净，HEAD=`330d3241a`，GitHub 落后 14 commit
4. 服务器现状探测：当前 Release `261830696-api8080`，健康 UP
5. Flyway 预检 3 步：全部通过
6. 本地打包：`RELEASE_ID="330d3241a-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh`
   - BUILD SUCCESS（22.4s）
   - jar 内 Flyway 迁移版本无重复 ✅
7. 产物校验：186 个 V*.sql，前端入口 `assets/index-DkKywp4g.js`
8. 上传 + 部署：`scp` + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）
   - Flyway validate 通过
   - Backend artifact 更新
   - Service 重启成功（PID 30341）
   - Frontend 一致性验证通过
9. 健康检查：第 1 次通过（09:12:32）
10. Smoke 测试：7/7 全通过
11. GitHub 镜像同步：`bash scripts/sync-to-github.sh`，两边 main 完全一致

## 七、验证结果

### 后端健康检查

| 组件 | 状态 |
|---|---|
| aiProvider | UP（doubao / deepseek-v3-2-251201） |
| db | UP（MySQL） |
| diskSpace | UP（free 44.8GB） |
| jwt | UP（HMAC-SHA256, STRONG） |
| livenessState | UP |
| readinessState | UP |
| redis | UP（6.2.19） |
| sidecar | UP（reachable） |
| ping | UP |

### Smoke 测试

| # | 端点 | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| 1 | `GET /actuator/health` | 200 | 200 | ✅ |
| 2 | `GET /actuator/health/readiness` | 200 | 200 | ✅ |
| 3 | `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| 4 | `GET /api/projects`（无认证） | 403 | 403 | ✅ |
| 5 | `GET /api/integration/crm/health` | 401 | 401 | ✅ |
| 6 | `GET /`（前端首页） | 200 | 200 | ✅ |
| 7 | `GET /login` | 200 | 200 | ✅ |
| 8 | 前端入口一致性 | `index-DkKywp4g.js` | `index-DkKywp4g.js` | ✅ |

> **登录 Smoke 跳过说明**：admin 密码未授予，完整登录 smoke 无法完成，使用 400/403/401 替代验证接口路由（第 6 次起固化的替代策略）。

## 八、GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 部署前状态 | GitHub main 落后 Gitee main 14 个 commit |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| 同步结果 | ✅ 两边 main 完全一致（`330d3241a`） |

## 九、回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上一可用 Release | `261830696-api8080`（第 25 次） |
| 上一 Release 目录 | `/opt/xiyu-bid/releases/261830696-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-330d3241a-*.sql.gz` |
| 回滚命令 | `cp /opt/xiyu-bid/releases/261830696-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |

## 十、经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| 1. Flyway 预检 3 步法 | ✅ 全部执行（validate + DB 版本对比 + remote-deploy 内置） |
| 2. Readiness 延迟容忍 | ✅ 本次无延迟（第 1 次通过），但仍按 SOP 等待 |
| 3. 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空，`check:frontend-api-base` 通过 |
| 4. Smoke 测试 400/403/401 替代策略 | ✅ 全部使用替代验证 |
| 5. GitHub 镜像同步 | ✅ 部署前落后 14 commit，部署后已同步 |
| 6. 临时调试配置清理 | ⚠️ `SHOW_DETAILS=always` 保留（第 13-15 次用户决定保留，运维监控需要） |
| 8. systemctl sudo 权限 | ✅ `SYSTEMCTL_SUDO=true`（默认值，PR !1324 已修复） |
| 16. Mac HTTP_PROXY 502 | ✅ 所有 Smoke 通过 SSH 内部访问，未受 Mac 代理影响 |

## 十一、风险提示

1. **`SHOW_DETAILS=always` 仍保留**：生产健康端点暴露详情，运维监控需要。如后续需收紧安全，可改为 `never` 并重启后端。
2. **CA 模块组件重构**：`CADetailDrawer.vue` → `CADetailDialog.vue` 涉及前端交互变更，建议 UAT 阶段重点验证 CA 详情查看/登记/归还流程。
3. **ZIP 打包器重构**：文件名处理与去重逻辑统一，建议验证项目档案导出、人员证书导出、业绩导出的 ZIP 文件命名是否符合预期。

## 十二、部署确认清单

- [x] 早操三连执行完成
- [x] 基线确认（git status 干净，HEAD = origin/main）
- [x] 服务器现状探测（健康 UP，Release ID 正确）
- [x] Flyway 预检 3 步全部通过
- [x] 本地打包成功（BUILD SUCCESS，jar 内迁移无重复）
- [x] 产物校验通过（186 个 V*.sql，前端入口一致）
- [x] 上传 + 部署成功（Flyway validate 通过，service 重启成功）
- [x] 健康检查通过（所有组件 UP，readinessState UP）
- [x] Smoke 测试 7/7 全通过
- [x] GitHub 镜像同步完成（两边 main 完全一致）
- [x] 配置清理检查完成（`SHOW_DETAILS=always` 按用户决定保留）
- [x] 部署报告生成

---

**部署执行**：Trae Agent（主工作区）
**部署确认**：待用户确认
