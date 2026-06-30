# Feature Specification: MySQL Integration Test Rollout for Role Resolution & Tender Commands

**Feature Branch**: `agent/codex/mysql-integration-test-rollout`（spec 目录 `specs/005-mysql-integration-test-rollout`，遵循 AGENTS.md 多 Agent worktree 命名约定，不使用 speckit 默认 `005-` 分支）

**Created**: 2026-06-30

**Status**: Draft

**Input**: User description: "将 MySQL 集成测试模式推广到 EffectiveRoleResolver、TenderCommandService"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 开发者能在真实 MySQL 下回归验证角色解析正确性 (Priority: P1)

作为后端开发者，我希望 EffectiveRoleResolver 在真实 MySQL 8.0 + 真实 Flyway 全迁移链下被集成测试覆盖，这样我能防止 CO-361 / CO-373 类"五次反复修复"的回归再次发生——尤其是 OSS 用户 `role_id=NULL` 时实体 `getRoleCode()` 回退 `"manager"` 的根因，以及缓存 miss 时跨表查询 `users.role_id` + `roles.code` 的真实 SQL 行为。

**Why this priority**: EffectiveRoleResolver 是 CO-373 治理的产物，也是服务层权限校验的**唯一**角色码读取入口。任何回归都会直接导致权限漏洞或越权。当前纯 Mockito unit test 完全无法捕获 DB 层真实行为，是已知最高风险缺口。

**Independent Test**: 测试可独立运行——启动 Testcontainers MySQL，注入真实 OSS 用户（`role_id=NULL`）和本地用户数据，调用 `EffectiveRoleResolver.resolveRoleCode(user)`，断言返回的角色码与 source 标签。不依赖 TenderCommandService 或任何其他业务流程。

**Acceptance Scenarios**:

1. **Given** OSS 用户在 `users` 表中 `role_id=NULL`、`external_org_source_app='oss'`，缓存未命中，**When** 调用 `resolveRoleCode(user)`，**Then** 返回 `null`（fail-closed，**不**回退 `"manager"`），source 标签为 `CACHE_MISS_FAIL_CLOSED`，且不调用 `user.getRoleCode()` 实体方法
2. **Given** OSS 用户在 `users` 表中 `role_id` 指向 `roles` 表中 `code='bid-Team'` 的真实记录，缓存未命中，**When** 调用 `resolveRoleCode(user)`，**Then** 返回 `"bid-Team"`（来自实体），source 标签为 `CACHE_MISS_FAIL_CLOSED`
3. **Given** OSS 用户缓存命中（`RoleCodeCachePort` 返回 `"bid-projectLeader"`），**When** 调用 `resolveRoleCode(user)`，**Then** 返回缓存值，**不**回退查 `users` 表（验证缓存优先语义）
4. **Given** 本地用户（`external_org_source_app` 为空或 null），缓存未命中，**When** 调用 `resolveRoleCode(user)`，**Then** 返回实体 `getRoleCode()`（如 `"manager"`），source 标签为 `LOCAL_USER`
5. **Given** 空字符串 `external_org_source_app`（边界：归一化为本地用户），**When** 调用 `resolveRoleCode(user)`，**Then** 行为与场景 4 一致

---

### User Story 2 - 开发者能在真实 MySQL 下回归验证标讯命令的事务一致性与 DB 约束 (Priority: P1)

作为后端开发者，我希望 TenderCommandService 的关键写操作（`linkCrmOpportunity`、`assignOnCrmLink`、`tryAutoAssign`、`deleteTender`）在真实 MySQL 8.0 下被集成测试覆盖，这样我能验证 DB 层 UNIQUE 约束、跨表事务一致性、失败回滚等 Mock 测试无法覆盖的行为，防止 CO-297 类重复分配、CO-261 类通知漏发、CO-333 类自动分配误匹配的回归。

**Why this priority**: TenderCommandService 是标讯模块的核心写服务，承载 CO-261/CO-265/CO-297/CO-333 等多次治理的业务逻辑。现有纯 Mock 测试只能验证"调用了哪些方法"，无法验证"DB 真实落库了什么、事务真实回滚了没、UNIQUE 约束真实挡住了没"。这些是生产事故的高发区。

**Independent Test**: 测试可独立运行——启动 Testcontainers MySQL，注入真实 `Tender` 记录、`TenderAssignmentRecord` 记录、CRM 占用记录，调用 TenderCommandService 的写方法，断言 DB 真实状态（用 `JdbcTemplate` 直查）和异常传播。不依赖 EffectiveRoleResolver 的集成测试。

**Acceptance Scenarios**:

1. **Given** 已存在的 `Tender` 记录，其 `crm_opportunity_id` 已被另一个 Tender 占用，**When** 调用 `linkCrmOpportunity(tenderId, crmOpportunityId)`，**Then** 抛 409 异常，**且**原 Tender 的 `crm_opportunity_id` **未**被覆盖（DB 层 UNIQUE 约束 + 应用层 `crmOccupancyChecker` 双层防御，CO-297）
2. **Given** CRM 链接成功后触发 `assignOnCrmLink`，**When** 自动分配执行，**Then** `TenderAssignmentRecord` 与 `Tender` 在同一事务中真实落库（跨表一致性），Tender 状态变为 `TRACKING`，assignee 记录可被 `JdbcTemplate` 查到
3. **Given** `tryAutoAssign` 因姓名无匹配而失败，**When** 异常向上传播，**Then** Tender 状态**未**变为 `TRACKING`，assignee 记录**未**落库（事务真实回滚，验证 `@Transactional` 行为）
4. **Given** Tender 关联了多个附件记录，**When** 调用 `deleteTender(tenderId)`，**Then** Tender 与附件在同一事务中删除，DB 中无法再查到（级联事务一致性）
5. **Given** 同一采购方 hash 的 Tender 已存在（`purchaser_hash` UNIQUE 约束），**When** 调用 `createTender` 创建重复，**Then** 抛约束冲突异常，CO-265 重复检测生效

---

### User Story 3 - 测试维护者能复用统一基类快速添加新 MySQL 集成测试 (Priority: P2)

作为测试维护者，我希望本次推广明确建立"业务服务 MySQL 集成测试"的范本（基类复用、依赖打通、数据清理策略），这样后续给其他服务（如 BidCommandService、PlatformAccountService 等）补集成测试时能直接套用，不必每次重新设计。

**Why this priority**: 项目已有 `AbstractMysqlIntegrationTest` 基类和 `PlatformAccountBorrowServiceMysqlIntegrationTest` 范本，但仅 1 个业务服务样例，模式尚未沉淀为团队共识。本次推广到 EffectiveRoleResolver + TenderCommandService 后，范本数增至 3 个，覆盖纯查询服务（Resolver）和写服务（CommandService）两类典型场景，足以形成可复用模式。

**Independent Test**: 通过代码审查验证——新增的两个集成测试类都继承 `AbstractMysqlIntegrationTest`，使用 `@ActiveProfiles("flyway-mysql")` + `@Import(NoOpPasswordEncryptionTestConfig.class)`，用 `@BeforeEach` + `JdbcTemplate.update("DELETE ...")` 清理数据，类名遵循 `XxxServiceMysqlIntegrationTest` 约定，放在被测类同包下。

**Acceptance Scenarios**:

1. **Given** 一个新的业务服务需要补 MySQL 集成测试，**When** 开发者参考本次产出的两个测试类，**Then** 能在不修改基类的前提下完成新测试（基类无需为本次任务改动）
2. **Given** CI 环境（GitHub Actions）与本地环境（macOS + 手动 MySQL 容器），**When** 新测试运行，**Then** 两个环境都能正常跑通（基类已处理 CI/本地 fallback，新测试无需重复处理）
3. **Given** `mvn test` 运行所有测试，**When** 新集成测试加入，**Then** 既有 unit test 全部通过，新集成测试不破坏现有测试套件

---

### Edge Cases

- 当 Testcontainers 不可用（无 Docker）时，集成测试如何处理？—— 基类 `AbstractMysqlIntegrationTest` 已设计 fail-fast（不带 `disabledWithoutDocker=true`），本地无 Docker 时会启动失败而非静默跳过，本次新增测试继承此行为
- 当 `RoleCodeCachePort` 真实适配器（`crm.application.OssPermissionCache`）依赖外部 OSS API 时，集成测试如何隔离？—— 通过 `@Import` 注入测试专用的 `RoleCodeCachePort` 实现（内存 Map），不调用真实 OSS
- 当 `TenderCommandService` 的 `@Auditable` 切面在测试上下文中行为异常时？—— ArchitectureTest.RULE 17 已将 TenderCommandService 列入白名单（已知 `@Transactional` + `@Auditable` 债务），集成测试应关注事务行为本身，不验证审计日志写入
- 当 `mvn test` 因新增集成测试而显著变慢时？—— 当前无 failsafe 分阶段，所有测试在同一 phase 跑；需评估新增测试的总耗时，若超过团队阈值（建议 < 60s 增量），后续可拆分 unit/integration 阶段
- 当 TenderCommandService 有 15 个依赖注入字段时，集成测试如何构造？—— 用 `@Autowired` 注入真实 Spring 上下文中的 Bean，不手动 `new`；对非测试关注的依赖（如通知发送、CRM API 调用）用 `@MockBean` 替换

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 新增 `EffectiveRoleResolverMysqlIntegrationTest` MUST 覆盖 OSS 用户 `role_id=NULL` 时 fail-closed 返回 `null` 的真实 DB 行为，且不回退实体 `getRoleCode()` 的 `"manager"` 默认值
- **FR-002**: 新增 `EffectiveRoleResolverMysqlIntegrationTest` MUST 覆盖缓存 miss 时跨表查询 `users.role_id` → `roles.code` 的真实 SQL 路径（通过 `RoleCodeCachePort` 适配器或等价测试 stub）
- **FR-003**: 新增 `EffectiveRoleResolverMysqlIntegrationTest` MUST 覆盖 `external_org_source_app` 字段在 MySQL 中的真实存储与查询（空字符串、null、非空值三种状态）
- **FR-004**: 新增 `TenderCommandServiceMysqlIntegrationTest` MUST 覆盖 `linkCrmOpportunity` 的 DB 层 UNIQUE 约束（`crm_opportunity_id` 占用检测）与应用层 `crmOccupancyChecker` 双层防御（CO-297）
- **FR-005**: 新增 `TenderCommandServiceMysqlIntegrationTest` MUST 覆盖 `assignOnCrmLink` 写 `TenderAssignmentRecord` 与 `Tender` 状态更新的跨表事务一致性
- **FR-006**: 新增 `TenderCommandServiceMysqlIntegrationTest` MUST 覆盖 `tryAutoAssign` 失败时事务真实回滚（Tender 状态未变、assignee 未落库）
- **FR-007**: 新增 `TenderCommandServiceMysqlIntegrationTest` MUST 覆盖 `deleteTender` 与附件的级联事务一致性
- **FR-008**: 新增 `TenderCommandServiceMysqlIntegrationTest` MUST 覆盖 `createTender` 的 `purchaser_hash` UNIQUE 约束（CO-265 重复检测）
- **FR-009**: 两个新集成测试类 MUST 继承 `AbstractMysqlIntegrationTest` 基类，使用 `@ActiveProfiles("flyway-mysql")` + `@Import(NoOpPasswordEncryptionTestConfig.class)`
- **FR-010**: 两个新集成测试类 MUST 用 `@BeforeEach` + `JdbcTemplate.update("DELETE ...")` 清理数据，**不**在测试方法上加 `@Transactional`（让 Service 的 `@Transactional` 真实提交/回滚）
- **FR-011**: 两个新集成测试类 MUST 不破坏既有 Mockito unit test（`EffectiveRoleResolverTest`、`TenderCommandServiceTest`、`TenderCommandServiceLinkCrmOpportunityDedupTest` 全部继续通过）
- **FR-012**: 两个新集成测试类 MUST 在 `mvn test` 中默认运行（当前无 failsafe 分阶段，无需额外配置）

### Key Entities *(include if feature involves data)*

- **EffectiveRoleResolver**: 服务层权限校验的角色码读取外壳，读 OSS 缓存（`RoleCodeCachePort`）→ 委托纯核心 `EffectiveRolePolicy.decide` → 按 source 分级记日志。CO-373 治理产物，服务层调用 `User.getRoleCode()` 的唯一合法替代入口
- **RoleCodeCachePort**: OSS 角色码缓存接口（位于 security 包），打破 security↔crm 循环依赖。真实适配器为 `crm.application.OssPermissionCache`
- **User**: 用户实体，`role_id` 可空（OSS 用户常为 NULL），`external_org_source_app` 标识用户来源（OSS / 本地）
- **TenderCommandService**: 标讯模块核心写服务，承载 createTender / updateTender / deleteTender / linkCrmOpportunity / applyAssignmentResult 等命令。ArchitectureTest.RULE 17 白名单成员（`@Transactional` + `@Auditable` 已知债务）
- **TenderAssignmentRecord**: 标讯分配记录实体，与 Tender 跨表事务写入
- **AbstractMysqlIntegrationTest**: 已有的 MySQL 集成测试基类，处理 CI（Testcontainers）/本地（手动容器）fallback、sql_mode 调整、utf8mb4_unicode_ci 校对设置

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 新增的 EffectiveRoleResolver 集成测试覆盖至少 5 个 DB round-trip 场景（OSS role_id=NULL 命中/未命中、缓存命中/未命中、本地用户、空字符串边界），全部在真实 MySQL 8.0 下通过
- **SC-002**: 新增的 TenderCommandService 集成测试覆盖至少 5 个 DB round-trip 场景（linkCrmOpportunity 双层防御、assignOnCrmLink 跨表一致性、tryAutoAssign 失败回滚、deleteTender 级联事务、createTender 重复检测），全部在真实 MySQL 8.0 下通过
- **SC-003**: 新增集成测试运行后，既有 Mockito unit test 全部继续通过（零回归），`mvn test` 整体绿
- **SC-004**: 新增集成测试在 CI（GitHub Actions，Testcontainers）和本地（手动 MySQL 容器）两种环境下均可运行（基类 fallback 机制无需为新测试修改）
- **SC-005**: 新增集成测试的增量运行时间不超过 60 秒（不含 Spring 上下文首次启动），不显著拖慢 `mvn test` 反馈循环
- **SC-006**: 本次产出的两个测试类可作为后续其他业务服务（BidCommandService、PlatformAccountService 等）补 MySQL 集成测试的直接范本，无需修改 `AbstractMysqlIntegrationTest` 基类

## Assumptions

- 假设开发者已在本地或 CI 环境配置好 Docker（Testcontainers 依赖），无 Docker 时测试 fail-fast
- 假设 `AbstractMysqlIntegrationTest` 基类的 CI/本地 fallback 设计无需为新测试修改（已通过 `PlatformAccountBorrowServiceMysqlIntegrationTest` 验证）
- 假设 `RoleCodeCachePort` 在集成测试中可通过 `@Import` 注入测试专用实现（内存 Map），无需启动真实 OSS API 调用
- 假设 `TenderCommandService` 的非测试关注依赖（通知发送 `NotificationService`、CRM API `CrmClient` 等）可用 `@MockBean` 替换，不影响事务行为验证
- 假设本次任务不修改 main 代码（仅新增测试类 + 可能的测试支持配置），不触发 ArchitectureTest RULE 检查
- 假设 `mvn test` 当前无 failsafe 分阶段，新增集成测试会与 unit test 同 phase 运行；若增量耗时超阈值，后续可单独拆分（本次不做）
- 依赖现有基础设施：`AbstractMysqlIntegrationTest`、`NoOpPasswordEncryptionTestConfig`、`application-flyway-mysql.yml`、testcontainers 依赖（pom.xml 已就绪）
