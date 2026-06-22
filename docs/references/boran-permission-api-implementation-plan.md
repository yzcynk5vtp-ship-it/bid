# 泊冉系统权限接口实施计划

> 版本：v1.0
> 日期：2026-06-22
> 状态：初稿

---

## 一、现状分析

### 1.1 泊冉系统接口要求

泊冉系统定义了 5 个权限接口的标准调用顺序：

| 顺序 | 接口名称 | 接口路径 | 说明 |
|------|---------|---------|------|
| 1 | 登录鉴权 | `/oauth/login` | 获取 token 并缓存 |
| 2 | 获取员工信息 | `/oauth/getUserInfo` | 根据 token 获取员工信息 |
| 3 | 获取菜单树 | `/oauth/getUserPermission` | 获取用户菜单树 |
| 4 | 获取用户角色 | `/oss/admin-web/v1/output/data/getUserJobListByJobNumberList` | 根据工号获取用户角色 |
| 5 | 登出 | `/oauth/logout` | 删除缓存 |

**缓存策略**：菜单权限或用户角色在登录时写入缓存，退出登录删除缓存，重新登录获取最新权限数据。

### 1.2 当前系统实现

#### 1.2.1 后端实现

| 接口 | 文档要求 | 当前实现 | 状态 | 配置位置 |
|------|---------|---------|------|---------|
| 登录鉴权 | `/oauth/login` | `/oauth/login` | ✅ 已实现 | `CrmProperties.oauthLoginPath` |
| 获取员工信息 | `/oauth/getUserInfo` | `/employee/info` | ❌ 未实现 | `CrmProperties.employeePath` |
| 获取菜单树 | `/oauth/getUserPermission` | `/sysMenuUrl/getUserMenuTree` | ❌ 未实现 | `OrganizationIntegrationProperties.userMenuTreePath` |
| 获取用户角色 | `/oss/.../getUserJobListByJobNumberList` | 同左 | ✅ 已实现 | `OrganizationIntegrationProperties.batchJobRoleLookupPath` |
| 登出 | `/oauth/logout` | `/auth/logout` | ⚠️ 路径不一致 | `CrmProperties.logoutPath` |

#### 1.2.2 当前登录流程

```
前端                           后端                              OSS/CRM
  │                              │                                 │
  ├─ POST /api/auth/login ───────►│                                 │
  │                              ├─ POST /oauth/login ────────────►│
  │                              │◄─ { access_token, ... } ────────┤
  │                              │                                 │
  │                              ├─ 查询本地数据库获取用户信息        │
  │                              ├─ 查询本地角色权限                  │
  │◄─ { user, token, ... } ─────┤                                 │
  │                              │                                 │
```

#### 1.2.3 当前菜单权限获取

当前菜单权限存储在本地数据库，通过 `DataScopeConfigService.getRoleMenuPermissions()` 获取，不是实时从 OSS 系统获取。

**组织架构同步时的菜单获取**：
- 使用 `OssRoleMenuPermissionAutoSync.mergeUserMenuPermissionsIntoRole()`
- 调用 `gateway.fetchUserMenuTree(jobNumber)` 获取菜单树
- 路径：`/sysMenuUrl/getUserMenuTree`

### 1.3 差距分析

#### 差距 1：获取员工信息接口路径不一致

- **文档要求**：`/oauth/getUserInfo`
- **当前实现**：`/employee/info`
- **影响**：如果 OSS 系统只提供 `/oauth/getUserInfo`，则无法正确获取员工信息

#### 差距 2：获取菜单树接口路径不一致

- **文档要求**：`/oauth/getUserPermission`
- **当前实现**：`/sysMenuUrl/getUserMenuTree`
- **影响**：如果 OSS 系统只提供 `/oauth/getUserPermission`，则无法正确获取菜单权限

#### 差距 3：登出接口路径不一致

- **文档要求**：`/oauth/logout`
- **当前实现**：`/auth/logout`
- **影响**：登出时可能无法正确清除 OSS 系统的缓存

#### 差距 4：登录流程中未按顺序调用接口

- **文档要求**：登录后按顺序调用 getUserInfo → getUserPermission → getUserJobList
- **当前实现**：只在组织架构同步时获取菜单树，登录时直接从本地数据库获取用户信息和权限

---

## 二、需要确认的问题

### 2.1 OSS 系统接口确认

| 问题 | 重要性 | 回答 |
|------|--------|------|
| OSS 系统是否同时提供 `/oauth/getUserInfo` 和 `/employee/info`？ | 🔴 高 | 待确认 |
| OSS 系统是否同时提供 `/oauth/getUserPermission` 和 `/sysMenuUrl/getUserMenuTree`？ | 🔴 高 | 待确认 |
| OSS 系统是否同时提供 `/oauth/logout` 和 `/auth/logout`？ | 🟡 中 | 待确认 |
| 这些接口的请求/响应格式是否相同？ | 🔴 高 | 待确认 |
| 接口调用是否有顺序依赖？ | 🟡 中 | 待确认 |

### 2.2 缓存策略确认

| 问题 | 重要性 | 回答 |
|------|--------|------|
| OSS 系统是否要求在登录时缓存权限数据？ | 🔴 高 | 待确认 |
| 缓存的有效期是多久？ | 🟡 中 | 待确认 |
| 登出时是否必须调用 `/oauth/logout` 清除缓存？ | 🟡 中 | 待确认 |

---

## 三、实施计划

### 阶段 1：接口路径配置化（最小改动）

**目标**：将接口路径改为可配置，支持切换到泊冉文档要求的路径

**修改文件**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `application.yml` | 修改 | 新增/修改接口路径配置 |
| `application-dev.yml` | 修改 | 开发环境配置 |
| `CrmProperties.java` | 修改 | 新增配置属性 |
| `OrganizationIntegrationProperties.java` | 修改 | 新增配置属性 |
| `CrmAuthService.java` | 修改 | 调用配置化路径 |
| `OssDelegationService.java` | 修改 | 调用配置化路径 |
| `OrganizationDirectoryHttpGateway.java` | 修改 | 调用配置化路径 |

**配置变更**：

```yaml
# application.yml
app:
  crm:
    auth:
      # 员工信息接口路径
      employee-path: ${XIYU_CRM_AUTH_EMPLOYEE_PATH:/oauth/getUserInfo}
      # 登出接口路径
      logout-path: ${XIYU_CRM_AUTH_LOGOUT_PATH:/oauth/logout}

xiyu:
  integrations:
    organization:
      directory:
        # 菜单树接口路径
        user-menu-tree-path: ${XIYU_ORG_USER_MENU_TREE_PATH:/oauth/getUserPermission}
```

**测试用例**：

```java
@Test
void shouldUseConfiguredEmployeePath() {
    // Given: 配置 employee-path = /oauth/getUserInfo
    // When: 调用获取员工信息接口
    // Then: 请求发送到 /oauth/getUserInfo
    verify(crmHttpClient).post(eq("/oauth/getUserInfo"), any(), any());
}

@Test
void shouldUseConfiguredLogoutPath() {
    // Given: 配置 logout-path = /oauth/logout
    // When: 用户登出
    // Then: 请求发送到 /oauth/logout
    verify(crmHttpClient).post(eq("/oauth/logout"), any(), any());
}

@Test
void shouldUseConfiguredMenuTreePath() {
    // Given: 配置 user-menu-tree-path = /oauth/getUserPermission
    // When: 获取用户菜单树
    // Then: 请求发送到 /oauth/getUserPermission
    verify(restClient).get(eq("/oauth/getUserPermission"), any(), any());
}
```

---

### 阶段 2：登录流程增强（可选）

**目标**：在登录流程中按顺序调用 OSS 接口

**前提**：OSS 系统要求在登录时按顺序调用接口

**修改文件**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `AuthService.java` | 修改 | 登录时调用 OSS 接口 |
| `OssLoginService.java` | 新增 | OSS 登录服务 |
| `OssUserInfo.java` | 新增 | OSS 用户信息 DTO |
| `OssPermission.java` | 新增 | OSS 权限信息 DTO |

**新增服务**：

```java
/**
 * OSS 登录服务。
 * 按泊冉文档要求的顺序调用 OSS 接口。
 */
@Service
public class OssLoginService {

    private final CrmHttpClient crmHttpClient;
    private final CrmProperties crmProperties;
    private final OrganizationDirectoryGateway directoryGateway;

    /**
     * 执行 OSS 登录流程。
     *
     * 顺序：
     * 1. /oauth/login - 获取 token
     * 2. /oauth/getUserInfo - 获取员工信息
     * 3. /oauth/getUserPermission - 获取菜单树
     * 4. /oss/.../getUserJobListByJobNumberList - 获取用户角色
     */
    public OssLoginResult login(String username, String password) {
        // 1. 登录获取 token
        String token = loginAndGetToken(username, password);

        // 2. 获取员工信息
        OssUserInfo userInfo = getUserInfo(token);

        // 3. 获取菜单权限
        List<String> menuPermissions = getMenuPermissions(token, userInfo.getJobNumber());

        // 4. 获取用户角色
        List<String> roles = getUserRoles(userInfo.getJobNumber());

        return new OssLoginResult(token, userInfo, menuPermissions, roles);
    }

    private String loginAndGetToken(String username, String password) {
        // 调用 /oauth/login
    }

    private OssUserInfo getUserInfo(String token) {
        // 调用 /oauth/getUserInfo
    }

    private List<String> getMenuPermissions(String token, String jobNumber) {
        // 调用 /oauth/getUserPermission
    }

    private List<String> getUserRoles(String jobNumber) {
        // 调用 /oss/.../getUserJobListByJobNumberList
    }
}
```

---

### 阶段 3：缓存策略实现

**目标**：实现泊冉文档要求的缓存策略

**修改文件**：

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `OssTokenCache.java` | 修改 | OSS token 缓存 |
| `OssUserInfoCache.java` | 新增 | OSS 用户信息缓存 |
| `OssPermissionCache.java` | 新增 | OSS 权限缓存 |
| `CrmAuthService.java` | 修改 | 登出时清除缓存 |

**缓存设计**：

```java
/**
 * OSS 用户信息缓存。
 */
@Component
public class OssUserInfoCache {

    private final Cache<String, OssUserInfo> cache;

    public OssUserInfoCache() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(30))
                .maximumSize(10_000)
                .build();
    }

    public void put(String token, OssUserInfo userInfo) {
        cache.put(token, userInfo);
    }

    public Optional<OssUserInfo> get(String token) {
        return Optional.ofNullable(cache.getIfPresent(token));
    }

    public void invalidate(String token) {
        cache.invalidate(token);
    }

    public void clear() {
        cache.clear();
    }
}

/**
 * OSS 权限缓存。
 */
@Component
public class OssPermissionCache {

    private final Cache<String, OssPermission> cache;

    public OssPermissionCache() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(30))
                .maximumSize(10_000)
                .build();
    }

    public void put(String token, OssPermission permission) {
        cache.put(token, permission);
    }

    public Optional<OssPermission> get(String token) {
        return Optional.ofNullable(cache.getIfPresent(token));
    }

    public void invalidate(String token) {
        cache.invalidate(token);
    }

    public void clear() {
        cache.clear();
    }
}
```

**登出时清除缓存**：

```java
@Service
public class CrmAuthService {

    private final OssUserInfoCache userInfoCache;
    private final OssPermissionCache permissionCache;

    public void logout() {
        // 调用 /oauth/logout
        // ...

        // 清除缓存
        userInfoCache.clear();
        permissionCache.clear();
        ossTokenCache.clear();
        crmTokenCache.clear();
    }
}
```

---

## 四、实施步骤

### Step 1：接口路径配置化（推荐先做）

1. 修改 `CrmProperties.java`：
   - 新增 `employee-path` 配置属性，默认值改为 `/oauth/getUserInfo`
   - 新增 `logout-path` 配置属性，默认值改为 `/oauth/logout`

2. 修改 `OrganizationIntegrationProperties.java`：
   - 新增 `user-menu-tree-path` 配置属性，默认值改为 `/oauth/getUserPermission`

3. 修改相关服务类：
   - `CrmAuthService.java`：使用配置化的 `logoutPath`
   - `OssDelegationService.java`：使用配置化的 `employeePath`
   - `OrganizationDirectoryHttpGateway.java`：使用配置化的 `userMenuTreePath`

4. 更新配置文件：
   - `application.yml`
   - `application-dev.yml`
   - `application-test.yml`

5. 编写测试用例

6. 运行测试验证

### Step 2：OSS 接口验证

1. 在测试环境配置 OSS 接口路径
2. 测试登录流程
3. 验证接口调用日志
4. 确认返回数据格式

### Step 3：缓存策略实现（可选）

1. 新增缓存类
2. 修改登录服务
3. 修改登出服务
4. 测试缓存和清除

---

## 五、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| OSS 接口路径不存在 | 高 | 先在测试环境验证，确认后再生产部署 |
| 接口响应格式不同 | 中 | 编写适配器处理不同响应格式 |
| 登录流程变慢 | 中 | 使用并行调用减少延迟 |
| 缓存失效 | 中 | 设置合理的缓存过期时间 |

---

## 六、测试计划

### 6.1 单元测试

```java
@Test
void shouldCallCorrectEndpointsInOrder() {
    // Given
    when(crmHttpClient.postForm(any(), eq("/oauth/login"), any()))
        .thenReturn(successResponse());

    // When
    ossLoginService.login("user", "pass");

    // Then - 验证按顺序调用
    InOrder inOrder = inOrder(crmHttpClient);
    inOrder.verify(crmHttpClient).postForm(any(), eq("/oauth/login"), any());
    inOrder.verify(crmHttpClient).post(any(), eq("/oauth/getUserInfo"), eq("token"), any());
    inOrder.verify(crmHttpClient).post(any(), eq("/oauth/getUserPermission"), eq("token"), any());
    inOrder.verify(crmHttpClient).post(any(), eq("/oss/.../getUserJobListByJobNumberList"), any(), any());
}

@Test
void shouldClearCachesOnLogout() {
    // Given
    ossLoginService.login("user", "pass");

    // When
    crmAuthService.logout();

    // Then
    verify(userInfoCache).clear();
    verify(permissionCache).clear();
    verify(ossTokenCache).clear();
}

@Test
void shouldUseConfiguredEndpoints() {
    // Given: 配置 /oauth/getUserInfo
    // When: 调用获取员工信息
    // Then: 请求发送到 /oauth/getUserInfo
    verify(crmHttpClient).post(any(), eq("/oauth/getUserInfo"), any(), any());
}
```

### 6.2 集成测试

1. 在测试环境运行完整登录流程
2. 验证 OSS 接口调用顺序
3. 验证缓存行为
4. 验证登出清除缓存

### 6.3 回归测试

1. 确保现有登录功能不受影响
2. 确保组织架构同步功能不受影响
3. 确保其他 CRM 功能不受影响

---

## 七、部署计划

### 7.1 环境配置

| 环境 | employee-path | logout-path | user-menu-tree-path |
|------|--------------|-------------|---------------------|
| 开发 | `/employee/info` | `/auth/logout` | `/sysMenuUrl/getUserMenuTree` |
| 测试 | `/oauth/getUserInfo` | `/oauth/logout` | `/oauth/getUserPermission` |
| 生产 | `/oauth/getUserInfo` | `/oauth/logout` | `/oauth/getUserPermission` |

### 7.2 部署步骤

1. **预部署**
   - 在测试环境配置新路径
   - 运行所有测试
   - 手动验证登录流程

2. **部署**
   - 部署配置变更
   - 重启后端服务
   - 监控日志

3. **验证**
   - 验证登录功能
   - 验证权限获取
   - 验证登出功能

---

## 八、后续优化

### 8.1 性能优化

- 使用并行调用减少登录延迟
- 实现增量同步而不是全量同步
- 添加缓存命中率监控

### 8.2 可观测性

- 添加 OSS 接口调用指标
- 添加缓存命中率指标
- 添加错误率告警

### 8.3 容错处理

- 实现熔断器模式
- 实现降级策略
- 实现重试机制

---

## 九、附录

### A. 配置文件模板

```yaml
# application-prod.yml
app:
  crm:
    auth:
      employee-path: /oauth/getUserInfo
      logout-path: /oauth/logout

xiyu:
  integrations:
    organization:
      directory:
        user-menu-tree-path: /oauth/getUserPermission
```

### B. 环境变量模板

```bash
# .env.prod
XIYU_CRM_AUTH_EMPLOYEE_PATH=/oauth/getUserInfo
XIYU_CRM_AUTH_LOGOUT_PATH=/oauth/logout
XIYU_ORG_USER_MENU_TREE_PATH=/oauth/getUserPermission
```

### C. 相关文档链接

- [泊冉系统获取用户权限接口顺序](boran-permission-api-sequence.md)
- [CRM 对接规范](crm-integration-lessons.md)
- [组织架构集成方案](integration-organization-event-sdk.md)
