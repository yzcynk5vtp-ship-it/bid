/**
 * 下载工具函数
 *
 * 提供通用的文件下载能力，支持从 Content-Disposition 头解析文件名。
 */

/**
 * 从 Content-Disposition 头中解析文件名
 * @param {string} disposition - Content-Disposition 头的值
 * @param {string} fallbackName - 解析失败时的备用文件名
 * @returns {string} 解析到的文件名
 */
export function parseFilenameFromDisposition(disposition, fallbackName) {
  if (!disposition) return fallbackName
  // 优先匹配 RFC 5987 格式：filename*=UTF-8''encoded-name
  const rfc5987Match = disposition.match(/filename\*=UTF-8''(.+?)(?:;|$)/i)
  if (rfc5987Match) {
    try {
      return decodeURIComponent(rfc5987Match[1].trim())
    } catch {
      return rfc5987Match[1].trim()
    }
  }
  // 回退匹配普通格式：filename="name" 或 filename=name
  const simpleMatch = disposition.match(/filename="?([^";\n]+)"?/i)
  if (simpleMatch) {
    return simpleMatch[1].trim()
  }
  return fallbackName
}

/**
 * 通过 fetch + blob 方式下载文件，支持从响应头获取文件名
 *
 * 适用场景：需要从 Content-Disposition 头读取真实文件名的下载。
 * 不适用场景：大文件（>100MB）下载，因为会将整个文件加载到内存。
 *
 * @param {string} url - 下载地址
 * @param {string} fallbackName - 无法从响应头获取文件名时的备用名称
 * @returns {Promise<void>}
 */
export async function downloadWithFilename(url, fallbackName) {
  if (!url) return
  try {
    const resp = await fetch(url, { credentials: 'include' })
    if (!resp.ok) {
      window.open(url, '_blank')
      return
    }
    const disposition = resp.headers.get('Content-Disposition') || ''
    const filename = parseFilenameFromDisposition(disposition, fallbackName)
    const blob = await resp.blob()
    triggerBlobDownload(blob, filename)
  } catch {
    window.open(url, '_blank')
  }
}

/**
 * 通过 blob URL 触发浏览器下载
 *
 * 适用场景：已有 blob 数据，需要触发下载。
 *
 * @param {Blob} blob - 文件 blob 数据
 * @param {string} filename - 下载文件名
 */
export function triggerBlobDownload(blob, filename) {
  if (!blob || typeof window === 'undefined') return
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  // 延迟释放 blob URL，确保浏览器有足够时间开始下载
  setTimeout(() => URL.revokeObjectURL(url), 100)
}
