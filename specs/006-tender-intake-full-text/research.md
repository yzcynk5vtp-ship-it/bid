# Research: 标讯识别抽取完整招标公告原文到 tenderInfo 字段

**Date**: 2026-06-30
**Feature**: [spec.md](spec.md) | [plan.md](plan.md)

## Research Questions

### RQ-1: 数据库 `tender_info` 字段当前类型是什么？需要迁移吗？

**Decision**: 需要 Flyway 迁移，从 `VARCHAR(5000)` 升级到 `TEXT`。

**Rationale**:
- V1006 迁移脚本第 33 行明确定义：`CALL add_column_if_not_exists('tenders', 'tender_info', 'VARCHAR(5000) NULL COMMENT ''标讯信息''');`
- MySQL 8.0 中 `VARCHAR(5000)` 实际存储上限受 `max_allowed_packet` 和行大小限制（65535 字节总行宽），5000 字符 UTF-8 中文约 15000 字节，加上其他字段已接近行宽上限
- `TEXT` 类型支持 65535 字节，足以容纳 20000 字中文（约 60000 字节 UTF-8）
- 若未来需要超过 65535 字节，可升级到 `MEDIUMTEXT`（16MB），但 20000 字需求下 `TEXT` 足够

**Alternatives Considered**:
- `MEDIUMTEXT`：过度设计，20000 字用不到 16MB
- 保持 `VARCHAR(5000)` 改前端 maxlength：会导致数据库截断，违反 FR-007

---

### RQ-2: `TenderRequirementOutput` 是 record 还是 POJO？如何新增字段？

**Decision**: 是 POJO（mutable POJO with public fields），新增 `public String tenderInfo;` 即可。

**Rationale**:
- 文件第 2 行注释：`// Output: Mutable POJO – tender extraction fields (Jackson + jsonschema-generator compatible)`
- 第 9 行：`public class TenderRequirementOutput {`（无 `record` 关键字）
- 字段全是 `public String`/`public List<String>`，无 `final`，无 getter/setter
- Jackson + jsonschema-generator 自动根据 public 字段生成 JSON schema 传给 AI，新增字段会自动包含

**Alternatives Considered**:
- 改为 record：违反"不引入复杂度"原则，且需要改动所有引用点（约 30 处 `.fieldName` 改为 `.fieldName()`），超出本 feature 范围

---

### RQ-3: `mergeAndMap` 和 `putTenderIntakeFields` 如何映射新字段？

**Decision**: 在 `putTenderIntakeFields` 方法末尾新增 `putIfBlank(data, "tenderInfo", item.tenderInfo);`。

**Rationale**:
- `mergeAndMap`（OpenAiTenderDocumentAnalyzer.java:191-223）已有完整流程：遍历 results → 映射到 profile → 合并 → 写入 `Map<String, Object> data`
- 第 217 行：`if (DocInsightProfiles.isTenderIntake(input.profileCode())) putTenderIntakeFields(data, results);`
- `putTenderIntakeFields`（第 225-236 行）遍历 results 调用 `putIfBlank` 把 tender-intake 专属字段写入 data
- `putIfBlank`（第 238-242 行）有"空值不覆盖"逻辑，符合 FR-004（不覆盖用户已填内容）
- 新增 `tenderInfo` 映射只需 1 行代码，复用现有 `putIfBlank` 机制

**Alternatives Considered**:
- 在 `mergeAndMap` 主流程中直接 `data.put("tenderInfo", ...)`：会绕过 `putIfBlank` 的空值检查，可能覆盖已有值

---

### RQ-4: `buildTenderIntakePrompt` 当前指令与新需求冲突吗？

**Decision**: 有冲突，需调整指令。当前指令明确禁止"全文要求拆解"，新需求要求输出完整原文。

**Rationale**:
- TenderDocumentPrompts.java:49 当前指令：`任务：只抽取这些字段，服务于销售人工核对后保存入库；不要做投标资格、评分办法、响应材料等全文要求拆解。`
- 这条指令是为了避免 AI 输出冗长的 requirementItems（结构化需求项列表），与"输出完整原文到 tenderInfo"不冲突——tenderInfo 是单个 String 字段，不是结构化拆解
- 调整方案：保留"不要做 requirementItems 拆解"指令，新增 tenderInfo 字段说明：`- tenderInfo：招标公告完整原文，包含项目概况、资格要求、技术要求、商务要求、评分办法、联系方式等全部章节；直接复制候选文本中的公告正文，不要摘要、不要改写、不要拆解。`

**Alternatives Considered**:
- 删除"不要做全文要求拆解"指令：会导致 AI 重新输出 requirementItems，违反 FR-004（现有 25 个字段抽取不变）
- 新增独立 prompt 函数 `buildTenderIntakePromptWithFullText`：过度设计，复用现有函数加一个字段指令即可

---

### RQ-5: V1007 蓝图配置如何更新 `tenderInfo.maxLength`？

**Decision**: 新增 V 脚本，用 `UPDATE system_settings SET value = ...` 替换整个 JSON 字符串。

**Rationale**:
- V1007 是 `UPDATE system_settings SET value = '{"fields":[...,{"key":"tenderInfo","maxLength":5000},...]}' WHERE scope = 'tender.entry' AND org_id IS NULL;`
- Flyway 迁移脚本不可变（项目硬约束），不能修改 V1007
- MySQL 8.0 的 `JSON_REPLACE()` 函数可精准更新 JSON 字段：`UPDATE system_settings SET value = JSON_REPLACE(value, '$.fields[*].maxLength', 20000) WHERE scope = 'tender.entry' AND org_id IS NULL AND JSON_EXTRACT(value, '$.fields[*].key') LIKE '%tenderInfo%';` — 但通配符路径在 MySQL 8.0 中行为复杂
- 更稳妥方案：用 `REPLACE(value, '"key":"tenderInfo","label":"标讯信息","type":"TEXTAREA","required":false,"rows":3,"maxLength":5000', '"key":"tenderInfo","label":"标讯信息","type":"TEXTAREA","required":false,"rows":3,"maxLength":20000')` 字符串替换

**Alternatives Considered**:
- 用 `JSON_SET`：需要先 `JSON_EXTRACT` 找到 tenderInfo 字段路径，路径不稳定
- 重新构造完整 JSON：维护成本高，遗漏字段会破坏蓝图配置

**最终方案**: 使用 `REPLACE()` 字符串精准替换 `tenderInfo` 那一段的 `maxLength:5000` 为 `maxLength:20000`，且回滚脚本用反向 `REPLACE`。

---

### RQ-6: AI 模型能否在 PT45S 超时内输出 20000 字？

**Decision**: 建议将 `ai.deepseek.tender-intake-timeout` 从 PT45S 调整为 PT90S，并在 plan 中标注为可选优化项。

**Rationale**:
- DeepSeek chat completions 默认 max_tokens 通常是 4096-8192 tokens，20000 字中文约 6000-8000 tokens，理论可输出
- 但 PT45S 对长文本生成可能紧张（DeepSeek 推理速度约 100-200 tokens/s，8000 tokens 需 40-80 秒）
- OpenAiBidAgentConfigurationResolver.java:31 默认 `PT45S`，可通过 `ai.deepseek.tender-intake-timeout` 环境变量调整
- 建议默认值改为 PT90S，生产环境根据实际响应时间再调优

**Alternatives Considered**:
- 保持 PT45S：可能导致长文本输出超时失败，违反 FR-001
- 改为 PT180S：过度保守，短文本场景浪费时间

---

### RQ-7: 现有测试覆盖情况？哪些测试需要修改？

**Decision**: 现有测试主要覆盖 `buildFullTenderPrompt`（多 chunk 拆解）和 `mergeAndMap` 主流程，`buildTenderIntakePrompt` 单元测试较少。需新增 tenderInfo 字段的单元测试。

**Rationale**:
- `TenderRequirementOutput` 当前无独立单元测试（只是 POJO）
- `OpenAiTenderDocumentAnalyzerTest` 已有 mergeAndMap 测试，需新增 tenderInfo 映射验证
- `TenderDocumentPromptsTest` 需验证 prompt 文本包含 tenderInfo 字段指令
- `TenderIntegrationTest` 需新增 20000 字 tenderInfo 持久化测试

**Alternatives Considered**:
- 仅依赖现有测试：无法验证 tenderInfo 字段被正确映射和持久化，违反 TDD 原则

---

## Summary of Decisions

| 编号 | 问题 | 决策 |
|---|---|---|
| RQ-1 | DB 字段类型 | VARCHAR(5000) → TEXT，新增 V+U 脚本 |
| RQ-2 | Output 结构 | POJO，新增 `public String tenderInfo;` |
| RQ-3 | 字段映射 | `putIfBlank(data, "tenderInfo", item.tenderInfo);` |
| RQ-4 | Prompt 指令 | 保留"不做 requirementItems 拆解"，新增 tenderInfo 字段说明 |
| RQ-5 | 蓝图配置更新 | `REPLACE()` 字符串精准替换 maxLength:5000 → 20000 |
| RQ-6 | AI 超时 | 建议从 PT45S 调整为 PT90S（可选优化） |
| RQ-7 | 测试覆盖 | 新增 3 个测试类 + 修改 1 个集成测试 |
