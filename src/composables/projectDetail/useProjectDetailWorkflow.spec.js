import { describe, it, expect, vi } from 'vitest'
import { nextTick } from 'vue'
import { useProjectDetailWorkflow } from './useProjectDetailWorkflow.js'

// Mock stores
const mockUserStore = {
  userName: 'admin',
  currentUser: { id: 1, name: 'admin' }
}
const mockRoute = { params: { id: 'P001' } }
const mockProjectStore = {
  currentProject: null,
  fetchProject: vi.fn(),
  getProjectById: vi.fn()
}

function createMockContext(overrides = {}) {
  return {
    userStore: mockUserStore,
    route: mockRoute,
    projectStore: mockProjectStore,
    isApiProject: true,
    message: { warning: vi.fn(), success: vi.fn() },
    processDialogVisible: { value: false },
    reviewerDialogVisible: { value: false },
    ...overrides,
  }
}

vi.mock('@/api', () => ({
  projectApi: { getProject: vi.fn().mockResolvedValue(null) }
}))

describe('useProjectDetailWorkflow — UserPicker 统一后验证', () => {
  it('composable 可正常初始化', () => {
    const workflow = useProjectDetailWorkflow(createMockContext())
    expect(workflow.bidProcess).toBeDefined()
    expect(workflow.reviewers).toBeDefined()
    expect(workflow.draftForm).toBeDefined()
  })

  it('handleReviewerSelect 保存 UserPicker 回传的完整用户对象', () => {
    const workflow = useProjectDetailWorkflow(createMockContext())
    const user = { id: 'U999', name: '王评审', fullName: '王评审' }
    workflow.handleReviewerSelect(user)
    expect(workflow.selectedReviewerUser.value).toEqual(user)
  })

  it('handleConfirmAddReviewer 使用 UserPicker 选中的用户添加评审人', () => {
    const workflow = useProjectDetailWorkflow(createMockContext())
    workflow.handleAddReviewer()
    workflow.reviewerForm.value = { userId: 'U999', role: 'tech' }
    workflow.handleReviewerSelect({ id: 'U999', name: '王评审' })
    workflow.handleConfirmAddReviewer()

    const added = workflow.reviewers.value.find((r) => r.id === 'U999')
    expect(added).toBeDefined()
    expect(added.name).toBe('王评审')
    expect(added.role).toBe('tech')
    expect(added.status).toBe('pending')
  })
})
