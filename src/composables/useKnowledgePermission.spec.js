import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useKnowledgePermission } from './useKnowledgePermission'

// 可变 mock store，测试用例可动态修改 menuPermissions
const mockUserStore = {
  userRole: 'admin',
  currentUser: { role: 'admin' },
  menuPermissions: ['all'],
}

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => mockUserStore,
}))

describe('useKnowledgePermission', () => {
  beforeEach(() => {
    // 重置为 admin 全权限
    mockUserStore.menuPermissions = ['all']
    mockUserStore.userRole = 'admin'
  })

  it('admin with all permissions can manage all knowledge modules', () => {
    const { canManage, canManagePerformance, canManageWarehouse, canManagePersonnel, canAdminAlert } =
      useKnowledgePermission()
    expect(canManage.value).toBe(true)
    expect(canManagePerformance.value).toBe(true)
    expect(canManageWarehouse.value).toBe(true)
    expect(canManagePersonnel.value).toBe(true)
    expect(canAdminAlert.value).toBe(true)
  })

  it('bid-Team with performance.manage / warehouse.manage / personnel.manage can manage all three modules (CO-438 回归)', () => {
    mockUserStore.menuPermissions = [
      'performance.manage',
      'warehouse.manage',
      'personnel.manage',
    ]
    const { canManagePerformance, canManageWarehouse, canManagePersonnel } =
      useKnowledgePermission()
    expect(canManagePerformance.value).toBe(true)
    expect(canManageWarehouse.value).toBe(true)
    expect(canManagePersonnel.value).toBe(true)
  })

  it('bid-administration without knowledge module permissions cannot manage any of the three modules', () => {
    mockUserStore.menuPermissions = ['certificate.manage', 'qualification.view']
    const { canManagePerformance, canManageWarehouse, canManagePersonnel } =
      useKnowledgePermission()
    expect(canManagePerformance.value).toBe(false)
    expect(canManageWarehouse.value).toBe(false)
    expect(canManagePersonnel.value).toBe(false)
  })

  it('user with only performance.manage can manage performance but not warehouse/personnel', () => {
    mockUserStore.menuPermissions = ['performance.manage']
    const { canManagePerformance, canManageWarehouse, canManagePersonnel } =
      useKnowledgePermission()
    expect(canManagePerformance.value).toBe(true)
    expect(canManageWarehouse.value).toBe(false)
    expect(canManagePersonnel.value).toBe(false)
  })

  it('empty menuPermissions denies all', () => {
    mockUserStore.menuPermissions = []
    const { canManage, canManagePerformance, canManageWarehouse, canManagePersonnel } =
      useKnowledgePermission()
    expect(canManage.value).toBe(false)
    expect(canManagePerformance.value).toBe(false)
    expect(canManageWarehouse.value).toBe(false)
    expect(canManagePersonnel.value).toBe(false)
  })
})
