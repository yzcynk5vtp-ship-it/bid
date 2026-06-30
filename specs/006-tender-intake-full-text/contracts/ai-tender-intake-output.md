# AI Tender Intake Output Contract

**Date**: 2026-06-30
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md)

## Contract: AI 标讯识别（TENDER_INTAKE profile）输出结构

### 上下文

当 `/bidding/create` 页面用户上传招标文件或粘贴文本触发 AI 识别时，后端调用 `OpenAiTenderDocumentAnalyzer.analyze(DocumentAnalysisInput)` 方法，使用 `TENDER_INTAKE` profile。

AI 模型（DeepSeek/通义千问/豆包，OpenAI 兼容协议）接收 `TenderDocumentPrompts.buildTenderIntakePrompt` 生成的 prompt，返回 JSON 结构化输出，反序列化为 `TenderRequirementOutput` POJO。

### 输入契约

**Prompt 输入**（`buildTenderIntakePrompt` 生成）：
- 候选文本（`<candidate_text>` 标签包裹，已经过 `TenderIntakeTextProcessor` 预处理，上限 20000 字符）
- 文件名（已 sanitize）
- 字段抽取指令列表（包含 `tenderInfo` 字段说明）

### 输出契约

**AI 返回 JSON 结构**（由 `TenderRequirementOutput` POJO 反序列化）：

```json
{
  "projectName": "string (可空)",
  "tenderTitle": "string (可空)",
  "tenderScope": "string (≤120字摘要)",
  "tenderInfo": "string (完整招标公告原文，可空)",
  "purchaserName": "string (可空)",
  "budget": "string (人民币元数字字符串，可空)",
  "region": "string (省+市格式，可空)",
  "industry": "string (可空)",
  "tenderAgency": "string (可空)",
  "bidOpeningTime": "string (ISO 日期时间，可空)",
  "contactName": "string (可空)",
  "contactPhone": "string (11位手机号，可空)",
  "contactLandline": "string (区号-号码，可空)",
  "contactEmail": "string (可空)",
  "contactName2": "string (可空)",
  "contactPhone2": "string (可空)",
  "contactLandline2": "string (可空)",
  "contactEmail2": "string (可空)",
  "customerType": "string (枚举值，可空)",
  "priority": "string (S/A/B/C，可空)",
  "publishDate": "string (可空)",
  "deadline": "string (ISO 日期时间，可空)",
  "deadlineText": "string (可空)",
  "qualificationRequirements": [],
  "technicalRequirements": [],
  "commercialRequirements": [],
  "scoringCriteria": [],
  "scoringCriteriaItems": [],
  "requiredMaterials": [],
  "riskPoints": [],
  "tags": [],
  "requirementItems": []
}
```

### 新增字段：`tenderInfo`

| 属性 | 值 |
|---|---|
| 字段名 | `tenderInfo` |
| 类型 | String |
| 是否必填 | 否（扫描件/AI 失败时为空） |
| 长度限制 | 无（AI 模型 max_tokens 限制除外，建议 ≤20000 字） |
| 语义 | 招标公告完整原文，包含项目概况、资格要求、技术要求、商务要求、评分办法、联系方式等全部章节 |
| 与 `tenderScope` 区别 | `tenderScope` 是 AI 提炼的 ≤120 字摘要；`tenderInfo` 是完整原文，两者并存不互相覆盖 |

### Prompt 指令契约（新增 tenderInfo 字段说明）

在 `buildTenderIntakePrompt` 的字段列表中，`tenderScope` 之后新增：

```
- tenderInfo：招标公告完整原文，包含项目概况、资格要求、技术要求、商务要求、评分办法、联系方式等全部章节；直接复制候选文本中的公告正文，不要摘要、不要改写、不要拆解；候选文本不足时留空。
```

同时保留现有指令：`不需要 requirementItems；qualificationRequirements、technicalRequirements、commercialRequirements、scoringCriteriaItems 均返回空数组。`

### 映射契约

`OpenAiTenderDocumentAnalyzer.putTenderIntakeFields` 方法在遍历 results 时，对每个非空 item 调用：

```java
putIfBlank(data, "tenderInfo", item.tenderInfo);
```

`putIfBlank` 语义：仅当 `data` 中 `tenderInfo` 键为空或空白时，才用 `item.tenderInfo` 覆盖；非空则保留原值（不覆盖用户已填内容）。

### 错误处理契约

| 场景 | 行为 |
|---|---|
| AI 返回 200 OK + 完整 JSON | `tenderInfo` 字段正常映射到 `extractedData` |
| AI 返回 200 OK 但 `tenderInfo` 为空字符串 | `putIfBlank` 跳过，`extractedData.tenderInfo` 保持空 |
| AI 超时（PT90S） | `requestAiSafe` 捕获异常返回 null，`extractedData` 无 `tenderInfo` 键 |
| AI 余额不足（402） | 同上，前端显示"AI 服务余额不足" |
| 候选文本 <100 字（扫描件） | 跳过 AI 调用，返回 SCANNED_DOCUMENT 警告，`extractedData` 无 `tenderInfo` 键 |

### 兼容性契约

- **向后兼容**：现有 25 个字段抽取不受影响，回归测试全部通过
- **向前兼容**：若 AI 模型未返回 `tenderInfo` 字段（旧 prompt 缓存），`TenderRequirementOutput.tenderInfo` 为 null，`putIfBlank` 跳过，无副作用
- **前端兼容**：前端 `useTenderAiParse.js` 第 167 行已有 `tenderInfo→tenderInfo` 映射，无需改动
