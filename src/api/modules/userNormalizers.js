// Input: raw user-like DTOs from backend APIs
// Output: normalized option objects consumed by user pickers
// Pos: src/api/modules/ - User API normalization helper
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { firstNonBlank } from '@/utils/firstNonBlank.js'

export function normalizeUserOption(user = {}) {
  const employeeNumber = firstNonBlank(
    user.employeeNumber,
    user.employeeId,
    user.jobNumber,
    user.staffId,
    user.username
  )

  return {
    ...user,
    id: user.id ?? user.userId,
    departmentName: user.departmentName ?? user.deptName,
    employeeNumber,
    employeeId: firstNonBlank(user.employeeId, employeeNumber),
  }
}
