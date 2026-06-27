import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import AccountFormDialog from './AccountFormDialog.vue'
import httpClient from '@/api/client.js'

beforeEach(() => {
  setActivePinia(createPinia())
})

vi.mock('@/api/client.js', () => ({
  default: {
    get: vi.fn(),
  },
}))

function mountDialog(props = {}) {
  return mount(AccountFormDialog, {
    props: {
      modelValue: true,
      editRow: null,
      ...props,
    },
    global: {
      plugins: [createPinia()],
      stubs: {
        'el-dialog': { emits: ['open'], mounted() { this.$emit('open') }, template: '<section><slot /><slot name="footer" /></section>' },
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { props: ['label', 'required', 'error'], template: '<label>{{ label }}<slot /></label>' },
        'el-row': { template: '<div><slot /></div>' },
        'el-col': { template: '<div><slot /></div>' },
        'el-input': {
          name: 'ElInput',
          props: ['modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        },
        'el-select': {
          name: 'ElSelect',
          props: ['modelValue'],
          template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
        },
        'el-option': { template: '<option><slot /></option>' },
        'el-switch': {
          name: 'ElSwitch',
          props: ['modelValue'],
          template: '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />',
        },
        'el-button': { template: '<button><slot /></button>' },
        UserPicker: {
          name: 'UserPicker',
          props: ['modelValue', 'mode', 'placeholder', 'disabled', 'initialOptions', 'loadOnMount'],
          emits: ['update:modelValue', 'select'],
          template: '<div class="user-picker-stub" />',
        },
      },
    },
  })
}

describe('AccountFormDialog', () => {
  it('renders UserPicker for CA custodian selection', async () => {
    httpClient.get.mockResolvedValue({ data: [] })
    const wrapper = mountDialog()
    await flushPromises()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.exists()).toBe(true)
    expect(picker.props('mode')).toBe('candidates')
    expect(picker.props('loadOnMount')).toBe(false)
    expect(picker.props('placeholder')).toBe('请选择投标部门人员')
  })

  it('disables UserPicker when hasCa is false', async () => {
    httpClient.get.mockResolvedValue({ data: [] })
    const wrapper = mountDialog()
    await flushPromises()

    const switchEl = wrapper.findComponent({ name: 'ElSwitch' })
    await switchEl.vm.$emit('update:modelValue', false)
    await nextTick()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.props('disabled')).toBe(true)
  })

  it('passes bidding users from admin API as initial options', async () => {
    httpClient.get.mockResolvedValue({
      data: [
        { id: 1, fullName: '张三', username: 'zhangsan', employeeNumber: '20260509' },
        { id: 2, fullName: '李四', username: 'lisi', employeeNumber: '20260510' },
      ],
    })
    const wrapper = mountDialog()
    await flushPromises()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.props('initialOptions')).toEqual([
      { id: 1, name: '张三', username: 'zhangsan', employeeNumber: '20260509' },
      { id: 2, name: '李四', username: 'lisi', employeeNumber: '20260510' },
    ])
  })
})
