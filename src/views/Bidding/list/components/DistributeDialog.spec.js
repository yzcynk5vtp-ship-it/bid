import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import DistributeDialog from './DistributeDialog.vue'

function mountDialog(props = {}, form = { type: 'manual', assignees: [] }) {
  return shallowMount(DistributeDialog, {
    props: {
      modelValue: true,
      form,
      selectedTenders: [],
      candidates: [],
      preview: [],
      loading: false,
      assignRules: [],
      'onUpdate:form': (val) => Object.assign(form, val),
      ...props,
    },
    global: {
      stubs: {
        'el-dialog': { template: '<section><slot /><slot name="footer" /></section>' },
        'el-tag': { template: '<span><slot /></span>' },
        'el-radio-group': {
          name: 'ElRadioGroup',
          props: ['modelValue'],
          template: '<div><slot /></div>',
        },
        'el-radio-button': {
          name: 'ElRadioButton',
          props: ['value'],
          template: '<button>{{ value }}</button>',
        },
        'el-select': {
          name: 'ElSelect',
          props: ['modelValue'],
          template: '<select><slot /></select>',
        },
        'el-option': { template: '<option><slot /></option>' },
        'el-empty': { template: '<div>empty</div>' },
        'el-input': {
          name: 'ElInput',
          props: ['modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        },
        'el-button': { template: '<button><slot /></button>' },
        UserPicker: {
          name: 'UserPicker',
          props: {
            modelValue: null,
            mode: String,
            context: String,
            multiple: Boolean,
            placeholder: String,
            loadOnMount: Boolean,
            initialOptions: Array,
            showDepartment: Boolean,
          },
          emits: ['update:modelValue', 'select'],
          template: '<div class="user-picker-stub" />',
        },
      },
    },
  })
}

describe('DistributeDialog', () => {
  it('renders UserPicker in manual mode', () => {
    const wrapper = mountDialog()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.exists()).toBe(true)
    expect(picker.props('mode')).toBe('candidates')
    expect(picker.props('context')).toBe('tender')
    expect(picker.props('multiple')).toBe(true)
    expect(picker.props('loadOnMount')).toBe(false)
  })

  it('passes candidates as initial options to UserPicker', () => {
    const candidates = [
      { id: 1, name: '张三', departmentName: '交付部' },
      { id: 2, name: '李四', departmentName: '销售部' },
    ]
    const wrapper = mountDialog({ candidates })

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.props('initialOptions')).toEqual(candidates)
  })

  it('enables department display on UserPicker', () => {
    const wrapper = mountDialog()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.props('showDepartment')).toBe(true)
  })

  it('does not render UserPicker in auto mode', async () => {
    const wrapper = mountDialog({}, { type: 'auto', assignees: [] })
    await nextTick()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.exists()).toBe(false)
  })
})
