import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BatchAttachResultDialog from './BatchAttachResultDialog.vue'

describe('BatchAttachResultDialog', () => {
  const stubs = {
    'el-dialog': { template: '<div class="el-dialog"><slot /><slot name="footer" /></div>' },
    'el-table': { template: '<div class="el-table"><slot /></div>' },
    'el-table-column': { template: '<span />' },
    'el-button': { template: '<button class="el-button"><slot /></button>' },
    'el-icon': { template: '<span class="el-icon"><slot /></span>' }
  }

  it('should render stat cards with correct numbers', () => {
    const wrapper = mount(BatchAttachResultDialog, {
      props: {
        modelValue: true,
        data: { total: 8, success: 5, failed: 3, matched: [], unmatched: [] }
      },
      global: { stubs }
    })
    const numbers = wrapper.findAll('.stat-number')
    expect(numbers[0].text()).toBe('8')
    expect(numbers[1].text()).toBe('5')
    expect(numbers[2].text()).toBe('3')
  })

  it('should show matched section when there are matched items', () => {
    const wrapper = mount(BatchAttachResultDialog, {
      props: {
        modelValue: true,
        data: {
          total: 3, success: 2, failed: 1,
          matched: [
            { fileName: 'QUAL_C001_1_a.pdf', certificateNo: 'C001', qualificationName: 'ISO' }
          ],
          unmatched: []
        }
      },
      global: { stubs }
    })
    expect(wrapper.find('.matched-section').exists()).toBe(true)
    expect(wrapper.find('.unmatched-section').exists()).toBe(false)
  })

  it('should show unmatched section when there are unmatched items', () => {
    const wrapper = mount(BatchAttachResultDialog, {
      props: {
        modelValue: true,
        data: {
          total: 2, success: 0, failed: 2,
          matched: [],
          unmatched: [
            { fileName: 'bad.pdf', reason: '格式不符' }
          ]
        }
      },
      global: { stubs }
    })
    expect(wrapper.find('.matched-section').exists()).toBe(false)
    expect(wrapper.find('.unmatched-section').exists()).toBe(true)
  })

  it('should emit closed event on dialog close', async () => {
    const wrapper = mount(BatchAttachResultDialog, {
      props: {
        modelValue: true,
        data: { total: 0, success: 0, failed: 0, matched: [], unmatched: [] }
      },
      global: { stubs }
    })
    await wrapper.find('.el-dialog').trigger('closed')
    expect(wrapper.emitted('closed')).toBeTruthy()
  })
})
