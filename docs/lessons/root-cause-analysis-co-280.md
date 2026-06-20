# CRM 附件下载 URL 跨域跳转主页 根因分析

> Issue: CO-280
> 日期: 2026-06-20
> 排查者: trae
> 修复 PR: `!890` (commit `2b3597981`)

---

## 现场还原

**症状素描**：CRM 用户（`crm-test.ehsy.com`）点击标讯附件链接时，浏览器不下载文件，而是跳转到 CRM 主页 `#/index/work-place`。浏览器地址栏显示的 URL 是：

```
https://crm-test.ehsy.com/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2FTENDER_INTAKE%2Fcreate-tender%2F8ed887d6d13c-%E5%A4%8D%E7%9B%98%E6%8A%A5%E5%91%8A.docx#/index/work-place
```

**边界划定**：
- 西域系统内点击同一附件下载正常 ✅
- CRM 推送的 `http(s)://` 外部 URL 附件下载正常 ✅（PR !886 已修复代理下载）
- 仅 CRM 系统渲染西域推送的 `doc-insight://` 附件 URL 时跳转主页 ❌

---

## 剥洋葱：逆向调用链

### Layer 1 — 入口/URL 生成层

`TenderIntegrationMapper.toDownloadUrl()` 负责把 `doc-insight://` 格式的内部 URL 转换为可下载的 URL：

```java
// 修复前：backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationMapper.java
public static String toDownloadUrl(String u) {
    return "/api/doc-insight/download?fileUrl=" + URLEncoder.encode(u, StandardCharsets.UTF_8);
}
```

**零号病人定位**：返回的是**相对路径** `/api/...`，没有域名前缀。

### Layer 2 — CRM 前端渲染层

CRM 系统拿到西域推送的附件 URL（相对路径 `/api/...`），在 CRM 前端 Vue SPA 中渲染为 `<a href="/api/...">`。

浏览器在 `crm-test.ehsy.com` 页面上点击该链接时：
1. 浏览器解析相对路径 `/api/...` → 拼接当前域名 → `https://crm-test.ehsy.com/api/...`
2. 请求发到 CRM 服务器（不是西域服务器 `winbid-test.ehsy.com`）
3. CRM 是 Vue SPA，所有未匹配的路径都返回 `index.html`（前端路由 404 兜底）
4. URL 末尾的 `#/index/work-place` 是 CRM 前端路由，浏览器跳转到 CRM 主页

### Layer 3 — 数据层

DB 直查 tender 312 附件记录：

```sql
SELECT id, tender_id, file_name, file_url FROM tender_attachments WHERE tender_id=312;
-- file_url 存的是 doc-insight://TENDER_INTAKE/create-tender/8ed887d6d13c-复盘报告.docx
```

`doc-insight://` 是西域内部协议，必须通过 `/api/doc-insight/download` 端点转换。但推给 CRM 时只给了相对路径，CRM 无法跨域访问西域的下载端点。

---

## 必然性解释

- 西域 `TenderIntegrationMapper.toDownloadUrl()` 返回相对路径 `/api/...`
- CRM 是独立系统（`crm-test.ehsy.com`），与西域（`winbid-test.ehsy.com`）不同源
- 浏览器在 CRM 页面点击相对路径链接，必然拼接 CRM 域名
- CRM 服务器没有 `/api/doc-insight/download` 端点，返回 `index.html`（SPA 兜底）
- 浏览器跳转到 CRM 主页 `#/index/work-place`

**状态变迁图**：

```
西域推送标讯 → 附件 URL = /api/doc-insight/download?fileUrl=doc-insight://...
  → CRM 前端渲染 <a href="/api/...">
  → 用户点击 → 浏览器请求 https://crm-test.ehsy.com/api/...
  → CRM 返回 index.html（SPA 兜底）
  → 浏览器跳转到 CRM 主页 #/index/work-place
  → 用户看到主页而非下载文件
```

---

## 验证与修复

### 修复 diff

**核心修改**：`TenderIntegrationMapper` 添加 `publicBaseUrl` 配置，返回完整 URL：

```java
// 修复后：backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationMapper.java

private static String publicBaseUrl;

@Value("${xiyu.public-base-url:}")
public void setPublicBaseUrl(String value) {
    TenderIntegrationMapper.publicBaseUrl = value;
}

public static String toDownloadUrl(String u) {
    if (u == null || u.isBlank()) return u;
    // 已是下载地址，避免 CO-283 双重嵌套
    if (u.startsWith("/api/doc-insight/download?")) {
        return prependPublicBaseUrl(u);
    }
    if (u.startsWith("doc-insight://")) {
        return prependPublicBaseUrl("/api/doc-insight/download?fileUrl="
            + URLEncoder.encode(u, StandardCharsets.UTF_8));
    }
    return u;
}

private static String prependPublicBaseUrl(String relative) {
    if (publicBaseUrl == null || publicBaseUrl.isBlank()) return relative;
    return publicBaseUrl + relative;
}
```

**配置注入**：

```yaml
# backend/src/main/resources/application-prod.yml
xiyu:
  public-base-url: ${XIYU_PUBLIC_BASE_URL:}
```

```bash
# /etc/xiyu-bid/backend.env
XIYU_PUBLIC_BASE_URL=https://winbid-test.ehsy.com
```

### 最小验证

1. 单元测试 12 个场景（`TenderIntegrationMapperToDownloadUrlTest`）：
   - 未配置 publicBaseUrl → 返回相对路径（同源部署兼容）
   - 配置 publicBaseUrl → 返回完整 URL
   - `doc-insight://` URL 被正确 URL 编码
   - 已是 `/api/...` 下载地址的不再二次包装（CO-283 幂等）
   - `http(s)://` 外部 URL 原样返回
   - null/空字符串原样返回
   - `toFullUrl()` 处理三种格式
   - `normalizeFileUrls()` 同时处理 `doc-insight://` 和 `/api/...`

2. 服务器端到端验证（tender 312）：
   - API 返回 `attachment[0].fileUrl: https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=...` ✅
   - 下载端点 HTTP 200，10237 字节，有效 .docx 文件 ✅

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | `TenderIntegrationMapper.toDownloadUrl()` 返回相对路径 | ✅ |
| 必然性已证明 | CRM 不同源 + 浏览器相对路径解析 → 拼接 CRM 域名 → SPA 兜底跳转主页 | ✅ |
| 最小验证已设计 | 12 个单测 + 服务器端到端下载验证 | ✅ |
| 修复 diff 已提供 | 见上文 | ✅ |
| 防复发测试已设计 | `TenderIntegrationMapperToDownloadUrlTest` | ✅ |

**Verdict**: ✅ **PASS**

---

## 为什么之前没有提前发现

1. **同源部署下相对路径正常工作**：西域系统内部前端通过 Nginx 反代 `/api/` 到后端，相对路径完全可用，问题只在跨系统推送时暴露。
2. **接口文档未明确 URL 格式要求**：`integration-tender-api-v3.1.md` 说 `fileUrl` 是"附件下载 URL（需为公网可匿名访问的地址）"，但没强调必须是完整 URL，开发时容易忽略跨域场景。
3. **PR !884 曾尝试修复但被错误回滚**：PR !884 方向正确（添加 publicBaseUrl），但因当时误判根因（以为是下载端点不支持外部 URL），被 PR !886 错误回滚。直到 CRM 实测仍失败才重新识别真正根因。

---

## 防复发规范

1. **跨系统推送的 URL 必须是完整地址**（含协议+域名），不能依赖相对路径。相对路径只在同源部署下可用。
2. **URL 生成方法应支持配置化域名前缀**：通过环境变量注入 `publicBaseUrl`，开发环境为空（同源），生产环境为完整域名。
3. **URL 转换方法应保持幂等性**：已是下载地址的不再二次包装（CO-283），避免双重嵌套。
4. **跨系统集成测试必须模拟真实 CRM 调用场景**：不能只测同源访问，必须验证 CRM 拿到的 URL 在 CRM 域名下能正确跳转到西域。

---

## 相关文档

- [TenderIntegrationMapper.java](../../backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationMapper.java) — `toDownloadUrl()` + `toFullUrl()` + `prependPublicBaseUrl()`
- [TenderIntegrationMapperToDownloadUrlTest.java](../../backend/src/test/java/com/xiyu/bid/integration/external/TenderIntegrationMapperToDownloadUrlTest.java) — 12 个测试场景
- [application-prod.yml](../../backend/src/main/resources/application-prod.yml) — `xiyu.public-base-url` 配置
- [integration-tender-api-v3.1.md](../integration-tender-api-v3.1.md) — CRM 推标讯接口契约
- [crm-integration-lessons.md](./crm-integration-lessons.md) §8 — 跨系统 URL 推送通用规则
