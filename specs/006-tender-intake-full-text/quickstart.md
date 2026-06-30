# Quickstart: 标讯识别抽取完整招标公告原文到 tenderInfo 字段

**Date**: 2026-06-30
**Feature**: [spec.md](spec.md) | [plan.md](plan.md)

## 前置条件

1. **主工作区**：所有开发环境在 `/Users/user/xiyu/worktrees/trae` 启动（前端 1323 / 后端 18089 / Sidecar 8009 / MySQL `xiyu_bid_main` / Redis DB 0）
2. **环境变量**：`export XIYU_DEV_CONFIRMED=1`
3. **分支**：`agent/claude/tender-intake-full-text-extraction`（已基于 origin/main 创建）
4. **早操已执行**：`./scripts/sync-env.sh .` 已跑过，本地门禁全绿

## 开发流程

### Step 1: 数据库迁移脚本（先写测试再实现）

```bash
# 预约版本号（避免并行开发撞号）
bash scripts/next-migration-version.sh --reserve
# 输出示例：Next version: 1082, use: bash scripts/new-migration.sh expand-tender-info-to-text
```

创建两个迁移脚本：
- `backend/src/main/resources/db/migration-mysql/V1xxx__expand_tender_info_to_text.sql`
- `backend/src/main/resources/db/migration-mysql/V1xxx__expand_tender_info_to_text_rollback.sql`（U 脚本）

```sql
-- V 脚本
ALTER TABLE tenders MODIFY COLUMN tender_info TEXT NULL COMMENT '标讯信息';
```

```sql
-- U 脚本（回滚，会截断超长数据）
-- Input: 回滚将 tender_info 从 TEXT 改回 VARCHAR(5000)，超过 5000 字的数据会被截断
ALTER TABLE tenders MODIFY COLUMN tender_info VARCHAR(5000) NULL COMMENT '标讯信息';
```

### Step 2: 蓝图配置迁移脚本

```sql
-- V 脚本（更新 V1007 中 tenderInfo.maxLength:5000 → 20000）
UPDATE system_settings
SET value = REPLACE(value,
  '"key":"tenderInfo","label":"标讯信息","type":"TEXTAREA","required":false,"rows":3,"maxLength":5000',
  '"key":"tenderInfo","label":"标讯信息","type":"TEXTAREA","required":false,"rows":3,"maxLength":20000')
WHERE scope = 'tender.entry' AND org_id IS NULL;
```

### Step 3: 后端代码改动（TDD）

```bash
# 1. 先写测试（Red）
cd backend
mvn test -Dtest=TenderRequirementOutputTest  # 应失败
mvn test -Dtest=OpenAiTenderDocumentAnalyzerTest  # 应失败

# 2. 实现（Green）
# - TenderRequirementOutput.java 新增 public String tenderInfo;
# - OpenAiTenderDocumentAnalyzer.putTenderIntakeFields 新增 putIfBlank(data, "tenderInfo", item.tenderInfo);
# - TenderDocumentPrompts.buildTenderIntakePrompt 新增 tenderInfo 字段指令
# - Tender.java @Column 改为 columnDefinition="TEXT"
# - TenderRequest.java @Size(max=20000)

# 3. 验证测试通过（Green）
mvn test -Dtest=TenderRequirementOutputTest,OpenAiTenderDocumentAnalyzerTest,TenderDocumentPromptsTest

# 4. 跑架构测试（确保无违规）
mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

### Step 4: 前端代码改动

修改两个文件：
- `src/views/Bidding/list/components/TenderBasicInfoTab.vue` — `maxlength="5000"` → `maxlength="20000"`
- `src/views/Bidding/list/constants.js` — `tenderInfo: [{ max: 5000, ...}]` → `max: 20000`

```bash
cd /Users/user/xiyu/worktrees/claude
npm run build
npm run test:unit -- --grep tender
```

### Step 5: 集成测试 + E2E

```bash
# 后端集成测试
cd backend
mvn test -Dtest=TenderIntegrationTest

# E2E（在主工作区 trae 启动开发环境后）
cd /Users/user/xiyu/worktrees/trae
XIYU_DEV_CONFIRMED=1 npm run dev:all

# 另一个终端跑 E2E
cd /Users/user/xiyu/worktrees/claude
npm run test:e2e -- --grep tender-create-parse
```

### Step 6: 本地门禁

```bash
cd /Users/user/xiyu/worktrees/claude

# 前端门禁
npm run check:front-data-boundaries
npm run check:doc-governance
npm run check:line-budgets
npm run build
npm run test:unit

# 后端门禁
cd backend
mvn test
mvn -Pjava-quality,quality-strict checkstyle:check pmd:check spotbugs:check

# 14 道门禁
cd ..
bash scripts/pre-push-gate.sh
```

### Step 7: 提交 + 推送 + PR

```bash
# 提交（原子提交，每变必测）
git add backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/TenderRequirementOutput.java
git add backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiTenderDocumentAnalyzer.java
git add backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/TenderDocumentPrompts.java
git commit -m "feat(tender-intake): AI prompt 新增 tenderInfo 字段抽取完整招标公告原文"

git add backend/src/main/java/com/xiyu/bid/entity/Tender.java
git add backend/src/main/java/com/xiyu/bid/tender/dto/TenderRequest.java
git add backend/src/main/resources/db/migration-mysql/V1xxx__*.sql
git commit -m "feat(tender): tender_info 字段容量从 VARCHAR(5000) 升级到 TEXT，DTO 校验同步到 20000"

git add src/views/Bidding/list/components/TenderBasicInfoTab.vue
git add src/views/Bidding/list/constants.js
git commit -m "feat(frontend): 标讯信息输入框 maxlength 从 5000 提升到 20000"

git add backend/src/test/java/com/xiyu/bid/biddraftagent/infrastructure/openai/*
git add e2e/tender-create-parse.spec.js
git commit -m "test(tender-intake): 新增 tenderInfo 字段映射+回填+E2E 测试"

# 推送
git push origin agent/claude/tender-intake-full-text-extraction

# 创建 PR（使用脚本）
bash scripts/pr-create.sh
```

## 验证清单

- [ ] 数据库迁移脚本执行成功，`tender_info` 字段类型为 TEXT
- [ ] 回滚脚本可正常执行（`check-flyway-rollback.sh`）
- [ ] 后端单元测试全绿（含新增 tenderInfo 测试）
- [ ] 架构测试全绿（ArchitectureTest / FPJavaArchitectureTest / MaintainabilityArchitectureTest）
- [ ] 前端 build + unit test 全绿
- [ ] E2E `tender-create-parse` 通过
- [ ] 14 道 pre-push gate 全绿
- [ ] PR 描述包含影响范围（prompt 调整 + DB 迁移 + 前端限制 + 蓝图配置）
