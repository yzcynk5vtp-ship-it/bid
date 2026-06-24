// Input: raw user-like DTOs from backend APIs
// Output: normalized option objects consumed by user pickers
// Pos: src/api/modules/ - User API normalization helper
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

export function normalizeUserOption(user = {}) {
  return {
    ...user,
    id: user.id ?? user.userId,
    departmentName: user.departmentName ?? user.deptName,
    employeeId: user.employeeId ?? user.employeeNumber,
  }
}
