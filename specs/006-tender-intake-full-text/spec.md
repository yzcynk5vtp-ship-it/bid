# Feature Specification: 标讯识别抽取完整招标公告原文到 tenderInfo 字段

**Feature Branch**: `agent/claude/tender-intake-full-text-extraction`

**Created**: 2026-06-30

**Status**: Draft

**Input**: User description: "/bidding/create 用 AI 识别标讯的时候，应该把标讯讯息全部识别出来了，然后放到标讯信息这个自动，同时要判断一下 5000 字够不够"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - AI 识别后自动回填完整招标公告原文 (Priority: P1)

销售人员在 `/bidding/create` 新建标讯页面，通过粘贴文本或上传招标文件触发 AI 识别。识别成功后，系统不仅回填现有的 25 个结构化字段（项目名称、采购人、预算、截止时间等），还必须把招标公告的完整原文抽取出来，自动填到"标讯信息"字段（tenderInfo）中。销售人员无需再手工复制粘贴公告原文，识别完成后即可一眼看到完整讯息。

**Why this priority**: 这是本次需求的核心价值——让"标讯信息"字段从"用户手工填写"升级为"AI 自动回填完整原文"，节省销售 5-15 分钟手工整理时间，避免遗漏关键条款。

**Independent Test**: 上传一份完整的招标文件（含项目概况、资格要求、技术要求、评分办法等章节），识别完成后查看"标讯信息"字段，应包含招标公告的完整原文（不仅是 120 字摘要），可与原文件对照验证内容完整性。

**Acceptance Scenarios**:

1. **Given** 销售人员在 `/bidding/create` 页面，"标讯信息"字段为空，**When** 销售人员上传一份 8000 字的招标文件并触发 AI 识别，**Then** 识别完成后，"标讯信息"字段自动回填完整招标公告原文（约 8000 字），且 25 个结构化字段（项目名称、采购人、预算等）同步回填，无字段被覆盖或丢失
2. **Given** 销售人员在"粘贴识别"文本框中粘贴一段 3000 字的招标公告文本，**When** 点击"识别粘贴文字"按钮，**Then** "标讯信息"字段自动回填这段 3000 字的完整原文，同时结构化字段（标题、采购人、截止时间等）从原文中抽取回填
3. **Given** 销售人员上传的招标文件较短（仅 500 字），**When** AI 识别完成，**Then** "标讯信息"字段回填这 500 字原文，不出现截断、不出现"已截断"提示、不出现空白

---

### User Story 2 - 20000 字以内的招标公告原文不被截断 (Priority: P1)

销售人员上传的招标文件可能包含 5000-20000 字的招标公告正文。系统必须保证 20000 字以内的原文能完整抽取并保存到 tenderInfo 字段，不因前端 maxlength、数据库字段长度或后端 prompt 限制而被截断。

**Why this priority**: 用户明确要求"完整招标公告原文"，若 20000 字以内的原文被截断，会丢失关键条款（资格要求、评分办法等），导致销售人员误判项目风险，违反"功能完整性"原则。

**Independent Test**: 分别上传 5000 字、10000 字、20000 字的招标文件，识别完成后查看"标讯信息"字段，三种情况下原文都必须完整无截断。

**Acceptance Scenarios**:

1. **Given** 销售人员上传一份 18000 字的招标文件，**When** AI 识别完成，**Then** "标讯信息"字段回填 18000 字原文，前端不报"超出 5000 字限制"错误，数据库保存后查询返回的 tender_info 字段值长度等于 18000 字
2. **Given** 销售人员上传一份 25000 字的超长招标文件，**When** AI 识别完成，**Then** "标讯信息"字段回填前 20000 字原文，并在字段下方提示"原文超过 20000 字上限，已截断"（不报错，仅提示）
3. **Given** 数据库中已存在 tender_info 字段值为 15000 字的旧记录，**When** 销售人员编辑该标讯并保存，**Then** 15000 字原文不丢失、不截断，保存成功

---

### User Story 3 - 数据库字段容量与前端限制同步提升 (Priority: P2)

为支持 20000 字的 tenderInfo 容量，数据库 `tender_info` 字段类型如不足 20000 字（如当前为 VARCHAR(5000)），必须通过 Flyway 迁移脚本升级到 TEXT 或 MEDIUMTEXT。前端 `tenderInfo` 输入框的 maxlength 与校验规则同步从 5000 提升到 20000。

**Why this priority**: 这是支撑 User Story 1 和 2 的基础设施改动，没有数据库容量提升，前端 maxlength 改了也保存不进去。

**Independent Test**: 执行 Flyway 迁移脚本后，查询数据库表结构确认 `tender_info` 字段类型为 TEXT 或更大容量；前端"标讯信息"输入框允许输入 20000 字不报错。

**Acceptance Scenarios**:

1. **Given** 数据库 `tender_info` 当前为 VARCHAR(5000)，**When** 执行 Flyway 迁移脚本，**Then** 字段类型变为 TEXT（支持 65535 字节）或 MEDIUMTEXT（支持 16MB），且迁移脚本能通过 `check-flyway-rollback.sh` 验证有对应回滚脚本
2. **Given** 前端"标讯信息"输入框当前 maxlength=5000，**When** 改为 maxlength=20000，**Then** 输入 20000 字时不被截断，校验规则同步更新为"标讯信息不能超过 20000 字符"
3. **Given** 已有标讯记录的 tender_info 字段值长度 ≤5000 字，**When** 执行迁移脚本，**Then** 旧数据不丢失、不损坏，查询返回值与迁移前一致

---

### Edge Cases

- **当招标文件是扫描件（图片型 PDF）时**：后端 `DocumentIntelligenceServiceImpl` 已有逻辑检测 extractedText.length < 100 时跳过 AI 分析返回 SCANNED_DOCUMENT 警告。此时 tenderInfo 字段应为空，不报错。
- **当 AI 服务不可用（余额不足、超时、503）时**：后端走 `requestAiSafe` 返回 null，不阻塞标讯创建流程。tenderInfo 字段保持空，销售人员可手工填写。前端已有 402/502/503/429 错误提示。
- **当招标文件原文超过 20000 字时**：tenderInfo 截断到 20000 字，并在字段下方显示提示"原文超过 20000 字上限，已截断"。不报错、不阻塞保存。
- **当 AI 返回的 tenderInfo 为空字符串或 null 时**：前端 `applyParsedFields` 已有跳过空值逻辑（第 189 行），不会覆盖用户已填的内容。
- **当用户在编辑模式下加载已有标讯时**：`mapTenderToForm` 已有 `tenderInfo: tender.tenderInfo || ''` 回填逻辑，无需改动。
- **当数据库迁移在生产环境失败时**：必须有对应回滚脚本（U 脚本），且按 LL-007 教训，迁移脚本不能使用 MySQL 1093 错误的子查询写法。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 在 AI 识别标讯（profile=TENDER_INTAKE）时，让 AI 模型输出 `tenderInfo` 字段，内容为招标公告的完整原文（包含项目概况、资格要求、技术要求、商务要求、评分办法、联系方式等全部章节）
- **FR-002**: 系统 MUST 保证 `tenderInfo` 字段抽取范围与现有 `tenderScope`（项目概况 ≤120 字摘要）有明确语义区分——`tenderScope` 是 AI 提炼的短摘要，`tenderInfo` 是完整原文，两者并存不互相覆盖
- **FR-003**: 系统 MUST 在 `TenderRequirementOutput` 输出结构中新增 `tenderInfo` 字段，并通过 `mergeAndMap` 映射到 `extractedData`，确保前端 `applyParsedFields` 已有的 `tenderInfo→tenderInfo` 映射能正常工作
- **FR-004**: 系统 MUST 保证现有 25 个结构化字段（projectName、purchaserName、budget、deadline 等）的抽取逻辑不受 tenderInfo 新增影响，回归测试全部通过
- **FR-005**: 前端"标讯信息"输入框（`form.tenderInfo`）的 maxlength MUST 从 5000 提升到 20000，校验规则同步更新为"标讯信息不能超过 20000 字符"
- **FR-006**: 前端 MUST 在用户输入或 AI 回填的 tenderInfo 超过 20000 字时，截断到 20000 字并在字段下方显示"原文超过 20000 字上限，已截断"提示（不报错、不阻塞保存）
- **FR-007**: 数据库 `tender_info` 字段 MUST 能容纳 20000 字以上的内容。如当前字段类型容量不足，MUST 通过 Flyway 迁移脚本升级到 TEXT 或 MEDIUMTEXT，且 MUST 提供对应的回滚脚本（U 脚本）
- **FR-008**: Flyway 迁移脚本 MUST 避免 MySQL 1093 错误（不能在 UPDATE 子查询中直接引用同一张表），按 LL-007 教训使用派生表包装
- **FR-009**: 系统 MUST 在 AI 失败时（402/502/503/429/超时）保持现有容错行为——返回 null 不抛异常，tenderInfo 字段为空，销售人员可手工填写
- **FR-010**: 系统 MUST 在招标文件为扫描件（提取文本 <100 字）时跳过 AI 分析，tenderInfo 字段为空，返回 SCANNED_DOCUMENT 警告

### Key Entities *(include if feature involves data)*

- **TenderRequirementOutput**: AI 标讯识别的输出结构。新增 `tenderInfo` 字段（String，无长度限制，由 AI 输出完整原文）。现有 25 个字段保持不变。
- **DocumentAnalysisResult.extractedData**: Map<String, Object> 结构，承接 AI 输出的所有字段。新增 `tenderInfo` 键，由 `mergeAndMap` 方法映射。
- **Tender (frontend form)**: 前端标讯表单。`tenderInfo` 字段 maxlength 从 5000 提升到 20000。
- **Tender Entity (backend)**: 后端标讯实体。`tenderInfo` 字段对应的数据库列 `tender_info` 类型可能需要从 VARCHAR(5000) 升级到 TEXT。
- **Flyway Migration**: 新增 V 脚本（迁移）+ U 脚本（回滚），用于数据库字段类型升级。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 销售人员在 `/bidding/create` 页面上传一份 8000 字的完整招标文件并触发 AI 识别后，"标讯信息"字段在 60 秒内自动回填完整招标公告原文，原文长度与上传文件提取的文本长度差异 ≤5%（允许 AI 微调格式但不可截断内容）
- **SC-002**: 20000 字以内的招标公告原文在识别、回填、保存、查询全链路中无截断、无丢失，数据库查询返回的 `tender_info` 字段值长度等于 AI 输出的原文长度
- **SC-003**: 现有 25 个结构化字段（projectName、purchaserName、budget、deadline 等）的 AI 抽取准确率与改动前相比无回归（以现有单元测试通过率 100% 为准）
- **SC-004**: 前端"标讯信息"输入框允许用户输入或 AI 回填最多 20000 字，超过时显示"原文超过 20000 字上限，已截断"提示而非报错
- **SC-005**: Flyway 迁移脚本在生产环境执行成功率 100%，且 `check-flyway-rollback.sh` 验证回滚脚本可正常执行
- **SC-006**: 销售人员完成一次"上传招标文件→查看标讯信息字段→确认完整无截断"的操作总耗时 ≤60 秒（含 AI 识别时间），相比改动前的手工复制粘贴流程节省 5-15 分钟

## Assumptions

- **AI 模型能力假设**: OpenAI 兼容协议下的 DeepSeek/通义千问/豆包模型均能在 max_tokens 配置范围内输出 20000 字的完整原文。当前 `resolveTenderIntake()` 超时配置为 PT45S（可调），如长文本输出导致超时，将通过 `ai.deepseek.tender-intake-timeout` 配置项调整。
- **数据库字段当前类型假设**: 假设 `tender_info` 当前可能是 VARCHAR(5000) 或 TEXT，需在 plan 阶段通过 `docs/generated/db-schema.md` 或实际查询确认。如是 TEXT（65535 字节）则无需迁移，只需前端 maxlength 调整；如是 VARCHAR(5000) 则需要 Flyway 迁移到 TEXT。
- **现有 prompt 行为假设**: 现有 `buildTenderIntakePrompt` 抽取 25 个字段且明确禁止输出"投标资格、评分办法、响应材料等全文要求拆解"。本次改动需要调整该指令——允许 AI 输出完整招标公告原文到 tenderInfo 字段，但保持其他 25 个字段的抽取指令不变。
- **前端映射假设**: 前端 `useTenderAiParse.js` 第 167 行已有 `tenderInfo→tenderInfo` 映射，无需新增前端映射代码，只需后端开始返回该字段即可。
- **扫描件与 AI 失败容错假设**: 现有的扫描件检测和 `requestAiSafe` 容错逻辑不需要调整，tenderInfo 字段在 AI 失败时为空，符合销售人员预期。
- **超长原文处理假设**: 超过 20000 字的原文由前端 maxlength 截断并提示，后端不主动截断（除非 AI 模型本身输出限制）。
- **回归测试范围假设**: 现有 `TenderDocumentPrompts`、`OpenAiTenderDocumentAnalyzer`、`TenderRequirementOutput` 的单元测试必须全部通过，不因新增 tenderInfo 字段而破坏。
