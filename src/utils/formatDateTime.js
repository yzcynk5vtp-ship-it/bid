/**
 * 将 Date / 时间字符串格式化为后端 LocalDateTime 可解析的本地时间字符串：
 * yyyy-MM-ddTHH:mm:ss（无时区偏移，避免 UTC 日期错位）。
 */
export function formatLocalDateTime(value) {
  if (!value) return undefined
  const d = value instanceof Date ? value : new Date(value)
  if (isNaN(d.getTime())) return undefined
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * 将 Date / 日期字符串格式化为本地日期字符串：yyyy-MM-dd。
 */
export function formatLocalDate(value) {
  if (!value) return undefined
  const d = value instanceof Date ? value : new Date(value)
  if (isNaN(d.getTime())) return undefined
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
