import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// CO-408: 结果确认阶段 load 时根据 evidenceFileIds 回填 evidenceFiles
const mockCurrentUser = { id: 42, role: '/bidAdmin' }

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ({
    get userRole() { return mockCurrentUser.role },
    currentUser: mockCurrentUser,
    token: 'fake-token',
  }),
}))

vi.mock('@/api/config.js', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, getApiUrl: (path) => `http://test${path}` }
})

const getDocumentsMock = vi.fn()
vi.mock('@/api/modules/projectDocuments.js', () => ({
  getDocuments: (...args) => getDocumentsMock(...args),
}))

const getResultMock = vi.fn()
vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: {
    getResult: (...args) => getResultMock(...args),
    registerResult: vi.fn(),
  },
}))

vi.mock('@/constants/projectStages.js', () => ({ getResultConfirmNextTab: () => 'RETROSPECTIVE' }))
vi.mock('element-plus', () => ({ ElMessage: { info: vi.fn(), warning: vi.fn(), error: vi.fn(), success: vi.fn() } }))
vi.mock('@element-plus/icons-vue', () => ({ Delete: {}, Plus: {}, UploadFilled: {} }))

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
  ElInput: { props: ['modelValue', 'disabled'], template: '<input />' },
  ElTable: { template: '<div />' },
  ElTableColumn: { template: '<div />' },
}

async function mountResultStage(props = {}) {
  const { default: ResultConfirmStage } = await import('./ResultConfirmStage.vue')
  const wrapper = mount(ResultConfirmStage, { props: { projectId: 1, ...props }, global: { stubs } })
  await flushPromises()
  return wrapper
}

describe('ResultConfirmStage CO-408 回填凭证文件名', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDocumentsMock.mockReset()
    getResultMock.mockReset()
  })

  it('load 时根据 evidenceFileIds 拉取项目文档并回填 evidenceFiles', async () => {
    getResultMock.mockImplementation(() => Promise.resolve({
      data: { resultType: 'WON', evidenceFileIds: [3001, 3002], competitors: [] },
    }))
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [
        { id: 3001, name: '中标通知书.pdf' },
        { id: 3002, name: '合同副本.pdf' },
        { id: 3003, name: '无关文件.docx' },
      ],
    }))

    const wrapper = await mountResultStage()

    expect(getResultMock).toHaveBeenCalledWith(1)
    expect(getDocumentsMock).toHaveBeenCalledWith(1)
    const upload = wrapper.findComponent({ name: 'ElUpload' })
    expect(upload.props('fileList')).toHaveLength(2)
    expect(upload.props('fileList')[0].name).toBe('中标通知书.pdf')
    expect(upload.props('fileList')[0].response.data.id).toBe(3001)
    expect(upload.props('fileList')[1].name).toBe('合同副本.pdf')
    const names = wrapper.findAll('.mock-file-name').map(n => n.text())
    expect(names).toEqual(['中标通知书.pdf', '合同副本.pdf'])
  })

  it('evidenceFileIds 为空时不回填 evidenceFiles', async () => {
    getResultMock.mockImplementation(() => Promise.resolve({ data: { resultType: 'WON', evidenceFileIds: [] } }))
    const wrapper = await mountResultStage()
    expect(wrapper.findComponent({ name: 'ElUpload' }).props('fileList')).toHaveLength(0)
    expect(getDocumentsMock).not.toHaveBeenCalled()
  })
})
