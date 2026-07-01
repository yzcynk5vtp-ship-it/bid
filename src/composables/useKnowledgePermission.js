import { computed } from 'vue'
import { useUserStore } from '@/stores/user.js'
import { hasAnyPermission } from '@/utils/permission'

/**
 * 知识库模块权限组合式函数
 *
 * 基于 menuPermissions 判断，对齐后端 @PreAuthorize 权限点：
 * - performance.manage → 业绩管理
 * - warehouse.manage   → 仓库信息
 * - personnel.manage   → 人员证书
 *
 * 后端 RoleProfileCatalog 给 bid-Team 显式授予上述三个权限点，
 * 前端按权限点判断可避免角色白名单漏放（CO-438 根因）。
 */
export function useKnowledgePermission() {
  const userStore = useUserStore()
  const permissions = computed(() => userStore.menuPermissions || [])

  // 业绩管理：performance.manage
  const canManagePerformance = computed(() =>
    hasAnyPermission(permissions.value, ['performance.manage'])
  )

  // 仓库信息：warehouse.manage
  const canManageWarehouse = computed(() =>
    hasAnyPermission(permissions.value, ['warehouse.manage'])
  )

  // 人员证书：personnel.manage
  const canManagePersonnel = computed(() =>
    hasAnyPermission(permissions.value, ['personnel.manage'])
  )

  // 兼容旧调用方：canManage 用于跨模块场景。
  // 注意：调用方应优先使用细分权限（canManagePerformance/canManageWarehouse/canManagePersonnel），
  // 仅当确实需要"任意知识库模块权限"语义时才使用 canManage。
  const canManage = canManagePerformance

  // 提醒配置：对齐后端 performance.manage 权限点（业绩提醒配置接口走 performance.manage）
  const canAdminAlert = canManagePerformance

  return {
    canManage,
    canManagePerformance,
    canManageWarehouse,
    canManagePersonnel,
    canAdminAlert,
  }
}
