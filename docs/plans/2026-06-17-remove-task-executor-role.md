# CO-XXX: 删除 task_executor 角色

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 从系统中完全移除 `task_executor`（任务执行人）角色，包括数据库定义、后端权限注解、前端角色映射和测试。

**Architecture:** 删除角色需要数据库迁移 + 后端枚举/注解清理 + 前端映射清理。角色删除后，原 `task_executor` 用户需要被重新分配到其他角色。

**Tech Stack:** Java 21, Spring Boot, Vue 3, Flyway, MySQL 8.0

---

## 改动范围

| 文件 | 改动类型 | 说明 |
|------|----------|------|
| `backend/src/main/resources/db/migration-mysql/V1081__remove_task_executor_role.sql` | 新建 | 数据库迁移：删除角色 |
| `backend/src/main/java/.../tender/controller/TenderController.java` | 修改 | 移除 `TASK_EXECUTOR` 从 `@PreAuthorize` |
| `backend/src/main/java/.../resources/service/MarginQueryRole.java` | 修改 | 移除 `TASK_EXECUTOR` 枚举 |
| `src/composables/projectDetail/useProjectDraftingPermissions.js` | 修改 | 移除 `task_executor` 映射 |
| `src/components/common/LoginDevAccountsHint.vue` | 修改 | 移除 dev 账号提示 |
| `src/views/Project/stages/ResultConfirmStage.vue` | 修改 | 移除 `task_executor` 从 `OPERABLE_ROLES` |
| 测试文件（6+） | 修改 | 移除 `task_executor` 引用 |

---

## Task 1: 数据库迁移

**Files:**
- Create: `backend/src/main/resources/db/migration-mysql/V1081__remove_task_executor_role.sql`

**Step 1: 获取下一个迁移版本号**

Run: `bash scripts/next-migration-version.sh`
Expected: 输出下一个可用版本号（应该是 V1081）

**Step 2: 创建迁移脚本**

```sql
-- CO-XXX: 删除 task_executor（任务执行人）角色
-- 该角色功能已由 bid_specialist 覆盖，不再需要独立角色
DELETE FROM role_menu_permissions WHERE role_code = 'task_executor';
DELETE FROM roles WHERE code = 'task_executor';
```

**Step 3: 创建回滚脚本**

Create: `backend/src/main/resources/db/rollback/migration-mysql/U1081__remove_task_executor_role.sql`

```sql
-- Rollback: 恢复 task_executor 角色
INSERT INTO roles (code, name, description, is_active, is_system, scope, created_at, updated_at)
VALUES ('task_executor', '任务执行人', '标书任务承接与执行', true, true, 'self', NOW(), NOW());
```

**Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration-mysql/V1081__remove_task_executor_role.sql \
        backend/src/main/resources/db/rollback/migration-mysql/U1081__remove_task_executor_role.sql
git commit -m "feat(db): 删除 task_executor 角色迁移脚本"
```

---

## Task 2: 后端 Java 清理

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java:52,156`
- Modify: `backend/src/main/java/com/xiyu/bid/resources/service/MarginQueryRole.java:22`

**Step 1: TenderController.java — 移除 TASK_EXECUTOR**

Line 52: `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'BID_LEAD', 'BID_SENIOR', 'SALES', 'BID_SPECIALIST', 'TASK_EXECUTOR', 'ADMIN_STAFF')")`
→ 移除 `'TASK_EXECUTOR', `

Line 156: `@PreAuthorize("hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SPECIALIST', 'TASK_EXECUTOR', 'ADMIN_STAFF')")`
→ 移除 `'TASK_EXECUTOR', `

**Step 2: MarginQueryRole.java — 移除枚举**

Line 22: `TASK_EXECUTOR(MarginQueryRole::staffFragment),`
→ 删除此行

**Step 3: 验证编译**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java \
        backend/src/main/java/com/xiyu/bid/resources/service/MarginQueryRole.java
git commit -m "fix(tender): 移除 task_executor 角色引用"
```

---

## Task 3: 前端清理

**Files:**
- Modify: `src/composables/projectDetail/useProjectDraftingPermissions.js:29,44`
- Modify: `src/components/common/LoginDevAccountsHint.vue:29`
- Modify: `src/views/Project/stages/ResultConfirmStage.vue:126`

**Step 1: useProjectDraftingPermissions.js**

Line 29: 删除注释 `* - task_executor → 任务执行人（蓝图"任务执行人"）`
Line 44: 删除 `if (role === 'task_executor') return 'executor'  // 任务执行人`

**Step 2: LoginDevAccountsHint.vue**

Line 29: 删除 `'任务执行人: task_executor / Test@123',`

**Step 3: ResultConfirmStage.vue**

Line 126: 从 `OPERABLE_ROLES` 数组中移除 `'task_executor'`

**Step 4: 验证构建**

Run: `npm run build`
Expected: 构建成功

**Step 5: Commit**

```bash
git add src/composables/projectDetail/useProjectDraftingPermissions.js \
        src/components/common/LoginDevAccountsHint.vue \
        src/views/Project/stages/ResultConfirmStage.vue
git commit -m "fix(frontend): 移除 task_executor 角色引用"
```

---

## Task 4: 测试清理

**Files:**
- Modify: `src/composables/projectDetail/useProjectDraftingPermissions.spec.js`
- Modify: `backend/src/test/java/.../TenderEditPermissionPolicyTest.java`
- Modify: `backend/src/test/java/.../TenderTransferPermissionPolicyTest.java`
- Modify: `backend/src/test/java/.../ProjectTaskAuthorizationPolicyTest.java`
- Modify: `backend/src/test/java/.../LocalDevAccountInitializerTest.java`
- Modify: `backend/src/test/java/.../MarginQueryRoleTest.java`
- Modify: `backend/src/test/java/.../ProjectDraftingServiceTest.java`
- Modify: `backend/src/test/java/.../RoleProfileCatalogTenderLifecycleTest.java`

**Step 1: 更新所有测试文件**

移除所有 `task_executor` / `TASK_EXECUTOR` 引用。

**Step 2: 验证测试通过**

Run: `cd backend && mvn test -q && cd .. && npm run test:unit`
Expected: 全部通过

**Step 3: Commit**

```bash
git add -A
git commit -m "fix(test): 移除 task_executor 角色相关测试"
```

---

## 验证

1. `mvn compile` — 后端编译通过
2. `npm run build` — 前端构建通过
3. `mvn test` — 后端测试全绿
4. `npm run test:unit` — 前端测试全绿
5. `npm run check:doc-governance` — 文档治理通过
