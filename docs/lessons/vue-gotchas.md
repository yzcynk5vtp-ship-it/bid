# Vue 陷阱与调试经验

记录开发过程中遇到的 Vue 框架陷阱、调试方法论和设计教训。

---

## 1. Composable 中 ref 初始化与 props 同步陷阱

### 问题

```javascript
// ❌ 错误：ref() 只在初始化时求值一次，不会追踪 props 变化
export function useMyComposable(props) {
  const localState = ref(props.someValue)  // 初始化后不再更新
  return { localState }
}

// ✅ 正确：使用 watch 同步 props 变化
export function useMyComposable(props) {
  const localState = ref(props.someValue)
  watch(() => props.someValue, (newVal) => {
    localState.value = newVal
  })
  return { localState }
}
```

`ref()` 接收的是初始值，不是响应式引用。当父组件更新 props 时，composable 中的 ref 不会自动同步。

### 正确写法

```javascript
// 方案1：使用 watch 同步（适合需要本地修改的场景）
export function useMyComposable(props) {
  const localState = ref(props.someValue)
  
  watch(() => props.someValue, (newVal) => {
    if (newVal !== localState.value) {
      localState.value = newVal
    }
  })
  
  return { localState }
}

// 方案2：使用 computed（适合只读场景）
export function useMyComposable(props) {
  const localState = computed(() => props.someValue)
  return { localState }
}

// 方案3：使用 toRef（适合需要双向绑定的场景）
export function useMyComposable(props) {
  const localState = toRef(props, 'someValue')
  return { localState }
}
```

### 调试方法

如果怀疑 props 同步问题，在 composable 中加日志：

```javascript
export function useMyComposable(props) {
  const localState = ref(props.someValue)
  
  watch(() => props.someValue, (newVal, oldVal) => {
    console.log('[useMyComposable] props.someValue changed:', oldVal, '->', newVal)
  })
  
  watch(localState, (newVal, oldVal) => {
    console.log('[useMyComposable] localState changed:', oldVal, '->', newVal)
  })
  
  return { localState }
}
```

或者用 Vue DevTools 检查：
1. 打开 Vue DevTools
2. 找到使用 composable 的组件
3. 检查 props 和 computed/ref 的值是否一致

---

## 2. 身份 UI 不要用业务角色做 fallback

### 问题

CO-282 中，系统顶部栏显示「游客」，但后端 `/api/auth/me` 返回的是已登录用户「系统管理员」。根因不是后端存在游客用户，而是 Header 组件在 session 恢复中或 `currentUser` 为空时使用了业务身份文案兜底：

```javascript
// ❌ 错误：把业务角色当 fallback，会制造不存在的身份
const roleTextMap = {
  admin: '管理员',
  manager: '经理',
  sales: '项目负责人',
  staff: '员工',
  guest: '游客',
}

const userName = computed(() => userStore.currentUser?.name || '游客')
const userRoleText = computed(() => userStore.currentUser?.roleName || roleTextMap[userStore.userRole] || '游客')
```

这会造成两个问题：

1. 用户以为系统真的存在「游客」账号或权限状态错误。
2. 旧 bundle/cache 残留时，即使后端已修好，页面仍可能继续显示旧 fallback，干扰排查。

### 正确写法

```javascript
// ✅ 恢复中显示状态，无用户显示通用占位，不伪造业务身份
const roleTextMap = {
  admin: '管理员',
  manager: '经理',
  sales: '项目负责人',
  staff: '员工',
}

const userName = computed(() => {
  if (userStore.isRestoringSession) return '加载中'
  return userStore.userName || '用户'
})

const userRoleText = computed(() => {
  if (userStore.isRestoringSession) return '加载中'
  if (!userStore.currentUser) return '用户'
  return userStore.currentUser?.roleName || roleTextMap[userStore.userRole] || '用户'
})
```

### 配套防护

浏览器 storage 中旧 `user` hint 也要做结构校验，不能因为存在任意 JSON 就认为用户有效：

```javascript
const hasUserIdentityHint = (user) => {
  if (!user || typeof user !== 'object' || Array.isArray(user)) return false
  return user.id != null || hasText(user.name) || hasText(user.fullName) || hasText(user.username) || hasText(user.employeeNumber)
}
```

### 验证方法

```bash
pnpm vitest run src/components/layout/Header.spec.js
rg "游客|guest" src
```

预期：

```text
Header.spec.js passed
No matches found
```

### 相关文档

- `docs/lessons/root-cause-analysis-co-282.md` — CO-282 完整根因分析
- `src/components/layout/Header.vue` — 顶部栏身份展示
- `src/api/session.js` — storage user hint 结构校验

---

## 3. UserPicker 统一控件规范：mode=search + 统一接口（CO-390 root cause）

### 问题

CO-390 中，`AccountFormDialog.vue` 实现绑定联系人选择时，用了 `mode="candidates"` + 外部 `/api/admin/users` 预加载候选列表，偏离了项目统一标准。导致两个 Bug：

1. 绑定联系人未展示"姓名（工号）"格式（因为 `mode="candidates"` + `:load-on-mount="false"` 模式下 UserPicker 不调统一接口，只显示外部传入的 biddingUsers）
2. 投标组长/专员无法搜索人员（因为 `/api/admin/users` 控制器 `@PreAuthorize("hasRole('ADMIN')")` 返回 403，前端 `catch { /* silent */ }` 吞掉错误，biddingUsers 静默为空）

### 统一控件规范

项目所有"选人"场景必须用 `UserPicker` 组件 + `mode="search"` + 统一接口 `/api/users/search`，禁止绕过统一 API 直调 `/api/admin/users`。

**标准用法**：

```vue
<template>
  <UserPicker
    v-model="form.userId"
    mode="search"
    placeholder="模糊搜索选择人员"
    :initial-options="initialOptions"
    style="width: 100%"
    @select="onUserSelected"
  />
</template>

<script setup>
import UserPicker from '@/components/common/UserPicker.vue'

// 编辑态回显：从已保存的 label 构造 initialOptions
const initialOptions = computed(() => {
  if (!props.editRow?.userId) return []
  return [{ id: props.editRow.userId, name: props.editRow.userLabel }]
})

// 选中后联动回填其他字段（如 phone/email）
const onUserSelected = (user) => {
  form.phone = user.phone || ''
  form.email = user.email || ''
}
</script>
```

**全仓统一用法对照表**：

| 场景 | 文件 | mode | 数据来源 |
|---|---|---|---|
| 任务执行人 | TaskForm.vue | `search` | 统一接口 `/api/users/search` |
| 标书审核人 | DraftingStage.vue | `search` | 统一接口 `/api/users/search` |
| 评审人 | ProjectDetailReviewerDialog.vue | `search` | 统一接口 `/api/users/search` |
| CA 保管员 | CAFormDialog.vue | `search` | 统一接口 `/api/users/search` |
| 绑定联系人（CO-390 修复后） | AccountFormDialog.vue | `search` | 统一接口 `/api/users/search` |

### 关键设计点

1. **`mode="search"` 远程搜索**：用户输入时调 `usersApi.search(query, 10)` 模糊搜索，避免一次性加载全部用户。

2. **`formatUserLabel` 自动拼"姓名（工号）"**：[formatUserLabel.js](file:///Users/user/xiyu/worktrees/mimo/src/utils/formatUserLabel.js) 取 `user.fullName || user.name` + `user.employeeNumber` 拼成"姓名（工号）"。后端 `UserSearchResult.name = u.getFullName()`，`employeeNumber` 来自 `employeeNumberOrUsername(u)`。

3. **编辑态回显用 `initial-options`**：从 `editRow.userLabel`（后端派生的"姓名（工号）"标签）构造 `[{id, name: userLabel}]`，让 UserPicker 在未搜索时也能正确展示已选人员的标签。

4. **`@select` 回传完整对象**：UserPicker `@select` 事件回传 `mergedOptions.value.find(...)` 原始 user 对象（含 phone/email 等所有字段），业务函数可直接取字段联动回填。

5. **`normalizeUserOption` 用 `...user` 展开保留所有字段**：[userNormalizers.js](file:///Users/user/xiyu/worktrees/mimo/src/api/modules/userNormalizers.js) 用 `...user` 展开保留所有字段，后端新增字段时前端 API 层自动透传，无需修改 normalize 函数。

### 禁止用法

```javascript
// ❌ 错误 1：绕过统一 API 直调 /api/admin/users
import httpClient from '@/api/client'
const loadUsers = async () => {
  try {
    const res = await httpClient.get('/api/admin/users')  // ← 绕过统一 API
  } catch { /* silent */ }  // ← 吞掉 403
}

// ❌ 错误 2：用 mode="candidates" + 预加载全部用户
<UserPicker
  v-model="form.userId"
  mode="candidates"  // ← 偏离统一标准
  :load-on-mount="false"
  :initial-options="allUsers"  // ← 预加载全部用户，非远程搜索
/>

// ❌ 错误 3：用 mode="candidates" + load-on-mount=true 一次性加载全部用户
<UserPicker
  v-model="form.userId"
  mode="candidates"  // ← 偏离统一标准
  :load-on-mount="true"  // ← 一次性加载全部用户，性能差
/>
```

### 验证命令

```bash
# 检查是否有 UserPicker 用 mode="candidates"（应全部为 mode="search"）
grep -rn 'mode="candidates"' src/views src/components
# 期望输出：无（或仅有 UserPicker.vue 自身的 prop 定义）

# 检查是否有直调 /api/admin/users 的代码（应改用 usersApi.search）
grep -rn "/api/admin/users" src/views src/components
# 期望输出：无

# 检查统一接口用法
grep -rn 'usersApi.search\|getAssignableCandidates' src/views src/components
# 期望输出：所有选人场景都用统一 API
```

### 相关文档

- `docs/lessons/root-cause-analysis-co-390-unified-picker.md` — 完整根因分析
- `src/components/common/UserPicker.vue` — 统一选人控件实现
- `src/composables/useUserPicker.js` — search/loadCandidates composable
- `src/utils/formatUserLabel.js` — "姓名（工号）"格式化
- `src/api/modules/users.js` — usersApi.search / getAssignableCandidates
- `src/api/modules/userNormalizers.js` — normalizeUserOption `...user` 展开最佳实践
- `docs/lessons/lessons-learned.md` §26 — 联动回填链路 4 层全链路验证 SOP
