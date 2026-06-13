# API 安全审计报告 — 2026-06-13

## 元信息

| 字段 | 值 |
| --- | --- |
| 审计日期 | 2026-06-13 |
| Worktree | `/Users/user/xiyu/worktrees/claude` |
| 审计范围 | 后端鉴权 / 后端数据 (SQL、JWT、撤销、DTO) / 前端 (token、CORS、XSS、传输) |
| Lane 数 | 3 (auth / data / frontend) |
| 总发现 | 57 | HIGH 15 | MED 18 | LOW 24 | cross-lane 1 |

> **去重说明**: 唯一跨 lane 重复发现为 "JWT 未签发 / 未校验 `iss` / `aud` claim" (`backend/.../auth/JwtUtil.java:80-87, 180-202`) — Lane 1 评为 LOW (token 字段), Lane 2 评为 MED (签发方未校验)。按 "stronger wins" 原则合并为单条 MED 项, `crossLaneCount = 2`。

## 摘要表

| # | 等级 | 类型 | File : line | 简述 | 被几 lane 检出 |
| --- | --- | --- | --- | --- | --- |
| 1 | HIGH | 路径级白名单过宽 | `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java:69-89` | `/api/auth/sessions` 列入 `WHITE_LIST_URL` permitAll | 1 |
| 2 | HIGH | 凭据硬编码 / 启动初始化器 | `backend/src/main/java/com/xiyu/bid/bootstrap/LocalDevAccountInitializer.java:30,69` | 9 个 dev 账号统一 `Test@123` 硬编码密码 | 1 |
| 3 | HIGH | 用户枚举 | `backend/src/main/java/com/xiyu/bid/service/PasswordResetService.java:39-45` | `/api/auth/forgot-password` 通过响应文案区分邮箱存在性 | 1 |
| 4 | HIGH | 横向越权 (IDOR) | `backend/src/main/java/com/xiyu/bid/versionhistory/controller/DocumentVersionController.java:1-150` | 整类无 `@PreAuthorize` + `userId` 来自 query 可伪造 | 1 |
| 5 | HIGH | 横向越权 (IDOR) | `backend/src/main/java/com/xiyu/bid/casework/controller/ProjectArchiveController.java:42-253` | 整类无 `@PreAuthorize` + 详情/列表/统计/导出无 owner check | 1 |
| 6 | HIGH | 端点暴露面 | `backend/src/main/resources/application.yml:142-156` | `/actuator/info` 默认 enable + 未显式 disable `env`/`heapdump` | 1 |
| 7 | HIGH | 授权假设缺陷 | `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java:147-149` + `apikey/infrastructure/ApiKeyAuthenticationFilter.java:65-76` | `/api/integration/**` permitAll + filter 仅在带 `X-API-Key` 时才设置 Authentication | 1 |
| 8 | HIGH | 权限缺失 | `backend/src/main/java/com/xiyu/bid/analytics/controller/DashboardController.java:36-198` | 整类无 `@PreAuthorize`, `cache/clear` 可被任意已登录用户重置 | 1 |
| 9 | HIGH | SQL 注入 / 动态 SQL | `backend/src/main/java/com/xiyu/bid/resources/service/MarginService.java:31,51,67` + `MarginQuerySupport.java:89-126` | `createNativeQuery` 拼接 `role` switch 当前 safe 但 pattern fragile | 1 |
| 10 | HIGH | 敏感数据泄露 | `backend/src/main/java/com/xiyu/bid/entity/User.java:38-39` | `password` 字段无 `@JsonIgnore`, 未来 controller 直接返回 `User` 时 BCrypt hash 暴露 | 1 |
| 11 | HIGH | 会话管理 / 多副本失效 | `backend/src/main/java/com/xiyu/bid/auth/InMemoryTokenRevocationService.java:14-54` | 进程内 `ConcurrentHashMap`, 多 replica 部署 logout 不一致 | 1 |
| 12 | HIGH | 敏感数据暴露 (DTO plaintext) | `src/views/Resource/Account.vue:87` + `src/api/modules/resources/accounts.js:32` + `backend/src/main/java/com/xiyu/bid/platform/controller/PlatformAccountController.java:114-122` | 平台账号明文密码渲染在前端 toggle 单元格; 后端 `getPassword` 端点同样返回明文 | 2 (lane 1 + lane 3) |
| 13 | HIGH | XSS (token-stealable) | `src/api/session.js:6, 20-28` | JWT 存于 `localStorage` (默认 `rememberMe=true`), 任意 XSS 即可全量盗取 | 1 |
| 14 | HIGH | XSS (存储型) | `src/views/Project/stages/components/AiRecommendDrawer.vue:118` | `v-html="selectedCase.readerHTML"` 未走 `DOMPurify` | 1 |
| 15 | HIGH | XSS (存储型) | `src/views/Project/stages/ClosureStage.vue:199` | wangEditor HTML 输出通过 `v-html` 渲染, 无 `DOMPurify` | 1 |
| 16 | MED | 身份解析错误 | `backend/src/main/java/com/xiyu/bid/resources/controller/MarginController.java:106-128` | `auth.getName()` 当 `userId` 解析, `NumberFormatException` 静默降级到 null | 1 |
| 17 | MED | 隐式契约 / 权限缺失 | `backend/src/main/java/com/xiyu/bid/integration/controller/WeComIntegrationController.java:30-73` | 整类无 `@PreAuthorize`, 依赖路径级 ADMIN 兜底 | 1 |
| 18 | MED | 业务滥用 | `backend/src/main/java/com/xiyu/bid/integration/controller/WeComIntegrationController.java:54-66` | `sendTest` / `testConnectivity` 无 rate limit + 无 `@Auditable` | 1 |
| 19 | MED | 路径级白名单过宽 | `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java:86-87` | `/api/external/**` + `/api/systems/external/**` 通配 permitAll | 1 |
| 20 | MED | 业务滥用 / DoS | `backend/src/main/java/com/xiyu/bid/config/RateLimitFilter.java:57-77` | 仅 GET `/api/**` + login 限流, 写接口无限流 | 1 |
| 21 | MED | 启动器 profile 串扰 | `backend/src/main/java/com/xiyu/bid/bootstrap/DefaultAdminInitializer.java:19-22` + `LocalDevAccountInitializer.java:24` | prod+dev 双 profile 启动同时激活多个 initializer | 1 |
| 22 | MED | 配置漂移 / 维护风险 | `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java:91-130, 144-146` | `DEV_ONLY_WHITE_LIST` 暴露逻辑可读性差, 缺单元测试 | 1 |
| 23 | MED | 审计降级 | `backend/src/main/java/com/xiyu/bid/casework/controller/ProjectArchiveController.java:275-283` | 无 SecurityContext 时降级为 "系统", async 路径无法回溯 | 1 |
| 24 | MED | JWT 强度 / 过期时间 | `backend/src/main/java/com/xiyu/bid/config/JwtConfig.java:18-19` + `application.yml:106` | 默认 `expiration` 24h 偏长, 推荐 ≤ 1h | 1 |
| 25 | MED | 会话管理 / Fail-Open | `backend/src/main/java/com/xiyu/bid/auth/RedisTokenRevocationService.java:51-57` | Redis 异常 → 返回 `false`, revoked token 失效期间仍有效 | 1 |
| 26 | MED | JWT 强度 / 签发方未校验 | `backend/src/main/java/com/xiyu/bid/auth/JwtUtil.java:80-87, 180-202` | 未签发也未校验 `iss`/`aud` claim, 多服务/多环境共享密钥可重放 | 2 (lane 1 + lane 2) |
| 27 | MED | 敏感数据泄露 (日志) | `backend/src/main/java/com/xiyu/bid/service/EmailService.java:31,46` | `dev-mode=true` 时明文 password-reset / email-verification token 写日志 (默认 true) | 1 |
| 28 | MED | 输入校验 / 健壮性 | `backend/src/main/java/com/xiyu/bid/resources/service/MarginQuerySupport.java:196-215` | `LocalDateTime.parse` 未捕获 `DateTimeParseException` → 500 | 1 |
| 29 | MED | 会话管理 / 401 handling | `src/views/AI/SolutionReuse.vue:91-94` | `fetch()` 旁路 `httpClient`, 不走 401-refresh interceptor | 1 |
| 30 | MED | 会话管理 / 401 handling | `src/views/AI/MarketTiming.vue:69, 95, 104, 112` | 多个 `fetch()` 直接读 `sessionStorage('token')` | 1 |
| 31 | MED | 会话管理 / 401 handling | `src/views/System/settings/integration/CrmIntegrationCard.vue:45, 61` | raw `fetch('/api/settings', ...)` 旁路 `httpClient` | 1 |
| 32 | MED | 传输加密缺失 | `src/api/config.js:13-31` + `.env.api:3` | 默认 base URL 硬编码 `http://`, prod 缺 `isSecureContext` 守卫 | 1 |
| 33 | MED | 输入校验 | `src/components/login/LoginForm.vue:80-83` | 密码 `min: 3` 过宽, 与后端实际规则不一致 | 1 |
| 34 | LOW | 端点暴露 | `backend/src/main/java/com/xiyu/bid/project/controller/TenderInitMappingController.java:74-80` | 静态字典匿名可达, 与同模块其他 controller 不一致 | 1 |
| 35 | LOW | CSRF | `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java:137` | 全局 `csrf.disable()`; refresh cookie `SameSite=Lax` 跨站 POST 仍带 | 1 |
| 36 | LOW | 日志噪音 | `backend/src/main/java/com/xiyu/bid/auth/JwtAuthenticationFilter.java:93-95` | token 解析失败 `log.error`, 正常客户端带错 token 是预期行为 | 1 |
| 37 | LOW | 重复校验 | `backend/src/main/java/com/xiyu/bid/auth/JwtUtil.java:54-62` + `config/JwtConfig.java:23-25` | 密钥长度校验重复, 无安全风险仅冗余 | 1 |
| 38 | LOW | SecurityContext 传播 | `backend/src/main/java/com/xiyu/bid/ai/service/AiService.java:44,66` + `audit/service/AuditLogWriter.java:22` 等 6 处 | `@Async`/`@Scheduled` 默认不传播 SecurityContext | 1 |
| 39 | LOW | 用户枚举 | `backend/src/main/java/com/xiyu/bid/service/PasswordResetService.java:43-45` | disabled 用户可被单独枚举 | 1 |
| 40 | LOW | 用户枚举 | `backend/src/main/java/com/xiyu/bid/service/AuthService.java:70-77` | `/api/auth/register` 区分 username/email 已存在 | 1 |
| 41 | LOW | 敏感数据 | `backend/src/main/java/com/xiyu/bid/platform/controller/PlatformAccountController.java:114-122` | `getPassword` 返回明文 (本条与 H12 同根因, 本条聚焦 "无 audit + Cache-Control") | 1 |
| 42 | LOW | 角色一致性 | `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java:154` | `/api/cases/**` 路径级不含 2026-05 新增 `BID_SPECIALIST` / `ADMIN_STAFF` | 1 |
| 43 | LOW | 配置硬编码 | `backend/src/main/resources/application-e2e.yml:53` | e2e profile 弱 JWT secret 硬编码 | 1 |
| 44 | LOW | SQL 注入 / LIKE 通配符 | `UserRepository.java:55-57`, `AuditLogRepository.java:51-77`, `CaseRepository.java:91-95`, `MarginQuerySupport.java:185-188` | LIKE `%` / `_` 未转义, 搜索结果可被意外放大 | 1 |
| 45 | LOW | 内存泄漏 / OOM | `backend/src/main/java/com/xiyu/bid/auth/InMemoryTokenRevocationService.java:29-31,50-53` | 清理仅 lazy, 无 `@Scheduled` | 1 |
| 46 | LOW | 密码编码 (informational) | `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java:180-182` | BCrypt 正确使用, positive finding | 1 |
| 47 | LOW | JWT 算法 (informational) | `backend/src/main/java/com/xiyu/bid/auth/JwtUtil.java:65, 80-86, 180-186` | HS256 硬编码, `alg=none` 不可达, positive finding | 1 |
| 48 | LOW | JWT 密钥 (informational) | `backend/src/main/java/com/xiyu/bid/auth/JwtUtil.java:39, 55-62` + `JwtConfig.java:22-27` | 强制 ≥32 字节密钥, positive finding | 1 |
| 49 | LOW | Token 存储卫生 | `src/api/session.js:6, 17, 20-28, 35, 43-44, 79, 95-101` | 单一 `'token'` key 同时被 localStorage 和 sessionStorage 使用, 切换 `rememberMe` 时旧值不清 | 1 |
| 50 | LOW | XSS (defensible) | `src/components/common/doc-insight/DocVerificationWorkbench.vue:46` + `markdownSanitizer.js` | `v-html` 经过 `renderSafeMarkdown` 但依赖配置正确, 缺回归测试 | 1 |
| 51 | LOW | CORS / 客户端校验 | `src/api/client.js:37` | `withCredentials: true` 缺客户端 origin sanity check | 1 |
| 52 | LOW | Tabnabbing | `src/views/Resource/Account.vue:74`, `CABorrowDialog.vue:79`, `SiteDetail.vue:163`, `SOPDetail.vue:36, 43`, `AssetCard.vue:16` | `target="_blank"` 缺 `rel="noopener noreferrer"` | 1 |
| 53 | LOW | 上传校验 | `src/views/Project/stages/components/ProjectDocumentTable.vue:28, 56-68` | `<input type="file">` 无 client-side MIME / size cap | 1 |
| 54 | LOW | 审计 UX | `src/views/Resource/Account.vue:373-376` | 密码可见性 toggle 无 audit 上报 | 1 |
| 55 | LOW | 浏览器安全头 | `vite.config.js` + `index.html` | 无 CSP / `X-Frame-Options` meta | 1 |
| 56 | LOW | XSS (defensible) | `src/views/Project/stages/ClosureStage.vue:184` | `el-tooltip raw-content` 渲染静态字符串, 当前 safe 但模式同 v-html | 1 |
| 57 | LOW | 用户枚举 (注册) | (lane 1 #24 — 已合并入 H3 链; 单独保留以追踪区分) | 同 H3, 独立保留方便跟踪 register 端点 | 1 |

> **note**: Lane 1 #23 (disabled 用户枚举, `PasswordResetService:43-45`) 与 H3 同根因, 拆为独立项仅便于追踪 fix 单元, 不算 cross-lane。

## HIGH 项详情

### H1 — `/api/auth/sessions` 列入 `WHITE_LIST_URL` permitAll

- **来源 lane**: 1
- **类型**: 认证缺失 / 路径级白名单过宽
- **证据**:
  ```java
  // backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java:69-89
  private static final String[] WHITE_LIST_URL = {
      "/api/auth/login", "/api/auth/register", "/api/auth/logout",
      "/api/auth/refresh", "/api/auth/forgot-password", "/api/auth/reset-password",
      "/api/auth/sessions",  // <-- 会话列表/撤销端点被 permitAll
      ...
  };
  auth.requestMatchers(WHITE_LIST_URL).permitAll();
  ```
  `AuthController.sessions` 端点 (L162-168, L173-193) 用 `@PreAuthorize("isAuthenticated()")` 兜底, 但路径已被 `permitAll`。`DELETE /api/auth/sessions/{id}` 会无认证失效。
- **影响**: 防御纵深缺失; 当前依赖方法注解作为最后一道闸, 任何"清理无用注解"的重构都直接让会话管理变成匿名可访问。
- **修复建议**:
  1. 从 `WHITE_LIST_URL` 移除 `/api/auth/sessions`, 让 `anyRequest().authenticated()` 兜底。
  2. 加 ArchUnit 规则禁止 `WHITE_LIST_URL` 包含 `/api/auth/sessions*` / `/api/admin/**`。
- **crossLaneCount**: 1

### H2 — `LocalDevAccountInitializer` / `LocalDevProjectInitializer` 在 `dev` profile 注入弱密码

- **来源 lane**: 1
- **类型**: 启动初始化器泄露 / 凭据硬编码
- **证据**:
  ```java
  // backend/src/main/java/com/xiyu/bid/bootstrap/LocalDevAccountInitializer.java:30,69
  static final String LOCAL_TEST_PASSWORD = "Test@123";
  ...
  user.setPassword(passwordEncoder.encode(LOCAL_TEST_PASSWORD));
  userRepository.save(user);
  ```
  `@Profile("dev")` (L24) 启动时无条件 createOrUpdate 9 个角色账号, 密码统一为 `Test@123`。
- **影响**: dev / 联调环境 9 个真实角色账号可登录, 包括 `admin_staff` / `bid_admin` / `task_executor` 等生产环境也将存在的真实角色。
- **修复建议**:
  1. 不再在仓库 hardcode 默认密码。
  2. 让 `start.sh` 注入 `LOCAL_DEV_PASSWORD` 环境变量, 缺省则跳过。
  3. 加 `@ConditionalOnProperty(name = "app.bootstrap.local-dev.enabled", havingValue = "true", matchIfMissing = false)`。
- **crossLaneCount**: 1

### H3 — `/api/auth/forgot-password` 允许邮箱枚举

- **来源 lane**: 1
- **类型**: 敏感数据泄露 / 用户枚举
- **证据**:
  ```java
  // backend/src/main/java/com/xiyu/bid/service/PasswordResetService.java:39-45
  public String createPasswordResetToken(String email) {
      User user = userRepository.findByEmail(email)
              .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
      if (!user.getEnabled()) {
          throw new IllegalStateException("User account is disabled");
      }
      ...
  }
  ```
- **影响**: 攻击者批量提交邮箱列表, 通过响应文案区分"邮箱已注册" / "邮箱未注册" / "账号已禁用", 用于钓鱼或撞库配合。
- **修复建议**:
  1. `createPasswordResetToken` 不再抛异常; 一律返回成功响应。
  2. 对未启用账号也不区分。
  3. 确认 `forgot-password` 端点是否在 `RateLimitFilter` (L57-61) 的拦截范围内 (当前仅 `/api/auth/login` 触发登录限流, `/forgot-password` 不在)。
- **crossLaneCount**: 1

### H4 — `DocumentVersionController` 整类无 `@PreAuthorize` + `userId` 来自 query 可伪造

- **来源 lane**: 1
- **类型**: 横向越权 (IDOR) / 权限缺失 / 审计欺骗
- **证据**:
  ```java
  // backend/src/main/java/com/xiyu/bid/versionhistory/controller/DocumentVersionController.java:1-150
  @RestController
  @RequestMapping("/api/documents/{projectId}/versions")
  public class DocumentVersionController {
      // 0 个 @PreAuthorize
      @GetMapping("/{versionId}")
      public ResponseEntity<ApiResponse<DocumentVersionDTO>> getVersion(...)

      @PostMapping("/{versionId}/rollback")
      public ResponseEntity<...> rollbackToVersion(
              @PathVariable Long projectId,
              @PathVariable Long versionId,
              @RequestParam(required = false) Long userId) {  // <-- 来自 query
          versionHistoryService.rollbackToVersion(projectId, versionId, userId);
      }
  }
  ```
- **影响**: 任何已认证用户可读/回滚任意 `projectId` 的版本; `userId` 来自 query string 可伪造"是别人执行的", 审计日志记录错误操作人, 既成横向越权也成审计欺骗。
- **修复建议**:
  1. 控制器类上加 `@PreAuthorize("isAuthenticated()")`。
  2. 强制 service 层调用 `projectAccessScopeService.assertCurrentUserCanAccessProject(projectId)`。
  3. 删除 `rollbackToVersion` 的 `userId` 参数, 改用 `@AuthenticationPrincipal UserDetails` 解析。
- **crossLaneCount**: 1

### H5 — `ProjectArchiveController` 整类无 `@PreAuthorize` + 详情/导出无 owner check

- **来源 lane**: 1
- **类型**: 横向越权 / 权限缺失
- **证据**:
  ```java
  // backend/src/main/java/com/xiyu/bid/casework/controller/ProjectArchiveController.java:42-253
  @RestController
  @RequestMapping("/api/archive")
  public class ProjectArchiveController {
      // 0 个 @PreAuthorize
      @GetMapping("/{id}")                                   // 详情
      @GetMapping                                            // 列表
      @GetMapping("/stats")
      @PostMapping("/export-excel")
      @GetMapping("/export-zip/{projectId}")                 // 任意 projectId
      @PostMapping("/export-zip")
  }
  ```
  详情/列表/统计端点 (L53-72) 无任何 owner check; 文件预览/下载的 `assertCurrentUserCanAccessProject` 在 file/preview/download 端点已存在 (L83, L119), 但**详情/列表/统计/导出全无保护**。导出 zip/excel (L145-253) 内部 `archiveExportService.resolveExportableProjectIds()` 决定可见集, 但未授权用户也能看到"成功导出 0 条"响应, 不报错。
- **影响**: 已登录 STAFF 可读取任何 projectId 的档案详情 (含附件路径、内部备注); `export-zip/{projectId}` 路径携带的 `projectId` 可遍历。
- **修复建议**:
  1. 控制器加 `@PreAuthorize("isAuthenticated()")`, 业务方法在最前面调用 `workflowService.assertCurrentUserCanAccessProject(id)`。
  2. 暴露的 `Long userId` 参数 (L148, L219) 必须删除, 改从 security context 取。
- **crossLaneCount**: 1

### H6 — `/actuator/info` 默认 enable + `info` 端点细节未审

- **来源 lane**: 1
- **类型**: 端点暴露面 / 信息泄露
- **证据**:
  ```yaml
  # backend/src/main/resources/application.yml:142-156
  management:
    endpoints:
      web:
        exposure:
          include: health,info,prometheus,metrics
    endpoint:
      health:
        show-details: when_authorized
  ```
  prod 改为 `include: health` only 且 `show-details: never`, 收紧。但 default / dev 暴露 `info` (默认 true) + `prometheus` (应用指标); `info` 端点可能暴露 build / git 信息。
- **影响**: `/actuator/health` 在 default / dev `show-details: when_authorized` — 匿名访问 health 时不显示 details (OK), 但 `info` 端点未被显式 disable。
- **修复建议**:
  1. 显式配置 `management.endpoint.info.enabled: false`。
  2. 在 SecurityConfig 把 `/actuator/info` 路径处理确认 (L84 只放了 `health`, 没放 `info`, 这部分已正确)。
  3. 显式添加 `management.endpoint.env.enabled: false` + `heapdump` + `threaddump` 显式 false。
- **crossLaneCount**: 1

### H7 — `ApiKeyAuthenticationFilter` + `SecurityConfig.permitAll("/api/integration/**")` 默认信任网络

- **来源 lane**: 1
- **类型**: 端点暴露面 / 授权检查依赖
- **证据**:
  ```java
  // backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java:147-149
  auth.requestMatchers("/api/integration/**").permitAll();
  ```
  `ApiKeyAuthenticationFilter` 在请求带 `X-API-Key` 时**才**设置 Authentication; 无 API key 的请求**仍**会进入 controller (`apikey/infrastructure/ApiKeyAuthenticationFilter.java:65-76`)。
- **影响**: `/api/integration/**` 下所有 controller (如 `TenderIntegrationController`) 在无 API key 时仍可达; 若 controller 没有独立加 `@PreAuthorize("hasRole('EXTERNAL_API')")`, 任何匿名请求可访问。
- **修复建议**:
  1. 在 `ApiKeyAuthenticationFilter` 末尾对 `/api/integration/**` 路径, 当 rawKey 为空时显式 `sendError 401`。
  2. 验证 `/api/integration/**` 下所有 controller 是否带 `@PreAuthorize("hasRole('EXTERNAL_API')")`。
  3. 加单元测试: 无 X-API-Key 访问 `/api/integration/tenders` 必须 401。
- **crossLaneCount**: 1

### H8 — `DashboardController` 整类无 `@PreAuthorize` (与 CustomerTypeAnalytics 不一致)

- **来源 lane**: 1
- **类型**: 权限缺失 / 显式契约缺失 / 业务滥用
- **证据**:
  ```java
  // backend/src/main/java/com/xiyu/bid/analytics/controller/DashboardController.java:36-198
  @RestController
  @RequestMapping("/api/analytics")
  public class DashboardController {
      // 0 个 @PreAuthorize
      @PostMapping("/cache/clear")  // <-- 清缓存
      @GetMapping("/drill-down")    // <-- 任意 type/key
  }
  ```
- **影响**: `POST /api/analytics/cache/clear` 任何已登录用户可调用, 重置 dashboard 缓存, 造成后台 cache stampede; `/api/analytics/drill-down` 接收任意 `type` / `key` 参数, 在 service 层若未做白名单, 可能 SQL 注入或敏感数据全量返回。
- **修复建议**:
  1. 加 `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")` 到类级。
  2. `cache/clear` 需 `hasRole('ADMIN')`。
  3. `drill-down` 在 service 层加 `type` 白名单 (HQL 注入防御)。
- **crossLaneCount**: 1

### H9 — SQL 注入: `MarginService` 使用 `createNativeQuery` + role 拼接

- **来源 lane**: 2
- **类型**: SQL injection / 动态 SQL 拼接 (defense-in-depth)
- **证据**:
  ```java
  // backend/src/main/java/com/xiyu/bid/resources/service/MarginService.java:31,51,67
  StringBuilder sql = MarginQuerySupport.summaryBase();
  MarginQuerySupport.appendRole(sql, uid, role, "p", "pid");
  Query query = em.createNativeQuery(sql.toString());
  ```
  `appendRole` 通过 `StringBuilder.append()` 基于 `role.toLowerCase()` switch 构建 SQL (builder at `MarginQuerySupport.java:89-126`)。今日 `role` 是 server-side 值, 未来若任何调用传入 user-controlled `role`, 此处即为 SQLi sink。
- **影响**: 目前 safe, 但动态 SQL 模式是 foot-gun。`appendFilters` 接收 `Map<String, String> f` 并 `setParams` 调用 `LocalDateTime.parse(...)` (L196-215) 不捕获 `DateTimeParseException` — 关联健壮性 + 信息泄露问题。
- **修复建议**:
  1. 重构 `MarginService` 使用 JPA `Specification` (JPA Criteria API) 或固定 SQL + bind parameters。
  2. 若必须动态 SQL, 在拼接前对 `role` 校验 enum allow-list。
  3. `LocalDateTime.parse` 用 try/catch 包, 失败返回 400。
- **crossLaneCount**: 1

### H10 — 敏感数据泄露: `User.password` 字段无 `@JsonIgnore`

- **来源 lane**: 2
- **类型**: 敏感数据泄露 (defense-in-depth)
- **证据**:
  ```java
  // backend/src/main/java/com/xiyu/bid/entity/User.java:38-39
  @Column(nullable = false)
  private String password;
  ```
  无 `@JsonIgnore`。Entity 是 `@Data` (Lombok), 所以 getter/setter public, Jackson 在 `User` 实例被序列化进响应体时直接输出 BCrypt hash。
- **影响**: 任何 controller 直接返回 `User` (例如 `AdminUserService.findUser`、调试端点、被遗忘的 DTO mapping) 即泄露 BCrypt hash。Defense in depth 要求 entity 级注解保护。今日没有 controller 直接序列化 `User` (只有 `AuthResponse` / `AdminUserDTO`), 但任何未来 regression 即泄露。
- **修复建议**:
  1. `User.java` 的 `password` 字段加 `@JsonIgnore`。
  2. 考虑类级 `@JsonIgnoreProperties({"password", "wecomUserId", "externalOrgUserId", "lastOrgEventKey"})`。
- **crossLaneCount**: 1

### H11 — `InMemoryTokenRevocationService` 多 replica 不一致 + 无 `@ConditionalOnProperty` 保护

- **来源 lane**: 2
- **类型**: 会话管理 / 多副本失效
- **证据**:
  ```java
  // backend/src/main/java/com/xiyu/bid/auth/InMemoryTokenRevocationService.java:14-54
  @Service("inMemoryTokenRevocationService")
  public class InMemoryTokenRevocationService implements TokenRevocationService {
      private static final int CLEANUP_THRESHOLD = 1024;
      private final Map<String, Instant> revoked = new ConcurrentHashMap<>();
      // ...
  }
  ```
  类注释 "e2e 与 Redis 缺席场景兜底"; `RedisTokenRevocationService` 标注 `@Primary` 和 `@ConditionalOnBean(StringRedisTemplate.class)`, 生产有 Redis 时不应用 in-memory。**然而** 没有 `@Profile` 限制; Redis 短暂不可用时此 bean 仍可被加载。多 replica 部署: A 实例 logout, B 实例 token 仍有效。
- **影响**: 若此 bean 在多 replica 部署中曾为 `@Primary` (例如 Redis outage fallback), logout 只在单实例生效, 其他实例用户保持登录直到自然 JWT 过期 (24h)。
- **修复建议**:
  1. `InMemoryTokenRevocationService` 加 `@ConditionalOnProperty(name = "auth.token-revocation", havingValue = "in-memory", matchIfMissing = false)`。
  2. 确保生产只有 `RedisTokenRevocationService` 激活。
  3. 加 fail-closed 启动检查: 两者都未配置时拒绝启动。
- **crossLaneCount**: 1

### H12 — 平台账号明文密码渲染 / 返回 (HIGH, cross-lane)

- **来源 lane**: 1 + 3
- **类型**: 敏感数据暴露 (DTO plaintext + UI 渲染 + 后端端点)
- **证据**:
  ```vue
  <!-- src/views/Resource/Account.vue:87 -->
  <span class="password-text">{{ passwordVisible[row.id] ? row.password : '••••••' }}</span>
  ```
  ```js
  // src/api/modules/resources/accounts.js
  function normalizeAccount(item = {}) {
      return {
          ...
          password: item.password || '',
          ...
      }
  }
  ```
  ```java
  // backend/src/main/java/com/xiyu/bid/platform/controller/PlatformAccountController.java:114-122
  @GetMapping("/{id}/password")
  public ResponseEntity<...> getPassword(@PathVariable Long id) {
      // returns plaintext password
      log.warn("...");
      return ResponseEntity.ok(plaintext);
  }
  ```
- **影响**: 后端 `GET /api/platform/accounts` 的响应直接包含每个账号的 cleartext password; 任何拥有 "platform account list" 权限 (不仅 password-view) 的用户都能通过点击眼睛图标看到全部密码。跨租户泄露、浏览器扩展、肩窥风险。后端是泄露源头; 前端在单 table cell 中以 1-click toggle 直接渲染完整 DTO。
- **修复建议**:
  1. 后端: 从 `getList` / `getDetail` 响应中剔除 `password`; 仅从显式 `getPassword` 端点 (加 `@Auditable` + "show password" action) 返回。
  2. 后端: `getPassword` 端点返回值改为 "一次性 access URL + 5 分钟过期", `Cache-Control: no-store`。
  3. 前端: `AccountFormDialog.vue:18` 的 `v-model="form.password"` 同样在 cleartext over the wire — 结合 `Content-Type: application/json` + HTTP fallback (`config.js`) 形成高风险链。
- **crossLaneCount**: 2

### H13 — JWT 存于 `localStorage` (XSS-stealable)

- **来源 lane**: 3
- **类型**: Token 存储 / 会话管理
- **证据**:
  ```js
  // src/api/session.js:6, 20-28
  const ACCESS_TOKEN_KEY = 'token'
  export const setAccessToken = (token, remember = true) => {
      accessToken = token || null
      if (token) {
          const storage = remember ? window.localStorage : window.sessionStorage
          storage.setItem(ACCESS_TOKEN_KEY, token)
      }
      return accessToken
  }
  ```
  ```js
  // src/api/modules/auth.js
  async login(username, password, rememberMe = true) {
      const response = await httpClient.post('/api/auth/login', { username, password, rememberMe }, ...)
      setAccessToken(authPayload?.token, rememberMe)
  }
  ```
  Login 默认 `rememberMe = true` ⇒ token 存 `localStorage` 直到用户 logout。
- **影响**: 任何 XSS 或同源第三方脚本可经 `localStorage.getItem('token')` 盗取 JWT。`withCredentials: true` 无效, 因为 bearer header 由 `httpClient` interceptor (`client.js:115-118`) 单独读取。浏览器 dev console、扩展、宽松 CSP (目前无)、`v-html` XSS 全部能偷 token。
- **修复建议**:
  1. 将 access token 移到后端在 `/api/auth/login` 设置的 `HttpOnly; Secure; SameSite=Strict` cookie。
  2. 客户端依赖 cookie (`withCredentials: true` 已设)。
  3. SPA-friendly flow: 只保留短期 scope-limited "session id" cookie; refresh 时 rotate。
- **crossLaneCount**: 1

### H14 — `v-html` 渲染服务端 `readerHTML` 无 `DOMPurify`

- **来源 lane**: 3
- **类型**: XSS (Stored / Reflected)
- **证据**:
  ```vue
  <!-- src/views/Project/stages/components/AiRecommendDrawer.vue:118 -->
  <div class="reader-page">
      <h5>{{ selectedCase.scoringTitle }}</h5>
      <div v-html="selectedCase.readerHTML || selectedCase.highlightedText || selectedCase.responseText"></div>
  </div>
  ```
  ```js
  readerHTML: item.readerHTML || item.highlightedText || item.responseText || '',
  ```
- **影响**: `readerHTML` / `highlightedText` 直接来自后端 case-recommendation API。若 case 记录的 `responseText` (任何可创建 case 的入口) 含 raw HTML, 在 bid_specialist 浏览器内执行。无 DOMPurify / `renderSafeMarkdown`。结合 H13 (`localStorage` token), 一次即可 session takeover。
- **修复建议**:
  1. 所有 `responseText` / `highlightedText` / `readerHTML` 走 `DOMPurify.sanitize` (项目已有 `src/components/common/doc-insight/markdownSanitizer.js:7`)。
  2. 复用现有 helper 或 import `renderSafeMarkdown`, 文本字段用 `marked.parse` + `DOMPurify`。
- **crossLaneCount**: 1

### H15 — `v-html` 渲染 wangEditor HTML (Closure Stage 项目总结)

- **来源 lane**: 3
- **类型**: XSS (Stored)
- **证据**:
  ```vue
  <!-- src/views/Project/stages/ClosureStage.vue:199 -->
  <div v-else class="summary-readonly rich-text-content"
       v-html="form.projectSummary || '<span class=\'text-gray\'>(暂无项目总结)</span>'"></div>
  ```
  `form.projectSummary` 是 wangEditor 的 HTML 输出, 服务端持久化, read-only view 直接渲染。
- **影响**: 任何拥有 project-summary 写权限的用户可嵌入 `<img onerror=…>` 或 `<a href="javascript:…">` payload; `v-html` 无 sanitization → 其他用户浏览 closure 时 XSS。在投标平台意味着 manager / admin (最高权限目标) 都受影响。
- **修复建议**:
  1. wangEditor 输出走 `DOMPurify` (项目已有 pattern `markdownSanitizer.js`)。
  2. 或以 `disabled: true` 的只读 Editor 实例渲染 (取代 `v-html`)。
- **crossLaneCount**: 1

## MED / LOW 项

<details>
<summary><strong>MED 项 (18 条) — 点击展开</strong></summary>

| # | 等级 | 类型 | File : line | 简述 |
| --- | --- | --- | --- | --- |
| 16 | MED | 身份解析错误 | `backend/src/main/java/com/xiyu/bid/resources/controller/MarginController.java:106-128` | `auth.getName()` 当 `userId` 解析, `NumberFormatException` 静默降级到 null |
| 17 | MED | 隐式契约 / 权限缺失 | `backend/src/main/java/com/xiyu/bid/integration/controller/WeComIntegrationController.java:30-73` | 整类无 `@PreAuthorize`, 依赖路径级 ADMIN 兜底 |
| 18 | MED | 业务滥用 | `backend/src/main/java/com/xiyu/bid/integration/controller/WeComIntegrationController.java:54-66` | `sendTest` / `testConnectivity` 无 rate limit + 无 `@Auditable` |
| 19 | MED | 路径级白名单过宽 | `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java:86-87` | `/api/external/**` + `/api/systems/external/**` 通配 permitAll |
| 20 | MED | 业务滥用 / DoS | `backend/src/main/java/com/xiyu/bid/config/RateLimitFilter.java:57-77` | 仅 GET `/api/**` + login 限流, 写接口无限流 |
| 21 | MED | 启动器 profile 串扰 | `backend/src/main/java/com/xiyu/bid/bootstrap/DefaultAdminInitializer.java:19-22` + `LocalDevAccountInitializer.java:24` | prod+dev 双 profile 启动同时激活多个 initializer |
| 22 | MED | 配置漂移 / 维护风险 | `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java:91-130, 144-146` | `DEV_ONLY_WHITE_LIST` 暴露逻辑可读性差, 缺单元测试 |
| 23 | MED | 审计降级 | `backend/src/main/java/com/xiyu/bid/casework/controller/ProjectArchiveController.java:275-283` | 无 SecurityContext 时降级为 "系统", async 路径无法回溯 |
| 24 | MED | JWT 强度 / 过期时间 | `backend/src/main/java/com/xiyu/bid/config/JwtConfig.java:18-19` + `application.yml:106` | 默认 `expiration` 24h 偏长 |
| 25 | MED | 会话管理 / Fail-Open | `backend/src/main/java/com/xiyu/bid/auth/RedisTokenRevocationService.java:51-57` | Redis 异常 → 返回 `false`, revoked token 失效期间仍有效 |
| 26 | MED | JWT 强度 / 签发方未校验 | `backend/src/main/java/com/xiyu/bid/auth/JwtUtil.java:80-87, 180-202` | 未签发也未校验 `iss`/`aud` claim (cross-lane 2) |
| 27 | MED | 敏感数据泄露 (日志) | `backend/src/main/java/com/xiyu/bid/service/EmailService.java:31,46` | `dev-mode=true` 时明文 password-reset token 写日志 (默认 true) |
| 28 | MED | 输入校验 / 健壮性 | `backend/src/main/java/com/xiyu/bid/resources/service/MarginQuerySupport.java:196-215` | `LocalDateTime.parse` 未捕获 `DateTimeParseException` → 500 |
| 29 | MED | 会话管理 / 401 handling | `src/views/AI/SolutionReuse.vue:91-94` | `fetch()` 旁路 `httpClient`, 不走 401-refresh interceptor |
| 30 | MED | 会话管理 / 401 handling | `src/views/AI/MarketTiming.vue:69, 95, 104, 112` | 多个 `fetch()` 直接读 `sessionStorage('token')` |
| 31 | MED | 会话管理 / 401 handling | `src/views/System/settings/integration/CrmIntegrationCard.vue:45, 61` | raw `fetch('/api/settings', ...)` 旁路 `httpClient` |
| 32 | MED | 传输加密缺失 | `src/api/config.js:13-31` + `.env.api:3` | 默认 base URL 硬编码 `http://`, prod 缺 `isSecureContext` 守卫 |
| 33 | MED | 输入校验 | `src/components/login/LoginForm.vue:80-83` | 密码 `min: 3` 过宽, 与后端实际规则不一致 |

### MED 证据 + 修复概要

**M16** — `MarginController.userId(Authentication)` 把 `auth.getName()` 当 `Long userId` 解析; 若 `UserDetailsServiceImpl` 返回 username (非 userId), `NumberFormatException` → null → 静默走"全部数据"路径。**修复**: `@AuthenticationPrincipal User currentUser` 注入; service 层 null userId 时显式 401/403。

**M17** — `WeComIntegrationController` 整类无 `@PreAuthorize`, 依赖 `requestMatchers("/api/admin/**").hasRole("ADMIN")` 路径级兜底; 路径迁移即权限失效。**修复**: 类级 `@PreAuthorize("hasRole('ADMIN')")`; OAuth `state` 加 `SameSite=Strict` cookie 保护 CSRF。

**M18** — `sendTest` / `testConnectivity` 无 `@Auditable` 无 rate limit; ADMIN 可高频发任意 content。**修复**: `@Auditable` + method-level rate limit (5/小时) + content 长度限制 + 关键词白名单。

**M19** — `/api/external/**` + `/api/systems/external/**` 通配 permitAll; 任何 controller 落到两个前缀即公开化且无 controller-level guard 兜底。**修复**: 静态验证两个前缀下当前 controller; 加 ArchUnit; 移除通配改精确路径白名单。

**M20** — `RateLimitFilter` 仅 GET `/api/**` + login 限流; POST/PUT/DELETE 无全局限流; `/api/auth/forgot-password` / `/register` 不在拦截范围。**修复**: GET+POST+PUT+DELETE 全覆盖, 区分 `apiKey` / `user` 两种 bucket; forgot-password / register 加限流。

**M21** — `prod+dev` 双 profile 启动同时激活 `DefaultAdminInitializer` + `LocalDevAccountInitializer` + `E2eDemoDataInitializer`, 弱密码账号 + 演示数据污染 prod。**修复**: 用 `@ConditionalOnExpression` 显式组合条件; `start.sh` / `dev-services.sh` 校验 `SPRING_PROFILES_ACTIVE` 不同时含 `prod` 和 `dev`/`e2e`。

**M22** — `DEV_ONLY_WHITE_LIST` 暴露逻辑可读性差, 组合 `dev,prod` 时 `isProdLike=true` → 返回 false → 不放行 dev tooling (正确但需推敲)。**修复**: 显式注释 + 单元测试覆盖 `shouldAllowDevTooling` 全部 profile 组合; 修复 `application-prod.yml` 错别字 `validateOnNervousSystems` (L27)。

**M23** — `ProjectArchiveController.getCurrentOperatorName` 无 SecurityContext 时降级为 "系统"; 真正的风险是 service 层绕过 controller 被异步调用时, 审计"系统"操作无法回溯。**修复**: 移除降级逻辑, 改抛 `AuthenticationCredentialsNotFoundException`; service 层在 async 调用前手动塞 `SecurityContext`。

**M24** — JWT `expiration` 默认 24h (推荐 ≤ 1h); 被盗 token 24h 内有效, 配合 H11 多副本失效 blast radius 更大。**修复**: 默认降到 3600000 (1h); 7 天 refresh token 保证 session 连续; 启动 warning `expiration > 7200000` 非 dev profile。

**M25** — `RedisTokenRevocationService.isRevoked` Redis 异常 → 返回 `false` (Fail-Open); Redis outage 时 logout 无效, revoked token 失效期间仍有效。**修复**: catch 返回 `true` (fail-closed) 或抛异常 → filter 503; 至少 `log.error` + metric counter + circuit-breaker。

**M26** — (cross-lane 1+2) `JwtUtil` 未签发也未校验 `iss` / `aud`; 多服务 / 多环境共享密钥时 token 跨环境重放。**修复**: 签发 `.issuer("xiyu-bid-poc")`, 校验 `.requireIssuer("xiyu-bid-poc")` + `aud=xiyu-bid-api`。

**M27** — `EmailService` 在 `dev-mode=true` 时日志明文 password-reset / email-verification token (默认 `app.email.dev-mode: true`)。**修复**: 启动 assertion: `prod` profile + `dev-mode=true` 拒绝启动; 即使 dev mode 也 redact token。

**M28** — `MarginQuerySupport.setParams` 在 `paymentDateStart` 非 ISO 日期时未捕获 `DateTimeParseException` → 500 (Spring dev profile 暴露 stack trace)。**修复**: `DateTimeFormatter.ISO_LOCAL_DATE.parse(...)` + 翻译 `DateTimeParseException` 为 400。

**M29** — `SolutionReuse.vue:91-94` `fetch()` 直接读 `sessionStorage('token')`, 旁路 `httpClient` 401-refresh interceptor (`client.js:151-175`); token 过期 + refresh 有效时硬 401。**修复**: 改 `httpClient.get('/api/knowledge/cases', { params })`; 弃手动 `sessionStorage.getItem` + `Authorization` header。

**M30** — `MarketTiming.vue:68-71, 95, 104, 112` 三个 `fetch()` 同模式; 同 M29 修复。

**M31** — `CrmIntegrationCard.vue:42-67` raw `fetch('/api/settings', ...)`, 同时把 CRM `authToken` 在 body 里 round-trip; SSO redirect 在不同 tab 时 `Authorization: Bearer null` 触发后端日志污染。**修复**: `httpClient.put('/api/settings', ...)`; 把 CRM secret 移到后端存储字段, 不经浏览器往返。

**M32** — `config.js:13-31` fallback URL 硬编码 `http://`; `.env.api` ships `VITE_API_BASE_URL=http://localhost:18086` (HTTP)。生产未覆盖环境变量时 silent cleartext。**修复**: prod 模式 `normalizeApiBaseUrl` 在 `http://` 时 throw / rewrite `https://`; `index.html` 加 `Content-Security-Policy: upgrade-insecure-requests`; 在 deploy runbook 显式记录。

**M33** — `LoginForm.vue:80-83` 密码 `min: 3`; 前端 hint 与后端实际规则不一致, 鼓励弱密码心理模型。**修复**: 删 `min: 3`; 后端 `BadCredentials` 401 自行说话; 或从 `/api/auth/password-policy` 启动时拉真实规则。

</details>

<details>
<summary><strong>LOW 项 (24 条) — 点击展开</strong></summary>

| # | 等级 | 类型 | File : line | 简述 |
| --- | --- | --- | --- | --- |
| 34 | LOW | 端点暴露 | `backend/src/main/java/com/xiyu/bid/project/controller/TenderInitMappingController.java:74-80` | 静态字典匿名可达, 与同模块其他 controller 不一致 |
| 35 | LOW | CSRF | `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java:137` | 全局 `csrf.disable()`; refresh cookie `SameSite=Lax` 跨站 POST 仍带 |
| 36 | LOW | 日志噪音 | `backend/src/main/java/com/xiyu/bid/auth/JwtAuthenticationFilter.java:93-95` | token 解析失败 `log.error`, 正常客户端带错 token 是预期行为 |
| 37 | LOW | 重复校验 | `backend/src/main/java/com/xiyu/bid/auth/JwtUtil.java:54-62` + `config/JwtConfig.java:23-25` | 密钥长度校验重复, 无安全风险仅冗余 |
| 38 | LOW | SecurityContext 传播 | `ai/service/AiService.java:44,66` + `audit/service/AuditLogWriter.java:22` 等 6 处 | `@Async`/`@Scheduled` 默认不传播 SecurityContext |
| 39 | LOW | 用户枚举 | `backend/src/main/java/com/xiyu/bid/service/PasswordResetService.java:43-45` | disabled 用户可被单独枚举 (同 H3, 独立追踪) |
| 40 | LOW | 用户枚举 | `backend/src/main/java/com/xiyu/bid/service/AuthService.java:70-77` | `/api/auth/register` 区分 username/email 已存在 |
| 41 | LOW | 敏感数据 | `backend/src/main/java/com/xiyu/bid/platform/controller/PlatformAccountController.java:114-122` | `getPassword` 返回明文 (同 H12, 本条聚焦 "无 audit + Cache-Control") |
| 42 | LOW | 角色一致性 | `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java:154` | `/api/cases/**` 路径级不含 2026-05 新增 `BID_SPECIALIST` / `ADMIN_STAFF` |
| 43 | LOW | 配置硬编码 | `backend/src/main/resources/application-e2e.yml:53` | e2e profile 弱 JWT secret 硬编码 |
| 44 | LOW | SQL 注入 / LIKE 通配符 | `UserRepository.java:55-57`, `AuditLogRepository.java:51-77`, `CaseRepository.java:91-95`, `MarginQuerySupport.java:185-188` | LIKE `%` / `_` 未转义 |
| 45 | LOW | 内存泄漏 | `backend/src/main/java/com/xiyu/bid/auth/InMemoryTokenRevocationService.java:29-31,50-53` | 清理仅 lazy, 无 `@Scheduled` |
| 46 | LOW | 密码编码 (positive) | `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java:180-182` | BCrypt 正确使用, informational |
| 47 | LOW | JWT 算法 (positive) | `backend/src/main/java/com/xiyu/bid/auth/JwtUtil.java:65, 80-86, 180-186` | HS256 硬编码, `alg=none` 不可达, informational |
| 48 | LOW | JWT 密钥 (positive) | `backend/src/main/java/com/xiyu/bid/auth/JwtUtil.java:39, 55-62` + `JwtConfig.java:22-27` | 强制 ≥32 字节密钥, informational |
| 49 | LOW | Token 存储卫生 | `src/api/session.js:6, 17, 20-28, 35, 43-44, 79, 95-101` | 单一 `'token'` key 同时被 localStorage 和 sessionStorage 使用 |
| 50 | LOW | XSS (defensible) | `src/components/common/doc-insight/DocVerificationWorkbench.vue:46` + `markdownSanitizer.js` | `v-html` 经过 `renderSafeMarkdown`, 缺回归测试 |
| 51 | LOW | CORS / 客户端校验 | `src/api/client.js:37` | `withCredentials: true` 缺客户端 origin sanity check |
| 52 | LOW | Tabnabbing | `Account.vue:74`, `CABorrowDialog.vue:79`, `SiteDetail.vue:163`, `SOPDetail.vue:36, 43`, `AssetCard.vue:16` | `target="_blank"` 缺 `rel="noopener noreferrer"` |
| 53 | LOW | 上传校验 | `src/views/Project/stages/components/ProjectDocumentTable.vue:28, 56-68` | `<input type="file">` 无 client-side MIME / size cap |
| 54 | LOW | 审计 UX | `src/views/Resource/Account.vue:373-376` | 密码可见性 toggle 无 audit 上报 |
| 55 | LOW | 浏览器安全头 | `vite.config.js` + `index.html` | 无 CSP / `X-Frame-Options` meta |
| 56 | LOW | XSS (defensible) | `src/views/Project/stages/ClosureStage.vue:184` | `el-tooltip raw-content` 渲染静态字符串 |
| 57 | LOW | 用户枚举 (注册) | `backend/src/main/java/com/xiyu/bid/service/AuthService.java:70-77` | `/api/auth/register` 区分 username/email 已存在 (与 M33 同根, 独立跟踪) |

### LOW 修复概要

- **L34**: 类级 `@PreAuthorize("isAuthenticated()")`。
- **L35**: refresh / session 端点加 same-origin check; refresh cookie prod 强制 `SameSite=Strict`。
- **L36**: 改 `log.debug`, 仅配置错误时升级 `error`。
- **L37**: `JwtConfig` 只设字段, 让 `JwtUtil` 构造器抛异常。
- **L38**: 配置 `DelegatingSecurityContextAsyncTaskExecutor` 包装默认 executor。
- **L39**: 同 H3 修复 — 统一返回成功响应。
- **L40**: 公开注册端点仅返回"注册失败" (不区分原因); 内部 AdminUserService 精确错误可保留。
- **L41**: 同 H12 — `@Auditable` + 一次性 access URL + `Cache-Control: no-store`。
- **L42**: 路径级表达式与 `RoleProfileCatalog` 同步, 或 `.authenticated()` 兜底。
- **L43**: e2e profile 用单独 env 覆盖 secret, `JwtConfig` 长度校验兜底。
- **L44**: `escapeLike()` 转义 `%` / `_`; SQL 加 `ESCAPE '\\'`。
- **L45**: `@Scheduled(fixedDelay = 300000)` 调用 `cleanupExpired()`。
- **L46-48**: informational — 不需修复。
- **L49**: `setAccessToken` 写值前 `getBrowserStorages().forEach(s => s.removeItem(...))`。
- **L50**: 加 `__html` 单元测试 (`renderSafeMarkdown('<img src=x onerror=alert(1)>')` ⇒ empty); 标 security-critical。
- **L51**: 文档化依赖: 前端 `withCredentials: true` ⇒ 后端禁止 `*`, 必须 echo 精确 origin。
- **L52**: 所有 `target="_blank"` 加 `rel="noopener noreferrer"`; server-supplied URL 校验 scheme。
- **L53**: `beforeUpload` 钩子 + `ALLOWED_MIMES` + `MAX_FILE_SIZE_MB` 常量; centralize 到 composable。
- **L54**: toggle 包 `auditApi.logViewPassword(accountId)`。
- **L55**: `index.html` 加 CSP: `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' http: https:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`; `X-Frame-Options: DENY`。
- **L56**: 加 `// SECURITY: do not bind to user input` 注释, 或删 `raw-content` 改 Element Plus 自动转义。
- **L57**: 同 M33 — 不区分具体原因。

</details>

## 下一步建议

### 必须本 sprint 修 (HIGH — 15 项)

按风险×修复成本排序:

1. **H3** (邮箱枚举) — 改动小, 优先修。
2. **H6** (actuator/info) — 一行 yaml 即可禁用。
3. **H10** (`User.password` 加 `@JsonIgnore`) — 5 分钟修改。
4. **H4 / H5** (横向越权) — 加 `@PreAuthorize` + 强制 `assertCurrentUserCanAccessProject`。
5. **H7** (`/api/integration/**` 401 守卫) — filter 内 sendError。
6. **H11** (`InMemoryTokenRevocationService` `@ConditionalOnProperty` 保护)。
7. **H8** (`DashboardController` 加 `@PreAuthorize`)。
8. **H13** (JWT 迁 `HttpOnly` cookie) — 与 H14/H15 (XSS) 形成链式修复。
9. **H14 / H15** (XSS `v-html` 加 `DOMPurify`)。
10. **H12** (密码 DTO + `getPassword` 端点改造) — 后端剔除 + 一次性 access URL; 前端 `accounts.js:32` 同步剔除 `password`。
11. **H9** (`MarginService` JPA Specification 重构)。
12. **H2** (`LocalDevAccountInitializer` 加 `@ConditionalOnProperty` + env 注入)。

### 纳入下个 sprint (MED — 18 项)

按修复成本 / 价值密度:

1. **M29 / M30 / M31** (前端 `fetch()` 旁路) — 同类型, 一并扫一遍 `src/**/fetch(`, 改 `httpClient`。
2. **M26** (JWT `iss` / `aud`) — cross-lane, 优先级提升。
3. **M24** (JWT expiration 降到 1h)。
4. **M25** (`RedisTokenRevocationService` fail-closed)。
5. **M27** (`EmailService.dev-mode` 启动 assertion)。
6. **M20** (`RateLimitFilter` 覆盖写方法)。
7. **M32** (prod HTTPS 强制 + CSP `upgrade-insecure-requests`)。
8. **M17 / M18** (WeCom 类级 `@PreAuthorize` + `@Auditable` + rate limit)。
9. **M16** (`MarginController` `@AuthenticationPrincipal` 修复)。
10. **M19 / M22** (路径通配收紧 + 单元测试)。
11. **M21** (profile 串扰启动校验)。
12. **M28 / M33** (输入校验 + 密码 hint)。

### 可延后 (LOW — 24 项)

批量处理:
1. **信息泄露类**: L41/L42/L44 → 跟 MED 一起合 PR。
2. **配置卫生类**: L35/L37/L43 → 跟 H2 一起合 PR。
3. **前端卫生类**: L49/L50/L51/L52/L53/L54/L55/L56 → 跟 H13/H14/H15 一起合 PR, 加 `renderSafeMarkdown` 单元测试。
4. **positive findings**: L46/L47/L48 仅记录, 不需动作。
5. **审计 / SecurityContext**: L36/L38/L23/M23 → 合 PR "audit hardening"。

### 建议的修复任务分支

- `fix-api-security-high` — H1 ~ H15 (15 项 HIGH)
- `fix-api-security-med` — M16 ~ M33 (18 项 MED)
- `fix-api-security-low` — L34 ~ L57 (24 项 LOW, 批量收口)

> **分支策略**: 按 CLAUDE.md §5 SOP, 用 `scripts/agent-start-task.sh` 创建独立 worktree + 分支, 避免污染 `agent/claude-init`。每个 PR 完成后跑 `npm run agent:lock-release -- --all` 防 lock 阻塞后续 CI。

### ArchUnit 建议 (Lane 1 提供)

```java
@ArchTest
static final ArchRule controllers_must_have_security_annotation = methods()
    .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
    .and().arePublic()
    .should().beAnnotatedWith(PreAuthorize.class)
    .orShould().beDeclaredInClassesThat().areAnnotatedWith(PreAuthorize.class);
```

### 动态测试建议

| 工具 | 目标面 | 验证项 |
| --- | --- | --- |
| **ZAP active scan** | 登录 / 注销 / 密码重置流 | logout 后旧 token 立即失效 (H11/M25) |
| **ZAP fuzz** | `readerHTML` / `responseText` API (e.g. `bidAgentApi`, `casesApi.recommendCases`) | XSS payload 落地检查 (H14) |
| **ZAP scan** | `/actuator/*` | `/info`, `/env`, `/heapdump`, `/threaddump` 是否真 404 (H6) |
| **Playwright e2e fuzz** | `paymentDateStart=invalid` | 500 vs 400 行为 (M28) |
| **Playwright e2e** | STAFF 角色调用 `/api/analytics/cache/clear` | 200 vs 403 (H8) |
| **Playwright e2e** | `/api/integration/**` 无 X-API-Key | 必须 401 (H7) |
| **Burp authenticated API fuzz** | dev 9 个 STAFF / MANAGER 账号 | 越权调 ADMIN endpoint (H4/H5/H8 假设验证) |
| **Mock JWT 注入** | `/api/integration/**` 伪造 token | 必须 401 (H7) |
| **CSP / HttpOnly cookie 回归** | HIGH 修完后整体扫一遍 | 确认修复落地 |

### 依赖 CVE 全量

- 当前未跑 `npm audit` / `mvn dependency-check:check`。建议在 `fix-api-security-high` PR 中加 `mvn org.owasp:dependency-check-maven:check` + `npm audit --omit=dev` 到 CI。
- 重点关注: `jjwt 0.12.x` (jwt), `spring-boot-starter-security` (security), `axios` (CORS / CSRF), `marked` + `dompurify` (XSS sanitizer lib 自身版本)。

## 未在静态下判定项

> 以下项静态审计无法判定, 需动态测试 / 真实 exploit 验证 / 运行时配置确认。

1. **跨服务时序漏洞**: 多服务共享 `JWT_SECRET` 时 token 跨服务重放 (M26) — 静态只看到缺 `iss`/`aud`, 实际是否多服务 / 多环境共用 secret 需运维侧确认。
2. **真实 exploit 验证**: H4/H5/H8 的 IDOR 假设 (STAFF 能否真的读任意 project 详情) 需在 dev 环境 STAFF 登录后用 Burp / ZAP 调对应 endpoint 确认 service 层是否真的缺 owner check (静态只能看到 controller 缺 `@PreAuthorize`)。
3. **依赖 CVE 全量**: `npm audit` / `mvn dependency-check` 未在本次扫描覆盖。
4. **OAuth `state` 校验**: WeCom OAuth `oAuthStateService` 仅服务端 store, 多 tab 重放风险 (M17) — 需运行时验证是否真存在多 tab 重放路径。
5. **`/api/integration/**` controller 审计**: H7 修复建议 "验证所有 `/api/integration/**` controller 是否带 `@PreAuthorize`" — 需静态 grep `find` 列出全部 controller 后逐个审查, 本次 lane 1 仅指出 SecurityConfig 兜底缺。
6. **`/api/external/**` / `/api/systems/external/**` controller 清单**: 同 M19, 需静态列出 controller 后确认是否真不需要登录。
7. **refresh token cookie 同站 / 跨站行为**: M35 (CSRF) 需运行时验证浏览器实际是否发送 `SameSite=Lax` 的 refresh cookie。
8. **CSP 实际生效情况**: L55 修复建议需在部署后用 `securityheaders.com` / `curl -I` 验证浏览器实际收到的 CSP 头 (可能因 reverse proxy / CDN 注入而不同)。
9. **JWT `iss` / `aud` 在已有 token 上的兼容性**: M26 修复部署后是否会让已签发 token 全部失效, 影响范围需评估。
10. **EmailService `dev-mode=true` 在生产 profile 启动时是否真的抛错 (M27 建议)**: 需在部署后用 prod profile + `app.email.dev-mode=true` 启动一次确认拒绝。
11. **`fetch()` 旁路 401 refresh 的真实影响面 (M29/M30/M31)**: 需 e2e 在 token 即将过期时跑这些页面, 确认是否用户感知失败。
12. **`target="_blank` tabnabbing 的实际可利用性 (L52)**: 现代浏览器部分默认 `noopener`, 需运行时实测跨浏览器。

---

**报告生成**: 2026-06-13 · 合成器基于 3 lane 输入 (`lane-1-auth.md`, `lane-2-data.md`, `lane-3-frontend.md`) · 静态审计, 未运行真实 exploit。