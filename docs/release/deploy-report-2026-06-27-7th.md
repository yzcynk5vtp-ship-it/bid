# 部署报告：第 7 次发布

**发布 ID**: `0571e25b-api8080`
**部署时间**: 2026-06-27 11:30 CST (03:30 UTC) — JAR 部署 + 后端重启
**部署者**: Trae AI Agent（xiyu-bid-poc 基准区 main 分支）
**部署类型**: 手动 SSH 部署（remote-deploy.sh）

---

## 发布范围

| 项目 | 值 |
|---|---|
| **Commit** | `0571e25ba` — `!1203 refactor(qualification): 拆分 Qualification.vue 至 300 行内 + 替换硬编码颜色为 design token` |
| **源码变更** | 51 个新 commit（从第 6 次 `8ed1ac7dd` 起），涵盖安全修复、权限治理、SSO 优化、任务交付物修复、资质附件修复、V1103 迁移文件 |
| **前端构建** | Vite + Vue 3，生产同源构建（`baseURL=""`） |
| **后端构建** | Maven + Spring Boot 3.2.0，`mvn clean package -DskipTests` |
| **JAR 大小** | ~149 MB（`bid-poc-1.0.3.jar`） |
| **迁移数量** | 166 个（JAR 内），新增 V1103 |

### 主要变更内容（51 个 commit）

#### 安全 / 权限修复（重点）
1. **CO-361 任务可见性 + 项目访问权限修复**（!1197）：修复越权可达账户管理页面问题
2. **resource 模块路由 permissionKeys 补全**（!1187）：修复越权可达账户管理页面
3. **CO-362 OSS 权限缓存迁移 Redis 持久化**（!1186）：修复后端重启导致全量 403
4. **TenderAttachmentDownloadService SSRF 漏洞修复**（!1189）
5. **CO-363 人员证书模块全部接口权限注解补全**（!1191）
6. **SSO 角色解析不使用 OSS roleCode**：避免跨系统角色码重名误匹配
7. **标书审核人选择排除当前用户**（CO-367, !1194）

#### 业务功能修复
1. **CO-368 资质附件删除提示成功但实际未清空 fileUrl**（!1200）
2. **CO-370 任务交付物和完成情况说明在状态流转后丢失**（!1201）
3. **CO-356 资质证书批量上传附件完整修复（4 个子问题）**（!1196）
4. **CO-358 margin 接口 500——绑定 :muid 参数**（!1182）
5. **CO-362 补齐 personnel 缺失列 V1103**（!1185）：修复投标专员 500
6. **后端全量测试 36 个失败/错误修复**（!1195）

#### 数据库迁移
1. **V1103__repair_personnel_missing_columns.sql**：幂等存储过程，用 information_schema 判断后再 ALTER，补齐 personnel_certificate/personnel_education 缺失列（修复 V1065/V1066 schema 漂移）
2. **U1103__repair_personnel_missing_columns.sql**：对应回滚脚本

#### 其他
1. **Qualification.vue 拆分至 300 行内 + design token 替换**（!1203）
2. **scripts/ 默认端口对齐项目规范**（1323/18089/8009, !1202）
3. **sync-from-github.sh 变量引用修复**（!1193）
4. **CRM 商机负责人优先级修复经验教训沉淀**（!1190）

---

## 部署执行摘要

### 1. 发布准备
- **基准区**: `xiyu-bid-poc/main`，已同步到 `origin/main` 最新（`0571e25ba`）
- **打包**: `scripts/release/package-release.sh` → `.release/xiyu-bid-release-0571e25b-api8080.tar.gz`（137 MB 压缩后）
- **JAR 验证**: 含 V1103，无重复迁移版本，共 166 个迁移文件

### 2. ⚠️ Flyway 预检（关键步骤，避免冲突）

#### 2.1 生产 DB 当前状态检查
发现生产 DB `flyway_schema_history` 中 **V1103 已存在记录**（installed_rank=168, success=1, installed_on=2026-06-26 21:39:20），但 **checksum 为 NULL**（说明是第 6 次部署修复时手动 INSERT 的记录，非 Flyway 执行）。

#### 2.2 用当前生产 jar (8ed1ac7dd) 跑 validate
```
Successfully validated 167 migrations
VALIDATE OK - all checksums match
```
当前 jar 不含 V1103，Flyway 将 V1103 标记为 "Future"。

#### 2.3 用新 jar (0571e25b) 跑 validate
```
Successfully validated 167 migrations
VALIDATE OK - all checksums match
```
**关键发现**：Flyway 9.22.3 对 checksum=NULL 的记录**不算 mismatch**（手动 INSERT 的记录跳过 checksum 验证）。新 jar 中的 V1103 文件不会导致 validate 失败。

#### 2.4 结论
- ✅ V1103 在 DB 中已 success=1，Flyway 会跳过执行，不会重跑
- ✅ V1103 是幂等存储过程（information_schema + IF NOT EXISTS），即使重跑也安全
- ✅ 不需要 flyway repair，可以直接部署

### 3. 数据库备份
- **备份路径**: `/opt/xiyu-bid/db-backups/winbid-0571e25b-api8080-20260627112744.sql.gz`（2.7 MB）
- **SHA256**: `67580f88ac3aa0047f869f96445d04b601ff85445210986b821afefc28cd5656`
- **备份时间**: 2026-06-27 03:27:48 UTC

### 4. 发布激活
- **remote-deploy.sh** 执行成功
- 内置 Flyway validate 预检通过：`Successfully validated 167 migrations`
- JAR 复制到 `/opt/xiyu-bid/shared/backend/app.jar`
- 前端复制到 `/srv/www/xiyu-bid`
- backend restart 成功
- 前端一致性验证通过：`/assets/index-BX1iR9Z9.js`

---

## Smoke 测试结果

| 检查项 | 结果 |
|---|---|
| **Verdict** | ✅ **GO**（服务可用，登录需生产密码确认） |
| nginx + xiyu-bid-backend 状态 | ✅ 都 active |
| 内部健康检查 (18080) | ✅ `{"status":"UP","groups":["liveness","readiness"]}` |
| nginx 代理健康检查 (8080) | ✅ `{"status":"UP","groups":["liveness","readiness"]}` |
| 本地外部入口健康检查 | ✅ `{"status":"UP",...}`（绕过本地代理） |
| 前端首页 HTTP 200 | ✅ Content-Length 851, ETag 匹配 |
| 登录 API 接口可用 | ✅ 返回 401（密码错误，接口本身正常） |
| /actuator/info 安全策略 | ✅ 返回 403（未授权访问被拒绝，安全配置正常） |
| 完整登录流程 | ⏭️ skipped（生产 admin 密码未确认） |
| 业务 API 读测试 | ⏭️ skipped（需登录 token） |
| 后端启动日志 | ✅ 无 Flyway 错误，无 V1103 重跑（已正确跳过） |

---

## 回滚姿态

| 锚点 | 值 |
|---|---|
| **当前版本** | `0571e25b-api8080`（`0571e25ba`） |
| **前回滚版本** | `8ed1ac7dd-api8080`（第 6 次部署） |
| **JAR 备份位置** | `/opt/xiyu-bid/releases/8ed1ac7dd-api8080/backend/app.jar` |
| **前端备份位置** | `/opt/xiyu-bid/releases/8ed1ac7dd-api8080/frontend/` |
| **数据库备份** | `/opt/xiyu-bid/db-backups/winbid-0571e25b-api8080-20260627112744.sql.gz` |
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

## Flyway 治理经验沉淀

### 本次部署的 Flyway 验证流程（避免冲突）

1. **预检 1：用当前生产 jar 跑 validate**（确认当前 DB 状态健康）
2. **预检 2：对比源码迁移文件与 DB 一致性**（确认无 DB 已执行但源码缺失的迁移）
3. **预检 3：用新 jar 跑 validate**（确认新 jar 不会因 checksum mismatch 失败）
4. **预检 4：检查新迁移文件是否已在 DB 中**（避免重复执行非幂等迁移）

### 关键发现：Flyway 9.22.3 对 NULL checksum 的处理

- 手动 INSERT 到 `flyway_schema_history` 的记录，checksum 为 NULL
- Flyway validate 对 NULL checksum **不算 mismatch**（跳过 checksum 验证）
- 这意味着手动 INSERT 的记录不会阻断后续部署
- 但建议后续通过 `flyway repair` 把 NULL checksum 对齐到 jar 计算的 checksum，保持元数据一致性

### V1103 处理流程（本次部署的关键决策点）

1. 发现 V1103 在 DB 中已有记录（success=1, checksum=NULL）
2. V1103 是第 6 次部署修复时手动 INSERT 的（2026-06-26 21:39:20）
3. 新 jar 含 V1103 文件，但 DB 已有记录，Flyway 会跳过执行
4. V1103 是幂等存储过程，即使重跑也安全
5. 用新 jar 跑 validate 确认通过，直接部署

---

## 待处理事项

### 中优先级
1. **生产 admin 密码**: 冒烟测试无法完成完整登录流程，需确认生产环境 `ADMIN_PASSWORD` 环境变量设置的密码
2. **V1103 checksum 对齐**: 建议在下次维护窗口跑 `flyway repair`，把 DB 中 V1103 的 NULL checksum 对齐到 jar 计算的 checksum
3. **本地代理配置**: 本地有 `HTTP_PROXY=http://127.0.0.1:7897`，访问内网 172.16.38.78 时需加 `--noproxy '*'`

---

## 经验教训

1. **Flyway 9.22.3 对 NULL checksum 宽容处理**: 手动 INSERT 的记录（checksum=NULL）不会导致 validate 失败，这降低了手动修复的风险
2. **预检流程的价值**: 本次部署前用新 jar 跑 validate，提前确认 V1103 不会导致冲突，避免了第 6 次部署的 Flyway 启动失败事故
3. **幂等迁移的重要性**: V1103 使用 information_schema + IF NOT EXISTS 的幂等设计，即使重复执行也安全，降低了部署风险
4. **DB 元数据一致性**: 手动 INSERT 的 flyway_schema_history 记录应尽量通过 `flyway repair` 对齐 checksum，保持元数据一致性
5. **部署前数据库备份是必须的**: 即使预检通过，也要有完整的数据库备份作为回滚锚点
