# 根因分析：企微通知全部 SKIPPED + 生产环境配置占位符缺失

> **日期**：2026-07-02
> **PR**：#1510（outbound_log 状态误标修复）+ #1531（生产配置占位符统一）
> **影响范围**：企微通知链路、生产环境配置管理
> **排查时长**：约 4 小时（多次误判根因）

---

## 1. 问题现象

服务器打开 `NOTIFICATION_WECOM_ENABLED=true` 后，Job 确实在运行（`notification_delivery_task` 表有 8188 条记录），但：

- `notification_outbound_log` 表 8189 条记录**全是 SKIPPED + NOT_BOUND**，0 条 SENT
- 即使有工号的用户（03595、06234、08687、11484）也被 SKIPPED
- 96 条 DEAD_LETTER 任务的 `last_error_message` 全是 **Lorem Ipsum**（"nostrud nulla Ut"、"magna occaecat" 等）

---

## 2. 排查过程与误判

### 误判 1：以为是消息中心侧 `bid-platform` code 配了多条记录

**现象**：用 curl 测试真实消息中心接口，返回 500 "One record is expected, but the query result is multiple records"

**真相**：这个错误是针对**工号**的多条记录，不是 code 的多条记录。联系消息中心侧清理后接口恢复正常。

**教训**：错误信息的语义需要精确理解，不能凭关键词臆测。

### 误判 2：以为有工号用户被 SKIPPED 是代码逻辑 bug

**现象**：`WeComPushService.push()` 第 50 行只有 `isBlank(employeeNumber)` 才返回 skip，但有工号的用户也被 SKIPPED

**真相**：[NotificationDeliveryJobService.java#L149-L157](file:///Users/user/xiyu/worktrees/kimi/backend/src/main/java/com/xiyu/bid/notification/outbound/application/NotificationDeliveryJobService.java#L149-L157) 的 `applyFailureDecision()` 方法把 DEAD_LETTER/DROP 状态**错误标记为 SKIPPED + NOT_BOUND**（应该是 FAILED + ERROR）。代码 bug 导致 outbound_log 状态失真，误导排查方向。

**修复**：PR #1510 修正状态映射：
- DEAD_LETTER → FAILED + ERROR（不是 SKIPPED + NOT_BOUND）
- DROP → SKIPPED + DISABLED（不是 SKIPPED + NOT_BOUND）
- SUCCEED_WITH_LOG → SKIPPED + DISABLED（不是 SKIPPED + NOT_BOUND）

### 真正根因：Spring Boot 松散绑定陷阱

**关键发现**：Lorem Ipsum 错误信息是 **YApi mock** 返回的假数据。后端实际还在请求 `https://yapi.ehsy.com/mock/406`，不是真实消息中心地址。

**为什么环境变量没生效**：

1. `WecomMessageCenterProperties` 用 `@ConfigurationProperties(prefix = "app.wecom.message-center")`
2. 服务器配置了 `XIYU_WECOM_MESSAGE_CENTER_BASE_URL=http://base-oss-test.ehsy.com`
3. Spring Boot 松散绑定规则：`XIYU_WECOM_MESSAGE_CENTER_BASE_URL` → `xiyu.wecom.message.center.base.url`（**不是** `app.wecom.message-center.base-url`）
4. 因为 `XIYU_` 前缀不匹配 `app.` 前缀，环境变量无法自动映射
5. `application-prod.yml` 没有 `${XIYU_WECOM_MESSAGE_CENTER_BASE_URL:...}` 占位符（只在 `application-dev.yml` 有）
6. 最终用代码默认值 `https://yapi.ehsy.com/mock/406`（YApi mock 地址）

**修复**：服务器临时改用 `APP_WECOM_MESSAGE_CENTER_BASE_URL`（正确映射到 `app.wecom.message-center.base-url`），同时 PR #1531 在 `application-prod.yml` 添加占位符让 `XIYU_WECOM_MESSAGE_CENTER_*` 也能用。

---

## 3. Spring Boot 松散绑定规则详解

### 规则

Spring Boot 松散绑定（Relaxed Binding）将环境变量名映射到属性路径：

```
环境变量名 → 属性路径
APP_WECOM_MESSAGE_CENTER_BASE_URL → app.wecom.message-center.base-url
```

规则：
- 大写变小写
- 下划线变连字符（`.` 在 yml 中）
- 必须严格匹配 `@ConfigurationProperties(prefix = "app.wecom.message-center")` 的 prefix

### 陷阱

`XIYU_WECOM_MESSAGE_CENTER_BASE_URL` 会被映射为 `xiyu.wecom.message.center.base.url`，**不是** `app.wecom.message-center.base-url`。

| 环境变量名 | 松散绑定映射的属性 | 是否匹配 prefix=app.wecom.message-center |
|---|---|---|
| `APP_WECOM_MESSAGE_CENTER_BASE_URL` | `app.wecom.message-center.base-url` | ✅ |
| `XIYU_WECOM_MESSAGE_CENTER_BASE_URL` | `xiyu.wecom.message.center.base-url` | ❌ |

### 解决方案

**yml 占位符是环境变量名与属性路径的"桥"**：

```yaml
app:
  wecom:
    message-center:
      base-url: ${XIYU_WECOM_MESSAGE_CENTER_BASE_URL:https://...}
```

`${XIYU_WECOM_MESSAGE_CENTER_BASE_URL:...}` 是 Spring 的属性占位符语法，会先从环境变量读取，再赋值给 `app.wecom.message-center.base-url` 属性。这绕过了松散绑定的 prefix 匹配规则。

**结论**：当环境变量前缀（如 `XIYU_`）与属性 prefix（如 `app.`）不一致时，**必须在 yml 中显式声明占位符**，不能依赖松散绑定。

---

## 4. application-prod.yml 配置占位符缺失清单

PR #1531 修复了以下缺失：

| 配置项 | 修复前 | 修复后 |
|---|---|---|
| `app.wecom.message-center.*` | 只在 dev yml 声明 | prod yml 添加占位符 |
| `app.platform.base-url` | 无占位符（用代码默认值 localhost:1314） | prod yml 添加占位符 |
| `notification.wecom.enabled` | 无占位符 | prod yml 添加占位符 |
| `app.crm.*` | 在 prod yml 完全缺失 | prod yml 全量声明 |
| `webhook.crm.url` | 默认值是测试环境 URL | 默认值改为空 |
| `rate.limit` | application.yml 两段 YAML 文档变量名冲突 | 移除第二段重复定义 |

---

## 5. 企微通知链路验证 SOP

### 5.1 配置检查

```bash
# 服务器 env 文件
grep -E 'NOTIFICATION_WECOM|APP_WECOM|APP_PLATFORM' /etc/xiyu-bid/backend.env
```

应包含：
```
NOTIFICATION_WECOM_ENABLED=true
APP_WECOM_MESSAGE_CENTER_BASE_URL=http://base-oss-test.ehsy.com
APP_WECOM_MESSAGE_CENTER_APPLICATION_CODE=bid-platform
APP_PLATFORM_BASE_URL=http://winbid-test.ehsy.com
```

### 5.2 消息中心接口连通性

```bash
curl -s -X POST http://base-oss-test.ehsy.com/qywx/sendMSG \
  -H 'Content-Type: application/json' \
  -d '{"userName":"09118","message":"test","code":"bid-platform"}'
```

期望返回 `{"code":0,"success":true,...}`

### 5.3 数据库验证

```sql
-- 查推送状态分布
SELECT status, COUNT(*) FROM notification_outbound_log GROUP BY status;

-- 查最近推送
SELECT id, user_id, status, wecom_errcode, LEFT(wecom_errmsg,50) AS errmsg, created_at
FROM notification_outbound_log
ORDER BY created_at DESC LIMIT 10;
```

### 5.4 手动触发测试通知

```sql
SET NAMES utf8mb4;
INSERT INTO notification (type, title, body, created_by, created_at)
VALUES ('SYSTEM', '企微通知链路验证测试', '此为链路验证测试消息', 1, NOW());

-- 用 LAST_INSERT_ID() 作为 notification_id
INSERT INTO notification_delivery_task (notification_id, recipient_user_id, event_type, business_key, status, attempt_count, payload, created_at, updated_at)
VALUES (LAST_INSERT_ID(), <有工号的用户ID>, 'notification.wecom_push', 'test:manual', 'PENDING', 0,
  '{"notificationId":<id>,"recipientUserId":<uid>,"type":"SYSTEM","title":"测试","sourceEntityType":null,"sourceEntityId":null}',
  NOW(), NOW());
```

### 5.5 常见错误诊断

| outbound_log 状态 | skip_reason | wecom_errcode | 可能原因 |
|---|---|---|---|
| SKIPPED | NOT_BOUND | NULL | 用户无工号（正常）或 DEAD_LETTER 误标（PR #1510 修复前） |
| SENT | NULL | 0 | 推送成功 |
| FAILED | ERROR | NULL | HTTP 异常或 mock 地址返回错误 |
| FAILED | ERROR | -83xxxxx | YApi mock 返回的假错误码（检查 base-url 是否指向 mock） |
| FAILED | ERROR | NULL + "未找到企业微信配置: code=IT" | application-code 未配置或为空 |

---

## 6. 工程教训

### 6.1 配置占位符必须在 prod yml 显式声明

**规则**：所有 `@ConfigurationProperties` 类绑定的属性，必须在 `application-prod.yml` 中有对应的 `${ENV_VAR:default}` 占位符，即使默认值为空。

**原因**：
- 运维人员看 yml 就知道需要配哪些环境变量
- 避免依赖隐式的松散绑定（特别是 `XIYU_` 前缀无法映射到 `app.` 属性的情况）
- 配置变更可追溯（git diff 能看到）

### 6.2 环境变量前缀应与属性 prefix 一致

**规则**：环境变量名前缀应与 `@ConfigurationProperties` 的 prefix 严格对应，或通过 yml 占位符桥接。

**反例**：
- `@ConfigurationProperties(prefix = "app.wecom.message-center")` + 环境变量 `XIYU_WECOM_MESSAGE_CENTER_BASE_URL`（前缀不匹配）

**正例**：
- 环境变量 `APP_WECOM_MESSAGE_CENTER_BASE_URL`（前缀匹配，松散绑定自动映射）
- 或 yml 占位符 `base-url: ${XIYU_WECOM_MESSAGE_CENTER_BASE_URL:...}`（桥接）

### 6.3 错误日志的语义不能臆测

**教训**：消息中心返回 "multiple records" 错误时，最初臆测是 `bid-platform` code 配了多条，实际是工号对应多条企微账号。应通过对照实验（不同工号、不同 code）确认错误来源。

### 6.4 outbound_log 状态必须真实反映推送结果

**教训**：DEAD_LETTER 被标记为 SKIPPED + NOT_BOUND 会严重误导排查。状态映射必须语义准确：
- SENT：实际发送成功
- FAILED：发送失败（HTTP 错误、业务错误）
- SKIPPED：主动跳过（无工号、功能禁用）

### 6.5 默认值不应指向测试环境

**规则**：yml 中外部接口的默认值不应指向测试环境 URL，生产环境忘配会静默连接测试环境。

**反例**：`url: ${WEBHOOK_CRM_URL:https://crm-test.ehsy.com/...}`
**正例**：`url: ${WEBHOOK_CRM_URL:}`（空默认值，fail-closed）

### 6.6 application.yml 不应有多段 YAML 文档重复定义同一属性

**教训**：application.yml 用 `---` 分隔两段文档，两段都定义 `rate.limit` 但用不同环境变量名，导致第一段的变量被覆盖。多段文档应仅用于 profile 隔离（`spring.config.activate.on-profile`），不应在同一 profile 下重复定义。

---

## 7. 相关文件

- [WeComPushService.java](file:///Users/user/xiyu/worktrees/kimi/backend/src/main/java/com/xiyu/bid/notification/outbound/service/WeComPushService.java) — 企微推送编排
- [WecomMessageCenterProperties.java](file:///Users/user/xiyu/worktrees/kimi/backend/src/main/java/com/xiyu/bid/wecom/config/WecomMessageCenterProperties.java) — `@ConfigurationProperties(prefix = "app.wecom.message-center")`
- [NotificationDeliveryJobService.java](file:///Users/user/xiyu/worktrees/kimi/backend/src/main/java/com/xiyu/bid/notification/outbound/application/NotificationDeliveryJobService.java) — Job 调度 + outbound_log 状态映射
- [application-prod.yml](file:///Users/user/xiyu/worktrees/kimi/backend/src/main/resources/application-prod.yml) — 生产环境配置
- [application-dev.yml](file:///Users/user/xiyu/worktrees/kimi/backend/src/main/resources/application-dev.yml) — 开发环境配置（含 wecom 占位符）
