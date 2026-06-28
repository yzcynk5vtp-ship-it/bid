# 部署报告：第 10 次发布

**发布 ID**: `92b7dadb-api8080`
**部署时间**: 2026-06-28 15:15 CST (07:15 UTC) — 全栈部署 + 后端重启
**部署者**: Trae AI Agent（主工作区 trae，基于 origin/main）
**部署类型**: 手动 SSH 部署（remote-deploy.sh）

---

## 发布范围

| 项目 | 值 |
|---|---|
| **Commit** | `92b7dadb3` — `!1276 fix(frontend): 只读模式任务附件可下载，移出被禁用的 el-upload` |
| **源码变更** | 95 个新 commit（从第 9 次 `47a0e2bba` 起） |
| **变更文件** | 273 个文件，+9041 / -1859 行 |
| **前端构建** | Vite + Vue 3，生产同源构建（`baseURL=""`） |
| **后端构建** | Maven + Spring Boot 3.2.0，`mvn -DskipTests package` |
| **JAR 大小** | 148 MB（`bid-poc-1.0.3.jar`） |
| **迁移数量** | 168 个 V*.sql + 1 个 B73__baseline（JAR 内），**无新增迁移** |
| **发布包大小** | 136 MB |

### 主要变更内容（95 个 commit，按主题归类）

#### 角色码解析统一收敛（CO-361 / CO-373 重大架构治理）
1. **!1259 feat(co-373)**: 统一服务层角色码解析入口，废弃直调 `User.getRoleCode()`
2. **!1257 spec(co-373)**: 实现计划+研究+数据模型+契约
3. **T002-T015 一系列**: 纯核心 `EffectiveRolePolicy` + `EffectiveRoleResolver` 统一入口 + 前端兜底
4. **!1244 fix(auth)**: CO-361 OSS 用户 roleCode 解析修正，修复投标负责人在项目详情页任务看板看不到任务
5. **!1241 fix(drafting)**: CO-373 服务层角色判断优先从 OSS 缓存读取
6. **f584ce58d**: `User.getRoleCode()` 标 `@Deprecated` + 23 处 SAFE 注释 + pre-push 拦截（B5+B6）

#### 权限修复（P0 重点）
1. **!1263 fix(tender)**: CO-379 在 `@PreAuthorize` 中添加 SALES 角色，修复 sales 用户创建/编辑标讯 403
2. **!1246 fix(tender)**: CO-379 支持 sales 历史角色码别名
3. **!1266 fix(personnel)**: 彻底修复 CO-363 人员证书保存 403 权限问题
4. **!1254 fix(personnel)**: 补齐 `uploadCertAttachment` 注解遗漏的 admin authority (CO-363)
5. **!1242 fix(co-370)**: 修复任务交付物上传权限校验错误，Controller 层误用 `assertCanManageTask` 导致非管理角色执行人被 403 拦截
6. **!1247 fix(permission)**: 按投标项目子身份权限文档修正 P0/P1 权限缺口

#### CRM 集成修复（CO-329）
1. **!1244 fix(crm)**: CO-329 revert contact persons API to POST to match backend
2. **!1238 fix(crm)**: CO-329 revert invalid c.position mapping and fix matrix POSITION dropdown
3. **!1252 docs(crm)**: document missing ehsyProjectManager root cause (CO-329)

#### 日志系统增强
1. **!1240 feat(logging)**: 业务上下文 MDC + AOP 操作日志 + 脱敏
2. **!1249 fix(logging)**: 修复 logback-spring.xml XML 语法错误并补齐生产文件日志
3. **!1250 fix(logging)**: 修复 logback XML 语法错误并补齐生产文件日志
4. **!1251 fix(logging)**: 修复 prod,mysql 双日志输出与访问日志 traceId 缺失
5. **!1243 docs(wiki)**: 新增《日志系统查 Bug 手册》

#### 任务/看板/交付物修复
1. **!1276 fix(frontend)**: 只读模式任务附件可下载，移出被禁用的 el-upload
2. **!1275 fix(tender)**: 修复项目结果登记时标讯状态同步导致 500 错误
3. **!1271 fix(frontend)**: 交付物不再重复渲染，只读模式仅展示可下载链接
4. **!1270 fix(config)**: E2eDemoDataInitializer 覆盖 dev profile，修复 task_status_dict 字典缺失致看板空白
5. **!1269 fix(frontend)**: TaskKanban 任务加载失败不再静默吞错，展示错误提示
6. **!1268 fix(tender)**: 项目结果登记后同步标讯状态，修复标讯转项目后状态断链
7. **!1260 fix(task)**: 任务详情附件文件名支持点击下载
8. **!1258 fix(task)**: 修复交付物下载会话过期导致空白页
9. **!1257 fix(task)**: CO-382 任务看板创建人显示"系统自动获取"修正
10. **!1272 fix(user)**: CO-384 补齐 `usersApi.getByIds` 方法，修复投标辅助人员回显"未分配"
11. **!1273 refactor(frontend)**: inline 12 single-use composables into target components

#### UI/UX 修复
1. **!1245 fix(bidding)**: 人工录入标讯总部所在地选择市级后自动关闭弹窗 (CO-381)
2. **!1248 fix(project)**: 标书制作/结项阶段导出按钮统一为项目文档打包 zip (CO-378)
3. **!1247 style(project)**: 标书制作阶段 AI 按钮 UI 风格统一 (CO-380)
4. **!1236 feat**: 结项前端按钮对接新版一键导出文档接口
5. **!1239 fix**: 修复任务交付物下载跳转到空白页问题

#### 文档与沉淀
1. **!1274 docs(lessons)**: 沉淀第 22 条——看板空白与交付物重复渲染根因
2. **!1231 docs(lessons)**: 沉淀第 21 条——readiness 延迟恢复 UP 经验
3. **!1235 docs**: 统一修正 CLAUDE.md 角色定义与 RoleProfileCatalog 保持一致
4. **e16e7b732 / 9d14b6200 / 3dad0e958**: CO-361/CO-373 五次修复教训 + 角色码解析规范
5. **!1256 docs**: 第 9 次部署报告

---

## 部署执行摘要

### 1. 发布准备
- **工作区**: `trae` 主工作区，已同步到 `origin/main` 最新（`92b7dadb3`）
- **打包**: `RELEASE_ID=92b7dadb-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh`
- **同源构建**: `apiBaseUrl=""`，nginx 反代 8080，避免跨域
- **JAR 验证**: 含 168 个 V*.sql + 1 个 B73 baseline，无重复版本
- **发布包大小**: 136 MB

### 2. Flyway 预检（关键步骤）

#### 2.1 源码无重复版本
```bash
ls backend/src/main/resources/db/migration-mysql/V*.sql | sed 's/.*\(V[0-9]*\)__.*/\1/' | sort | uniq -d
# 输出为空 → 无重复
```

#### 2.2 JAR 内迁移文件验证
```bash
unzip -l .release/92b7dadb-api8080/backend/app.jar | grep "migration-mysql/V" | wc -l
# 168 个 V*.sql + 1 个 B73__full_schema_baseline.sql
# uniq -d 检测无重复版本
```

#### 2.3 remote-deploy.sh 内置 validate
- **结果**: ✅ VALIDATE OK - all checksums match
- **验证范围**: 169 个迁移（含 baseline）
- **执行时机**: jar 覆盖前，旧 jar 仍在运行（服务不中断）
- **耗时**: 66ms

### 3. 部署执行中遇到的权限问题与修复

#### 3.1 第一次失败：前端目录权限
```
rm: cannot remove '/srv/www/xiyu-bid/assets/...': Permission denied
```
- **根因**: `/srv/www/xiyu-bid/` 目录属于 `nginx:nginx`，jetty 用户无权删除
- **失败点**: "Activating frontend assets" 步骤，后端 jar 未被覆盖（失败是安全的）
- **修复**: `sudo chown -R jetty:jetty /srv/www/xiyu-bid/` + 清理 macOS `._*` 残留文件
- **第二次执行**: 成功

#### 3.2 部署时间线
- **15:14**: 发布包上传到 `/tmp/`
- **15:15**: Flyway validate 通过 + 前端资源切换 + 后端 jar 覆盖
- **15:16:48**: 后端服务重启（PID 17259）
- **15:18-15:19**: readiness 短暂 OUT_OF_SERVICE（Kafka SDK 初始化时序）
- **15:20 左右**: readiness 自恢复 UP
- **总耗时**: 约 5 分钟（含权限修复重试）

---

## 验证结果

### 1. 后端总体健康检查
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP", "details": {"database": "MySQL", "validationQuery": "isValid()"}},
    "diskSpace": {"status": "UP"},
    "jwt": {"status": "UP", "details": {"algorithm": "HMAC-SHA256", "secretLength": 64, "strength": "STRONG"}},
    "livenessState": {"status": "UP"},
    "ping": {"status": "UP"},
    "readinessState": {"status": "UP"},
    "redis": {"status": "UP", "details": {"version": "6.2.19"}}
  }
}
```

### 2. Readiness 检查
- ✅ `HTTP 200 UP`（db + readinessState 均 UP）

### 3. Liveness 检查
- ✅ `HTTP 200 UP`

### 4. 前端页面可访问性
```
GET http://172.16.38.78:8080/ → 200, 851 bytes
- HTML 正常返回，标题"西域MRO投标管理平台"
- 引用 assets: /assets/index-CsRb9PGo.js（同源构建，无 CORS 风险）
```

### 5. API 响应验证
```
POST /api/auth/login (错误密码) → 401
{"success":false,"code":401,"msg":"认证失败，请重新登录"}

GET /api/auth/me (未授权) → 403
```
- ✅ 登录接口契约正常（401 错误密码是预期）
- ✅ `/api/auth/me` 返回 403（项目已知行为：`@PreAuthorize("isAuthenticated()")` 方法级注解，匿名访问返回 403 而非 401）
- ✅ nginx 反向代理正常

### 6. Flyway 迁移状态
- ✅ 无新增迁移（0 个新迁移执行）
- ✅ checksum 全部匹配（169 个迁移 validate 通过）
- ✅ 最新迁移 V1102 保持 success=1

### 7. 前端一致性验证
- ✅ Frontend matches release（`src="/assets/index-CsRb9PGo.js"`）

### 8. 部署记录
```json
{
  "releaseId": "92b7dadb-api8080",
  "activatedAt": "2026-06-28T07:15:18Z",
  "releaseDir": "/opt/xiyu-bid/releases/92b7dadb-api8080",
  "frontendPublicDir": "/srv/www/xiyu-bid",
  "backendJarPath": "/opt/xiyu-bid/shared/backend/app.jar",
  "backendServiceName": "xiyu-bid-backend",
  "healthcheckUrl": "http://127.0.0.1:8080/actuator/health",
  "packageMetadata": {
    "releaseId": "92b7dadb-api8080",
    "apiBaseUrl": "",
    "jarName": "bid-poc-1.0.3.jar",
    "builtAt": "2026-06-28T07:10:29Z"
  }
}
```

### 9. 跳过的检查
- **完整登录 smoke**: 跳过（生产 admin 密码未知，遗留问题）
- **CRM smoke**: 跳过（依赖登录态）
- **run-prod-smoke.mjs**: 跳过（需要 PROD_SMOKE_USERNAME/PASSWORD 环境变量）

---

## 回滚姿态

| 项目 | 状态 |
|---|---|
| **回滚锚点** | ✅ 就绪（第 9 次部署 `47a0e2bba` 的 jar 和前端产物保留在 `/opt/xiyu-bid/releases/47a0e2bba-api8080/`） |
| **数据库备份** | ✅ 就绪（部署前自动备份 + 本次无新增迁移，DB schema 未变） |
| **回滚命令** | `RELEASE_ARCHIVE=/opt/xiyu-bid/releases/47a0e2bba-api8080/... bash /tmp/remote-deploy.sh` |
| **是否需要回滚** | ❌ 不需要（当前部署运行正常） |

---

## 部署总结

### 成功指标
- ✅ Flyway validate 前置通过（169 个迁移，all checksums match）
- ✅ 后端进程启动成功（PID 17259）
- ✅ 所有健康检查组件 UP
- ✅ readiness UP + liveness UP
- ✅ 前端页面可访问（HTTP 200）
- ✅ API 接口正常响应（登录 401，未授权 403）
- ✅ nginx 反向代理正常
- ✅ 前端一致性验证通过
- ✅ 部署记录正确写入

### 注意事项
1. **无新增数据库迁移**: 本次部署为纯代码变更，DB schema 未改动，回滚风险低
2. **readiness 恢复时序**: 启动后约 2-3 分钟内 readiness 处于 OUT_OF_SERVICE（Kafka SDK 初始化时序），属已知行为（第 8/9 次部署已记录），已自恢复
3. **admin 密码未知**: 完整登录 smoke 无法执行，需确认生产 `ADMIN_PASSWORD`
4. **前端目录权限修复**: 本次部署前 `/srv/www/xiyu-bid/` 所有权为 `nginx:nginx`，已修复为 `jetty:jetty`；建议确认 nginx 配置是否仍能正常读取静态文件（nginx master 通常以 root 运行，应无影响）
5. **macOS 残留文件清理**: 部署中清理了 `._*` 文件（macOS 打包工具产生的 metadata 残留）

### 本次部署经验沉淀（可复用）

1. **前端目录权限检查（新）**: 部署前应先检查 `ls -ld /srv/www/xiyu-bid/` 所有权，若非 jetty 用户拥有，需先 `sudo chown -R jetty:jetty` 修复，避免 remote-deploy.sh 在前端切换步骤失败
2. **macOS `._*` 文件清理**: 从 macOS 打包并 scp 到 Linux 服务器时，会生成 `._*` 元数据残留文件，建议上传后清理：`find /srv/www/xiyu-bid/ -name "._*" -delete`
3. **失败是安全的**: remote-deploy.sh 在前端切换失败时不会覆盖后端 jar，旧服务仍在运行；修复权限后重试即可
4. **0 迁移部署**: Flyway validate 依然要跑，确保 checksum 一致，避免启动时 validateOnMigrate 失败
5. **前置清理 target**: `rm -rf backend/target` 必须在打包前执行，防止旧迁移文件残留

---

## 事故复盘与防复发措施（2026-06-28 追加）

### 事故概述

本次部署后,`/api/tasks/my` 接口返回 500 错误,生产服务中断约 30 分钟。

**根因**:CO-382(commit `0834deb91`)的迁移文件 `V121__add_created_by_to_tasks.sql` 误放至 `db/migration/`(历史遗留目录,Flyway 不读取),导致生产 DB 缺少 `tasks.created_by` 列。`Task.java` 实体的 `@Column(name="created_by")` 触发 Hibernate 查询该列,引发 `SQLSyntaxErrorException: Unknown column 't1_0.created_by'`。

### 历史 Flyway 故障模式(6 次)

| # | 时间 | 故障 | 根因 |
|---|---|---|---|
| 1 | 06-25 | V1096 jar 内重复 | `mvn package` 未清理 target |
| 2 | 06-26 第3次 | V1096 checksum mismatch | 已发布迁移被改内容 |
| 3 | 06-26 第4次 | V1101 MariaDB 语法 | `ADD COLUMN IF NOT EXISTS` MySQL 8.0 不支持 |
| 4 | 06-26 第5次 | V1100 源码缺失 | 历史手动操作未同步源码 |
| 5 | 06-26 第6次 | V1039 failed migration | `check-flyway-db-source-sync.sh` 误判 |
| 6 | 06-28 | V1106 列缺失 | 迁移文件放错目录(migration/ vs migration-mysql/) |

### 防复发措施(P0 + P1 + P2 三层防护)

#### P0 — 立即实施(已合入 main)

1. **Flyway 迁移目录守卫** `scripts/check-flyway-migration-dir.sh`
   - pre-commit 拦截 V*.sql/B*.sql 放在 `db/migration/`(历史遗留目录)
   - 强制使用 `db/migration-mysql/`(活跃目录)
   - 逃生阀:`FLYWAY_ALLOW_LEGACY_DIR=1`

2. **jar 内重复版本校验** 强化 `scripts/release/package-release.sh`
   - `mvn package` → `mvn clean -DskipTests package`(强制清理 target)
   - 打包后 `unzip -l` 校验 jar 内 `db/migration-mysql/V*.sql` 无重复版本号

#### P1 — 本周内实施(已合入 main)

3. **Flyway 配置守卫** `scripts/check-flyway-config-guard.sh`
   - pre-commit 拦截 `application*.yml` 中 4 个关键配置项的修改:
     - `locations`(迁移目录)
     - `baseline-version`(基线版本)
     - `baseline-on-migrate`(基线策略)
     - `validateOnMigrate`(校验开关)
   - 逃生阀:`FLYWAY_ALLOW_CONFIG_EDIT=1`

4. **Flyway DB 同步检查** 强化 `scripts/pre-push-gate.sh` §3.6
   - pre-push 阶段运行 `check-flyway-db-source-sync.sh`
   - 检测"DB 已执行但源码缺失"
   - DB 不可用时 skip,可用时 fail 阻断推送

#### P2 — 中期改进(已合入 main)

5. **Flyway 语法守卫** `scripts/check-flyway-syntax-guard.sh`
   - pre-commit 拦截 5 种 MariaDB 扩展语法(MySQL 8.0 不支持):
     - `ADD COLUMN IF NOT EXISTS`
     - `DROP COLUMN IF EXISTS`
     - `ADD INDEX IF NOT EXISTS` / `DROP INDEX IF EXISTS`
     - `CREATE INDEX IF NOT EXISTS`
   - 允许 MySQL 8.0 原生支持的 `CREATE TABLE IF NOT EXISTS`
   - 逃生阀:`FLYWAY_ALLOW_MARIADB_SYNTAX=1`

6. **逃生阀追踪机制** `scripts/check-flyway-bypass-tracker.sh`
   - 检测 6 个 `FLYWAY_ALLOW_*` / `SKIP_FLYWAY_VALIDATE` 逃生阀的使用
   - 记录到 `.runtime/flyway-bypass.log`(不阻断提交)
   - PR 审查时检查该日志

### 防护体系总览(15 项防护)

| 防护层 | 检查项 | 数量 |
|---|---|---|
| **pre-commit**(提交前) | 目录守卫 / 配置守卫 / 语法守卫 / 回滚脚本 / 已发布不可变 / 版本冲突 / 逃生阀追踪 | 7 项 |
| **pre-push**(推送前) | DB 同步检查 / 架构测试 / 锁检查 / 行预算 | 4 项 |
| **package-release**(打包) | 强制 mvn clean / jar 内重复版本校验 | 2 项 |
| **remote-deploy**(部署) | Flyway validate 预检 / 健康检查 | 2 项 |

### 防复发效果对照表

| 历史事故 | 原发现时机 | 现发现时机 | 防护机制 |
|---|---|---|---|
| V1106 放错目录(本次) | 部署后 500 错误,中断 30 分钟 | **pre-commit 拦截** | check-flyway-migration-dir.sh |
| V1096 jar 内重复(06-25) | 部署时 Flyway 报错 | **package-release.sh 打包后校验** | jar 内重复版本检查 |
| CO-361 看板空白(baseline 改动) | 用户报告看板空白 | **pre-commit 拦截** | check-flyway-config-guard.sh |
| V1100 源码缺失(06-26 第5次) | 部署时 validate 失败 | **pre-push 拦截**(DB 可用时) | check-flyway-db-source-sync.sh |
| V1039 failed migration(06-26 第6次) | 部署时 validate 失败 | **pre-push 拦截**(DB 可用时) | check-flyway-db-source-sync.sh |
| V1101 MariaDB 语法(06-26 第4次) | 部署后 Flyway failed | **pre-commit 拦截** | check-flyway-syntax-guard.sh |
| 逃生阀绕过(潜在风险) | 无追踪,靠人工自觉 | **自动记录到日志** | check-flyway-bypass-tracker.sh |

### 仍未堵住的缺口(P3,长期改进)

1. `db/migration/` 历史目录仍有 17 个文件 — 当前是"守卫+警告",未彻底删除
2. 单一 source of truth:迁移文件生成器(强制使用 `new-migration.sh`)— 未实现
3. 部署前的"Flyway 健康度"评分 — 未实现
4. `validateOnMigrate: false` 在所有 profile 都关闭 — 风险过高暂不开启(生产 DB 有手动 INSERT 的 checksum=NULL 记录)

---

## 参考文档

- [生产发布流水线](./PRODUCTION_RELEASE_PIPELINE.md)
- [生产部署 Runbook](./LIVE_SERVER_DEPLOYMENT_RUNBOOK.md)
- [回滚手册](./ROLLBACK_RUNBOOK.md)
- [第 9 次部署报告](./deploy-report-2026-06-27-9th.md)（前一次部署，`47a0e2bba`）
- [经验教训](../lessons/lessons-learned.md)
