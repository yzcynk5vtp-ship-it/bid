import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { projectDetailKey } from '@/composables/projectDetail/context.js'
import ProjectTransferDialog from './ProjectTransferDialog.vue'

// Dialog 通过 inject(projectDetailKey) 拿到 ctx，故用 provide 注入 mock ctx
function createMockCtx(overrides = {}) {
  return {
    transferDialogVisible: { value: true },
    transferring: { value: false },
    transferForm: { newOwnerUserId: null, reason: '' },
    excludeOwnerIds: { value: [7246] },
    project: { id: 135, name: '测试项目', projectLeaderName: '陈梦瑶' },
    openTransfer: vi.fn(),
    closeTransfer: vi.fn(),
    handleTransferConfirm: vi.fn(),
    ...overrides,
  }
}

function mountDialog(ctx = createMockCtx()) {
  return mount(ProjectTransferDialog, {
    global: {
      provide: {
        [projectDetailKey]: ctx,
      },
      stubs: {
        ElDialog: {
          props: ['modelValue'],
          template: '<section><slot /><slot name="footer" /></section>',
        },
        ElForm: { template: '<form><slot /></form>' },
        ElFormItem: { template: '<div><slot /></div>' },
        ElInput: {
          props: ['modelValue', 'rows', 'maxlength'],
          emits: ['update:modelValue'],
          template: '<textarea data-test="reason-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        },
        ElButton: {
          props: ['loading', 'type'],
          template: '<button data-test="btn" :data-loading="String(loading)" :data-type="type || \'default\'" @click="$emit(\'click\')"><slot /></button>',
        },
        UserPicker: {
          props: ['modelValue', 'mode', 'placeholder', 'excludeIds'],
          emits: ['update:modelValue'],
          template: '<div data-test="user-picker" :data-exclude="String(excludeIds)" />',
        },
      },
    },
  })
}

describe('ProjectTransferDialog', () => {
  it('dialog 可见时渲染项目名称和当前负责人', () => {
    const wrapper = mountDialog()
    expect(wrapper.text()).toContain('测试项目')
    expect(wrapper.text()).toContain('陈梦瑶')
  })

  it('UserPicker 接收 excludeOwnerIds 防止转给当前负责人', () => {
    const wrapper = mountDialog()
    expect(wrapper.find('[data-test="user-picker"]').attributes('data-exclude')).toBe('7246')
  })

  it('点击「确认转移」按钮触发 handleTransferConfirm', async () => {
    const ctx = createMockCtx()
    const wrapper = mountDialog(ctx)
    const buttons = wrapper.findAll('[data-test="btn"]')
    const confirmBtn = buttons.find((b) => b.text().includes('确认转移'))
    await confirmBtn.trigger('click')
    expect(ctx.handleTransferConfirm).toHaveBeenCalled()
  })

  it('点击「取消」按钮触发 closeTransfer', async () => {
    const ctx = createMockCtx()
    const wrapper = mountDialog(ctx)
    const buttons = wrapper.findAll('[data-test="btn"]')
    const cancelBtn = buttons.find((b) => b.text().includes('取消'))
    await cancelBtn.trigger('click')
    expect(ctx.closeTransfer).toHaveBeenCalled()
  })

  it('transferring=true 时确认按钮显示 loading 状态', () => {
    const ctx = createMockCtx({ transferring: { value: true } })
    const wrapper = mountDialog(ctx)
    const buttons = wrapper.findAll('[data-test="btn"]')
    const confirmBtn = buttons.find((b) => b.text().includes('确认转移'))
    expect(confirmBtn.attributes('data-loading')).toBe('true')
  })
})
