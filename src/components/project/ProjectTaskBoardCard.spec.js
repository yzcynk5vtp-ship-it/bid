import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import ProjectTaskBoardCard from './ProjectTaskBoardCard.vue'

vi.mock('@/api/modules/taskStatusDict.js', () => ({
  taskStatusDictApi: {
    list: vi.fn().mockResolvedValue({
      success: true,
      data: [
        { code: 'TODO', name: '待办', category: 'OPEN', color: '#909399', sortOrder: 10, initial: true, terminal: false },
        { code: 'COMPLETED', name: '已完成', category: 'CLOSED', color: '#67c23a', sortOrder: 40, initial: false, terminal: true },
      ],
    }),
  },
}))

const baseStubs = {
  ElCard: {
    template: '<section><slot name="header" /><slot /></section>',
  },
  ElButton: {
    template: '<button v-bind="$attrs" type="button"><slot /></button>',
  },
  ElIcon: {
    template: '<span><slot /></span>',
  },
  TaskBoard: {
    name: 'TaskBoard',
    emits: ['task-click', 'status-change', 'generate-tasks', 'add-deliverable', 'remove-deliverable', 'submit-to-document'],
    template: '<div class="task-board-stub" />',
  },
  ElDrawer: {
    name: 'ElDrawer',
    props: ['modelValue', 'title', 'size', 'direction'],
    emits: ['update:modelValue'],
    template: '<aside v-if="modelValue" class="el-drawer-stub"><header>{{ title }}</header><slot /><footer><slot name="footer" /></footer></aside>',
  },
  TaskForm: {
    name: 'TaskForm',
    props: ['modelValue', 'mode'],
    emits: ['update:modelValue', 'submit', 'attachment-preview'],
    template: '<div class="task-form-stub" />',
    methods: {
      submit() {
        return { valid: true, data: { ...this.modelValue } }
      },
    },
  },
}

describe('ProjectTaskBoardCard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const userStore = useUserStore()
    userStore.currentUser = { id: 1, role: 'admin' }
  })
  it('exposes independent tender breakdown entry separately from score draft decomposition', async () => {
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [],
        projectId: 12,
      },
      global: {
        stubs: baseStubs,
      },
    })

    await wrapper.find('[data-test="tender-breakdown-button"]').trigger('click')
    await wrapper.find('[data-test="score-draft-button"]').trigger('click')

    expect(wrapper.emitted('tender-breakdown')).toHaveLength(1)
    expect(wrapper.emitted('score-draft-decompose')).toHaveLength(1)
  })

  it('keeps task board header actions visually separated by action purpose', () => {
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [],
        projectId: 12,
      },
      global: {
        stubs: baseStubs,
      },
    })

    expect(wrapper.find('[data-test="tender-breakdown-button"]').classes()).toEqual(
      expect.arrayContaining(['header-action', 'header-action--tender']),
    )
    expect(wrapper.find('[data-test="score-draft-button"]').classes()).toEqual(
      expect.arrayContaining(['header-action', 'header-action--score']),
    )
  })

  it('opens drawer in create mode when add-task button clicked', async () => {
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [],
        projectId: 12,
      },
      global: { stubs: baseStubs },
    })

    await wrapper.find('[data-test="add-task-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.vm.drawerVisible).toBe(true)
    expect(wrapper.vm.drawerMode).toBe('create')
    expect(wrapper.vm.editingTask).toEqual({})
    // Regression: the button must NOT emit the legacy `add-task` event,
    // which would trigger the parent's placeholder-creation path and
    // produce a duplicate task alongside the drawer save.
    expect(wrapper.emitted('add-task')).toBeFalsy()
  })

  it('opens drawer in view mode when TaskBoard emits task-click for an existing task', async () => {
    // Regression for IJSVX7 问题一：已创建任务表单仍可编辑
    // 修复前：drawerMode='edit' → 表单字段可改
    // 修复后：drawerMode='view' → TaskForm readonly，所有字段只读
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [{ id: 1, name: 'T', status: 'TODO' }],
        projectId: 12,
      },
      global: { stubs: baseStubs },
    })

    const board = wrapper.findComponent({ name: 'TaskBoard' })
    board.vm.$emit('task-click', { id: 1, name: 'T', status: 'TODO' })
    await flushPromises()

    expect(wrapper.vm.drawerVisible).toBe(true)
    expect(wrapper.vm.drawerMode).toBe('view')
    expect(wrapper.vm.editingTask.id).toBe(1)
    expect(wrapper.vm.editingTask.name).toBe('T')
  })

  it('hides the save action button when drawer opens in view mode', async () => {
    // Regression for IJSVX7：view mode 下"保存"按钮必须隐藏，
    // 否则分配人/执行人仍可通过底部按钮触发 mode='view' 下的提交。
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [{ id: 2, name: 'Locked', status: 'TODO' }],
        projectId: 12,
      },
      global: { stubs: baseStubs },
    })

    const board = wrapper.findComponent({ name: 'TaskBoard' })
    board.vm.$emit('task-click', { id: 2, name: 'Locked', status: 'TODO' })
    await flushPromises()

    expect(wrapper.vm.drawerMode).toBe('view')
    expect(wrapper.find('[data-test="task-drawer-save"]').exists()).toBe(false)
  })

  it('emits save-task with valid data when the save action fires', async () => {
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [],
        projectId: 12,
      },
      global: { stubs: baseStubs },
    })

    await wrapper.find('[data-test="add-task-button"]').trigger('click')
    await flushPromises()
    wrapper.vm.editingTask = { name: 'New' }
    await flushPromises()
    await wrapper.vm.handleSaveTask()

    const emitted = wrapper.emitted('save-task')
    expect(emitted).toBeTruthy()
    expect(emitted[0][0]).toEqual(expect.objectContaining({ mode: 'create', data: expect.objectContaining({ name: 'New' }) }))
    expect(wrapper.vm.drawerVisible).toBe(false)
  })

  it('forwards saved deliverable payload from TaskBoard without dropping the task id', async () => {
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [{ id: 31, name: 'T', status: 'TODO' }],
        projectId: 12,
      },
      global: { stubs: baseStubs },
    })

    wrapper.findComponent({ name: 'TaskBoard' }).vm.$emit('add-deliverable', 31, { id: 501, name: '附件.docx' })
    await flushPromises()

    expect(wrapper.emitted('add-deliverable')?.[0]).toEqual([31, { id: 501, name: '附件.docx' }])
  })

  it('opens saved task attachment preview from the drawer container', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
    const wrapper = mount(ProjectTaskBoardCard, {
      props: {
        canManageProjectTasks: true,
        tasks: [{ id: 31, name: 'T', status: 'TODO' }],
        projectId: 12,
      },
      global: { stubs: baseStubs },
    })

    wrapper.vm.openView({ id: 31, name: 'T', status: 'TODO' })
    await flushPromises()
    wrapper.findComponent({ name: 'TaskForm' }).vm.$emit('attachment-preview', {
      name: '任务附件.docx',
      url: '/api/projects/12/documents/801/download',
    })

    expect(openSpy).toHaveBeenCalledWith('/api/projects/12/documents/801/download', '_blank', 'noopener')
    openSpy.mockRestore()
  })
})

describe('CO-345: 任务看板顶部按钮按角色预过滤', () => {
  beforeEach(() => setActivePinia(createPinia()))

  function mountWithRole(role, props = {}) {
    const userStore = useUserStore()
    userStore.currentUser = { id: 1, role }
    return mount(ProjectTaskBoardCard, {
      props: {
        tasks: [],
        projectId: 12,
        canManageProjectTasks: true,
        ...props,
      },
      global: { stubs: baseStubs },
    })
  }

  it('admin 角色：3 个管理按钮都可见', () => {
    const w = mountWithRole('admin')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(true)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(true)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(true)
  })

  it('/bidAdmin 角色：3 个管理按钮都可见', () => {
    const w = mountWithRole('/bidAdmin')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(true)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(true)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(true)
  })

  it('bid-TeamLeader 角色：3 个管理按钮都可见', () => {
    const w = mountWithRole('bid-TeamLeader')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(true)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(true)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(true)
  })

  it('bid-projectLeader 角色：3 个管理按钮都可见', () => {
    const w = mountWithRole('bid-projectLeader')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(true)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(true)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(true)
  })

  it('bid-Team 角色：3 个管理按钮都可见', () => {
    const w = mountWithRole('bid-Team')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(true)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(true)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(true)
  })

  it('bid-otherDept 角色：3 个管理按钮都不可见', () => {
    const w = mountWithRole('bid-otherDept')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(false)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(false)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(false)
  })

  it('bid-administration 角色：3 个管理按钮都不可见', () => {
    const w = mountWithRole('bid-administration')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(false)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(false)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(false)
  })

  it('staff 角色：3 个管理按钮都不可见', () => {
    const w = mountWithRole('staff')
    expect(w.find('[data-test="tender-breakdown-button"]').exists()).toBe(false)
    expect(w.find('[data-test="score-draft-button"]').exists()).toBe(false)
    expect(w.find('[data-test="add-task-button"]').exists()).toBe(false)
  })

  it('重置任务按钮已删除（任何角色下都不出现）', () => {
    const adminWrapper = mountWithRole('admin')
    // 重置任务按钮没有 data-test，通过文本匹配
    const resetButtons = adminWrapper.findAll('button').filter(b => b.text().includes('重置任务'))
    expect(resetButtons).toHaveLength(0)
  })
})
