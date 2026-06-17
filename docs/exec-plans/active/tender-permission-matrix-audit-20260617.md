# 标讯中心 · 权限矩阵对照检查报告

> 检查日期：2026-06-17  
> 依据文档：飞书 `Kq7NduknFoCTi3xyI0vcyJfZnce` V1.0（2026-05-14 定稿）  
> 代码基线：`/Users/user/xiyu/worktrees/kimi/backend/src/main/java/com/xiyu/bid/tender/**` 及关联角色/数据范围代码  
> 检查方式：逐个功能点对照文档要求与当前代码实现，标注匹配 / 部分匹配 / 不匹配 / 缺失 / 风险。

---

## 0. 角色映射关系

| 文档角色（OSS 岗位 code） | 文档中文名 | 代码 roleCode | 代码中文名 | Spring Security 实际 authority | 数据范围配置 |
|---|---|---|---|---|---|
| `bid-SystemAdmin` | 投标系统管理员 | `admin` | 管理员 | `ROLE_ADMIN` | `all` |
| `bidAdmin` | 投标管理员 | `bid_admin` | 投标部门管理员 | `ROLE_BID_ADMIN` + `ROLE_ADMIN` | `all` |
| `bid-TeamLeader` | 投标组长 | `bid_lead` | 投标组长 | `ROLE_BID_LEAD` + `ROLE_MANAGER` | `all` |
| `bid-projectLeader` | 投标项目负责人 | `sales` | 项目负责人 | `ROLE_SALES` + `ROLE_MANAGER` | `self` |
| `bid-Team` | 投标专员 | `bid_specialist` | 投标专员 | `ROLE_BID_SPECIALIST` + `ROLE_STAFF` | `self` |
| `bid-otherDept` | 跨部门协同人员 | `bid_other_dept` | 跨部门协同人员 | `ROLE_BID_OTHER_DEPT`（无 legacy） | `self` |
| `bid-administration` | 行政人员 | `admin_staff` | 行政人员 | `ROLE_ADMIN_STAFF` + `ROLE_STAFF` | `self` |

**说明**：
- 角色映射来源：`docs/integration/organization-role-filter-config.yml`（岗位→roleCode）+ `RoleProfileCatalog.java`（roleCode→数据范围/菜单权限）+ `UserDetailsServiceImpl.java`（roleCode→Spring Security authority）。
- `bid-SystemAdmin` 在配置中**不按岗位映射**，而是通过 `personToRoleMappings` 按人员精确给 `admin`。
- 代码里额外存在 `bid_senior`（投标主管，合并 bid_admin + bid_lead）、`task_executor`（任务执行人）两个文档未列出的角色，其权限会在具体功能点中体现。

---

## 1. 功能树 × 权限矩阵逐项对照

### 1.1 标讯列表

| 三级功能 | 文档要求 | 当前代码实现 | 结论 | 差异说明 |
|---|---|---|---|---|
| 查看列表 | bidAdmin / bid-TeamLeader：全量<br>bid-projectLeader：仅自己的<br>bid-Team：仅分配给自己的 | `TenderController.getAllTenders` 使用 `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")`；列表返回前经 `TenderProjectAccessGuard.filterVisibleTenders` 二次过滤。 | ⚠️ 部分匹配 | ① bidAdmin/bid-TeamLeader 因 `dataScope=all` 实际可见全量，匹配。<br>② bid-projectLeader（sales，dataScope=self）只能看到 `creatorId/biddingPersonId/projectManagerId` 等于自己的标讯；被分配后 `projectManagerId` 会被写入，故大体匹配“仅自己的”。<br>③ **bid-Team（bid_specialist）文档要求“仅分配给自己的”，但代码按 `dataScope=self` 过滤，实际看到的是“自己创建/负责”的标讯，而非“被分配给自己的”**。 |
| 搜索/筛选 | 同上 | 同上，使用同一接口 `/api/tenders`。 | ⚠️ 部分匹配 | 同“查看列表”。 |
| 导出 | 各角色导出 = 其可见数据范围，不设单独权限点 | 未找到标讯列表导出后端接口。 | ❌ 缺失 | 后端无导出实现，无法验证。 |
| 编辑 | bidAdmin / bid-TeamLeader：全量 · 仅未立项状态<br>bid-projectLeader：自己的 · 见下方状态表<br>bid-Team：— | `PUT /api/tenders/{id}` 使用 `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")`；Service 层 `TenderCommandAccessGuard.assertCanUpdateTender` 判断。 | ❌ 不匹配 | ① **方法级注解把 bid-Team（bid_specialist，ROLE_STAFF）也放进了可调用范围**，与文档“—”不符。<br>② Service 层对 STAFF 只允许编辑“自己创建且状态为 PENDING_ASSIGNMENT”的标讯；对 ADMIN/MANAGER 仅校验可见性，**未校验“未立项”状态**。<br>③ 文档中 bid-projectLeader 在“跟踪中/已评估”可编辑，但代码中 ROLE_MANAGER（含 sales）不区分状态即可编辑，**未按状态递减收口**。<br>④ `AssignmentPermissionRules.canEditTender` 中写了更复杂的规则，但**全局搜索无调用方**，为死代码。 |
| 删除 | bidAdmin / bid-TeamLeader：全量 · 仅未评估状态<br>bid-projectLeader：自己创建的 · 仅未评估状态<br>bid-Team：— | `DELETE /api/tenders/{id}` 使用 `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")`；Service 层 `TenderCommandAccessGuard.assertCanDeleteTender` 判断。 | ❌ 不匹配 | ① **方法级注解把 bid-Team（bid_specialist，ROLE_STAFF）也放进了可调用范围**，与文档“—”不符。<br>② Service 层对 STAFF 只允许删除“自己创建且状态为 PENDING_ASSIGNMENT”的标讯；对 ADMIN/MANAGER 仅校验可见性 + 状态为 PENDING_ASSIGNMENT。<br>③ 文档要求“仅未评估状态可删除”，代码实现为“**仅 PENDING_ASSIGNMENT 可删除**”。 TRACKING 状态的标讯（已分配但尚未评估）在代码中不可删除，与文档“未评估”口径不一致。 |
| 分发/转派 | bidAdmin / bid-TeamLeader | `TenderTransferController.transferTender` 使用 `@PreAuthorize("isAuthenticated()")`；Service 层无角色校验。 | ❌ 严重不匹配 | **任何登录用户都能调用转派接口**。Controller 注释写“仅投标管理员/组长可操作”，但注解未限制角色。 |

### 1.2 标讯录入

| 三级功能 | 文档要求 | 当前代码实现 | 结论 | 差异说明 |
|---|---|---|---|---|
| 手动录入 | bidAdmin / bid-TeamLeader / bid-projectLeader / bid-Team / bid-SystemAdmin | `POST /api/tenders` 使用 `@PreAuthorize("hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR', 'SALES', 'BID_SPECIALIST')")`。 | ✅ 匹配 | admin、bid_admin 命中 ADMIN；bid_lead 命中 BID_LEAD；sales 命中 SALES；bid_specialist 命中 BID_SPECIALIST。 |
| 粘贴识别（AI） | 同上 | 未在 `TenderController` 中找到对应后端接口。 | ❓ 待确认 | 可能由前端直接调用 AI 服务或散落在其他 Controller，本次未覆盖到。 |
| 批量导入（Excel） | bidAdmin / bid-TeamLeader / bid-Team / bid-SystemAdmin | `POST /api/tenders/import` 使用 `@PreAuthorize("hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SPECIALIST')")`。 | ✅ 匹配 | 与手动录入角色一致，不含 bid-projectLeader（sales），符合文档。 |
| 下载模板 | bidAdmin / bid-TeamLeader / bid-Team / bid-SystemAdmin 公开下载 | `GET /api/tenders/import-template` 使用 `@PreAuthorize("hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SPECIALIST', 'TASK_EXECUTOR', 'ADMIN_STAFF')")`。 | ⚠️ 范围扩大 | 文档未列 `task_executor`、`admin_staff`，但代码放行了这两个角色。对文档列出的四个角色无影响。 |

### 1.3 标讯评估

| 三级/四级功能 | 文档要求 | 当前代码实现 | 结论 | 差异说明 |
|---|---|---|---|---|
| 填写评估表 | bid-projectLeader：被分配的标讯 | `TenderEvaluationController.getEvaluation` / `saveDraft` / `submitDraft` 均使用 `@PreAuthorize("isAuthenticated()")`；Service 层通过 `TenderAssignmentPermissions.canFill` 校验 latest assignee。 | ✅ 匹配 | 实例级权限由分配记录控制，符合“被分配的标讯”。 |
| 提交评估（不可撤回） | bid-projectLeader：被分配的标讯 | 同上。`TenderEvaluationSubmissionService.submit` 将评估状态改为 SUBMITTED，并推进 tender 状态 TRACKING → EVALUATED。 | ✅ 匹配 | 提交后不可撤回（代码在 SUBMITTED 状态下抛 `IllegalStateException`），与文档一致。 |
| └ 确认投标（标讯转项目进入待立项） | bidAdmin / bid-TeamLeader | 存在两条路径：<br>① `TenderController.participateBid`：注解 `isAuthenticated()`，Service 用 `permissions.canDecide`。<br>② `TenderEvaluationController.reviewTender`：注解 `hasAnyRole('ADMIN')`；内部也走 `permissions.canDecide`。 | ⚠️ 部分匹配 | `permissions.canDecide` 逻辑：用户有 global access（admin/bid_admin/bid_lead/bid_senior）即通过，否则必须是分配人。因此 bidAdmin/bid-TeamLeader 可通过。<br>但 `TenderController.participateBid` **Controller 层未限制角色**，仅依赖 Service 层 `canDecide`，与文档“投标管理员/组长”的显式角色要求相比，防护层过薄。 |
| └ 放弃投标（需填原因） | bidAdmin / bid-TeamLeader | 存在两条路径：<br>① `TenderController.abandonBid`：注解 `isAuthenticated()`，Service 用 `permissions.canDecide`。<br>② `TenderEvaluationController.reviewTender` 中 `approved=false` 也可弃标。 | ⚠️ 部分匹配 | 同“确认投标”：功能上 bidAdmin/bid-TeamLeader 可通过，但 Controller 层未显式限制角色。 |

### 1.4 补充功能（未挂菜单但需权限控制）

| 功能 | 文档要求 | 当前代码实现 | 结论 | 差异说明 |
|---|---|---|---|---|
| 查看标讯详情 | bidAdmin / bid-TeamLeader：全量<br>bid-projectLeader：仅自己的<br>bid-Team：仅分配给自己的 | `GET /api/tenders/{id}` 使用 `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")`；Service 层 `accessGuard.assertCanAccessTender`。 | ⚠️ 部分匹配 | 与“查看列表”相同：bid-Team 实际按 `self`（自己创建/负责）过滤，**不等于“仅分配给自己的”**。 |
| 关键字/目标客户清单配置 | — | 未找到对应后端接口。 | ❓ 待确认 | 文档标注“—”，但业务规则第 8 条提到由投标系统管理员初始化导入。代码中未见配置接口。 |
| 标讯去重 · 冲突拍板 | bidAdmin：最终决定<br>bid-TeamLeader：提报给管理员 | 去重逻辑在 `TenderDeduplicationService`，标讯创建时自动检测重复并抛 `TenderDuplicateException`；未见按角色区分的“冲突拍板”接口。 | ❌ 不匹配/缺失 | 当前实现为**创建前硬拦截**，无管理员的“最终决定”入口，也无组长的“提报”入口。 |
| 查看分发变更日志 | bidAdmin / bid-TeamLeader | 未找到专门的分发变更日志查询接口。`TenderAuditService` 有审计日志（`GET /api/tenders/{id}/audit-logs`），但注解为 `ADMIN/MANAGER/STAFF`，且未限定为“分发变更”。 | ❌ 不匹配 | 无独立的分发/转派变更日志权限点。 |
| 弃标/未中标后仍可查看列表 | 全部四个角色 | 列表查询的角色和数据范围过滤如前所述；弃标/未中标状态不会影响可见性判断。 | ✅ 匹配 | 状态不影响 `filterVisibleTenders`。 |

---

## 2. 投标项目负责人 · 操作权限随标讯状态递减

| 标讯状态 | 文档要求 | 当前代码实现 | 结论 | 差异说明 |
|---|---|---|---|---|
| 待分配 | ❌ 不可查看/编辑/删除 | `dataScope=self` 的用户（sales/bid_specialist）在 `PENDING_ASSIGNMENT` 状态下若 `creatorId` 为自己则可查看/编辑/删除；非创建人不可见。 | ⚠️ 部分匹配 | 标讯创建后若自动分配失败，创建人通常就是 sales/bid_specialist 自己，因此存在“待分配但自己创建”的可操作场景。文档写“尚未分配给自己 → 不可操作”，代码口径不一致。 |
| 跟踪中 | ✅ 可查看/编辑/删除 | sales/bid_specialist 若 `projectManagerId` 被写入则可见；但 `TenderCommandAccessGuard` 对 STAFF 只允许编辑/删除 `PENDING_ASSIGNMENT` 状态。 | ❌ 不匹配 | 文档明确跟踪中可自由操作，但代码中 STAFF 在 TRACKING 状态下**不可编辑/删除**（`assertStaffCanUpdateTender` / `assertStaffCanDeleteTender` 限制为 PENDING_ASSIGNMENT）。 |
| 已评估 | ✅ 可查看/编辑<br>❌ 不可删除 | 查看：同跟踪中。<br>编辑：Service 层 STAFF 仍只允许 PENDING_ASSIGNMENT，故已评估不可编辑。<br>删除：不可删除（PENDING_ASSIGNMENT 限制）。 | ❌ 不匹配 | 文档“已评估可编辑”，代码不允许 STAFF 编辑已评估标讯。 |
| 投标中 / 已中标 / 未中标 / 已放弃 | ✅ 可查看<br>❌ 不可编辑/删除 | 一旦进入 `BIDDING` 或之后状态，STAFF 不可编辑/删除；查看依赖 `self` 数据范围。 | ✅ 匹配 | 该部分与文档一致。 |

**核心偏差**：代码把 bid-projectLeader（sales）和 bid-Team（bid_specialist）都按 `ROLE_STAFF` + `dataScope=self` 处理，导致 STAFF 的编辑/删除被限制在 `PENDING_ASSIGNMENT` 状态。而文档对 bid-projectLeader 的要求是：跟踪中可自由操作、已评估可编辑不可删除。

---

## 3. 标讯状态机

| 项目 | 文档要求 | 当前代码实现 | 结论 |
|---|---|---|---|
| 状态枚举 | 待分配 → 跟踪中 → 已评估 → 投标中 → 已中标/未中标；可分支到已放弃 | `Tender.Status`：`PENDING_ASSIGNMENT`、`TRACKING`、`EVALUATED`、`BIDDING`、`WON`、`LOST`、`ABANDONED`。 | ✅ 匹配 |
| 状态迁移规则 | 文档以流程图表达 | `TenderStatusTransitionPolicy.assertTransition`：<br>`PENDING_ASSIGNMENT` → `TRACKING`/`ABANDONED`<br>`TRACKING` → `PENDING_ASSIGNMENT`/`EVALUATED`/`ABANDONED`<br>`EVALUATED` → `BIDDING`/`ABANDONED`<br>`BIDDING` → `WON`/`LOST`/`ABANDONED`<br>`WON`/`LOST`/`ABANDONED` 终态 | ✅ 匹配 |
| 来源/回写 | 已中标/未中标由项目模块回写 | `Bidding/Won/Lost` 回写需结合项目模块，本次未深入项目模块，但状态枚举定义存在。 | ⚠️ 未验证 |

---

## 4. 关键业务规则

| 规则 | 文档要求 | 当前代码实现 | 结论 | 差异说明 |
|---|---|---|---|---|
| 1. 评估表一次性提交 | 提交后不可撤回、不可修改，不支持存草稿 | `TenderEvaluationSubmissionService.submit` 在 `SUBMITTED` 后抛异常；但同一类中 `saveDraft` 支持保存草稿，且 V130 允许在 `EVALUATED` 状态下重新编辑保存（设 `requires_review=true`）。 | ❌ 不匹配 | 文档“不支持存草稿”，代码**支持草稿**且**支持已评估后重新编辑**。 |
| 2. 确认投标 = 转项目立项 | 确认后标讯进入“投标中”，同时项目模块自动创建项目 | `TenderSubmissionService.participateBid` 仅改状态为 BIDDING 并创建任务；`TenderEvaluationService.proceedToBid` / `TenderSubmissionService.proceedToBid` 会创建 Project。 | ⚠️ 部分匹配 | 存在两个“立项”入口，调用路径不一致，容易让前端/测试产生歧义。 |
| 3. 删除权限收口 | 只有“未评估”状态可删除 | `TenderCommandAccessGuard.assertCanDeleteTender` 限制为 `PENDING_ASSIGNMENT`。 | ❌ 不匹配 | 代码口径是“未分配”，比文档“未评估”更严格。 |
| 4. 编辑权限收口 | 已立项（投标中/已中标/未中标/已放弃）后不可编辑 | 对 STAFF 仅允许 PENDING_ASSIGNMENT；对 ADMIN/MANAGER 未按状态限制。 | ⚠️ 部分匹配 | bidAdmin/bid-TeamLeader 在 BIDDING/WON/LOST/ABANDONED 下仍可编辑，**未收口**。 |
| 5. 导出不单独设权限点 | 导出 = 可见数据范围 | 后端无导出接口。 | ❌ 缺失 | 未实现。 |
| 6. bidAdmin vs bidTeamLeader | 当前权限完全一致，roleCode 独立 | `RoleProfileCatalog` 中两者菜单权限高度重合，dataScope 均为 `all`。 | ✅ 匹配 |
| 7. 去重 | 系统标记疑似重复，提交人找管理员确定保留 | `TenderDeduplicationService` 创建时自动检测并抛异常，无管理员拍板入口。 | ❌ 不匹配 |
| 8. 关键字/客户清单 | 初始化由投标系统管理员一次性导入，后续走运维 | 未找到配置接口。 | ❓ 待确认 |
| 9. bidAdmin/bidTeamLeader 强行转派 | 任何状态可强行干预转派项目负责人 | `TenderTransferService` 限制只能在 `TRACKING` / `EVALUATED` 转派，且 Controller 无角色限制。 | ❌ 不匹配 |

---

## 5. 汇总

### 5.1 完全匹配项
1. 角色映射整体对应关系成立（文档 7 个角色均能在代码中找到 roleCode）。
2. 手动录入、批量导入、下载模板的角色白名单与文档基本一致。
3. 填写/提交评估表的实例级权限由分配记录控制，符合“被分配的标讯”。
4. 标讯状态枚举及状态迁移规则与文档一致。
5. 弃标/未中标后列表可见性不受状态影响。
6. bidAdmin 与 bid-TeamLeader 当前权限完全一致。

### 5.2 部分匹配 / 实现口径偏窄项
1. **bid-Team 的列表/详情数据范围**：文档要求“仅分配给自己的”，代码按 `dataScope=self` 实现为“自己创建/负责”。
2. **bid-projectLeader 的编辑权限**：文档“跟踪中可自由编辑、已评估可编辑”，代码对 STAFF 仅允许 `PENDING_ASSIGNMENT` 编辑。
3. **删除的状态限制**：文档“未评估可删除”，代码实现为“仅 PENDING_ASSIGNMENT 可删除”。
4. **确认/放弃投标**：功能上 bidAdmin/bid-TeamLeader 可通过，但 Controller 层未显式限制角色，依赖 Service 层 `canDecide`。
5. **确认投标后创建项目**：存在多个立项入口，调用路径不统一。

### 5.3 不匹配 / 需要修复项
1. **转派接口未限制角色**（`TenderTransferController` 仅 `isAuthenticated()`）→ 任何登录用户可调。
2. **编辑/删除接口把 bid-Team 放进白名单**：`PUT /api/tenders/{id}` 与 `DELETE /api/tenders/{id}` 使用 `ADMIN/MANAGER/STAFF`，导致 bid_specialist 可调，与文档“—”冲突。
3. **bidAdmin/bid-TeamLeader 的编辑未按“未立项”收口**：对 ADMIN/MANAGER 未校验 BIDDING/WON/LOST/ABANDONED 不可编辑。
4. **去重冲突拍板无角色入口**：当前是创建前硬拦截，无管理员最终决定/组长提报流程。
5. **评估表规则冲突**：文档“不支持草稿、不可修改”，代码支持草稿且支持已评估后重新编辑（V130）。
6. **强行干预转派**：文档“任何状态可强行转派”，代码限制为 TRACKING/EVALUATED，且 Controller 未限制角色。

### 5.4 缺失 / 未实现项
1. **标讯列表导出**：后端无对应接口。
2. **粘贴识别（AI）后端接口**：未在 TenderController 中找到。
3. **关键字/目标客户清单配置接口**：未找到。
4. **独立的分发/转派变更日志查询接口**：未找到。

### 5.5 代码级风险项
1. `AssignmentPermissionRules.canEditTender` 为**死代码**，无调用方，与 `TenderCommandAccessGuard` 规则并存但互不一致，容易误导维护。
2. `TenderController` 的 `participateBid/abandonBid` 与 `TenderEvaluationController` 的 `reviewTender/proceedToBid` 存在**接口职责重叠**，可能导致同一业务有两种调用路径。
3. `TenderProjectAccessGuard` 按 `dataScope=self` 过滤时检查 `creatorId/biddingPersonId/projectManagerId`，但 ** Tender 自动分配成功后未在代码中设置 `projectManagerId`**（`TenderCommandService.tryAutoAssign` 仅改状态），可能导致被自动分配的项目负责人无法看到自己应负责的标讯。
4. `TenderTransferController` 的类/方法级 `@PreAuthorize` 与注释描述不一致，属于典型的“注释说一套、代码做一套”。

---

## 6. 建议修复优先级

| 优先级 | 事项 | 影响 |
|---|---|---|
| P0 | 给 `TenderTransferController.transferTender` 加上 `@PreAuthorize("hasAnyRole('ADMIN','BID_LEAD','BID_SENIOR')")` | 安全漏洞，任何登录用户可转派 |
| P0 | 明确 `PUT/DELETE /api/tenders/{id}` 角色白名单，把 bid_specialist（STAFF）剔除，或按文档在 Service 层显式拒绝 | 越权编辑/删除 |
| P1 | 统一“确认投标/弃标/立项”接口，Controller 层显式限制为 bidAdmin/bid-TeamLeader | 权限表达不清晰 |
| P1 | 按文档补齐 bid-projectLeader 的状态递减权限（跟踪中可编辑、已评估可编辑不可删除） | 功能缺失 |
| P1 | 修复自动分配后未设置 `projectManagerId` 的问题 | 数据范围过滤失效 |
| P2 | 补齐导出、AI 粘贴识别、关键字/客户清单配置、分发变更日志等缺失接口 | 功能未实现 |
| P2 | 清理/替换死代码 `AssignmentPermissionRules.canEditTender`，或让现有守卫调用它 | 维护风险 |

---

*报告结束。*
