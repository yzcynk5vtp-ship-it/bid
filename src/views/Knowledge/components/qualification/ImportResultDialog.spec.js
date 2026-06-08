import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ImportResultDialog from './ImportResultDialog.vue'

describe('ImportResultDialog', () => {
  const stubs = {
    'el-dialog': { template: '<div class="el-dialog"><slot /><slot name="footer" /></div>' },
    'el-table': { template: '<div class="el-table"><slot /></div>' },
    'el-table-column': { template: '<span />' },
    'el-button': { template: '<button class="el-button"><slot /></button>' },
    'el-icon': { template: '<span class="el-icon"><slot /></span>' }
  }

  it('should render stat cards with correct numbers', () => {
    const wrapper = mount(ImportResultDialog, {
      props: {
        modelValue: true,
        data: { total: 10, success: 7, failed: 3, errors: [] }
      },
      global: { stubs }
    })
    const numbers = wrapper.findAll('.stat-number')
    expect(numbers[0].text()).toBe('10')
    expect(numbers[1].text()).toBe('7')
    expect(numbers[2].text()).toBe('3')
  })

  it('should show all-success message when no errors', () => {
    const wrapper = mount(ImportResultDialog, {
      props: {
        modelValue: true,
        data: { total: 5, success: 5, failed: 0, errors: [] }
      },
      global: { stubs }
    })
    expect(wrapper.find('.all-success').exists()).toBe(true)
    expect(wrapper.find('.error-section').exists()).toBe(false)
  })

  it('should show error table when there are errors', () => {
    const wrapper = mount(ImportResultDialog, {
      props: {
        modelValue: true,
        data: {
          total: 5, success: 3, failed: 2,
          errors: [
            { row: 2, certificateNo: 'C001', reason: '名称不能为空' },
            { row: 5, certificateNo: 'C002', reason: '日期格式错误' }
          ]
        }
      },
      global: { stubs }
    })
    expect(wrapper.find('.error-section').exists()).toBe(true)
    expect(wrapper.find('.all-success').exists()).toBe(false)
  })

  it('should emit update:modelValue on close', async () => {
    const wrapper = mount(ImportResultDialog, {
      props: {
        modelValue: true,
        data: { total: 1, success: 1, failed: 0, errors: [] }
      },
      global: { stubs }
    })
    await wrapper.find('.el-dialog').trigger('closed')
    expect(wrapper.emitted('closed')).toBeTruthy()
  })

  it('should trigger download on button click', () => {
    const createObjectURL = vi.fn(() => 'blob:url')
    const revokeObjectURL = vi.fn()
    URL.createObjectURL = createObjectURL
    URL.revokeObjectURL = revokeObjectURL

    const wrapper = mount(ImportResultDialog, {
      props: {
        modelValue: true,
        data: {
          total: 2, success: 1, failed: 1,
          errors: [{ row: 3, certificateNo: 'C001', reason: '错误' }]
        }
      },
      global: { stubs }
    })
    wrapper.find('.download-btn').trigger('click')
    expect(createObjectURL).toHaveBeenCalled()
  })
})
