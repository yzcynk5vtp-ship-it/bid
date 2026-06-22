# 泊冉系统权限接口差距分析

> 分析时间：2026-06-22
> 分析范围：泊冉系统获取用户权限接口顺序文档 vs 西域投标平台当前实现
> 详细程度：字段级验证

---

## 一、问题理解与假设

### 1.1 文档要求

泊冉系统定义了 5 个接口的调用顺序：

| 顺序 | 接口名称 | 接口路径 | YApi 地址 | 说明 |
|------|---------|---------|----------|------|
| 1 | 登录鉴权 | `/oauth/login` | https://yapi.ehsy.com/project/406/interface/api/23352 | 获取 token 并缓存 |
| 2 | 获取员工信息 | `/oauth/getUserInfo` | https://yapi.ehsy.com/project/406/interface/api/23358 | 根据 token 获取员工信息 |
| 3 | 获取菜单树 | `/oauth/getUserPermission` | https://yapi.ehsy.com/project/406/interface/api/23484 | 获取用户菜单树 |
| 4 | 获取用户角色 | `/oss/admin-web/v1/output/data/getUserJobListByJobNumberList` | https://yapi.ehsy.com/project/406/interface/api/26325 | 根据工号获取角色 |
| 5 | 登出 | `/oauth/logout` | https://yapi.ehsy.com/project/406/interface/api/23370 | 删除缓存 |

**缓存策略**：菜单权限或用户角色在登录时写入缓存，退出登录删除缓存，重新登录获取最新权限数据。

### 1.2 假设

1. 泊冉系统是西域 OSS 系统的权限模块
2. 这些接口是 OSS 系统对外提供的标准权限接口
3. 西域投标平台需要对接这些接口以获取用户权限信息
4. 泊冉文档描述的是标准调用顺序，实际可能支持多种调用方式

---

## 二、当前实现分析

### 2.1 接口 1：登录鉴权 `/oauth/login`

**状态**：✅ 已实现

**YApi**：https://yapi.ehsy.com/project/406/interface/api/23352

**实现位置**：

| 文件 | 行号 | 方法 | 说明 |
|------|------|------|------|
| `OssDelegationService.java` | 30-52 | `authenticate()` | 组织架构用户登录委托 |
| `CrmAuthService.java` | 85-118 | `applyOssToken()` | CRM 系统获取 OSS token |

**代码引用**：

```java
// OssDelegationService.java:30-52
public boolean authenticate(User user, String rawPassword) {
    String baseUrl = crmProperties.getEffectiveAuthBaseUrl();
    String path = crmProperties.getAuth().getOauthLoginPath();  // /oauth/login
    // ...
}

// CrmAuthService.java:85-118
private CrmToken applyOssToken() {
    String path = properties.getAuth().getOauthLoginPath();  // /oauth/login
    // ...
}
```

**配置**：

```yaml
# application.yml:96
app.crm.auth.oauth-login-path: ${XIYU_CRM_AUTH_OAUTH_LOGIN_PATH:/oauth/login}
```

**缓存机制**：

| 缓存类 | 位置 | 说明 |
|-------|------|------|
| `CrmTokenCache` | `CrmAuthService.java:32` | OSS token 缓存 |

**差距**：无

---

### 2.2 接口 2：根据 token 获取员工信息 `/oauth/getUserInfo`

**状态**：❌ 路径不一致

**YApi**：https://yapi.ehsy.com/project/406/interface/api/23358

**文档要求**：调用 `/oauth/getUserInfo`

**当前实现**：
- 使用 `/employee/info` 接口

**代码引用**：

```java
// CrmProperties.java:189
private String employeePath = "/employee/info";

// CrmProperties.java:176-190
@Data
public static class CrmAuthPaths {
    private String oauthLoginPath = "/oauth/login";
    private String logoutPath = "/auth/logout";        // ❌ 路径不一致
    private String menuTreePath = "/menu/tree";
    private String employeePath = "/employee/info";      // ❌ 应该是 /oauth/getUserInfo
}
```

**配置**：

```yaml
# application.yml:99
app.crm.auth.employee-path: ${XIYU_CRM_AUTH_EMPLOYEE_PATH:/employee/info}
```

**使用情况**：
- `OssDelegationService` 使用此路径验证用户
- 未在 `CrmAuthService` 中直接使用

**差距分析**：

| 项目 | 文档要求 | 当前实现 | 影响 |
|------|---------|---------|------|
| 接口路径 | `/oauth/getUserInfo` | `/employee/info` | 可能返回不同数据 |
| 鉴权方式 | Bearer token | Bearer token | 一致 |
| 响应格式 | 员工信息 | 员工信息 | 可能不同 |

**需要确认**：
1. OSS 系统是否同时提供两个路径？
2. 两个接口返回的数据格式是否相同？
3. 是否可以替换为 `/oauth/getUserInfo`？

---

### 2.3 接口 3：获取用户菜单树 `/oauth/getUserPermission`

**状态**：❌ 路径不一致

**YApi**：https://yapi.ehsy.com/project/406/interface/api/23484

**文档要求**：调用 `/oauth/getUserPermission`

**当前实现**：
- 使用 `/sysMenuUrl/getUserMenuTree` 接口

**代码引用**：

```java
// OrganizationIntegrationProperties.java:79-80
/** OSS 菜单树接口路径：GET /sysMenuUrl/getUserMenuTree */
private String userMenuTreePath = "/sysMenuUrl/getUserMenuTree";

// OrganizationDirectoryHttpGateway.java:179-197
@Override
public Optional<List<OssMenuTreeNode>> fetchUserMenuTree(
        String jobNumber,
        OrganizationDirectoryLookupContext context
) {
    String url = buildUrl(directory.getUserMenuTreePath());
    // /sysMenuUrl/getUserMenuTree
}
```

**配置**：

```yaml
# application.yml:241
xiyu.integrations.organization.directory.user-menu-tree-path: ${XIYU_ORG_USER_MENU_TREE_PATH:/sysMenuUrl/getUserMenuTree}
```

**使用情况**：
- `OssRoleMenuPermissionAutoSync.mergeUserMenuPermissionsIntoRole()` 调用此接口
- 在组织架构同步时获取菜单权限
- **不在登录流程中调用**

**差距分析**：

| 项目 | 文档要求 | 当前实现 | 影响 |
|------|---------|---------|------|
| 接口路径 | `/oauth/getUserPermission` | `/sysMenuUrl/getUserMenuTree` | 可能返回不同数据 |
| HTTP 方法 | GET | GET | 一致 |
| 鉴权方式 | Bearer token | Bearer token | 一致 |
| 响应格式 | 菜单树 | 菜单树 | 可能不同 |

**关键问题**：泊冉文档要求**登录后立即获取菜单树**，但当前实现是在**组织架构同步时**获取，且缓存在角色级别而非用户级别。

**需要确认**：
1. OSS 系统是否同时提供两个路径？
2. 登录时是否必须调用此接口？
3. 当前实现是否能满足业务需求？

---

### 2.4 接口 4：获取用户角色 `/oss/admin-web/v1/output/data/getUserJobListByJobNumberList`

**状态**：✅ 已实现

**YApi**：https://yapi.ehsy.com/project/406/interface/api/26325

**代码引用**：

```java
// OrganizationIntegrationProperties.java:74
private String batchJobRoleLookupPath = "/oss/admin-web/v1/output/data/getUserJobListByJobNumberList";

// OrganizationDirectoryHttpGateway.java:128-133
@Override
public Map<String, OssUserJobAndRoleDto> getUserJobAndRoleListByJobNumbers(
        List<String> jobNumbers,
        OrganizationDirectoryLookupContext context
) {
    return batchClient.getUserJobAndRoleListByJobNumbers(jobNumbers, context);
}
```

**配置**：

```yaml
# application.yml:233
xiyu.integrations.organization.directory.batch-job-role-lookup-path: ${XIYU_ORG_DIRECTORY_BATCH_JOB_ROLE_LOOKUP_PATH:/oss/admin-web/v1/output/data/getUserJobListByJobNumberList}
```

**差距**：无

---

### 2.5 接口 5：登出接口 `/oauth/logout`

**状态**：⚠️ 路径不一致

**YApi**：https://yapi.ehsy.com/project/406/interface/api/23370

**文档要求**：调用 `/oauth/logout`

**当前实现**：
- 使用 `/auth/logout` 接口

**代码引用**：

```java
// CrmProperties.java:184-185
@Data
public static class CrmAuthPaths {
    private String logoutPath = "/auth/logout";  // ❌ 应该是 /oauth/logout
}

// CrmAuthService.java:61-75
public void logout() {
    CrmToken token = ossTokenCache.get().orElse(null);
    if (token != null) {
        String baseUrl = properties.getEffectiveAuthBaseUrl();
        String path = properties.getAuth().getLogoutPath();  // /auth/logout
        httpClient.post(baseUrl, path, token.accessToken(), null);
    }
    ossTokenCache.clear();
    crmTokenCache.clear();
}
```

**配置**：

```yaml
# application.yml:97
app.crm.auth.logout-path: ${XIYU_CRM_AUTH_LOGOUT_PATH:/auth/logout}
```

**差距分析**：

| 项目 | 文档要求 | 当前实现 | 影响 |
|------|---------|---------|------|
| 接口路径 | `/oauth/logout` | `/auth/logout` | 可能无法清除 OSS 缓存 |
| 清除缓存 | OSS token + 菜单权限 | OSS token + CRM token | 缺少菜单权限清除 |

**关键问题**：当前登出时只清除了 `ossTokenCache` 和 `crmTokenCache`，但没有清除菜单权限缓存。

---

## 三、缓存策略分析

### 3.1 Token 缓存

**状态**：✅ 已实现

**实现**：

```java
// CrmAuthService.java:31-34
/** OSS token 缓存（用于 OSS 组织架构接口） */
private final CrmTokenCache ossTokenCache = new CrmTokenCache();
/** CRM JWT token 缓存（用于商机/客户/消息接口） */
private final CrmTokenCache crmTokenCache = new CrmTokenCache();
```

**缓存配置**：

| 配置项 | 默认值 | 说明 |
|-------|-------|------|
| `tokenRenewBeforeExpiryRatio` | 10 | TTL 剩余 10% 时续期 |
| `tokenCoolDownRetries` | 3 | 连续失败 3 次后冷却 |
| `tokenCoolDownMs` | 60000 | 冷却时间 60 秒 |

### 3.2 菜单权限缓存

**状态**：⚠️ 部分实现

**当前实现**：
- 菜单权限存储在数据库 `RoleProfile.menu_permissions` 字段
- 组织架构同步时通过 `OssRoleMenuPermissionAutoSync` 合并到角色

**问题**：
1. 没有用户级别的菜单权限缓存
2. 没有登录时获取菜单权限的流程
3. 登出时没有清除菜单权限缓存

### 3.3 用户角色缓存

**状态**：❌ 未实现

**问题**：
- 没有用户级别的角色缓存
- 每次登录都从数据库获取角色信息

---

## 四、差距汇总

| 接口 | 文档要求路径 | 当前实现路径 | 状态 | 代码位置 | 配置位置 |
|------|-------------|-------------|------|---------|---------|
| 1. 登录鉴权 | `/oauth/login` | `/oauth/login` | ✅ 已实现 | `CrmAuthService:85` | `application.yml:96` |
| 2. 获取员工信息 | `/oauth/getUserInfo` | `/employee/info` | ❌ 路径不一致 | `CrmProperties:189` | `application.yml:99` |
| 3. 获取菜单树 | `/oauth/getUserPermission` | `/sysMenuUrl/getUserMenuTree` | ❌ 路径不一致 | `OrganizationIntegrationProperties:80` | `application.yml:241` |
| 4. 获取用户角色 | `/oss/.../getUserJobListByJobNumberList` | 同左 | ✅ 已实现 | `OrganizationDirectoryHttpGateway:128` | `application.yml:233` |
| 5. 登出 | `/oauth/logout` | `/auth/logout` | ⚠️ 路径不一致 | `CrmProperties:185` | `application.yml:97` |

---

## 五、需要确认的问题清单

### 5.1 OSS 系统接口确认

| # | 问题 | 重要性 | 回答 | 备注 |
|---|------|--------|------|------|
| Q1 | OSS 系统是否同时提供 `/oauth/getUserInfo` 和 `/employee/info`？ | 🔴 高 | | |
| Q2 | 两个接口返回的数据格式是否相同？ | 🔴 高 | | |
| Q3 | OSS 系统是否同时提供 `/oauth/getUserPermission` 和 `/sysMenuUrl/getUserMenuTree`？ | 🔴 高 | | |
| Q4 | 两个菜单接口返回的数据格式是否相同？ | 🔴 高 | | |
| Q5 | OSS 系统是否同时提供 `/oauth/logout` 和 `/auth/logout`？ | 🟡 中 | | |
| Q6 | 登录流程是否必须按顺序调用接口？ | 🟡 中 | | |
| Q7 | 是否有接口调用频率限制？ | 🟡 中 | | |

### 5.2 缓存策略确认

| # | 问题 | 重要性 | 回答 | 备注 |
|---|------|--------|------|------|
| Q8 | OSS 系统是否要求在登录时缓存权限数据？ | 🔴 高 | | |
| Q9 | 缓存的有效期是多久？ | 🟡 中 | | |
| Q10 | 登出时是否必须调用 `/oauth/logout` 清除缓存？ | 🟡 中 | | |
| Q11 | 是否需要实现用户级别的菜单权限缓存？ | 🟡 中 | | |

---

## 六、详细实施计划

### 阶段 1：配置路径调整（最小改动）

**目标**：将接口路径改为可配置，支持切换到泊冉文档要求的路径

**修改文件清单**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `backend/src/main/resources/application.yml` | 修改 | 新增配置项 |
| `backend/src/main/resources/application-dev.yml` | 修改 | 开发环境配置 |
| `backend/src/main/resources/application-test.yml` | 新增 | 测试环境配置 |
| `backend/src/main/java/com/xiyu/bid/crm/config/CrmProperties.java` | 修改 | 默认值 |
| `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationIntegrationProperties.java` | 修改 | 默认值 |

**配置变更**：

```yaml
# application.yml
app:
  crm:
    auth:
      # 改为泊冉文档要求
      employee-path: ${XIYU_CRM_AUTH_EMPLOYEE_PATH:/oauth/getUserInfo}
      logout-path: ${XIYU_CRM_AUTH_LOGOUT_PATH:/oauth/logout}

xiyu:
  integrations:
    organization:
      directory:
        # 改为泊冉文档要求
        user-menu-tree-path: ${XIYU_ORG_USER_MENU_TREE_PATH:/oauth/getUserPermission}
```

**测试用例**：

```java
// CrmAuthServiceTest.java
@Test
void shouldCallConfiguredLogoutPath() {
    // Given: 配置 logout-path = /oauth/logout
    // When: 用户登出
    crmAuthService.logout();
    // Then: 请求发送到 /oauth/logout
    verify(crmHttpClient).post(eq("/oauth/logout"), any(), any(), any());
}
```

---

### 阶段 2：登录流程增强（可选）

**目标**：在登录流程中按顺序调用 OSS 接口

**前提**：OSS 系统要求在登录时按顺序调用接口

**修改文件清单**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `backend/src/main/java/com/xiyu/bid/crm/application/OssLoginFlowService.java` | 新增 | OSS 登录流程服务 |
| `backend/src/main/java/com/xiyu/bid/crm/dto/OssUserInfo.java` | 新增 | 用户信息 DTO |
| `backend/src/main/java/com/xiyu/bid/crm/dto/OssPermission.java` | 新增 | 权限信息 DTO |
| `backend/src/main/java/com/xiyu/bid/service/AuthService.java` | 修改 | 调用新服务 |
| `backend/src/test/java/com/xiyu/bid/crm/application/OssLoginFlowServiceTest.java` | 新增 | 测试用例 |

---

### 阶段 3：缓存策略完善（可选）

**目标**：实现用户级别的菜单权限缓存

**修改文件清单**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `backend/src/main/java/com/xiyu/bid/crm/cache/OssUserInfoCache.java` | 新增 | 用户信息缓存 |
| `backend/src/main/java/com/xiyu/bid/crm/cache/OssPermissionCache.java` | 新增 | 权限缓存 |
| `backend/src/main/java/com/xiyu/bid/crm/application/CrmAuthService.java` | 修改 | 登出时清除缓存 |
| `backend/src/test/java/com/xiyu/bid/crm/cache/OssPermissionCacheTest.java` | 新增 | 测试用例 |

---

## 七、风险评估

### 7.1 高风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| OSS 接口路径不存在 | 登录功能失效 | 先在测试环境验证 |
| 接口响应格式不同 | 数据解析失败 | 编写适配器处理 |
| 登录流程变慢 | 用户体验下降 | 使用并行调用 |

### 7.2 中风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 缓存策略不符合预期 | 权限不同步 | 配置化缓存策略 |
| 测试覆盖不足 | 回归风险 | 补充测试用例 |

---

## 八、结论

### 8.1 差距总结

| 类别 | 已实现 | 未实现 | 部分实现 |
|------|-------|-------|---------|
| 接口路径 | 2/5 | 2/5 | 1/5 |
| 缓存策略 | 1/3 | 1/3 | 1/3 |

### 8.2 推荐方案

**方案 A：配置优先（推荐先做）**
- 修改配置文件默认值
- 支持环境变量覆盖
- 先验证接口可用性

**方案 B：完整实现（按需）**
- 实现完整的登录流程
- 实现用户级权限缓存
- 需要更多测试验证

### 8.3 下一步行动

1. **立即行动**：
   - [ ] 与 OSS 系统负责人确认接口可用性
   - [ ] 获取接口文档和测试环境

2. **短期行动**：
   - [ ] 修改配置默认值
   - [ ] 在测试环境验证
   - [ ] 补充测试用例

3. **长期行动**：
   - [ ] 根据业务需求决定是否实现完整登录流程
   - [ ] 实现用户级权限缓存（如需要）
   - [ ] 添加监控指标

---

## 九、附录

### A. 相关文档

- [泊冉系统获取用户权限接口顺序（原始文档）](boran-permission-api-sequence.md)
- [泊冉系统权限接口实施计划](boran-permission-api-implementation-plan.md)

### B. 相关代码位置

| 模块 | 路径 |
|------|------|
| CRM 配置 | `com.xiyu.bid.crm.config.CrmProperties` |
| CRM 认证 | `com.xiyu.bid.crm.application.CrmAuthService` |
| OSS 委托 | `com.xiyu.bid.crm.application.OssDelegationService` |
| 组织架构集成 | `com.xiyu.bid.integration.organization` |
| 菜单权限同步 | `com.xiyu.bid.integration.organization.application.OssRoleMenuPermissionAutoSync` |

### C. 配置项清单

| 配置项 | 当前值 | 建议值 | 环境变量 |
|-------|-------|-------|---------|
| `app.crm.auth.employee-path` | `/employee/info` | `/oauth/getUserInfo` | `XIYU_CRM_AUTH_EMPLOYEE_PATH` |
| `app.crm.auth.logout-path` | `/auth/logout` | `/oauth/logout` | `XIYU_CRM_AUTH_LOGOUT_PATH` |
| `xiyu.integrations.organization.directory.user-menu-tree-path` | `/sysMenuUrl/getUserMenuTree` | `/oauth/getUserPermission` | `XIYU_ORG_USER_MENU_TREE_PATH` |
