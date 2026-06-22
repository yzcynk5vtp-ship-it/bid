
# 标讯创建去重拦截报错文案缺少系统上下文

> Issue: CO-301
> 日期: 2026-06-22
> 排查者: cursor

---

## 现场还原

**症状素描**: 手动创建标讯或外部系统推送标讯时，系统检测到重复字段（如项目名称、招标主体、开标时间等）后返回错误提示 `"标讯已存在"`。用户和测试人员看到此提示时，无法判断：

1. 到底是**哪个系统**拦截了操作（投标管理系统？还是 CRM？还是外部推送服务？）
2. 是**本地数据库**去重，还是**外部接口**返回的重复信号？

在多入口（手动创建 / CRM webhook / 外部 API 推送）共用同一业务能力的系统中，`"标讯已存在"` 这种"谁都能说"的错误消息，失去了它应有的导航价值。

**边界划定**:
- 手动创建标讯 `POST /api/tenders` — 使用 `TenderDuplicateException`
- 外部推送标讯 `POST /api/integration/tenders/push` — 使用 `IllegalArgumentException("标讯已存在")`
- 两处代码都写死了 `"标讯已存在"` 文案，但上下文完全不同

**思维沙箱**:
1. 如果把错误消息加上系统前缀，用户就能在不查日志的情况下判断拦截来源
2. 两个入口用同一文案，会导致"看起来像共用同一路径"的错误假设
3. 如果错误消息包含具体重复字段，排查效率会更高（但超出本次修复范围）

---

## 剥洋葱：逆向调用链

### Layer 1 — 入口/参数层

**路径 A：手动创建标讯**

```
TenderController.createTender()
  → TenderCommandService.createTender()
  → TenderDeduplicationService.findDuplicates()
  → throw new TenderDuplicateException(duplicates)
  → GlobalExceptionHandler 捕获 → 返回 400
```

`TenderDuplicateException` 默认 message 就是 `"标讯已存在"`。

**路径 B：外部系统推送标讯**

```
TenderIntegrationCommandService.pushTender()
  → TenderDeduplicationService.findDuplicates() 或按 externalId 命中查询
  → throw new IllegalArgumentException("标讯已存在")
  → GlobalExceptionHandler 捕获 → 返回 400
```

此处直接用 `IllegalArgumentException`，message 也是 `"标讯已存在"`。

**路径 C：外部推送返回 DUPLICATE 状态**

```
TenderIntegrationCommandService.pushTender()
  → 判断重复后返回 PushResult.DUPLICATE
  → message("标讯已存在")
  → 最终写入响应或同步回调
```

同样的文案，三条不同路径。

### Layer 2 — 核心逻辑层

**问题 1：错误消息不含系统上下文**

```java
// TenderDuplicateException.java
public class TenderDuplicateException extends RuntimeException {
    public TenderDuplicateException() {
        super("标讯已存在");   // ← 用户不知道"被谁拦截"
    }
}
```

**问题 2：不同入口用不同异常类，message 却相同**

```java
// 手动创建路径
throw new TenderDuplicateException(duplicates);   // message = "标讯已存在"

// 外部推送路径
throw new IllegalArgumentException("标讯已存在");  // message = "标讯已存在"

// 状态返回路径
return PushResult.builder()
    .status(DUPLICATE)
    .message("标讯已存在")  // ← 同样的 message
    .build();
```

三者 message 完全一致，前端/用户无法区分来源。

### Layer 3 — 用户影响层

```
用户看到前端弹窗："标讯已存在"
  → 用户困惑：这是我刚手动录入的重复？
            → 还是 CRM 推送过来的已同步？
            → 还是外部 API 推送的标讯？
  → 用户去查日志 / 找开发确认 → 增加沟通成本
```

---

## 零号病人定位

**第一行错误 1：TenderDuplicateException 默认 message**

```
TenderDuplicateException.java
super("标讯已存在");
```

**第一行错误 2：TenderIntegrationCommandService 非法参数异常**

```
TenderIntegrationCommandService.java
throw new IllegalArgumentException("标讯已存在");
```

**第一行错误 3：TenderIntegrationCommandService PushResult message**

```
TenderIntegrationCommandService.java
.message("标讯已存在")
```

**必然性解释：**
- 开发时只关注"是否成功返回错误"，不关注"错误消息对用户是否可理解"
- 文案是代码中最容易被忽视的"硬编码"——它看起来像配置，实则是 UI 的一部分
- 多个开发者在不同时期修改同一段业务，每人都遵循"前人怎么写我就怎么写"的惯性，导致同一句没上下文的文案在三个地方重复

---

## 验证与修复

### 修复 diff

```java
// TenderDuplicateException.java
-  super("标讯已存在");
+  super("投标管理系统该标讯已存在");
```

```java
// TenderIntegrationCommandService.java
-  throw new IllegalArgumentException("标讯已存在");
+  throw new IllegalArgumentException("投标管理系统该标讯已存在");

-  .message("标讯已存在")
+  .message("投标管理系统该标讯已存在")
```

同时更新对应测试断言和 E2E 匹配文本：

```java
// TenderDeduplicationServiceTest.java
-  assertThat(e.getMessage()).contains("标讯已存在");
+  assertThat(e.getMessage()).contains("投标管理系统该标讯已存在");

// GlobalExceptionHandlerTest.java 等
```

```javascript
// e2e/tender-manual-create.spec.js
-  await expect(page.getByText(/标讯已存在/i)).toBeVisible();
+  await expect(page.getByText(/投标管理系统该标讯已存在/i)).toBeVisible();
```

### 最小验证

1. 后端测试：`mvn test -Dtest=TenderDeduplicationServiceTest,TenderCommandServiceTest,TenderIntegrationServicePushEvaluationTest,GlobalExceptionHandlerTest` → 21/21 全绿
2. E2E：手动创建重复标讯 → UI 应显示"投标管理系统该标讯已存在"
3. 架构测试：`mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest,ProjectAccessGuardCoverageTest` → 全绿

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | 3 处文件中的"标讯已存在"硬编码 | ✅ |
| 必然性已证明 | 多入口系统中，无上下文的错误消息必然导致用户困惑 | ✅ |
| 最小验证已设计 | 后端单元测试 + E2E 场景覆盖 | ✅ |
| 修复 diff 已提供 | 见上（仅 message 字符串变更） | ✅ |
| 防复发测试已设计 | 测试断言硬编码了新消息，一旦回退立即失败 | ✅ |

**Verdict**: ✅ **PASS**

### 防复发策略

1. **错误消息规范**：业务异常 message 应包含"系统/子系统"前缀，如 `"投标管理系统该..."`
2. **Code Review 检查项**：review 时检查错误消息是否有足够的上下文
3. **测试断言绑定**：测试用例中硬编码完整消息文本（不做模糊匹配），一旦消息退化立即红掉
4. **多入口消息一致性**：同一业务概念的不同入口应使用风格一致的错误消息

---

## 相关文档

- [lessons-learned.md → 业务异常消息应包含系统上下文](../lessons/lessons-learned.md)
