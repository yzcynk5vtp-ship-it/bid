# 第 31 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署序号 | 第 31 次 |
| 部署日期 | 2026-07-01 23:08 (CST) |
| Release ID | `d32227acf-api8080` |
| 目标服务器 | `172.16.38.78` (winbid-01.test) |
| 前一次部署 | `b962dc953-api8080`（第 30 次，2026-07-01 11:47） |
| 部署类型 | 纯代码部署（无 Flyway 迁移） |
| 健康检查 | ✅ 第 1 次尝试即通过（无 Kafka readiness 延迟） |
| 回滚状态 | 未需要 |

## 基线信息

| 项目 | 值 |
|---|---|
| 主工作区 | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae-init`（锚点分支，ff-only 同步） |
| 部署 commit | `d32227acf478bbbcf4cb4ef6f00d52328561578d` |
| 前一次 commit | `b962dc953`（第 30 次） |
| 增量 commit 数 | 25 个 |
| Flyway 迁移文件变更 | 无 |
| GitHub 镜像同步 | ✅ 部署后已同步（253 commit → 0） |

## PR 列表（25 个增量 commit）

| PR | Commit | 描述 |
|---|---|---|
| !1491 | `95bf484a7` | docs(release): 第 30 次部署报告 |
| !1492 | `b487304b9` | fix(CO-454): el-form-item 加 :required 绑定控制星号显示 |
| !1493 | `cf0efa13d` | fix(CO-435): 修复CA编辑空密码被前端rules拦截 |
| !1494 | `11a79daaa` | fix(crm): crmSalesNo为空时用username作为salesNo生成专属token |
| !1495 | `3215b1347` | fix(organization): UserEnabledDetector 修正 activationStatus 字段名 |
| !1496 | `7ad7c47c6` | fix(performance): 修复导出ZIP和详情页附件下载报"URI with undefined scheme"错误 |
| !1497 | `3ff8a49b9` | fix(CO-442): 业绩附件支持 Word/Excel 上传 |
| !1498 | `90d10e99d` | fix(dashboard): 修复投标专员登录后工作台指标 403 |
| !1499 | `9f5b930a7` | enable-wecom-notification-switch: Automation skill-progression-map update |
| !1500 | `a080c1cc4` | fix(bid-review): 任务看板标书审核卡片点击跳转到标书制作页 |
| !1501 | `f9354656b` | CO-455: 项目立项提交时招标文件字段必传 |
| !1502 | `f020220b9` | feat(frontend): 接入 Sentry 前端错误追踪 |
| !1503 | `bf3fd72bd` | feat(initiation): 项目立项选择有保证金时缴纳截止日期必填 (CO-457) |
| !1504 | `3d4a29b09` | fix(notification): 通知跳转失效根因修复 + UX 优化 |
| !1505 | `d32227acf` | fix(project): 统一负责人分配 null 语义，修复驳回重审时辅助人员丢失 (CO-456) |

## 改动范围

本次部署为纯代码部署，无 DB 迁移文件变更。主要改动涵盖：

1. **CO-456**: 项目负责人分配 null 语义统一，修复驳回重审时辅助人员丢失
2. **CO-457**: 项目立项选择有保证金时缴纳截止日期必填
3. **CO-455**: 项目立项提交时招标文件字段必传
4. **CO-454**: el-form-item 加 :required 绑定控制星号显示
5. **CO-442**: 业绩附件支持 Word/Excel 上传
6. **CO-435**: 修复 CA 编辑空密码被前端 rules 拦截
7. **通知跳转修复**: 通知跳转失效根因修复 + UX 优化
8. **Dashboard 403 修复**: 投标专员登录后工作台指标 403
9. **Sentry 前端错误追踪**: 接入 Sentry 前端监控
10. **企微通知开关**: 添加企微通知启用开关 @ConditionalOnProperty
11. **CRM 修复**: crmSalesNo 为空时用 username 作为 salesNo 生成专属 token
12. **组织修复**: UserEnabledDetector 修正 activationStatus 字段名
13. **业绩修复**: 修复导出 ZIP 和详情页附件下载报 "URI with undefined scheme" 错误
14. **任务看板修复**: 标书审核卡片点击跳转到标书制作页

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - 191 migrations validated, all checksums match |
| Step 2: DB 版本对比 | ✅ DB 最新版本 V1127（2026-07-01 15:13:50 安装），无新迁移 |
| Step 3: remote-deploy 内置 | ✅ VALIDATE OK - all checksums match |

## 部署步骤

1. ✅ 早操三连（sync-env.sh + check-git-wrapper.sh）
2. ✅ 确认基线（HEAD = d32227acf = origin/main）
3. ✅ 服务器现状检查（前次 release b962dc953，health UP）
4. ✅ Flyway 预检 3 步法通过
5. ✅ 本地打包（`RELEASE_ID=d32227acf-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh`）
6. ✅ 产物校验（jar 内 190 个迁移文件无重复，前端入口 `assets/index-ce3I-2H1.js`）
7. ✅ 上传 + 部署（scp + remote-deploy.sh，SYSTEMCTL_SUDO=true）
8. ✅ 健康检查（第 1 次尝试即通过）

## 验证结果

### 后端健康检查

```
status: UP
components:
  - aiProvider: UP (doubao, deepseek-v3-2-251201)
  - db: UP (MySQL)
  - diskSpace: UP (43GB free)
  - jwt: UP (HMAC-SHA256, STRONG)
  - livenessState: UP
  - ping: UP
  - readinessState: UP
  - redis: UP (6.2.19)
  - sidecar: UP (http://localhost:8000)
```

### Smoke 测试（SSH 内部访问，绕过 Mac 代理）

| 测试项 | 预期 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | HTTP 200 | ✅ |
| `/actuator/health/readiness` | 200 UP | HTTP 200 | ✅ |
| `/api/auth/login` (POST {}) | 400 (验证错误) | HTTP 400 | ✅ |
| `/api/projects` | 403 (需认证) | HTTP 403 | ✅ |
| `/api/integration/crm/health` | 401 (需认证) | HTTP 401 | ✅ |
| 前端首页 `/` | 200 | HTTP 200 | ✅ |
| 前端登录页 `/login` | 200 | HTTP 200 | ✅ |
| 前端 assets 入口 | index-ce3I-2H1.js | index-ce3I-2H1.js | ✅ |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 部署前落后 | 253 commit |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| 同步结果 | ✅ Gitee main = GitHub main = `d32227acf` |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 前次 release | `b962dc953-api8080` |
| 前次 release 目录 | `/opt/xiyu-bid/releases/b962dc953-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-d32227acf-<timestamp>.sql.gz` |
| 回滚方式 | 恢复前次 jar + 前端 assets（无 DB 迁移，无需回滚 DB） |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ 执行（validate + DB 版本对比 + remote-deploy 内置） |
| #2 Kafka SDK readiness 延迟 | ✅ 未出现（第 1 次尝试即通过） |
| #3 生产前端同源构建 | ✅ VITE_API_BASE_URL= 显式设空 |
| #4 Smoke 测试限制 | ✅ 用 400/403/401 替代验证（admin 密码未知） |
| #5 GitHub 镜像同步 | ✅ 部署后同步（253 → 0） |
| #6 临时调试配置清理 | ⚠️ SHOW_DETAILS=always 保留（用户第 13/14/15 次决定保留） |
| #8 systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true（默认值） |
| #16 Mac HTTP_PROXY 502 | ✅ 通过 SSH 内部访问绕过代理 |

## 风险提示

1. **SHOW_DETAILS=always 保留**：服务器 `/etc/xiyu-bid/backend.env` 中 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍保留（用户第 13/14/15 次决定保留，运维监控需要）。如后续需收紧安全，可改为 `never` 并重启后端。
2. **无 DB 迁移**：本次部署为纯代码部署，无 DB 回滚风险。

## 部署确认清单

- [x] 早操三连完成
- [x] 基线确认（HEAD = origin/main）
- [x] Flyway 预检 3 步法通过
- [x] 本地打包成功（jar + 前端 assets）
- [x] 产物校验通过
- [x] 上传 + 部署成功
- [x] 健康检查 UP（第 1 次尝试通过）
- [x] Smoke 测试 8/8 通过
- [x] GitHub 镜像同步完成
- [x] 部署报告生成

---

**部署人**: Trae Agent  
**部署时间**: 2026-07-01 23:08 CST  
**Release ID**: `d32227acf-api8080`
