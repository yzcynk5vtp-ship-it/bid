import { formatDisplayName } from './formatDisplayName.js'
import { firstNonBlank } from './firstNonBlank.js'

/**
 * Universal user option label formatter.
 * Tries common field names for name and employee number across different APIs.
 * Format: "姓名（工号）" — e.g. "张三（20260509）"
 */
export function formatUserLabel(user) {
  if (!user) return '—'
  const name = firstNonBlank(user.fullName, user.name, user.nickname)
  const employeeNumber = firstNonBlank(
    user.employeeNumber,
    user.employeeId,
    user.jobNumber,
    user.staffId,
    user.username
  )
  return formatDisplayName(name, employeeNumber)
}
