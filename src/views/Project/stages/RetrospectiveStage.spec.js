import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// CO-408: 复盘阶段 load 时根据 reportFileIds 回填 reportFiles
const mockCurrentUser = { id: 42, role: '/bidAdmin' }

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    get userRole() { return mockCurrentUser.role },
    currentUser: mockCurrentUser,
    token: 'fake-token',
  }),
}))
vi.mock('@/utils/permission', () => ({ isBidManager: (role) => role === '/bidAdmin' || role === 'admin' }))

vi.mock('@/api/config.js', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, getApiUrl: (path) => `http://test${path}` }
})

const getDocumentsMock = vi.fn()
vi.mock('@/api/modules/projectDocuments.js', () => ({
  getDocuments: (...args) => getDocumentsMock(...args),
}))

const getRetrospectiveMock = vi.fn()
vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getRetrospective: (...args) => getRetrospectiveMock(...args),
    submitRetrospective: vi.fn(),
  },
}))

vi.mock('./retrospectiveLossReasons.js', () => ({ lossReasonOptions: [] }))
vi.mock('element-plus', () => ({ ElMessage: { info: vi.fn(), warning: vi.fn(), error: vi.fn(), success: vi.fn() } }))
vi.mock('@element-plus/icons-vue', () => ({ UploadFilled: {} }))

const stubs = {
  ElCard: { template: '<section><slot name="header" /><slot /></section>' },
  ElUpload: {
    name: 'ElUpload',
    props: { fileList: { type: Array, default: () => [] }, disabled: Boolean },
    template: `<div class="mock-upload">
      <div v-for="(file, idx) in fileList" :key="file.uid || file.name || idx" class="mock-upload-row">
        <span class="mock-file-name">{{ file.name }}</span>
      </div>
    </div>`,
  },
  ElIcon: { template: '<span />' },
  ElButton: { props: ['loading', 'disabled'], template: '<button><slot /></button>' },
  ElForm: { template: '<div><slot /></div>' },
  ElFormItem: { template: '<div><slot /></div>' },
  ElInput: { props: ['modelValue', 'disabled'], template: '<input />' },
  ElDatePicker: { template: '<input />' },
  ElSelect: { template: '<div />' },
  ElOption: { template: '<div />' },
  ElTag: { template: '<span />' },
  ElCheckboxGroup: { template: '<div><slot /></div>' },
  ElCheckbox: { template: '<label />' },
  ElEmpty: { template: '<div />' },
}

async function mountRetrospectiveStage(props = {}) {
  const { default: RetrospectiveStage } = await import('./RetrospectiveStage.vue')
  const wrapper = mount(RetrospectiveStage, {
    props: { projectId: 1, resultType: 'WON', ...props },
    global: { stubs },
  })
  await flushPromises()
  return wrapper
}

describe('RetrospectiveStage CO-408 回填复盘报告文件名', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDocumentsMock.mockReset()
    getRetrospectiveMock.mockReset()
  })

  it('load 时根据 reportFileIds 拉取项目文档并回填 reportFiles', async () => {
    getRetrospectiveMock.mockImplementation(() => Promise.resolve({
      success: true,
      data: { meetingTime: '2026-06-29 10:00:00', reportFileIds: [3001, 3002], reviewStatus: null },
    }))
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [
        { id: 3001, name: '复盘报告.docx' },
        { id: 3002, name: '改进措施.pdf' },
        { id: 3003, name: '无关文件.docx' },
      ],
    }))

    const wrapper = await mountRetrospectiveStage()

    expect(getRetrospectiveMock).toHaveBeenCalledWith(1)
    expect(getDocumentsMock).toHaveBeenCalledWith(1)
    const upload = wrapper.findComponent({ name: 'ElUpload' })
    expect(upload.props('fileList')).toHaveLength(2)
    expect(upload.props('fileList')[0].name).toBe('复盘报告.docx')
    expect(upload.props('fileList')[0].response.data.id).toBe(3001)
    expect(upload.props('fileList')[1].name).toBe('改进措施.pdf')
    const names = wrapper.findAll('.mock-file-name').map(n => n.text())
    expect(names).toEqual(['复盘报告.docx', '改进措施.pdf'])
  })

  it('reportFileIds 为空时不回填 reportFiles', async () => {
    getRetrospectiveMock.mockImplementation(() => Promise.resolve({
      success: true, data: { meetingTime: '', reportFileIds: [] },
    }))
    const wrapper = await mountRetrospectiveStage()
    expect(wrapper.findComponent({ name: 'ElUpload' }).props('fileList')).toHaveLength(0)
    expect(getDocumentsMock).not.toHaveBeenCalled()
  })
})
