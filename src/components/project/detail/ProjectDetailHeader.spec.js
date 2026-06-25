import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

const context = {
  project: { name: '测试项目', status: 'approved' },
  canSubmit: false,
  canRecordResult: false,
  handleSubmitApproval: vi.fn(),
  handleRecordResult: vi.fn(),
  getStatusType: () => 'success',
  getStatusText: () => '已立项',
}

vi.mock('@/composables/projectDetail/context.js', async () => {
  const actual = await vi.importActual('@/composables/projectDetail/context.js')
  return {
    ...actual,
    useProjectDetailContext: () => context,
  }
})

const elementStubs = {
  ElPageHeader: {
    template: `
      <section class="el-page-header">
        <div class="el-page-header__content"><slot name="content" /></div>
        <div class="el-page-header__extra"><slot name="extra" /></div>
      </section>
    `,
  },
  ElButton: {
    props: ['icon', 'type', 'loading'],
    emits: ['click'],
    template: '<button class="el-button" @click="$emit(\'click\')"><slot /></button>',
  },
  ElIcon: { template: '<i><slot /></i>' },
  ElTag: { template: '<span class="el-tag"><slot /></span>' },
}

describe('ProjectDetailHeader', () => {
  it('hides the removed header action buttons (edit / result-closure / smart-assistant)', async () => {
    const { default: ProjectDetailHeader } = await import('./ProjectDetailHeader.vue')
    const wrapper = mount(ProjectDetailHeader, {
      global: {
        stubs: elementStubs,
      },
    })

    expect(wrapper.text()).not.toContain('编辑')
    expect(wrapper.text()).not.toContain('结果闭环')
    expect(wrapper.text()).not.toContain('智能助手')
  })
})
