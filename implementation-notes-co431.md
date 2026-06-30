# CO-431 关联 CRM 商机时客户职位映射错误 — 实施笔记

> 任务：关联 CRM 商机时，客户对接人职位信息映射错误（CRM「招标文件制作人」→ 投标系统「项目最高决策人」）。
> 分支：`agent/zcode/co-431-crm-position-mapping`
> 入口 ticket：[CO-431](https://linear.app/ericforai/issue/CO-431)
> 阶段：**第二阶段 — 真正修复（已实施）**

## 1. 重要的方向修正：第一轮方案是被回滚过的

### 第一轮判断（错误）

初读 `useCrmOpportunitySelector.js:190` 看到 `CUSTOMER_INFO_ROWS[idx % 14].roleKey` 按下标分配，且 `CRM_POSITION_TO_ROLE` 已 import 但未用，判断根因为"前端按下标分配 roleKey"，提议用 `CRM_POSITION_TO_ROLE[c.position]` 字典映射。

### 用户要求按 lessons-learned §23【全链路日志排查 SOP】复查

git log 揭示 CO-329 已经走过这条路：

| 时间 | commit | 改动 | 结果 |
|---|---|---|---|
| 6/27 13:55 | `245fc816c` | 用 `CRM_POSITION_TO_ROLE[c.position]` 字典映射 | **被判定"invalid"** |
| 6/27 20:45 | `41f02ccc1` | **回滚**到 `CUSTOMER_INFO_ROWS[idx % 14]`，msg: "revert **invalid** c.position mapping" | 当前现状 |

**第一轮方案正是 7 小时后被回滚的那个方案。** 如果直接照抄就重蹈覆辙（lessons §4 警告）。

## 2. 按 SOP 查到的真实证据（非猜测）

CO-329 评论里有两份**真实抓包/日志证据**，但互相矛盾：

| 证据 | 来源 | 内容 | position 字段 |
|---|---|---|---|
| ① 生产日志 | eric 06-26 02:44 | `{"code":"0","data":[{"name":...,"position":"1",...}]}` | **有，值 "1"**（数字字符串） |
| ② F12 抓包 | dom.chuya 06-26 08:43 | `{"id":15476,"name":"张三","phone":...}` | **没有这个字段** |

回滚 commit `41f02ccc1` 的依据是 eric 06-27 13:57 的评论："实际上 CRM 接口响应里根本不存在 position 这个字段"。

## 3. 用代码证据解释矛盾 — eric 当年误判了

`application.yml:57` 配置 `default-property-inclusion: non_null`。后端 `ContactPersonInfoVO` 是 record，`position` 为 null 时 Jackson 序列化会**丢弃该字段**。

所以真相是：
- CRM 上游对接人**填了职位** → 返回 `position:"1"`（证据①）→ 后端透传 → 前端能看到
- CRM 上游对接人**没填职位** → CRM 不返回 position → 后端 VO position=null → `non_null` 丢掉 → 前端看不到（证据②）

eric 把"某个没填职位的对接人导致 position 缺失"误判成"接口根本不返回 position"，于是回滚正确方向改用 idx 分配。结果：
- 短期"14 个值都带过来"（不丢人，用户以为修好）
- 长期**填了职位的对接人也被按 idx 错位分配** ← CO-431 报的现象

## 4. SOP §23 第 4 条「禁止乱猜」— 必须先确诊

issue CO-431 描述"CRM 中填写的「招标文件制作人」"暗示 position 可能是**中文职位名**而非数字。如果是中文，`CRM_POSITION_TO_ROLE["招标文件制作人"]`=undefined 会再次导致对接人被过滤。

本地无法确诊：dev 环境 backend.log 无 contact-person 调用记录，MySQL 未连接。

**结论：不能贸然改业务逻辑，先加诊断日志确诊 position 真实格式。**

## 5. 本次改动（仅诊断日志，不改业务逻辑）

### 改动文件

| 文件 | 改动 |
|---|---|
| `backend/src/main/java/com/xiyu/bid/crm/application/CrmContactPersonService.java` | `parseListResponse` 加 3 行 INFO 日志，打印每条对接人的 `id`/`name`/`position` 原始值（从 JsonNode 取，绕过 non_null） |
| `backend/src/test/java/com/xiyu/bid/crm/application/CrmContactPersonServiceTest.java` | 加 ListAppender 日志断言模式 + 2 个测试（含 position / 缺失 position） |

### 日志格式

```
CRM contact-person raw position: id=1, name=张三, position=8
CRM contact-person raw position: id=2, name=李四, position=<missing>
```

`<missing>` 标记缺失的 position，方便 grep。

### 设计要点

1. 从 `resolved.node()`（JsonNode）取 position 原始值，**绕过 non_null 序列化**——这是关键，否则 null position 会被吞，日志看不到真相
2. 只加日志，不动返回值、不改解析逻辑、不影响现有 8 个测试
3. 复刻项目已有的 `OperationLogAspectTest` 日志断言模式（ListAppender），不引入新依赖

## 6. TDD 验证

- **Red**：2 个新测试失败（当前日志不含 position 原始值），原有 8 个全绿
- **Green**：加日志后 10 个测试全绿
- 命令：`cd backend && mvn -o test -Dtest=CrmContactPersonServiceTest`

## 7. 部署后验证清单（交给用户）

部署后需要做一次关联 CRM 商机操作（选一个对接人填了职位的商机），然后看日志：

```bash
# 在生产服务器上
journalctl -u xiyu-bid-backend --since "10 minutes ago" | grep "CRM contact-person raw position"
```

预期能看到类似：
- `position=8` → 数字格式，对应"招标文件制作人"，可放心用 `CRM_POSITION_TO_ROLE` 映射
- `position=招标文件制作人` → 中文格式，需要新建中文→roleKey 映射表
- `position=<missing>` → CRM 后台没填职位，需要回退策略

确诊后才能定第二阶段（改前端映射）的具体方案。**这是 lessons §23「禁止乱猜」的硬要求。**

## 8. 不做的事（防顺手重构）

- ❌ 不改前端 `useCrmOpportunitySelector.js`（等诊断结果）
- ❌ 不改 `customerInfoMatrixConfig.js`（映射表本身没错）
- ❌ 不改 `ContactPersonInfoVO`（VO 已正确透传 position）
- ❌ 不动 `non_null` 全局配置（影响面太大，与本任务无关）
- ❌ 不删 `CRM_POSITION_TO_ROLE` import（dead import 待第二阶段处理）

---

## 第二阶段：真正修复（CO-431 根因修复）

### 决定性证据 — 不需要等诊断日志了

原计划等 PR #1419 诊断日志部署后看真实 position 格式再改。但深挖 CO-329 Linear 评论时发现 **eric 自己在 PR #1124 评论里已贴出生产日志 smoking gun**：

```
CRM POST /contact-person-info/page-list → 200 OK
{"code":"0","msg":"success","data":[{"id":...,name":...,position:"1",...}]}

ERROR Failed to parse CRM contact-person response
com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException:
  Unrecognized field "position"
```

**CRM 确实返回 position 字段，格式是数字字符串 "1"。** 这是 eric 在 PR #1124 里亲自贴的生产日志原文。

eric 后来在 revert 时（06-27 13:57 评论）却说"实际上 CRM 接口响应里根本不存在 position 这个字段"——**与他自己在 PR #1124 贴的日志直接矛盾**。这正是 lessons §4 警告的"回滚 PR 前必须确认根因"——revert 基于误判。

### 矛盾的成因 — F12 看到的是被修剪过的 JSON

dom.chuye 在 F12 抓到的响应没有 position（那条测试数据 position 真的是 null），而 `application.yml:57` 的 `non_null` 把 null position 抹掉了 → 前端看不到 → 误判"接口不返回 position"。

但 eric 在 PR #1124 里看到的 `CrmHttpClient` 原始日志（CRM 给后端的响应）有 `position:"1"`。两层证据本不矛盾，只是"填了 vs 没填职位"的区别，被误读成"接口有没有这个字段"。

### 修复方案（最小改动）

`useCrmOpportunitySelector.js:189-209` 把 `idx % 14` 按下标分配，改回 `CRM_POSITION_TO_ROLE[c.position]` 按 position 字段映射，position 缺失/超出字典时按 idx 兜底落位：

```javascript
customerInfos = contacts.map((c, idx) => {
  const posNum = c.position != null && c.position !== '' ? c.position : null
  const roleKey = (posNum != null && CRM_POSITION_TO_ROLE[posNum]) || CUSTOMER_INFO_ROWS[idx % 14].roleKey
  const posIdx = roleKey ? CUSTOMER_INFO_ROWS.findIndex(r => r.roleKey === roleKey) : -1
  const positionValue = posIdx >= 0 ? String(posIdx + 1) : String((idx % 14) + 1)
  return { roleKey, NAME: c.name || '', ... POSITION: positionValue, ... }
}).filter(c => c.roleKey)
```

**与 6/27 被 revert 的 `245fc816c` 的关键区别**：那次 `CRM_POSITION_TO_ROLE[c.position]` 命中不到就 `return null` 过滤掉（`.filter(Boolean)`），导致"没填职位"的对接人被丢弃 → 14 个值变少 → dom 报"14 个值全部丢失"。本次加 `|| CUSTOMER_INFO_ROWS[idx % 14].roleKey` 兜底，保证不丢人，同时填了职位的按 position 正确落位。

### 修复阶段改动文件

| 文件 | 改动 |
|---|---|
| `src/views/Bidding/detail/components/useCrmOpportunitySelector.js` | `idx % 14` → `CRM_POSITION_TO_ROLE[c.position]` + idx 兜底 |
| `src/views/Bidding/detail/components/useCrmOpportunitySelector.spec.js` | +2 测试：position 映射 + position 缺失兜底 |

### TDD 验证

- **Red**：新测试"CO-431 按 CRM position 字段映射角色"失败——`roleKey: 'PROJECT_HIGHEST_DECISION_MAKER'`（idx=0 错配）≠ 期望 `BID_DOCUMENT_PREPARER`（position=8）
- **Green**：修复后 12 个测试全绿（10 原有 + 2 新增）
- **回归**：`src/views/Bidding/detail/components/` 6 个测试文件 39 个测试全绿
- 命令：`npx vitest run src/views/Bidding/detail/components/`

### 验证成功的标准

部署后重新关联一个对接人填了职位的 CRM 商机：
1. position=8 的对接人应落到「招标文件制作人」行，职位下拉显示"招标文件制作人"
2. position=5 的对接人应落到「电商公司总经理」行
3. 没填职位的对接人仍能兜底落位（不丢失），按出现顺序填到剩余行

### 不做的事（防顺手重构）

- ❌ 不改 `customerInfoMatrixConfig.js`（映射表/字典本身没错）
- ❌ 不改 `ContactPersonInfoVO`（VO 已正确透传 position）
- ❌ 不动 `non_null` 全局配置（影响面太大，与本任务无关）
- ❌ 不删 PR #1419 诊断日志（保留，便于后续核对 position 实际值）
- ❌ 不改 `CrmHttpClient.java:198` 的 200 字符截断（与本任务无关，单独 issue 处理）
