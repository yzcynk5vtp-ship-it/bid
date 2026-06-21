# 阶段变更通知 created_by 为空导致提交投标 500 根因分析

> 日期: 2026-06-21
> 排查者: zcode
> 修复 PR: `!925` / `agent/zcode/submit-bid-completed-task-gate`

---

## 现场还原

**症状素描**：修复「提交投标」409 后，用户再次点击 `POST /api/projects/13/drafting/submit-bid` 不再返回任务未完成冲突，而是返回 500。服务器日志显示阶段已经从 `DRAFTING` 切到 `EVALUATING`，随后通知写入失败：`Column 'created_by' cannot be null`。

**边界划定**：
- 阶段流转本身已成功执行：日志出现 `Project stage transitioned project=13 DRAFTING→EVALUATING` ✅
- 失败发生在后置通知副作用，不是审核状态、任务闸门或阶段流转策略 ❌
- `Notification.createdBy` 在实体/数据库层非空，调用方传 null 必然触发数据库约束 ❌

**思维沙箱**：遇到「同一个接口从 409 变成 500」时，不能沿用前一个根因猜测；必须看服务器日志确认新失败点。

---

## 剥洋葱：逆向调用链

### Layer 1 — 服务器日志层

服务器日志给出的关键证据：

```text
Project stage transitioned project=13 DRAFTING→EVALUATING
SQL Error: 1048, SQLState: 23000
Column 'created_by' cannot be null
sendNotification failed for project=13: could not execute statement
[insert into notification (body,created_at,created_by,payload_json,source_entity_id,source_entity_type,title,type) values (?,?,?,?,?,?,?,?)]
```

这证明 500 的第一现场不是 `AllTasksCompletedPolicy`，而是阶段切换后的通知插入。

### Layer 2 — submitBid 后置通知层

`submitBid` 阶段流转后会发送「进入评标」团队通知：

```java
// backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java:182-187
projectStageService.requestTransition(projectId, ProjectStage.EVALUATING,
        ProjectStageTransitionPolicy.GateInputs.EMPTY);
ensureEvaluationInitialized(projectId, currentUserId);

// 通知 #10: 提交投标→进入评标 → 团队成员
notificationService.notifyStageTransition(projectId, ProjectStage.DRAFTING, ProjectStage.EVALUATING, currentUserId);
```

修复后这里显式传入 `currentUserId`，避免通知创建人丢失。

### Layer 3 — 通知服务 actor 层

修复前阶段通知只有三参签名，没有 actor 参数，内部最终用 null 作为通知创建人。修复后增加 actor-aware 重载，并保留旧签名的系统用户兜底：

```java
// backend/src/main/java/com/xiyu/bid/project/notification/ProjectNotificationService.java:27-73
private static final Long SYSTEM_USER_ID = 0L;

public void notifyStageTransition(Long projectId, ProjectStage fromStage, ProjectStage toStage) {
    notifyStageTransition(projectId, fromStage, toStage, SYSTEM_USER_ID);
}

public void notifyStageTransition(Long projectId, ProjectStage fromStage, ProjectStage toStage, Long userId) {
    Project project = findProject(projectId);
    if (project == null) return;

    List<Long> teamMemberIds = getProjectTeamMemberIds(projectId);
    if (teamMemberIds.isEmpty()) return;

    sendNotification(projectId, "项目阶段变更", NotificationType.INFO,
            userId == null ? SYSTEM_USER_ID : userId, teamMemberIds, "");
}
```

---

## 零号病人定位

**第一行错误：**

```java
// backend/src/main/java/com/xiyu/bid/project/notification/ProjectNotificationService.java（修复前）
public void notifyStageTransition(Long projectId, ProjectStage fromStage, ProjectStage toStage) {
    ...
    sendNotification(projectId, "项目阶段变更", NotificationType.INFO,
            null, teamMemberIds, "");
}
```

**必然性解释：**

1. `submitBid` 阶段切换成功后调用阶段变更通知。
2. 阶段变更通知没有携带 actor，传入 `sendNotification` 的 userId 为 null。
3. `sendNotification` 调用 `NotificationApplicationService.createNotification(..., userId)`。
4. 通知实体的 `created_by` 非空，数据库插入时收到 null。
5. MySQL 抛出 `Column 'created_by' cannot be null`，当前事务被污染，接口最终返回 500。

**状态变迁图：**

```text
submitBid
  → requestTransition(DRAFTING → EVALUATING) 成功
  → ensureEvaluationInitialized 成功
  → notifyStageTransition(...)
  → createNotification(..., createdBy=null)
  → DB NOT NULL 约束失败
  → 外层事务回滚/接口 500
```

---

## 验证与修复

### 修复 diff

```diff
// backend/src/main/java/com/xiyu/bid/project/notification/ProjectNotificationService.java
+ private static final Long SYSTEM_USER_ID = 0L;
+
  public void notifyStageTransition(Long projectId, ProjectStage fromStage, ProjectStage toStage) {
-     Project project = findProject(projectId);
-     if (project == null) return;
-
-     List<Long> teamMemberIds = getProjectTeamMemberIds(projectId);
-     if (teamMemberIds.isEmpty()) return;
-
-     sendNotification(projectId, "项目阶段变更", NotificationType.INFO,
-             null, teamMemberIds, "");
+     notifyStageTransition(projectId, fromStage, toStage, SYSTEM_USER_ID);
+ }
+
+ public void notifyStageTransition(Long projectId, ProjectStage fromStage, ProjectStage toStage, Long userId) {
+     Project project = findProject(projectId);
+     if (project == null) return;
+
+     List<Long> teamMemberIds = getProjectTeamMemberIds(projectId);
+     if (teamMemberIds.isEmpty()) return;
+
+     sendNotification(projectId, "项目阶段变更", NotificationType.INFO,
+             userId == null ? SYSTEM_USER_ID : userId, teamMemberIds, "");
  }
```

```diff
// backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java
- notificationService.notifyStageTransition(projectId, ProjectStage.DRAFTING, ProjectStage.EVALUATING);
+ notificationService.notifyStageTransition(projectId, ProjectStage.DRAFTING, ProjectStage.EVALUATING, currentUserId);
```

**最小验证：**

```java
// backend/src/test/java/com/xiyu/bid/project/notification/ProjectNotificationServiceTest.java:183-193
@Test
@DisplayName("legacy signature uses system actor instead of null createdBy")
void legacySignatureUsesSystemActor() {
    when(projectRepository.findById(PID)).thenReturn(Optional.of(project("测试项目")));
    when(projectMemberRepository.findByProjectId(PID))
            .thenReturn(List.of(member(1L, "VIEWER"), member(2L, "EDITOR")));

    svc.notifyStageTransition(PID, ProjectStage.DRAFTING, ProjectStage.EVALUATING);

    verify(notificationService).createNotification(requestCaptor.capture(), eq(0L));
    assertThat(requestCaptor.getValue().recipientUserIds()).containsExactlyInAnyOrder(1L, 2L);
}
```

```java
// backend/src/test/java/com/xiyu/bid/project/service/ProjectDraftingServiceTest.java:231-235
verify(notificationService).notifyStageTransition(
        eq(1L),
        eq(ProjectStage.DRAFTING),
        eq(ProjectStage.EVALUATING),
        eq(1L));
```

回归验证：

```bash
cd backend
mvn test -Dtest=ProjectNotificationServiceTest,ProjectDraftingServiceTest,AllTasksCompletedPolicyTest,BidReviewPolicyTest,ProjectAccessGuardCoverageTest
```

结果：83 个测试通过，0 failure/error/skip。

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | `notifyStageTransition` 阶段通知使用 null actor | ✅ |
| 必然性已证明 | null actor → `notification.created_by` null → DB 1048 → 500 | ✅ |
| 最小验证已设计 | 四参 actor 测试 + 三参系统 actor 兼容测试 | ✅ |
| 修复 diff 已提供 | 新增 actor-aware 重载，submitBid 传 `currentUserId` | ✅ |
| 防复发测试已设计 | 见下 | ✅ |

**Verdict**: ✅ **PASS**

### 防复发测试

1. 所有写通知的 public 方法都应明确 actor 来源，不能把 null 当作默认创建人。
2. 保留旧签名时，必须用明确的系统 actor 兜底，并用单测锁住。
3. 对「主业务成功 + 后置通知失败」类问题，排查时必须先看服务端日志确认失败发生在主链路还是副作用链路。

### 未完全解决的后续风险

`SYSTEM_USER_ID = 0L` 是最小兼容方案，避免引入 schema 变更或大范围调用方改造。如果未来审计要求 `created_by` 必须引用真实用户表记录，应统一定义系统用户账号或改造通知创建人模型。
