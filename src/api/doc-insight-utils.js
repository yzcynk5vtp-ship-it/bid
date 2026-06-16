/**
 * doc-insight 模块工具函数
 * 处理内部 URI 与 HTTP URL 的转换
 */

/**
 * 将 doc-insight:// 内部 URI 转换为 HTTP 下载 URL。
 * 其他格式（http/https）直接返回原值。
 * @param {string} fileUrl - 原始文件 URL
 * @returns {string} 可在浏览器中打开的下载 URL
 */
export function toDownloadUrl(fileUrl) {
  if (!fileUrl) return ''
  if (fileUrl.startsWith('doc-insight://')) {
    return `/api/doc-insight/download?fileUrl=${encodeURIComponent(fileUrl)}`
  }
  return fileUrl
}
