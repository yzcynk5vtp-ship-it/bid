---
title: CRM 商机对接接口契约（CO-262 对齐版）
purpose: 给西域 CRM 客户方的对接技术文档
audience: 客户方 CRM 后端开发
related_issue: CO-262
related_prs: !795 / !897 / !898 / !910 / !913
date: 2026-06-21
author: trae
status: 待客户方确认
---

# CRM 商机对接接口契约（CO-262 对齐版）

> 本文档是西域数智化投标管理平台（**投标系统**）与客户方 CRM 系统的**业务+技术对接**精简版，
> 用于解决 CO-262 议题：`CRM商机关联-投标评估表字段映射错误（风险预判/支持备注错位 + GAP缺附件）`。
> 完整接口规范见 `docs/integration/西域CRM商机对接接口.md`。

---

## 1. 三个核心接口

| 方向 | 接口 | 触发场景 |
|---|---|---|
| **投标系统 → CRM** | `POST /customer-chance/page-list` | 标讯详情页"关联 CRM 商机"按钮 → 商机列表分页查询 |
| **投标系统 → CRM** | `POST /contact-person-info/page-list` | 选中商机后查询对接人（构建客户信息矩阵） |
| **CRM → 投标系统** | `PUT /api/integration/tenders/{sourceSystem}/{sourceId}` | CRM 创建/更新标讯 → 同步到投标系统，触发评估数据持久化 |

**Auth**：所有"投标系统 → CRM"接口需 `Authorization: Bearer {token}` Header；
token 通过 `POST /common/inner/generateToken` 用 `nickName + salesNo` 换取。

**CRM → 投标系统**的 `/api/integration/tenders/...` 由我们侧白名单放行，无需 JWT。

---

## 2. CRM 商机列表接口（page-list）—— CRM 客户方需要保证的字段

### 2.1 请求

```json
POST /customer-chance/page-list
Authorization: Bearer {token}
Content-Type: application/json

{
  "pageIndex": 1,
  "pageSize": 10,
  "body": {
    "name": "string (可选，商机名称模糊匹配)",
    "code": "string (可选，商机编号精确匹配)",
    "groupName": ["string"] (可选，集团名称数组，用于按招标主体筛选),
    "projectStatus": [0] (可选，项目状态数组),
    "selectAll": true (可选，全量查询标志)
  }
}
```

### 2.2 响应 —— **本次 CO-262 重点关注以下 6 个字段**

```json
{
  "code": "200",
  "msg": "success",
  "totalCount": 0,
  "pageSize": 10,
  "pageIndex": 1,
  "dataList": [
    {
      "id": 0,                                    // 商机主键（数字），用于反查 code
      "code": "CC20260621320",                    // 商机编号（CC... 格式），前端关联标识
      "name": "string",                           // 商机名称
      "groupName": "string",                      // 集团/招标主体

      // ──────── CO-262 重点对齐的 6 个字段 ────────
      "riskPrediction": "string",                 // ⭐ 风险预判（投标系统 basic.riskAssessment 来源）
      "remark": "string",                         // ⭐ 需要的支持备注（投标系统 basic.supportNotes 来源）
      "projectGap": "string",                     // ⭐ 项目 GAP 描述（投标系统 basic.projectPlanGap 来源）
      "gapFile": "string",                        // ⭐ GAP 附件 URL（投标系统 basic.projectPlanGapFiles 来源）

      "backupPlan": true,                         // ⭐ 是否有兜底方案（Boolean，必填字段，null 会导致 shouldBid 计算错误）
      "backupPlanText": "string",                 // 兜底方案文本（冗余展示用）

      // ──────── 其他常用字段（已在用） ────────
      "bidDocumentDisadvantage": "string",        // 招标文件不利项
      "managerUnderstandProcess": "string",       // 项目经理是否了解评标全流程（"是"/"否"）
      "planSupplierCount": 0,                     // 计划入围供应商数量（Integer）
      "ecommerceMroAmount": 0.0,                  // 电商 MRO + 办公流水金额（万，BigDecimal）
      "customerRevenue": 0.0,                     // 客户营收（万，BigDecimal）
      "evaluationTime": "string"                  // 评标时间（yyyy-MM-dd HH:mm:ss）
    }
  ]
}
```

### 2.3 字段含义对照表（CO-262 修复后的映射关系）

| CRM 字段 | 类型 | 必填 | 含义 | 投标系统落点 | 备注 |
|---|---|---|---|---|---|
| `id` | Long | 是 | 商机主键 | 用于反查 `code` | 数字，前端不直接展示 |
| `code` | String | 是 | 商机编号 | `tender.crm_opportunity_id` | CC 开头，关联标识 |
| `name` | String | 是 | 商机名称 | 关联展示 | |
| `riskPrediction` | String | 否 | **风险预判** | `evaluation.basic.riskAssessment` | ⚠️ 之前错填到 `supportNotes`，已修 |
| `remark` | String | 否 | **需要的支持** | `evaluation.basic.supportNotes` | ⚠️ 之前填空字符串，已修 |
| `projectGap` | String | 否 | 项目 GAP 描述 | `evaluation.basic.projectPlanGap` | |
| `gapFile` | String | **否** | **GAP 附件 URL** | `evaluation.basic.projectPlanGapFiles: [{fileName:'GAP附件', fileUrl:gapFile}]` | ⚠️ 之前字段缺失，已修（详见 §4） |
| `backupPlan` | **Boolean** | **是** | 是否有兜底方案 | `evaluation.basic.contingencyPlan` + `evaluation.recommendation.shouldBid` | ⚠️ **强烈建议必填**，null 会被前端兜底为 `false` |
| `bidDocumentDisadvantage` | String | 否 | 招标文件不利项 | `evaluation.basic.unfavorableItems` | |
| `managerUnderstandProcess` | String | 否 | 项目经理了解评标流程 | `evaluation.basic.processKnowledge` | |
| `planSupplierCount` | Long | 否 | 计划入围供应商数量 | `evaluation.basic.plannedShortlistedCount` | |
| `ecommerceMroAmount` | BigDecimal | 否 | MRO 办公流水金额（万） | `evaluation.basic.mroOfficeFlowAmount` | |
| `customerRevenue` | BigDecimal | 否 | 客户营收（万） | `evaluation.basic.customerRevenue` | |
| `evaluationTime` | String | 否 | 评标时间 | `evaluation.basic.contractPeriodStart` | |

### 2.4 字段命名红线（必须保证）

| 错误写法 | 正确写法 | 后果 |
|---|---|---|
| `riskForecast` | `riskPrediction` | 风险预判填空 |
| `comment` / `comments` / `note` | `remark` | 支持备注填空 |
| `gapAttachment` / `gapFileUrl` | `gapFile` | GAP 附件丢失 |
| `hasBackupPlan` / `backupPlanFlag` | `backupPlan` | 兜底方案判断错误 |

**所有字段名**严格按 §2.2 表 JSON key，**区分大小写**、**不要加任何前缀/后缀**。

---

## 3. CRM 对接人接口（contact-person-info）

### 3.1 请求

```json
POST /contact-person-info/page-list
Authorization: Bearer {token}
Content-Type: application/json

{
  "ccId": 0  // 商机主键 id（不是 code）
}
```

### 3.2 响应（关键字段）

```json
{
  "code": "200",
  "data": [
    {
      "id": 0,
      "name": "string",                        // 对接人姓名
      "phone": "string",                       // 手机号
      "email": "string",                       // 邮箱
      "ehsyProjectManager": "string",         // 西域项目负责人
      "contacted": true,                       // 是否触达
      "contactMethod": "string",               // 触达方式
      "preferenceLevel": "string",             // 倾向性（HIGH/MIDDLE/LOW）
      "preferenceBasis": "string",             // 倾向性评估依据
      "guidedBidDocument": true,               // 是否引导标书
      "getKeyInfo": true,                      // 是否可获取关键信息
      "deleteDisadvantage": true,              // 是否可删除不利项
      "syncInfo": true,                        // 是否可实时同步
      "guaranteeWin": true,                    // 是否明确中标
      "impactRate": "string"                   // 中标影响率（如 "80%"）
    }
  ]
}
```

> 字段名同样严格按表 JSON key，区分大小写。

---

## 4. **关键：GAP 附件字段（`gapFile`）—— CO-262 重点**

### 4.1 当前 A 方案（已在投标系统 main 合入，推荐保持）

**CRM 客户方推什么**：
- `gapFile: "https://crm-domain.com/path/to/file.pdf"` —— **单个 URL 字符串**
- 无附件时 `gapFile: ""` 或 `gapFile: null`
- 一次只允许 1 个附件

**投标系统侧处理**：
- 后端 `CustomerChanceVO.gapFile: String` 接收
- 前端 `useCrmOpportunitySelector.js:199` 自动包装为 `[{fileName: 'GAP附件', fileUrl: gapFile}]`
- 后端 `TenderEvaluationGapFilesSync` 持久化到 `project_documents` 表

**优点**：客户方改造最小，URL 单值即可
**限制**：
- 文件名固定显示为"GAP附件"，无法展示真实文件名
- 一次只能挂 1 个附件

### 4.2 推荐 B 方案（建议客户方升级）

**CRM 客户方推什么**：
```json
{
  "gapFile": [
    "https://crm-domain.com/path/to/gap-1.pdf",
    "https://crm-domain.com/path/to/gap-2.pdf"
  ]
}
```

**需要客户方配合改动**：
- CRM 商机表增加 `gap_file` 字段为 JSON 数组（或多 URL 字符串）
- 接口文档同步更新 `CustomerChanceVO.gapFile` 类型为 `List<String>`

**投标系统侧需要做**（升级工作）：
- `CustomerChanceVO.gapFile: String` → `List<String>` 或 `List<GapFileRef>`
- 前端 `useCrmOpportunitySelector.js:199` 数组遍历
- 测试更新

### 4.3 完整方案 C（最完整，需 CRM 客户方深度改造）

**CRM 客户方推什么**：
```json
{
  "gapFile": [
    {"fileName": "差距分析报告.pdf", "fileUrl": "https://crm-domain.com/path/to/gap-1.pdf"},
    {"fileName": "补充说明.docx",   "fileUrl": "https://crm-domain.com/path/to/gap-2.docx"}
  ]
}
```

**CRM 客户方需要**：
- CRM 商机附件表结构升级（增加原始文件名）
- 接口文档同步
- **改造窗口评估**：通常 2-3 周 + 联调

---

## 5. CRM → 投标系统 标讯回传（bidInfoSync + 集成推送）

### 5.1 CRM 主动创建/更新标讯接口

```
PUT /api/integration/tenders/{sourceSystem}/{sourceId}
Content-Type: application/json
```

请求体（v3.1）：
```json
{
  "sourceSystem": "crm",                  // 必填
  "sourceId": "CC20260621320",            // 必填，对应 CRM 商机 code
  "title": "string",                      // 必填，标讯标题
  "customerName": "string",               // 客户名称
  "dueDate": "2026-06-30T18:00",          // 投标截止（ISO datetime）
  "bidOpeningTime": "2026-07-01T10:00",   // 开标时间
  "registrationDeadline": "2026-06-25T18:00", // 报名截止

  "crmOpportunityId": "CC20260621320",    // CO-276：CRM 商机编号 code
  "crmOpportunityName": "string",         // 商机名称

  "evaluation": {                         // ⭐ CO-262 P0-2：项目评估数据
    "evaluationBasic": {
      "projectPlanGap": "差距描述",
      "projectPlanGapFiles": [            // ⭐ GAP 附件结构（与投标系统 DTO 一致）
        {
          "fileName": "GAP附件",
          "fileUrl": "https://crm-domain.com/path/to/gap.pdf"
        }
      ],
      "riskAssessment": "风险预判",
      "supportNotes": "需要的支持",
      "contingencyPlan": "是",
      "unfavorableItems": "招标文件不利项",
      "processKnowledge": "是",
      "plannedShortlistedCount": 5,
      "mroOfficeFlowAmount": 200.0,
      "customerRevenue": 5000.0,
      "contractPeriodStart": "2026-07-01T10:00"
    },
    "evaluationCustomerInfos": [...],
    "evaluationRecommendation": {
      "shouldBid": true,
      "reason": "string (留空或文本)"
    }
  },

  "forceUpdate": true                     // 是否强制覆盖已有数据
}
```

### 5.2 CRM 标讯状态回传（bidInfoSync）

```
POST /api/integration/tenders/bidInfoSync
```

```json
{
  "bidInfoList": [
    {
      "name": "string",                  // 标讯名称
      "code": "string",                  // 标讯编号
      "status": 0,                       // ⚠️ 状态值：1-跟进中 2-中标 3-丢标 4-流标 5-投标中 6-弃标
      "statusEditor": "string",          // 状态变更人
      "statusEditTime": "2026-06-21 10:00:00", // yyyy-MM-dd HH:mm:ss
      "feedback": "string (json 字符串)"
    }
  ]
}
```

---

## 6. 接口测试用例

### 6.1 客户方需要保证的最小集

| # | 场景 | CRM 推送示例 | 投标系统预期 |
|---|---|---|---|
| 1 | 风险预判有值 | `riskPrediction: "竞争对手低价冲击"` | 评估表"风险预判"字段 = "竞争对手低价冲击" |
| 2 | 支持备注有值 | `remark: "客户决策周期长"` | 评估表"需要的支持"字段 = "客户决策周期长" |
| 3 | GAP 描述 | `projectGap: "时间紧张"` | 评估表"项目 GAP"字段 = "时间紧张" |
| 4 | GAP 附件 URL | `gapFile: "https://crm.com/gap.pdf"` | 评估表"项目计划 GAP"下显示可下载链接 |
| 5 | 兜底方案（boolean） | `backupPlan: true` | "是否有兜底方案"="是"，"是否投标"=false |
| 6 | 兜底方案（boolean） | `backupPlan: false` | "是否有兜底方案"="否"，"是否投标"=true |
| 7 | 兜底方案（null） | `backupPlan: null` | 兜底为 false，**前端显示可能不准确** |
| 8 | 字段缺失 | 不传 `gapFile` | 评估表附件列表为空，不报错 |

### 6.2 标讯回传测试

| # | 场景 | CRM 推送 | 投标系统预期 |
|---|---|---|---|
| 1 | 创建标讯 + 关联商机 | `PUT /api/integration/tenders/crm/CCxxx` + `crmOpportunityId: "CCxxx"` | 标讯自动关联对应商机 |
| 2 | 更新评估表 GAP | `PUT /api/integration/tenders/crm/CCxxx` + `evaluation.evaluationBasic.projectPlanGapFiles: [...]` | project_documents 表新增/更新 GAP 附件 |
| 3 | 状态回传 | `POST /api/integration/tenders/bidInfoSync` + `status: 2` | 标讯状态变更为"中标" |

---

## 7. 客户方需确认的 3 件事

请客户方后端开发确认以下 3 件事，然后我们推进实施：

1. **`gapFile` 字段类型**：A 方案（String 单 URL）现状可用；B/C 方案需 CRM 改造，**客户方是否有改造窗口？**
2. **`backupPlan` 默认值**：未填写时 API 返回 `null` 还是 `false`？这影响"是否投标"判断（CO-262 issue 残留风险点）
3. **`riskPrediction` / `remark` 字段是否已就绪**：部分旧版本 CRM 可能没有这两个字段，需客户方确认升级

---

## 8. 联系方式

- 投标系统技术对接：参见团队 Linear workspace
- Linear 议题：CO-262（CRM商机关联-投标评估表字段映射错误）
- 已合 PR：`!795` `!897` `!898` `!910` `!913`
- 测试环境：`https://winbid-test.ehsy.com/bidding/325`（商机 CC20260621320）