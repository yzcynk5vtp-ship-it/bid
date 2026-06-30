# Data Model: 标讯识别抽取完整招标公告原文到 tenderInfo 字段

**Date**: 2026-06-30
**Feature**: [spec.md](spec.md) | [plan.md](plan.md)

## Entity Changes

### 1. `tenders` 表（数据库层）

| 字段名 | 当前类型 | 新类型 | 说明 |
|---|---|---|---|
| `tender_info` | `VARCHAR(5000) NULL` | `TEXT NULL` | 支持 20000 字中文，TEXT 上限 65535 字节 |

**Migration Scripts**:
- `V1xxx__expand_tender_info_to_text.sql` — `ALTER TABLE tenders MODIFY COLUMN tender_info TEXT NULL COMMENT '标讯信息';`
- `V1xxx__expand_tender_info_to_text_rollback.sql` — `ALTER TABLE tenders MODIFY COLUMN tender_info VARCHAR(5000) NULL COMMENT '标讯信息';`（回滚时会截断超长数据，需在回滚脚本头部注明）

### 2. `Tender` JPA 实体（`backend/src/main/java/com/xiyu/bid/entity/Tender.java`）

```java
// 当前（第 170-171 行）：
@Column(name = "tender_info", length = 5000)
private String tenderInfo;

// 改动后：
@Column(name = "tender_info", columnDefinition = "TEXT")
private String tenderInfo;
```

**Notes**:
- 移除 `length = 5000`，改用 `columnDefinition = "TEXT"` 让 JPA 与 Flyway 迁移后的实际类型一致
- `ddl-auto=validate` 模式下，`columnDefinition = "TEXT"` 与数据库 `TEXT` 类型匹配，不会触发 schema 偏差警告

### 3. `TenderRequest` DTO（`backend/src/main/java/com/xiyu/bid/tender/dto/TenderRequest.java`）

```java
// 当前（第 151-152 行）：
@Size(max = 5000, message = "标讯信息长度不能超过5000个字符")
private String tenderInfo;

// 改动后：
@Size(max = 20000, message = "标讯信息长度不能超过20000个字符")
private String tenderInfo;
```

### 4. `TenderDTO` 响应 DTO（`backend/src/main/java/com/xiyu/bid/tender/dto/TenderDTO.java`）

无改动。第 83 行 `private String tenderInfo;` 已存在，无校验注解（响应不需要校验）。

### 5. `TenderRequirementOutput` AI 输出结构（`backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/TenderRequirementOutput.java`）

```java
// 新增字段（在 tenderScope 之后）：
public String tenderInfo;
```

**Notes**:
- 这是 POJO（非 record），新增 public 字段即可
- Jackson + jsonschema-generator 自动将此字段加入 JSON schema 传给 AI
- AI 返回的 `tenderInfo` 字段值会自动反序列化到此字段

### 6. `DocumentAnalysisResult.extractedData` Map

新增键：`"tenderInfo"` → String 值（完整招标公告原文）

由 `OpenAiTenderDocumentAnalyzer.putTenderIntakeFields` 方法写入：
```java
putIfBlank(data, "tenderInfo", item.tenderInfo);
```

## State Transitions

无状态机改动。`tenderInfo` 字段是纯文本存储，无状态转换。

## Validation Rules

| 层级 | 字段 | 规则 | 来源 |
|---|---|---|---|
| JPA 实体 | `tenderInfo` | `columnDefinition = "TEXT"`，无 length 限制 | 数据库 schema 对齐 |
| 请求 DTO | `tenderInfo` | `@Size(max = 20000)` | FR-005 |
| 前端输入框 | `form.tenderInfo` | `maxlength=20000` | FR-005 |
| 前端校验规则 | `tenderInfo` | `max: 20000, message: '标讯信息不能超过20000字符'` | FR-005 |
| AI 输出 | `tenderInfo` | 无长度限制（AI 模型 max_tokens 限制除外） | FR-001 |
| 蓝图配置 | `tenderInfo.maxLength` | `20000`（V1007 → 新 V 脚本更新） | FR-005 |

## Relationships

无新增实体关系。`tenderInfo` 是 `tenders` 表的现有字段，本次仅扩展容量。
