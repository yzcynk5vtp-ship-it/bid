# CO-394 后端权限表达式统一 — 实施笔记

> 任务：知识库 5 模块后端 Controller `@PreAuthorize` 统一到 Warehouse 模板风格（`hasAuthority('<permission-constant>')`）。
> 分支：`agent/zcode/co394-backend-perm-align`
> 入口 ticket：[CO-394](https://linear.app/ericforai/issue/CO-394)
> 范围：仅后端 P0+P1（不含前端 P2、不含 dataScope）。

## 决策记录（spec 之外或与 spec 不一致的部分）

### 1. CO-394 描述/评论基于过时审计，与实际代码有出入

CO-394 description 与评论 1 的实施方案多处基于**过时审计**，实际代码已部分迁移：

| CO-394 描述 | 实际代码现状 | 本次处理 |
|---|---|---|
| 人员证书类级 `hasAnyRole('ADMIN','MANAGER')` 死锁 | 类级已是 `isAuthenticated()`（L55），方法级混用 `hasAnyAuthority(...)` + `hasAuthority('personnel.view')` | 写端点统一为 `personnel.manage`，读端点保持 `personnel.view` |
| 品牌授权"类级+方法级全 `ROLE_MANAGER`" | 类级已迁移到 `brand-auth.view`（L48），仅写端点仍 `hasAnyRole('ADMIN','MANAGER')` | 仅改写端点，常量已就绪直接复用 |
| 业绩管理"仅 GET 可达" | 读端点退化为 `isAuthenticated()`（更宽松） | 读端点也收紧为 `performance.manage`（修复过宽权限） |

### 2. 权限点粒度选择：单一 `*.manage`（非读写分离）

CO-394 评论 1 提议的 `KNOWLEDGE_AUTHORITIES = "hasAnyAuthority('/bidAdmin', 'bid-TeamLeader', 'bid-Team', 'admin')"` **未被采用**——这仍是角色码白名单思路，只是从 `hasAnyRole` 换成 `hasAnyAuthority`，未对齐 Warehouse 模板。

实际选择：每模块单一 `*.manage` 权限点（对齐 `warehouse.manage`），原因是：
- Warehouse 模块已是单一权限点，作为目标范本
- brand-auth 已有 view/create/edit/revoke 4 个细粒度常量，但读写分离会让 Flyway 脚本和 catalog 改动量翻倍，且与 Warehouse 不一致
- 人员证书已有 `personnel.view`（只读）在用，保留 view + 新增 manage 是最小破坏

### 3. 必须配套 Flyway 脚本（关键约束）

`RoleProfileBootstrapArchitectureTest` 架构门禁**禁止** bootstrap 同步 menuPermissions。CO-393/403/409 全部是「Java + Flyway」双写。仅改 Java 代码会导致已运行 DB 的角色 menuPermissions 不含新权限点 → `hasAuthority` 403。

- `menu_permissions` 是**逗号分隔字符串**（varchar 4000），非 JSON
- 参照 V1118 的 `CASE WHEN ... LIKE '%xxx%' THEN ... ELSE CONCAT(..., ',"xxx"') END` 幂等追加模式
- 版本号 V1120-V1123（V1119 已被 CO-1400 占用）

### 4. Ticket 拆分：按模块 4 个子 ticket

CO-394 评论 1 提议 6 个子 ticket（含前端 P2）。本次仅后端，拆 4 个：
- CO-394-A 品牌授权（P0）
- CO-394-B 人员证书（P0，兼顾 CO-391）
- CO-394-C 业绩管理（P1）
- CO-394-D 资质证书（P1，含错名修正）

### 5. 业绩读端点收紧决策

业绩管理读端点（list/get）当前是 `isAuthenticated()`（任何登录用户可读），本次收紧为 `hasAuthority('performance.manage')`。这是**修复过宽权限**，但可能挡住非 3 角色 + admin 的用户。评审确认：业绩本就只对投标三角色 + admin 开放，收紧符合业务意图。

## 各模块实施记录

### CO-394-A 品牌授权

**改动文件**：
- `ManufacturerAuthorizationController.java`：类级从 `hasAuthority('brand-auth.view')` 放宽为 `isAuthenticated()`（对齐 Warehouse 模板），10 个方法级注解从 `hasAnyRole('ADMIN','MANAGER')` 或硬编码字符串切换为 `hasAuthority('<PERM>')` + `RoleProfileCatalog` 常量
- `RoleProfileCatalogTest.java`：新增 2 个断言（三角色含 view/create/edit；组长+管理员含 revoke，专员不含）

**权限点映射**：
- list/detail/logs/export/template → `BRAND_AUTH_VIEW_PERMISSION`（只读）
- create/uploadAttachments/importExcel → `BRAND_AUTH_CREATE_PERMISSION`（写入）
- update → `BRAND_AUTH_EDIT_PERMISSION`
- revoke → `BRAND_AUTH_REVOKE_PERMISSION`

**Flyway 脚本**：**不需要**。V1012 已写入 brand-auth.* 权限点到旧角色码，V1092 角色码重命名时权限点随行保留。DB 中 `/bidAdmin`/`bid-TeamLeader`/`bid-Team` 三角色 menuPermissions 已含 brand-auth 权限点。

**验证**：`mvn -f backend/pom.xml compile` 通过；`RoleProfileCatalogTest` 9 tests passed（原 7 + 新增 2）。

**决策权衡**：类级注解从 `brand-auth.view` 改为 `isAuthenticated()`，是为了对齐 Warehouse 模板（类级最宽松入口 + 方法级收敛）。实际安全性不变——所有端点都有方法级权限点收敛。

### CO-394-B 人员证书

（待实施）

### CO-394-C 业绩管理

（待实施）

### CO-394-D 资质证书

（待实施）

## 验证记录

（待补充）
