# CO-285 附件下载文件名显示为 "download" 根因分析

> 创建时间：2026-06-21
> 状态：已修复
> 相关 PR：#926, #929, #931

## 问题描述

标讯详情页附件下载时，文件名称显示为默认的 "download" 而非实际文件名（如 "招标公告及要求.pdf"）。

## 根因分析

### 第一次尝试（PR #926）：Content-Disposition 头编码问题

**假设**：中文文件名没有正确编码，浏览器无法解析。

**修复**：在 `DocInsightController.java` 中添加 RFC 6266 `filename*=UTF-8''...` 编码。

**结果**：❌ 无效。问题不在编码，而是浏览器根本读取不到 Content-Disposition 头。

### 第二次尝试（PR #929）：CORS 配置问题

**假设**：跨域场景下浏览器无法访问 Content-Disposition 头。

**修复**：在 `SecurityConfig.java` 中添加 `setExposedHeaders(["Content-Disposition", "Content-Length"])`。

**结果**：❌ 无效。CORS 配置只对 JavaScript 发起的请求有效，对 `<a>` 标签直接导航无效。

### 第三次尝试（PR #931）：前端下载方式问题

**假设**：前端使用 `<a>` 标签直接导航下载，浏览器会忽略 Content-Disposition 头。

**修复**：改用 `fetch` + `blob` + `link.download` 方式下载，从响应头中读取文件名。

**结果**：✅ 有效。这才是真正的根因。

## 根因总结

**根本原因**：浏览器对 `<a>` 标签直接导航的下载行为，会忽略 Content-Disposition 头，使用 URL 路径中的最后一段作为文件名。

对于 URL `/api/doc-insight/download?fileUrl=...`，浏览器会把 `download` 当作文件名（因为 URL 路径最后一段是 `download`）。

**为什么排查了 3 轮才定位**：
1. 第一轮只关注了后端响应头的编码，没有验证浏览器是否真的能读取到这个头
2. 第二轮只关注了 CORS 配置，没有理解 CORS 只对 JavaScript 请求有效
3. 第三轮才意识到问题在前端的下载方式，而不是后端的响应头

## 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 问题分析不深入，只看表面现象 | 修复前先用浏览器开发者工具验证实际的响应头和请求行为 | Bug 修复前必须先复现并验证实际行为，而不是只看代码推测 |
| 多次修复无效，没有及时调整方向 | 当第一次修复无效时，应该立即改变排查方向，而不是继续在同一个方向上深入 | 修复无效时，立即回到"问题是什么"重新分析，而不是继续修复 |
| 忽略了浏览器行为差异 | `<a>` 标签直接导航和 JavaScript fetch 请求的下载行为不同 | 涉及文件下载时，必须明确是哪种下载方式，并验证其行为 |
| 重复造轮子 | 项目中已有 4 处类似的下载工具函数，又新增了 1 处 | 新增工具函数前，先搜索项目中是否已有类似实现 |

## 操作规范（建议固化）

1. **Bug 修复前必须先复现**：用浏览器开发者工具（F12 → Network）查看实际的请求和响应，而不是只看代码推测。
2. **修复无效时立即调整方向**：如果第一次修复无效，不要继续在同一个方向上深入，而是回到问题本身重新分析。
3. **明确下载方式**：涉及文件下载时，必须明确是 `<a>` 标签导航、`window.open`、还是 `fetch+blob`，并验证其行为。
4. **新增工具函数前先搜索**：使用 `grep` 搜索项目中是否已有类似实现，避免重复造轮子。

## 验证命令

```bash
# 检查项目中是否还有其他重复的下载工具函数
grep -r "function.*download.*blob\|triggerBlobDownload\|downloadBlob" src/

# 检查 CORS 配置是否正确暴露了 Content-Disposition 头
curl -s -D- -o /dev/null -X OPTIONS "http://172.16.38.78:8080/api/doc-insight/download" \
  -H "Origin: http://172.16.38.78:8080" \
  -H "Access-Control-Request-Method: GET" | grep -i "access-control-expose"
```

## 相关文档

- `src/utils/download.js` — 提取的公共下载工具函数
- `src/utils/download.spec.js` — 单元测试
- `docs/lessons/lessons-learned.md` — 通用工程教训
