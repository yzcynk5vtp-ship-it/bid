# CRM 商机字段映射与查询规则

> 本文档记录「西域数智化投标管理平台」与 CRM 商机接口之间的字段映射、查询规则及常见陷阱。
> 任何改动 CRM 查询/回传逻辑的 PR 必须同步更新本文件。

## 1. 对接接口概览

| 平台端点 | 代理的 CRM 端点 | 用途 |
|---|---|---|
| `POST /api/xiyu/crm/chances/page-list` | `POST /customer-chance/page-list` | 通用商机分页查询 |
| `POST /api/xiyu/crm/chances/search-by-tender` | `POST /customer-chance/page-list` | 按标讯信息（蓝图规则）精确匹配商机 |
| `POST /api/xiyu/crm/chances/bid-info-sync` | `POST /customer-chance/bidInfoSync` | 标讯状态回传 CRM |
| `POST /api/xiyu/crm/chances/contact-persons` | `POST /contact-person-info/page-list` | 按商机 ID 查询对接人 |

## 2. 产品蓝图要求

标讯详情页「关联 CRM 商机」选择器的初始查询，必须按以下标讯字段组合匹配：

- **招标主体** → 对应 CRM `groupName`（集团名称）
- **报名截止时间** → 对应 CRM `evaluationTime`（评标时间）精确日期
- **开标时间** → 对应 CRM `evaluationTime`（评标时间）精确日期

> 历史 bug：前端曾使用 `selectAll` 拉取全量商机，再按 `tenderSubject` 前端过滤，导致匹配率极低。
> 修复后由后端 `search-by-tender` 统一按 `groupName + evaluationTime` 精确查询并合并去重。

## 3. 标讯 → CRM 请求字段映射

### 3.1 通用分页查询 `page-list`

| 标讯/前端概念 | 后端字段 | CRM `body` 字段 | 说明 |
|---|---|---|---|
| 招标主体 | `tenderer` | `groupName` | CRM 仅支持按集团名称过滤，不支持按招标主体模糊匹配 |
| 商机名称 | `name` | `name` | 精确/模糊匹配商机名称 |
| 商机编号 | `code` | `code` | 精确匹配商机编号 |
| 项目状态 | `projectStatus` | `projectStatus` | 多选 |
| 评标开始时间 | `evaluationStartTime` | `evaluationStartTime` | 格式 `yyyy-MM-dd HH:mm:ss` |
| 评标结束时间 | `evaluationEndTime` | `evaluationEndTime` | 格式 `yyyy-MM-dd HH:mm:ss` |
| 全量兜底 | `selectAll` | `selectAll` | 当缺少招标主体时使用 |

### 3.2 蓝图匹配查询 `search-by-tender`

前端请求：

```json
{
  "tenderer": "山东海化集团有限公司",
  "registrationDeadline": "2026-06-03 23:59:00",
  "bidOpeningTime": "2026-06-04 10:00:00",
  "pageIndex": 1,
  "pageSize": 10
}
```

后端匹配策略由 `app.crm.matching-strategy` 控制，支持：

| 策略 | 行为 | 适用场景 |
|---|---|---|
| `EXACT` | 先按 `groupName + evaluationTime` 精确匹配报名截止/开标日期；若为空，兜底 `groupName`；再为空，兜底 `selectAll` | 数据质量高，追求精确匹配 |
| `GROUP` | 按 `groupName` 匹配；若为空，兜底 `selectAll` | **默认**，平衡召回与相关性 |
| `ALL` | 直接拉取全量商机 | 快速恢复/排查 |

日期字段支持 `yyyy-MM-dd HH:mm:ss`、`yyyy-MM-dd`、`ISO_LOCAL_DATE_TIME`、`ISO_OFFSET_DATE_TIME` 等多种格式。

后端实际会拆分为一次或多次 `page-list` 调用，最终结果按 `id` 去重合并后返回。

## 4. CRM 响应字段 → 前端展示字段映射

| CRM 响应字段 | 前端展示位置 | 说明 |
|---|---|---|
| `id` | 商机唯一标识 | 关联时回写到标讯 `crm_opportunity_id` |
| `code` | 商机编号 | 表格列、已选摘要 |
| `name` | 商机名称 | 表格列、已选摘要 |
| `groupName` | 集团 | 表格列、展开详情 |
| `tenderSubject` | 招标主体 | 展开详情（CRM 返回，但不可过滤） |
| `projectLeaderName` | 项目负责人 | 表格列、展开详情 |
| `projectStatus` / `projectStatusText` | 项目状态 | 表格列、展开详情 |
| `projectRisk` / `projectRiskText` | 项目风险 | 展开详情 |
| `evaluationTime` | 评标时间 | 展开详情；关联后回填评估表 `contractPeriodStart` |
| `planSupplierCount` | 计划入围 | 展开详情；回填评估表 `shortlistedCount` |
| `ecommerceMroAmount` | 电商流水(万) | 展开详情；回填评估表 `platformServiceFee` |
| `bidDocumentDisadvantage` | 兜底方案劣势 | 回填评估表 `competitorAnalysis` |
| `riskPrediction` | 风险预测 | 回填评估表 `recommendation.reason` |
| `backupPlan` / `backupPlanText` | 兜底方案 | 影响 `recommendation.shouldBid` |
| `remark` | 备注/支持需求 | 回填评估表 `projectBackground` |

## 5. 已知 CRM 能力与限制

- `groupName` 必须精确匹配（CRM 侧按数组精确匹配）。
- `tenderSubject` 仅作为返回字段，**不能作为查询条件**。
- `name` 按商机名称匹配，不是招标主体。
- `evaluationTime` 是日期精度，后端按整天范围查询。
- CRM 商机 `evaluationTime` 可能与标讯的「报名截止」或「开标时间」不一致，需要人工确认。

## 6. 改动 checklist

修改本文件前，请确认：

1. 已同步更新 `docs/references/crm-field-mapping.md`。
2. 已补充/更新 `CrmChanceServiceTest` 和 `CrmChanceControllerIntegrationTest`。
3. 已更新 `src/api/modules/crm.js` 中的 JSDoc。
4. 已在真实 CRM 环境（或探针脚本）验证查询结果。
