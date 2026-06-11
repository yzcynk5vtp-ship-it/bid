/**
 * Global person name formatter for CO-167.
 * Format: "姓名（工号）" — e.g. "张三（20260509）"
 * Fallback: plain name if employeeNumber is empty/null.
 */
export function formatDisplayName(name, employeeNumber) {
  if (!name) return '—'
  if (!employeeNumber) return name
  return `${name}（${employeeNumber}）`
}
