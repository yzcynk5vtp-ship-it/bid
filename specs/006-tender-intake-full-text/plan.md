# Implementation Plan: 标讯识别抽取完整招标公告原文到 tenderInfo 字段

**Branch**: `agent/claude/tender-intake-full-text-extraction` | **Date**: 2026-06-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-tender-intake-full-text/spec.md`

## Summary

后端 AI 标讯识别 prompt（`TenderDocumentPrompts.buildTenderIntakePrompt`）新增 `tenderInfo` 字段抽取指令，让 AI 输出完整招标公告原文；输出结构 `TenderRequirementOutput` 新增 `tenderInfo` 字段，通过 `mergeAndMap` 映射到 `extractedData`，复用前端已有的 `tenderInfo→tenderInfo` 回填映射。前端 `tenderInfo` 输入框 maxlength 从 5000 提升到 20000，校验规则同步。数据库 `tenders.tender_info` 字段从 `VARCHAR(5000)` 迁移到 `TEXT`（支持 65535 字节），蓝图配置 V1007 中 `tenderInfo.maxLength=5000` 通过新增 V 脚本更新为 20000。

## Technical Context

**Language/Version**: Java 21（后端）+ JavaScript/ES2022（前端）

**Primary Dependencies**: Spring Boot 3.2 + Spring Data JPA + Flyway + MySQL 8.0 JDBC Driver | Vue 3 + Vite 5 + Element Plus | OpenAI 兼容协议 AI 客户端（DeepSeek/通义千问/豆包）

**Storage**: MySQL 8.0（`tenders.tender_info` 字段，当前 `VARCHAR(5000)`，需迁移到 `TEXT`）

**Testing**: JUnit 5 + Mockito + MockMvc（后端单元/集成测试）| Vitest（前端单元测试）+ Playwright（E2E）

**Target Platform**: Linux 服务器（生产）+ macOS 本地开发（主工作区 trae）

**Project Type**: Web application（backend + frontend 单仓库）

**Performance Goals**: AI 识别完整招标公告原文（≤20000 字）在 PT45S 超时内完成（如不够则调整 `ai.deepseek.tender-intake-timeout` 到 PT90S）

**Constraints**: 
- 单文件 Java 硬上限 300 行（棘轮门禁）
- `TenderRequirementOutput` 当前是 record，新增字段必须保持 record 风格
- Flyway 迁移脚本不可变（不能改 V1006/V1007，必须新增 V 脚本）
- 必须 avoiding MySQL 1093 错误（LL-007 教训）

**Scale/Scope**: 改动文件数 ~10 个，新增代码 ~150 行，影响 1 个 AI prompt + 1 个 record + 1 个 JPA 实体字段 + 1 个 DTO 校验 + 2 个 Flyway 迁移脚本 + 2 个前端文件 + 测试

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 通过 |
|---|---|---|
| I. FP-Java Architecture | `TenderRequirementOutput` 是 record（纯核心数据），新增 `tenderInfo` 字段保持 record 风格；`OpenAiTenderDocumentAnalyzer` 是 imperative shell，调用 AI 并映射结果；`TenderDocumentPrompts` 是 prompt 模板（无业务逻辑）。符合分层 | ✅ |
| II. Real-API Only | 不引入 Mock，AI 调用走真实 OpenAI 兼容协议。测试中的 AI 调用使用现有 Mockito mock 模式（`OpenAiStructuredOutputService` 已有 mock 支持） | ✅ |
| III. Test-Driven Development | 严格 Red→Green→Refactor：先写 `TenderRequirementOutputTest` 验证 tenderInfo 字段存在，再写 `OpenAiTenderDocumentAnalyzerTest` 验证映射，最后实现。E2E 覆盖 `/bidding/create` 上传文件后 tenderInfo 回填 | ✅ |
| IV. Split-First & Simplicity | 改动范围小，不涉及拆分。`TenderDocumentPrompts.java` 当前 ~72 行，新增 tenderInfo 指令后约 80 行，远低于 200 行软上限 | ✅ |
| V. OSS Integration | 不涉及 OSS 集成 | ✅ N/A |
| VI. Boring Proven Patterns | `TEXT` 字段类型是 MySQL 标准实践；`@Size(max=20000)` 是标准 Bean Validation；前端 maxlength 是 Element Plus 标准用法。无魔法特性 | ✅ |
| Code Quality Gates | Checkstyle + PMD + SpotBugs 必须全绿；前端 `check:front-data-boundaries` + `check:line-budgets` 必须通过 | ✅ |
| Security & Access Control | 不涉及权限调整。`/api/doc-insight/parse` 现有 `@PreAuthorize` 不变 | ✅ |
| Development Workflow | 已执行 sync-env.sh + 早操；已通过 who-touches.sh 检查（本路径无其他 agent 改动）；将 push WIP 分支 | ✅ |
| DB Migrations | 新增 V 脚本 + 对应 U 脚本（回滚）。脚本放在 `backend/src/main/resources/db/migration-mysql/`。避免 MySQL 1093 错误 | ✅ |

**Gate Result**: ✅ 全部通过，无违反需说明。

## Project Structure

### Documentation (this feature)

```text
specs/006-tender-intake-full-text/
├── plan.md              # 本文件
├── research.md          # Phase 0 research 输出
├── data-model.md        # Phase 1 数据模型
├── quickstart.md        # Phase 1 快速开始
├── contracts/           # Phase 1 接口契约
│   └── ai-tender-intake-output.md   # AI 输出结构契约
└── tasks.md             # Phase 2 任务分解（/speckit-tasks 生成）
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/xiyu/bid/
│   ├── biddraftagent/infrastructure/openai/
│   │   ├── TenderDocumentPrompts.java          # 修改：buildTenderIntakePrompt 增加 tenderInfo 字段指令
│   │   ├── TenderRequirementOutput.java        # 修改：新增 tenderInfo 字段（record component）
│   │   └── OpenAiTenderDocumentAnalyzer.java   # 修改：mergeAndMap/putTenderIntakeFields 增加 tenderInfo 映射
│   ├── entity/Tender.java                       # 修改：@Column length=5000 → columnDefinition="TEXT"
│   └── tender/dto/TenderRequest.java            # 修改：@Size(max=5000) → @Size(max=20000)
├── src/main/resources/db/migration-mysql/
│   ├── V1xxx__expand_tender_info_to_text.sql           # 新增：VARCHAR(5000) → TEXT
│   ├── V1xxx__expand_tender_info_to_text_rollback.sql  # 新增：回滚脚本（U 脚本）
│   ├── V1yyy__update_tender_blueprint_maxlength.sql    # 新增：更新 V1007 蓝图配置 tenderInfo.maxLength=20000
│   └── V1yyy__update_tender_blueprint_maxlength_rollback.sql  # 新增：回滚脚本
└── src/test/java/com/xiyu/bid/
    ├── biddraftagent/infrastructure/openai/
    │   ├── TenderDocumentPromptsTest.java      # 新增/修改：验证 prompt 包含 tenderInfo 指令
    │   ├── TenderRequirementOutputTest.java    # 新增：验证 record 序列化包含 tenderInfo
    │   └── OpenAiTenderDocumentAnalyzerTest.java  # 修改：验证 tenderInfo 映射到 extractedData
    └── tender/
        └── TenderIntegrationTest.java          # 修改：验证 20000 字 tenderInfo 持久化成功

src/
├── views/Bidding/list/components/
│   └── TenderBasicInfoTab.vue                  # 修改：maxlength=5000 → 20000；增加超长截断提示
├── views/Bidding/list/
│   ├── constants.js                            # 修改：tenderInfo 校验规则 max:5000 → 20000
│   └── composables/
│       └── useTenderAiParse.js                 # 验证：现有 tenderInfo→tenderInfo 映射无需改动（仅确认）
└── api/modules/
    └── tenders.js                              # 验证：API 调用无需改动

e2e/
└── tender-create-parse.spec.js                 # 修改：新增 AI 识别后 tenderInfo 回填验证
```

**Structure Decision**: 本项目是已有 web application，复用现有 `backend/` + `src/` 结构。改动分布在 `biddraftagent`（AI 识别核心）、`entity`（JPA 实体）、`tender/dto`（请求 DTO）、`db/migration-mysql`（迁移脚本）、`views/Bidding`（前端页面）5 个位置，均为最小侵入式改动，不新增模块。

## Complexity Tracking

> 无 Constitution Check 违反，无需填表。

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | N/A | N/A |
