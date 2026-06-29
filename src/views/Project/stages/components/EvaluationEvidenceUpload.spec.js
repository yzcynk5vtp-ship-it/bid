import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// CO-408: 评标文件再次进入页面时根据 existingDocIds 回填 fileList
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

vi.mock('@/api/modules/projectLifecycle.js', () => ({
  projectLifecycleApi: { attachEvaluationEvidence: vi.fn(() => Promise.resolve()) },
}))

vi.mock('element-plus', () => ({ ElMessage: { info: vi.fn(), warning: vi.fn(), error: vi.fn(), success: vi.fn() } }))
vi.mock('@element-plus/icons-vue', () => ({ UploadFilled: {} }))

const stubs = {
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
  ElButton: { props: ['disabled'], template: '<button><slot /></button>' },
}

async function mountUpload(props = {}) {
  const { default: EvaluationEvidenceUpload } = await import('./EvaluationEvidenceUpload.vue')
  const wrapper = mount(EvaluationEvidenceUpload, {
    props: { projectId: 1, existingDocIds: [], editable: true, ...props },
    global: { stubs },
  })
  await flushPromises()
  return wrapper
}

describe('EvaluationEvidenceUpload CO-408 回填评标文件名', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDocumentsMock.mockReset()
  })

  it('existingDocIds 非空时根据 ids 拉取项目文档并回填 fileList', async () => {
    // 项目下有 3 个文档，existingDocIds 指定 2 个为评标文件
    getDocumentsMock.mockImplementation(() => Promise.resolve({
      data: [
        { id: 3001, name: '开标一览表_v1.pdf' },
        { id: 3002, name: '评标记录.docx' },
        { id: 3003, name: '其他文件.pdf' },
      ],
    }))

    const wrapper = await mountUpload({ existingDocIds: [3001, 3002] })

    expect(getDocumentsMock).toHaveBeenCalledWith(1)
    const upload = wrapper.findComponent({ name: 'ElUpload' })
    expect(upload.props('fileList')).toHaveLength(2)
    expect(upload.props('fileList')[0].name).toBe('开标一览表_v1.pdf')
    expect(upload.props('fileList')[0].response.data.id).toBe(3001)
    expect(upload.props('fileList')[1].name).toBe('评标记录.docx')
    // 渲染层文件名可见
    const names = wrapper.findAll('.mock-file-name').map(n => n.text())
    expect(names).toEqual(['开标一览表_v1.pdf', '评标记录.docx'])
  })

  it('existingDocIds 为空时 fileList 清空', async () => {
    getDocumentsMock.mockImplementation(() => Promise.resolve({ data: [] }))
    const wrapper = await mountUpload({ existingDocIds: [] })
    expect(wrapper.findComponent({ name: 'ElUpload' }).props('fileList')).toHaveLength(0)
    // 不应调用 getDocuments（ids 为空时早返回）
    expect(getDocumentsMock).not.toHaveBeenCalled()
  })
})
