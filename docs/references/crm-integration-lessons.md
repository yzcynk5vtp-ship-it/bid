# CRM 外部系统对接经验教训

> 来源：2026-06-16 CRM 商机查询反复“无数据”事故复盘
> 适用范围：所有与西域 CRM、招标平台等外部系统的集成开发

## 1. 外部系统集成必须先用真实数据验证语义

- 不能只看接口字段名（如 `evaluationTime` vs `报名截止时间`），必须在真实 CRM 上跑一遍，确认字段含义、取值范围、数据质量。
- 匹配策略（精确 / 集团 / 全量）必须在真实数据上验证召回率和准确率后再定默认策略。

## 2. 测试要断言用户结果，不要断言实现方式

- 前端单测不应断言“必须调用 `search-by-tender`”，这会锁定错误策略。
- 应断言：**给定招标主体，弹窗中必须出现可选择的商机**；至于调用哪个接口，应由实现决定。
- 后端单测应断言：给定策略 +  tenderer，返回列表符合预期；而不是断言内部调用了几次 CRM。

## 3. 兜底策略要显式，不要静默 fallback

- 当精确匹配为空、自动切换到集团或全量查询时，必须在 UI 上明确提示用户：
  > “精确匹配为空，已按集团/全部商机展示。”
- 静默 fallback 会让用户误以为返回的就是“最相关”结果，导致选择错误商机。

## 4. 探针脚本必须进流水线

- `scripts/crm-opportunity-probe.mjs` 必须作为部署后 smoke 的一环执行，空结果直接阻断发布（NO-GO）。
- 探针不能只是本地手动跑一次，要配置环境变量 `PROBE_*` 并接入 `scripts/release/run-prod-smoke.mjs`。

## 5. 高风险匹配策略需要 Feature Flag

- 通过 `app.crm.matching-strategy`（`EXACT` / `GROUP` / `ALL`）控制查询策略，支持环境变量覆盖。
- 下次 CRM 字段或数据再变，应能秒切策略并回滚，无需改代码再部署。

## 6. UI 修复和业务逻辑修复要拆 PR

- 弹窗布局优化（UI）和 CRM 查询策略修复（业务逻辑）应分别提 PR：
  - `agent/kimi/fix-crm-selector-ui`
  - `agent/kimi/fix-crm-query-strategy`
- 混在一个 PR 会导致回滚和 review 成本翻倍。

## 7. 后端集成必须记录完整请求/响应体

- `CrmChanceService` 必须记录发给 CRM 的完整 body、响应状态码、返回条数。
- 生产问题排查时，应能直接从日志判断是：参数格式问题、匹配条件问题，还是 CRM 本身无数据。

## 8. 跨系统日期要定义统一格式和时区契约

- 前端使用 ISO 字符串、后端使用 `yyyy-MM-dd HH:mm:ss`、CRM 只接受日期精度，三方必须显式约定：
  - 传输格式：ISO 8601 with offset
  - 存储格式：`yyyy-MM-dd HH:mm:ss`（本地时间，以业务发生时区为准）
  - 外部系统适配：在后端统一转换为 CRM 要求的精度，禁止前端传多种格式让后端猜测
- 该契约应写入本文档和项目记忆，所有外部系统对接复用。

## 相关文档

- [crm-field-mapping.md](./crm-field-mapping.md) — 字段映射与查询策略说明
- [西域CRM商机对接接口.md](../integration/西域CRM商机对接接口.md) — CRM 接口规范

---

## 9. CRM 商机字段映射错位导致评估表数据错乱（CO-262 / PR795）

> 来源：2026-06-18 CO-262 CRM 商机关联投标评估表字段映射错误事故复盘（PR795）
> 适用范围：所有 CRM 商机字段映射到投标评估表的场景

### 事故一句话总结

CRM 商机关联投标评估表时，`useCrmOpportunitySelector.js` 把 CRM 字段映射到错误的评估表字段：风险预判填了商机备注、支持备注为空、投标建议理由填了风险预判、GAP 附件未回填。

### 根因：字段映射逻辑错位

```javascript
// ❌ 错误映射（PR795 修复前）
basic: {
  riskAssessment: chance.remark || '',           // 风险预判 ← 商机备注（错）
  supportNotes: '',                               // 支持备注 ← 空（错）
  projectPlanGap: chance.projectGap || '',
  // projectPlanGapFiles 未回填（错）
},
recommendation: { shouldBid: !chance.backupPlan, reason: chance.riskPrediction || '' }, // 投标建议理由 ← 风险预判（错）

// ✅ 正确映射（PR795 修复后）
basic: {
  riskAssessment: chance.riskPrediction || '',    // 风险预判 ← CRM 风险预判
  supportNotes: chance.remark || '',              // 支持备注 ← CRM 商机备注
  projectPlanGap: chance.projectGap || '',
  projectPlanGapFiles: chance.gapFile ? [{ fileName: 'GAP附件', fileUrl: chance.gapFile }] : [], // GAP 附件回填
},
recommendation: { shouldBid: !chance.backupPlan, reason: '' }, // 投标建议理由留空给用户填
```

**关键认知**：
- CRM 商机字段语义见 `CustomerChanceVO.java`：`riskPrediction`（风险预判）、`remark`（商机备注）、`gapFile`（GAP 附件 URL）
- 评估表字段语义：`riskAssessment`（风险预判）、`supportNotes`（支持备注）、`projectPlanGapFiles`（GAP 附件列表）
- 映射必须**语义对齐**，不能只看字段名是否"看起来相关"

### 字段映射对照表（PR795 修正后）

| 评估表字段 | CRM 商机字段 | 语义 |
|---|---|---|
| `basic.riskAssessment` | `chance.riskPrediction` | 风险预判 |
| `basic.supportNotes` | `chance.remark` | 商机备注/支持备注 |
| `basic.projectPlanGap` | `chance.projectGap` | 项目计划 GAP |
| `basic.projectPlanGapFiles` | `chance.gapFile` | GAP 附件 URL |
| `basic.unfavorableItems` | `chance.bidDocumentDisadvantage` | 招标文件不利项 |
| `basic.contingencyPlan` | `chance.backupPlan` | 兜底方案 |
| `basic.processKnowledge` | `chance.managerUnderstandProcess` | 客户经理是否了解流程 |
| `recommendation.reason` | （留空） | 投标建议理由，由用户手动填写 |

### 手动输入模式同步修复

手动输入模式（非 CRM 选择）也需同步修复字段映射：

```javascript
// ❌ 错误：手动输入模式
riskAssessment: mf.remark || '',           // 风险预判 ← 备注（错）
supportNotes: '',                          // 支持备注 ← 空（错）

// ✅ 正确：手动输入模式
riskAssessment: mf.projectRiskText || '',  // 风险预判 ← 项目风险文本
supportNotes: mf.remark || '',             // 支持备注 ← 备注
projectPlanGapFiles: [],                   // 手动输入无 GAP 附件
```

### 诊断方法

1. 用户反馈"评估表字段填错"或"该填的没填、不该填的填了"
2. 检查 `useCrmOpportunitySelector.js` 的字段映射逻辑
3. 对照 `CustomerChanceVO.java` 确认 CRM 字段语义
4. 对照评估表 DTO 确认目标字段语义
5. 验证映射是否语义对齐

### 通用规则：字段映射必须语义对齐

1. **不能只看字段名**：`remark`（备注）不等于 `riskPrediction`（风险预判）
2. **必须对照源头定义**：CRM 字段语义以 `CustomerChanceVO.java` 为准，不能凭猜测
3. **留空字段要显式**：如 `recommendation.reason` 留空给用户填，必须显式写 `reason: ''`，不能映射到其他字段
4. **附件字段要回填**：`projectPlanGapFiles` 必须从 `chance.gapFile` 回填，不能遗漏

### 测试验证

修复后新增 2 个测试用例到 `useCrmOpportunitySelector.spec.js`（共 6 个测试全部通过）：
- 测试 1：CRM 选择模式字段映射验证（riskAssessment←riskPrediction, supportNotes←remark, projectPlanGapFiles←gapFile, reason 为空）
- 测试 2：手动输入模式字段映射验证（riskAssessment←projectRiskText, supportNotes←remark）

### 排查方法

```bash
# 查找 CRM 商机字段定义
grep -rn "class CustomerChanceVO" backend/src/main/java/

# 查找字段映射逻辑
grep -rn "riskAssessment\|supportNotes\|projectPlanGapFiles" src/views/Bidding/detail/components/

# 查找评估表 DTO 字段定义
grep -rn "record EvaluationBasicDTO" backend/src/main/java/
```

## 相关文档

- [crm-field-mapping.md](./crm-field-mapping.md) — 字段映射与查询策略说明
- [西域CRM商机对接接口.md](../integration/西域CRM商机对接接口.md) — CRM 接口规范
- [useCrmOpportunitySelector.js](../../src/views/Bidding/detail/components/useCrmOpportunitySelector.js) — 字段映射修复位置
- [CustomerChanceVO.java](../../backend/src/main/java/com/xiyu/bid/crm/infrastructure/dto/CustomerChanceVO.java) — CRM 商机字段定义
