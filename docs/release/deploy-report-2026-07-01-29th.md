# 第 29 次生产部署报告 — 2026-07-01

## 部署概览

| 项目 | 值 |
|---|---|
| 部署序号 | 第 29 次 |
| 部署日期 | 2026-07-01 |
| Release ID | `20a82ecb0-api8080` |
| 上一版本 | `8759f4263-api8080`（第 28 次，2026-07-01 07:12:10 UTC） |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 部署人 | trae agent |
| 部署结果 | ✅ 成功 |
| 健康检查 | ✅ 第 1 次即通过（17:20 CST，无 Kafka 延迟） |
| Smoke 测试 | ✅ 7/7 全通过 |
| 回滚状态 | 未需要，ready |

## 基线信息

- **本地分支**：`agent/trae/deploy-28th-report`（早操后 HEAD 与 origin/main 对齐）
- **本地 HEAD**：`20a82ecb089ba35570f533e63e01e025b336c9ef`
- **Gitee origin/main**：`20a82ecb0`（!1473 fix(CO-441): 回归修复 v2 — TRACKING+projectManagerId=null 显示「分配」而非「转派」）
- **GitHub 镜像**：部署前落后 25 commit，部署后已同步一致
- **增量 commits**：24 个（从 `8759f4263` 到 `20a82ecb0`）
- **变更范围**：backend + frontend 多模块

## PR 列表（去重后的核心 PR）

| PR | 类型 | 说明 |
|---|---|---|
| !1467 | fix(performance) | 修复业绩导出 Excel 空白和 ZIP 格式错误，支持按筛选/勾选导出 (CO-445) |
| !1468 | fix(performance) | CO-446 删除业绩管理列表相似业绩功能 |
| !1471 | fix(permission) | CO-438 Rework - menuPermissions 为空时回退到角色白名单 |
| !1472 | fix(CO-450) | 修复保证金管理导出返回JSON而非Excel的问题 |
| !1473 | fix(CO-441) | 回归修复 v2 — TRACKING+projectManagerId=null 显示「分配」而非「转派」 |
| !1474 | fix(admin) | OSS用户role_id为NULL时保存CRM工号失败 |
| !1475 | fix(organization) | UserEnabledDetector 兼容 status 字段字符串形态 |
| !1476 | fix(notification) | createdBy null fallback + 删除冗余 WeComListener 消除 delivery_task 唯一键冲突 |
| !1477 | fix(CAImportDialog) | 批量导入弹窗关闭后状态未重置 (CO-449) |
| !1478 | fix(ca) | CO-440 修复 REQUIRES_NEW + try-catch 反模式导致 UnexpectedRollbackException |
| !1479 | fix(bid) | CO-443 标书文件 category 枚举不匹配导致提交审核闸门误判 |

> 注：增量 commit 列表含多次 revert/re-push，上述为去重后的实际 PR 列表。

## 改动范围

- **后端**：
  - 业绩管理：修复导出 Excel 空白 + ZIP 格式错误，支持按筛选/勾选导出，删除"相似业绩"功能
  - 权限治理：menuPermissions 为空时回退到角色白名单（CO-438 Rework）
  - 保证金管理：修复导出返回 JSON 而非 Excel 的问题 (CO-450)
  - 标讯分配：CO-441 回归修复 v2 — TRACKING+projectManagerId=null 显示「分配」而非「转派」
  - OSS 用户：role_id 为 NULL 时保存 CRM 工号失败修复
  - 组织同步：UserEnabledDetector 兼容 status 字段字符串形态
  - 通知系统：createdBy null fallback + 删除冗余 WeComListener 消除 delivery_task 唯一键冲突
  - CA 证书：CO-440 修复 REQUIRES_NEW + try-catch 反模式导致 UnexpectedRollbackException
  - 标书审核：CO-443 标书文件 category 枚举不匹配导致提交审核闸门误判

- **前端**：
  - 业绩管理：导出功能修复 + 删除相似业绩入口
  - CA 批量导入弹窗关闭后状态未重置 (CO-449)
  - 标讯分配显示逻辑调整（分配/转派文案）

- **数据库迁移**：
  - 无新增迁移（DB 版本保持 V1127）

## Flyway 预检 3 步结果

1. **服务器 validate**：✅ 通过（191 migrations, checksums match）
2. **DB 版本对比**：✅ DB 最新 V1127，无新增迁移
3. **remote-deploy 内置 validate**：✅ 通过

## 部署步骤

1. 早操三连：`source dev-env.sh` + `sync-env.sh` + `check-git-wrapper.sh` ✅
2. 确认基线：git status 干净，HEAD = origin/main ✅
3. 服务器现状：deployed-release.json + health + 增量 commit ✅
4. Flyway 预检 3 步 ✅
5. 本地打包：`RELEASE_ID=20a82ecb0-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh` ✅
6. 产物校验：jar 内迁移文件无重复版本 ✅
7. 上传 + 部署：scp + remote-deploy.sh（SYSTEMCTL_SUDO=true） ✅
8. 健康检查：第 1 次即通过（无 Kafka 延迟） ✅
9. Smoke 测试：7/7 全通过 ✅
10. GitHub 镜像同步：已同步一致 ✅
11. 配置清理检查：`SHOW_DETAILS=always` 保留（运维监控需要） ⚠️

## 验证结果

### 健康检查（内部访问）

```json
{
  "status": "UP",
  "components": {
    "aiProvider": {"status": "UP", "provider": "doubao", "model": "deepseek-v3-2-251201"},
    "db": {"status": "UP", "database": "MySQL"},
    "diskSpace": {"status": "UP"},
    "jwt": {"status": "UP"},
    "livenessState": {"status": "UP"},
    "ping": {"status": "UP"},
    "readinessState": {"status": "UP"},
    "redis": {"status": "UP", "version": "6.2.19"},
    "sidecar": {"status": "UP", "url": "http://localhost:8000"}
  }
}
```

### 迁移应用验证

本次无新增迁移，DB 版本保持 V1127。

### Smoke 测试（外部访问，绕过 Mac 代理）

| 端点 | 预期 | 实际 | 状态 |
|---|---|---|---|
| `/actuator/health` | 200 | 200 | ✅ |
| `/actuator/health/readiness` | 200 | 200 | ✅ |
| `/api/auth/login` (空密码) | 400 | 400 | ✅ |
| `/api/projects` | 403 | 403 | ✅ |
| `/api/integration/crm/health` | 401 | 401 | ✅ |
| `/` (前端首页) | 200 | 200 | ✅ |
| `/assets/index-*.js` | 匹配 | `index-CS6K7a4v.js` | ✅ |

## GitHub 同步

- **部署前**：GitHub 镜像落后 25 commit
- **部署后**：`bash scripts/sync-to-github.sh` 已同步
- **验证**：两边 main HEAD 均为 `20a82ecb089ba35570f533e63e01e025b336c9ef`

## 回滚信息

- **回滚脚本**：无新增迁移，沿用 V1127 回滚脚本 `U1127__backfill_oss_user_employee_number.sql`
- **回滚风险**：回滚后本次修复的 10+ 个 bug 会复发（CO-438/440/441/443/445/446/449/450 等）
- **数据库备份**：`/opt/xiyu-bid/db-backups/winbid-20a82ecb0-api8080-*.sql.gz`
- **前端 artifact**：`.release/8759f4263-api8080/`
- **后端 artifact**：`.release/8759f4263-api8080/backend/app.jar`

## 经验沉淀应用情况

本次部署遵循 `xiyu-server-deploy` skill 16 条经验：

1. **Flyway 预检 3 步法**：全部通过 ✅
2. **Readiness 延迟恢复**：未出现（第 1 次即通过） ✅
3. **生产前端同源构建**：`VITE_API_BASE_URL=` 显式设空 ✅
4. **Smoke 测试限制**：admin 密码未知，使用 400/403/401 替代验证 ✅
5. **GitHub 镜像同步**：部署后已同步 ✅
6. **临时调试配置清理**：`SHOW_DETAILS=always` 保留（运维监控需要） ⚠️
7. **幂等迁移设计**：无新增迁移 ✅
8. **systemctl sudo 权限**：`SYSTEMCTL_SUDO=true` 已配置 ✅
9. **git.properties commit id**：未检查（非关键）
10. **破坏性 schema 变更**：无新增迁移 ✅
11. **服务器 /tmp/migration-mysql/ 目录过时**：未依赖 info 输出，直接查 SQL ✅
12. **rollback 脚本命名规范**：无新增迁移 ✅
13. **前端目录权限**：未涉及（前端已存在）
14. **macOS `._*` 残留文件**：`COPYFILE_DISABLE=1` 打包时已设置 ✅
15. **Flyway 防护体系**：15 项防护全部生效 ✅
16. **Mac HTTP_PROXY 502**：使用 `--noproxy '*'` 绕过代理 ✅

## 风险提示

- **SHOW_DETAILS=always**：生产环境暴露健康详情，后续可收紧为 `never`
- **无新增迁移但代码改动量大**：24 个 commit 涉及 10+ 个功能模块，回滚成本较高
- **notification delivery_task 唯一键冲突**：本次通过删除冗余 listener 解决，需观察后续是否还会出现

## 部署确认清单

- [x] 早操三连执行完毕
- [x] Flyway 预检 3 步全部通过
- [x] 本地打包产物校验通过
- [x] 远程部署成功
- [x] 健康检查通过
- [x] Smoke 测试 7/7 全通过
- [x] GitHub 镜像已同步
- [x] 部署报告已生成
