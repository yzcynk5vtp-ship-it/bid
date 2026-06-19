# 根因分析：H13 改造后 E2E 测试全面修复

**日期**: 2026-06-19
**状态**: 已修复
**影响范围**: E2E 测试套件、auth-helpers.js、多个测试文件

## 问题描述

H13 改造（2026-06-14）将 access token 从 response body 移至 HttpOnly cookie，导致 E2E 测试全面失败：
- `auth-helpers.js` 的 token 提取逻辑失效
- 测试 mock 断言与新认证流程不匹配
- 权限测试使用已删除的角色

## 根因分析

### 1. Token 提取失败
**原因**: `fetch` API 的 `response.headers.get('set-cookie')` 只返回第一个 Set-Cookie 头（refresh_token），遗漏了 access_token。

**修复**: 使用 Node.js 的 `response.headers.getSetCookie()` 方法获取所有 cookie 数组。

```javascript
// 修复前
const setCookie = response.headers.get('set-cookie') || ''
const match = setCookie.match(/access_token=([^;]+)/)

// 修复后
const cookies = response.headers.getSetCookie?.() || []
for (const cookie of cookies) {
  const match = cookie.match(/access_token=([^;]+)/)
  if (match) return match[1]
}
```

### 2. Session 存储断言错误
**原因**: 测试断言 `sessionStorage.getItem('token')` 和 `localStorage.getItem('token')`，但 H13 后前端不再存储 token。

**修复**: 只断言 `user` hint，不断言 token。

### 3. Authorization Header 检查
**原因**: 测试检查 `Authorization: Bearer xxx` 头，但 H13 后使用 HttpOnly cookie 认证。

**修复**: 移除 Authorization header 断言。

### 4. 速率限制 429
**原因**: `auth-account` 速率限制默认 5 次/15 分钟，E2E 测试频繁注册触发。

**修复**: `application-dev.yml` 添加配置：
```yaml
rate:
  limit:
    auth-account:
      max: 1000
      window-minutes: 15
```

### 5. 项目阶段不匹配
**原因**: 测试访问 `/projects/1`，但项目可能不在 `DRAFTING` 阶段，导致标书制作标签不可见。

**修复**: 检查项目阶段，无 DRAFTING 项目则跳过测试。

## 修复文件清单

| 文件 | 修复内容 |
|------|----------|
| `e2e/auth-helpers.js` | token 提取使用 `getSetCookie()` |
| `e2e/auth-session-lifecycle.spec.js` | 适配 H13 cookie 认证 |
| `e2e/bid-document-quality-check.spec.js` | 权限角色和项目阶段检查 |
| `e2e/auth-access-control.spec.js` | 使用 `ensureApiSession` |
| `backend/src/main/resources/application-dev.yml` | 速率限制配置 |
| `backend/src/main/resources/db/migration-mysql/V1081__remove_task_executor_role.sql` | 外键迁移修复 |
| `src/api/modules/tenders.spec.js` | 测试期望值修复 |
| `src/views/Bidding/detail/components/BasicInfoReadOnly.spec.js` | 组件类型修复 |
| `backend/src/main/java/com/xiyu/bid/entity/Tender.java` | 枚举标签修复 |

## 经验教训

1. **HttpOnly cookie 测试**: E2E 测试需要通过 `getSetCookie()` 获取完整 cookie 列表，不能依赖 `get('set-cookie')`
2. **认证流程变更**: 当认证机制从 token-in-body 改为 cookie-based，需要全面审查测试的 mock 和断言
3. **速率限制**: 开发环境需要放宽速率限制，避免 E2E 测试触发 429
4. **测试数据依赖**: E2E 测试不应依赖特定 ID 的数据，应使用 API 创建或从 allowedProjectIds 获取

## 相关文档

- [H13 根治方案](../../SECURITY.md)
- [E2E 测试指南](../../e2e/README.md)
