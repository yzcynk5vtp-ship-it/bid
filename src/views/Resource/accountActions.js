/**
 * 解析账户列表行应按当前用户角色展示的操作项。
 *
 * 业务规则来源：CO-388
 * - admin / bidAdmin / bid-TeamLeader：编辑、登记归还、下架
 * - bid-Team 且是当前绑定联系人：编辑、登记归还、下架
 * - bid-Team 且不是当前绑定联系人：借用
 * - 其他角色（如 bid-projectLeader）：申请使用
 *
 * @param {string} roleCode 当前用户角色编码
 * @param {string|number} currentUserId 当前用户 ID
 * @param {{ contactPerson?: string|number }} account 账户行数据
 * @returns {string[]} 操作 key 数组：'edit' | 'return' | 'takeDown' | 'borrow' | 'apply'
 */
export function resolveAccountActions(roleCode, currentUserId, account) {
  const role = String(roleCode || '')
  const isManager = ['admin', '/bidAdmin', 'bid-TeamLeader'].includes(role)
  const isBidTeam = role === 'bid-Team'
  const isContactPerson = String(account?.contactPerson || '') === String(currentUserId || '')

  if (isManager) {
    return ['edit', 'return', 'takeDown']
  }

  if (isBidTeam) {
    return isContactPerson ? ['edit', 'return', 'takeDown'] : ['borrow']
  }

  return ['apply']
}
