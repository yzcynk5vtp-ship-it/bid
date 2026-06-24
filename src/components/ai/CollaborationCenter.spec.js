import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import CollaborationCenter from './CollaborationCenter.vue'

// Mock useUserStore
const mockUserStore = {
  currentUser: { id: 1, name: 'admin', roleCode: 'admin' },
  users: [],
  resolveUserIdByName: (name) => (name ? 1 : null),
  userName: 'admin'
}
vi.mock('@/stores/user', () => ({
  useUserStore: () => mockUserStore
}))

// Mock collaborationApi
vi.mock('@/api', () => ({
  collaborationApi: {
    getChapters: vi.fn().mockResolvedValue([]),
    getChangeHistory: vi.fn().mockResolvedValue([]),
    saveChapter: vi.fn().mockResolvedValue({}),
    remind: vi.fn().mockResolvedValue({})
  }
}))

// Mock ElMessage & ElMessageBox
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

describe('CollaborationCenter — UserPicker 迁移验证', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('组件可正常挂载（shallow mount）', () => {
    const wrapper = shallowMount(CollaborationCenter, {
      props: { modelValue: true, projectId: 'P001' },
      global: {
        stubs: {
          teleport: true,
          transition: true,
          'el-dialog': true,
          'el-tabs': true,
          'el-tab-pane': true,
          'el-table': true,
          'el-table-column': true,
          'el-button': true,
          'el-input': true,
          'el-tag': true,
          'el-tooltip': true,
          UserPicker: true
        }
      }
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('不再引用 userStore.users（死代码已移除）', () => {
    const wrapper = shallowMount(CollaborationCenter, {
      props: { modelValue: true, projectId: 'P001' },
      global: {
        stubs: {
          teleport: true,
          transition: true,
          'el-dialog': true,
          'el-tabs': true,
          'el-tab-pane': true,
          'el-table': true,
          'el-table-column': true,
          'el-button': true,
          'el-input': true,
          'el-tag': true,
          'el-tooltip': true,
          UserPicker: true
        }
      }
    })
    // users computed 已移除
    expect(wrapper.vm.users).toBeUndefined()
  })
})
