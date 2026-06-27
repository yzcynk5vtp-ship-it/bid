import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import ProjectDetailWorkflowDialogs from './ProjectDetailWorkflowDialogs.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

function mountComponent(contextOverrides = {}) {
  const context = {
    userStore: { userName: '当前用户' },
    processDialogVisible: ref(false),
    activeProcessTab: ref('draft'),
    draftForm: ref({ preparer: '', templateId: '', files: [], remark: '' }),
    templates: ref([]),
    reviewers: ref([]),
    handleSaveDraft: vi.fn(),
    handleReview: vi.fn(),
    handleAddReviewer: vi.fn(),
    handleCompleteReview: vi.fn(),
    canCompleteReview: vi.fn(),
    getReviewerRoleType: vi.fn(),
    getReviewerRoleText: vi.fn(),
    getReviewStatusType: vi.fn(),
    getReviewStatusText: vi.fn(),
    ...contextOverrides,
  }

  return mount(ProjectDetailWorkflowDialogs, {
    global: {
      provide: {
        [projectDetailKey]: context,
      },
      stubs: {
        'el-dialog': { template: '<section><slot /><slot name="footer" /></section>' },
        'el-tabs': { template: '<div><slot /></div>' },
        'el-tab-pane': { template: '<div><slot /></div>' },
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { template: '<div><slot /></div>' },
        'el-select': { template: '<div><slot /></div>' },
        'el-option': { template: '<div />' },
        'el-input': { template: '<input />' },
        'el-button': { template: '<button><slot /></button>' },
        'el-table': { template: '<div><slot /></div>' },
        'el-table-column': { template: '<div />' },
        'el-tag': { template: '<span><slot /></span>' },
        'ProjectDetailReviewerDialog': { template: '<div class="reviewer-dialog-stub" />' },
      },
    },
  })
}

describe('ProjectDetailWorkflowDialogs', () => {
  it('renders the process dialog and the reviewer dialog stub', () => {
    const wrapper = mountComponent()

    expect(wrapper.find('section').exists()).toBe(true)
    expect(wrapper.find('.reviewer-dialog-stub').exists()).toBe(true)
  })
})
