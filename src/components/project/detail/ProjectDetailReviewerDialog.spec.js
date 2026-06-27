import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import ProjectDetailReviewerDialog from './ProjectDetailReviewerDialog.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

function mountDialog(contextOverrides = {}) {
  const context = {
    reviewerDialogVisible: ref(true),
    reviewerForm: ref({ userId: '', role: '' }),
    handleReviewerSelect: vi.fn(),
    handleConfirmAddReviewer: vi.fn(),
    ...contextOverrides,
  }
  const wrapper = mount(ProjectDetailReviewerDialog, {
    global: {
      provide: {
        [projectDetailKey]: context,
      },
      stubs: {
        'el-dialog': { template: '<section><slot /><slot name="footer" /></section>' },
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { props: ['label', 'required'], template: '<label>{{ label }}<slot /></label>' },
        'el-select': {
          name: 'ElSelect',
          props: ['modelValue'],
          template: '<select><slot /></select>',
        },
        'el-option': { template: '<option><slot /></option>' },
        'el-button': { template: '<button><slot /></button>' },
        UserPicker: {
          name: 'UserPicker',
          props: ['modelValue', 'mode', 'placeholder'],
          emits: ['update:modelValue', 'select'],
          template: '<div class="user-picker-stub" />',
        },
      },
    },
  })
  return { wrapper, context }
}

describe('ProjectDetailReviewerDialog', () => {
  it('renders UserPicker for reviewer selection', () => {
    const { wrapper } = mountDialog()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.exists()).toBe(true)
    expect(picker.props('mode')).toBe('search')
    expect(picker.props('placeholder')).toBe('请选择评审人')
  })

  it('binds UserPicker v-model to reviewerForm.userId', async () => {
    const { wrapper, context } = mountDialog()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    await picker.vm.$emit('update:modelValue', 'U001')
    expect(context.reviewerForm.value.userId).toBe('U001')
  })

  it('calls handleReviewerSelect on UserPicker @select', async () => {
    const { wrapper, context } = mountDialog()
    const user = { id: 'U001', name: '王评审' }

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    await picker.vm.$emit('select', user)

    expect(context.handleReviewerSelect).toHaveBeenCalledWith(user)
  })
})
