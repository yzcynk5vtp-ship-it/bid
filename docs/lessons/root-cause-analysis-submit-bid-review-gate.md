# 提交投标误复用任务完成闸门 根因分析

> 日期: 2026-06-21
> 排查者: zcode
> 修复 PR: `!923` / `agent/zcode/submit-bid-completed-task-gate`

---

## 现场还原

**症状素描**：项目 `/project/13` 的投标文件审核已经通过后，点击「提交投标」调用 `POST /api/projects/13/drafting/submit-bid` 返回 409：`仍有 1 个任务未完成，无法提交投标`。

**边界划定**：
- 「投标文件审核通过后提交投标」应推进到 `EVALUATING` 阶段 ✅
- `/drafting/advance` 这类传统「任务全部完成后推进」闸门仍应保留任务完成检查 ✅
- 只有 `submit-bid` 把两个不同业务语义混在一起，导致审核通过后仍被未完成任务挡住 ❌

**思维沙箱**：不要把 `AllTasksCompletedPolicy` 改成把更多任务状态视为完成；真正变化的是 `submit-bid` 的业务前置条件，不是任务完成策略本身。

---

## 剥洋葱：逆向调用链

### Layer 1 — API 入口层

用户点击「提交投标」后进入 `ProjectDraftingService.submitBid`。该方法负责权限校验、审核状态校验、阶段流转和通知。

```java
// backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java:138-143
/**
 * 提交投标：投标文件审核通过后推进到 EVALUATING 阶段。
 */
@Auditable(action = "SUBMIT_BID", entityType = "Project",
        description = "提交投标并推进到评标阶段")
public ProjectDraftingViewDto submitBid(Long projectId, Long currentUserId) {
```

### Layer 2 — 审核语义层

`submitBid` 已有专门的投标文件审核状态校验：

```java
// backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java:169-174
var reviewState = bidReviewAppService.getReviewState(projectId);
var reviewDecision = BidReviewPolicy.canSubmitBid(parseStatus(reviewState.status()));
if (!reviewDecision.allowed()) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, reviewDecision.reason());
}
```

这说明「能否提交投标」的核心语义已经落在 `BidReviewPolicy`，即审核通过才能提交。

### Layer 3 — 陈旧闸门层

修复前 `submitBid` 还额外复用了任务完成闸门：

```java
// backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java（修复前）
AllTasksCompletedPolicy.Decision d = gateDecision(projectId);
if (!d.allowed()) {
    int incomplete = ((AllTasksCompletedPolicy.Decision.Deny) d).incompleteCount();
    throw new ResponseStatusException(HttpStatus.CONFLICT,
            "仍有 " + incomplete + " 个任务未完成，无法提交投标");
}
```

这段逻辑把「编制任务全部完成」和「投标文件审核通过」两个不同闸门叠加在一起，导致审核已经通过仍可能因为任务未完成返回 409。

---

## 零号病人定位

**第一行错误：**

```java
// backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java（修复前）
AllTasksCompletedPolicy.Decision d = gateDecision(projectId);
```

**必然性解释：**

1. 项目存在 1 个 `IN_PROGRESS` 任务。
2. `AllTasksCompletedPolicy` 只允许 `COMPLETED` / `CANCELLED` 作为终态。
3. `submitBid` 在审核通过后仍调用 `gateDecision(projectId)`。
4. `gateDecision` 返回 deny，`submitBid` 抛出 409。
5. 因此用户看到 `仍有 1 个任务未完成，无法提交投标`，无法进入评标。

**状态变迁图：**

```text
投标文件提交审核
  → 审核人批准，BidReviewStatus=APPROVED
  → 用户点击「提交投标」
  → submitBid 先通过 BidReviewPolicy
  → 又进入 AllTasksCompletedPolicy
  → 发现 IN_PROGRESS 任务
  → 409，阶段停留 DRAFTING
```

---

## 验证与修复

### 修复 diff

只从 `submitBid` 移除任务完成闸门，保留 `/drafting/advance` 的闸门语义不变：

```diff
// backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java
- AllTasksCompletedPolicy.Decision d = gateDecision(projectId);
- if (!d.allowed()) {
-     int incomplete = ((AllTasksCompletedPolicy.Decision.Deny) d).incompleteCount();
-     throw new ResponseStatusException(HttpStatus.CONFLICT,
-             "仍有 " + incomplete + " 个任务未完成，无法提交投标");
- }
-
  ProjectStage currentStage = projectStageService.currentStage(projectId);
  if (currentStage != ProjectStage.DRAFTING) {
      log.info("Bid submission skipped (idempotent) project={} currentStage={}",
              projectId, currentStage);
```

**最小验证：**

新增测试覆盖「审核已通过 + 仍有未完成任务 + 当前用户是项目主负责人」时，`submitBid` 仍推进阶段：

```java
// backend/src/test/java/com/xiyu/bid/project/service/ProjectDraftingServiceTest.java:216-235
@Test
void submitBid_approvedReview_allowsIncompleteTasks() {
    prepareSubmitBidHappyPath();
    when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
            Task.builder().id(1L).projectId(1L).title("a").status(Task.Status.IN_PROGRESS).build()));
    when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "sales")));
    prepareLeadAssignment(1L, 2L);

    var view = service.submitBid(1L, 1L);

    assertThat(view).isNotNull();
    verify(projectStageService).requestTransition(
            eq(1L),
            eq(ProjectStage.EVALUATING),
            any());
}
```

回归验证：

```bash
cd backend
mvn test -Dtest=ProjectDraftingServiceTest,AllTasksCompletedPolicyTest,BidReviewPolicyTest,ProjectAccessGuardCoverageTest
```

结果：55 个测试通过，0 failure/error/skip。

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | `ProjectDraftingService.submitBid` 误调用 `gateDecision(projectId)` | ✅ |
| 必然性已证明 | APPROVED + IN_PROGRESS task → `AllTasksCompletedPolicy` deny → 409 | ✅ |
| 最小验证已设计 | `submitBid_approvedReview_allowsIncompleteTasks` | ✅ |
| 修复 diff 已提供 | 仅移除 `submitBid` 内任务闸门，不改 policy | ✅ |
| 防复发测试已设计 | 见下 | ✅ |

**Verdict**: ✅ **PASS**

### 防复发测试

1. `submitBid` 必须覆盖「审核通过但任务未完成仍允许提交」正例。
2. `/drafting/advance` 必须继续覆盖「任务未完成返回 409」反例。
3. 不要通过扩大 `AllTasksCompletedPolicy` 终态集合来绕过业务语义问题；`REVIEW` 等非终态不应被当作完成。
