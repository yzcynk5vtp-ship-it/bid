// Input: AdaptiveFormPage.vue component
// Output: unit tests for AdaptiveFormPage
// Pos: src/components/common/ - Component test
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

const getActiveFormDefinition = vi.fn()
const getConditionRules = vi.fn()
const getVisibilityRules = vi.fn()

vi.mock('@/api/modules/workflowForm.js', () => ({
  formDefinitionApi: {
    getActiveFormDefinition: (...args) => getActiveFormDefinition(...args),
    getConditionRules: (...args) => getConditionRules(...args),
    getVisibilityRules: (...args) => getVisibilityRules(...args)
  }
}))

import AdaptiveFormPage from './AdaptiveFormPage.vue'

function mountPage(props = {}, slots = {}) {
  setActivePinia(createPinia())
  return mount(AdaptiveFormPage, {
    props: { scope: 'test.scope', ...props },
    slots: {
      'fallback-form': '<div data-testid="fallback">fallback-form</div>',
      ...slots
    },
    global: {
      plugins: [createPinia()],
      stubs: {
        'el-icon': { template: '<span><slot /></span>' },
        'el-alert': {
          props: ['type', 'title'],
          template: '<div data-testid="error-alert" :data-type="type">{{ title }}</div>'
        }
      }
    }
  })
}

describe('AdaptiveFormPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getConditionRules.mockResolvedValue({ data: [] })
    getVisibilityRules.mockResolvedValue({ data: [] })
  })

  it('renders fallback form silently when form definition returns 404', async () => {
    const error = new Error('Not Found')
    error.response = { status: 404, data: { msg: '资源不存在' } }
    getActiveFormDefinition.mockRejectedValue(error)

    const wrapper = mountPage()
    await flushPromises()
    await nextTick()

    expect(wrapper.find('[data-testid="fallback"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="error-alert"]').exists()).toBe(false)
  })

  it('shows error banner for non-404 errors', async () => {
    const error = new Error('Server Error')
    error.response = { status: 500, data: { msg: '服务器内部错误' } }
    getActiveFormDefinition.mockRejectedValue(error)

    const wrapper = mountPage()
    await flushPromises()
    await nextTick()

    expect(wrapper.find('[data-testid="fallback"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="error-alert"]').exists()).toBe(true)
  })
})
