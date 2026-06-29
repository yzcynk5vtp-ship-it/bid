# CO-390 绑定联系人未用统一 UserPicker + 权限 403 被吞导致投标组长/专员无法搜索根因分析

> Issue: CO-390（Linear）/ 内部 Bug 修复 follow-up
> 日期: 2026-06-29
> 排查者: mimo
> 修复 PR: `#1333` (commit `79f3fd6c4`, merge `fde0f7500`)

---

## 现场还原

**症状素描**：用户反馈 2 个 Bug：
1. 绑定联系人字段，展示的人员选择表单需要是姓名（工号）形式，与项目负责人或投标负责人选择控件的逻辑一致。
2. 当前使用投标组长或投标专员账号，新增账户信息时，绑定联系人字段，无法搜索筛选人员。

**边界划定**：
- AccountFormDialog.vue 用 `mode="candidates"` + `/api/admin/users` 预加载候选列表 ❌（偏离统一标准）
- `/api/admin/users` 控制器 `@PreAuthorize("hasRole('ADMIN')")` → 投标组长/专员 403 ❌
- 前端 `catch { /* silent */ }` 吞掉 403 错误 ❌
- 其他场景（TaskForm/DraftingStage/CAFormDialog）均用 `mode="search"` + 统一接口 `/api/users/search` ✅

---

## 剥洋葱：两个症状其实是两层链路

### 链路 A — 未用统一选人控件

CO-390 主提交（PR !1312）实现 AccountFormDialog 绑定联系人时，用了 `mode="candidates"` + 外部 `/api/admin/users` 预加载候选列表，偏离了项目统一标准。

**对比全仓 UserPicker 用法**：

| 场景 | 文件 | mode | 数据来源 |
|---|---|---|---|
| 任务执行人 | TaskForm.vue | `search` | 统一接口 `/api/users/search` |
| 标书审核人 | DraftingStage.vue | `search` | 统一接口 `/api/users/search` |
| 评审人 | ProjectDetailReviewerDialog.vue | `search` | 统一接口 `/api/users/search` |
| CA 保管员 | CAFormDialog.vue | `search` | 统一接口 `/api/users/search` |
| **绑定联系人（CO-390 主提交）** | AccountFormDialog.vue | **`candidates`** | **`/api/admin/users`（绕过统一 API）** |

统一控件 UserPicker.vue 通过 `formatUserLabel` 自动展示"姓名（工号）"格式，但 `mode="candidates"` + `:load-on-mount="false"` 模式下 UserPicker 不调统一接口，只显示外部传入的 `biddingUsers`。

### 链路 B — 权限 403 被吞

`/api/admin/users` 对应 `AdminUserController`，该控制器 `@PreAuthorize("hasRole('ADMIN')")` 只允许 ADMIN 角色。

**必然性解释**：

```
投标组长（bid-TeamLeader）/投标专员（bid-Team）登录
  ↓
打开新增账户对话框 → onOpen() 调 loadBiddingUsers()
  ↓
GET /api/admin/users → Spring Security 拦截 → 403 Access Denied
  ↓
catch { /* silent */ } 吞掉错误
  ↓
biddingUsers.value = []（空数组）
  ↓
UserPicker initial-options=[] + load-on-mount=false
  ↓
下拉无候选 + 不触发远程搜索 → 无法搜索筛选
```

---

## 零号病人定位

### 1. 前端绕过统一 API

**第一行错误**：`AccountFormDialog.vue` 直接调 `httpClient.get('/api/admin/users')` 而非用 `usersApi.search()` / `usersApi.getAssignableCandidates()`。

```javascript
// AccountFormDialog.vue（错误）
import httpClient from '@/api/client'
const loadBiddingUsers = async () => {
  try {
    const res = await httpClient.get('/api/admin/users')  // ← 绕过统一 API
    // ...
  } catch { /* silent */ }  // ← 吞掉 403
}
```

### 2. 后端权限收紧

**第一行错误**：`AdminUserController` 用 `@PreAuthorize("hasRole('ADMIN')")` 限制 ADMIN 角色。

```java
// AdminUserController.java
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")  // ← 投标组长/专员 403
public class AdminUserController { ... }
```

**对比统一接口权限**：

| 接口 | 控制器 | 权限 |
|---|---|---|
| `/api/admin/users` | AdminUserController | `hasRole('ADMIN')` ❌ |
| `/api/users/search` | UserSearchController | `isAuthenticated()` ✅ |
| `/api/users/assignable-candidates` | AssignmentCandidateController | `isAuthenticated()` ✅ |

---

## 验证与修复

### 修复 diff 摘要

1. **`AccountFormDialog.vue`**：UserPicker `mode="candidates"` → `mode="search"`，删除 `/api/admin/users` 直调 + `biddingUsers` 预加载逻辑，新增 `contactPersonInitialOptions` computed 用于编辑态回显。
2. **`UserSearchResult.java`**：record 新增 `phone`, `email` 字段（供统一控件选中后联动回填）。
3. **`UserSearchService.java`**：`search()` 和 `findByIds()` 填充 `u.getPhone()` / `u.getEmail()`。
4. **测试更新**：`UserSearchServiceTest` + `UserSearchControllerTest` + `AccountFormDialog.spec.js`。

### 关键修复代码

```vue
<!-- AccountFormDialog.vue（修复后） -->
<UserPicker
  v-model="form.contactPerson"
  mode="search"
  placeholder="模糊搜索选择联系人"
  :initial-options="contactPersonInitialOptions"
  style="width: 100%"
  @select="onContactPersonSelected"
/>
```

```java
// UserSearchResult.java（修复后）
public record UserSearchResult(Long id, String name, String employeeNumber,
                               String role, String departmentName, String roleCode,
                               String phone, String email) {  // ← 新增 phone/email
}
```

### 测试验证

- 后端: `UserSearchServiceTest` + `UserSearchControllerTest` 全绿
- 前端: `AccountFormDialog.spec.js` 6/6 全绿
- build + line-budgets 通过
- Pre-push gate: 14 通过, 0 失败

### 思维链 Review 验证（4 层链路）

phone/email 联动回填链路（关键验证点）：

| 层级 | 验证点 | 状态 |
|------|--------|------|
| 后端 | `UserSearchService` → `UserSearchResult(phone, email)` | ✅ |
| API 层 | `usersApi.search` → `normalizeUserOption` 用 `...user` 展开保留所有字段 | ✅ |
| 组件层 | UserPicker `@select` 回传 `mergedOptions.value.find(...)` 原始 user 对象 | ✅ |
| 业务层 | AccountFormDialog `onContactPersonSelected(user)` 取 `user.phone` / `user.email` 联动回填 | ✅ |

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| Bug 1 根因已定位 | AccountFormDialog 用 mode=candidates 偏离统一标准 | ✅ |
| Bug 2 根因已定位 | /api/admin/users @PreAuthorize(hasRole('ADMIN')) 403 被吞 | ✅ |
| 必然性已证明 | 投标组长/专员调用 /api/admin/users 必然 403，catch 必然吞错 | ✅ |
| 修复 diff 已提供 | `79f3fd6c4` / PR `#1333` | ✅ |
| 防复发测试已设计 | 6 个前端测试 + 后端字段数断言更新 | ✅ |
| 4 层联动链路已验证 | 后端 → API → 组件 → 业务全链路对齐 | ✅ |

**Verdict**: ✅ **PASS**

---

## 为什么之前没有提前发现

1. **CO-390 主提交视角集中在字段类型升级**：contactPerson String→Long + custodian/caCustodian 删除，没有审视选人控件是否用统一标准。
2. **`/api/admin/users` 是历史遗留接口**：早期账户管理只给管理员用，CO-390 主提交复用了这个接口，没有考虑到投标组长/专员也需要新增账户。
3. **`catch { /* silent */ }` 吞错模式隐藏了权限问题**：前端吞掉 403，`biddingUsers` 静默为空，用户看到的是"无法搜索"而非"权限不足"，误导排查方向。
4. **测试账号单一**：测试环境主要用管理员账号测试新增账户功能，没有覆盖投标组长/专员角色。

---

## 防复发规范

1. **新增 UserPicker 场景必须用统一控件标准**：`mode="search"` + 统一接口 `/api/users/search`，禁止绕过统一 API 直调 `/api/admin/users`。详见 `docs/lessons/vue-gotchas.md` §3。

2. **前端禁止 `catch { /* silent */ }` 吞掉 API 错误**：至少记录日志或弹出错误提示。详见 `docs/lessons/lessons-learned.md` §25。

3. **后端接口权限分级设计**：
   - 管理员专属接口：`@PreAuthorize("hasRole('ADMIN')")`，路径含 `/admin/`
   - 通用业务接口：`@PreAuthorize("isAuthenticated()")`，路径不含 `/admin/`
   - 前端调用方必须根据场景选择对应接口，不能为图方便直调管理员接口。

4. **联动回填链路必须 4 层全链路验证**：后端 DTO 字段 → API normalize → @select 回传对象 → 业务联动。详见 `docs/lessons/lessons-learned.md` §26。

5. **测试账号必须覆盖非管理员场景**：新增/编辑功能测试不能只用 admin 账号，必须覆盖投标组长/专员等角色。

---

## 相关文档与代码

- `src/views/Resource/AccountFormDialog.vue` — UserPicker mode=search + contactPersonInitialOptions
- `src/components/common/UserPicker.vue` — 统一选人控件
- `src/composables/useUserPicker.js` — search/loadCandidates composable
- `src/utils/formatUserLabel.js` — "姓名（工号）"格式化
- `src/api/modules/users.js` — usersApi.search / getAssignableCandidates
- `src/api/modules/userNormalizers.js` — normalizeUserOption `...user` 展开保留字段
- `backend/src/main/java/com/xiyu/bid/mention/dto/UserSearchResult.java` — record 新增 phone/email
- `backend/src/main/java/com/xiyu/bid/mention/service/UserSearchService.java` — 填充 phone/email
- `backend/src/main/java/com/xiyu/bid/mention/controller/UserSearchController.java` — `@PreAuthorize("isAuthenticated()")`
- `backend/src/main/java/com/xiyu/bid/controller/AdminUserController.java` — `@PreAuthorize("hasRole('ADMIN')")`（对比）
- `docs/lessons/lessons-learned.md` §25 — catch silent 吞错教训
- `docs/lessons/lessons-learned.md` §26 — 联动回填链路验证 SOP
- `docs/lessons/vue-gotchas.md` §3 — UserPicker 统一控件规范
