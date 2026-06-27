import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// CO-367: 标书审核人不能选择自己
// 验证 DraftingStage.vue 的 reviewerExcludeIds computed 将当前登录用户排除出审核人候选列表
const mockCurrentUser = { id: 42, role: '/bidAdmin', menuPermissions: [] }

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ({
    get userRole() { return mockCurrentUser.role },
    hasPermission: (key) => mockCurrentUser.menuPermissions.includes(key),
    currentUser: mockCurrentUser,
    token: 'fake-token',
  }),
}))

vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getDrafting: vi.fn(() => Promise.resolve({ data: {} })),
    submitBidForReview: vi.fn(),
    approveBid: vi.fn(),
    rejectBid: vi.fn(),
    submitBid: vi.fn(),
  },
}))

vi.mock('@/api/config.js', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    getApiUrl: (path) => `http://test${path}`,
  }
})
vi.mock('@/constants/projectStages.js', () => ({ STAGE_TRANSITION_MAP: { DRAFTING: 'EVALUATING' } }))
vi.mock('element-plus', () => ({ ElMessage: { info: vi.fn(), warning: vi.fn(), error: vi.fn(), success: vi.fn() } }))
vi.mock('@element-plus/icons-vue', () => ({
  DocumentChecked: {}, MagicStick: {}, Search: {}, Trophy: {}, UploadFilled: {},
}))

// 提供 projectDetail context：项目参与者不包含当前用户 42
vi.mock('@/composables/projectDetail/context.js', () => ({
  useProjectDetailContext: () => ({
    project: { value: { managerId: 1, teamMembers: [2], primaryLeadUserId: 3, secondaryLeadUserId: 4 } },
    userStore: { currentUser: mockCurrentUser },
    bidAgent: {},
    bidDocQualityResult: { value: null },
    runBidDocumentQualityCheck: () => {},
  }),
}))

const stubs = {
  ProjectDocumentTable: { template: '<div />' },
  UserPicker: {
    name: 'UserPicker',
    props: ['excludeIds', 'modelValue', 'mode', 'initialOptions', 'placeholder', 'clearable'],
    template: '<div data-test="picker" />',
  },
  ElCard: { template: '<section><slot name="header" /><slot /></section>' },
  ElUpload: { template: '<div />' },
  ElButton: { props: ['loading', 'disabled'], template: '<button><slot /></button>' },
  ElAlert: { template: '<div />' },
  ElDialog: { template: '<div />' },
  ElInput: { template: '<input />' },
  ElCheckbox: { template: '<input type="checkbox" />' },
  AiRecommendDrawer: { template: '<div />' },
  PerformanceRecommendDrawer: { template: '<div />' },
  QualityCheckDialog: { template: '<div />' },
}

async function mountDraftingStage() {
  const { default: DraftingStage } = await import('./DraftingStage.vue')
  const wrapper = mount(DraftingStage, { props: { projectId: 1 }, global: { stubs } })
  await nextTick()
  await nextTick()
  return wrapper
}

describe('DraftingStage reviewerExcludeIds - CO-367', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('排除当前登录用户，避免选择自己作为标书审核人', async () => {
    const wrapper = await mountDraftingStage()
    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.exists()).toBe(true)
    const excludeIds = picker.props('excludeIds')
    // 当前用户 ID 42 必须在排除列表中
    expect(excludeIds).toContain(42)
  })

  it('同时排除项目参与者（项目经理/团队成员/主副负责人）', async () => {
    const wrapper = await mountDraftingStage()
    const picker = wrapper.findComponent({ name: 'UserPicker' })
    const excludeIds = picker.props('excludeIds')
    // 项目参与者：1, 2, 3, 4 + 当前用户 42
    expect(excludeIds).toEqual(expect.arrayContaining([1, 2, 3, 4, 42]))
    expect(excludeIds.length).toBe(5)
  })
})
