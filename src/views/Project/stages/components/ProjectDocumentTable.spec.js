// Input: mocked project documents API + element-plus table stubs
// Output: regression spec covering the uploader column field binding
// Pos: src/views/Project/stages/components/ - ProjectDocumentTable component tests

import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const getDocumentsMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/modules/projects.js', () => ({
  projectsApi: {
    getDocuments: getDocumentsMock,
    uploadDocument: vi.fn(),
    deleteDocument: vi.fn(),
  },
}))

import ProjectDocumentTable from './ProjectDocumentTable.vue'

const globalStubs = {
  ElCard: { template: '<div class="el-card-stub"><slot /><slot name="header" /></div>' },
  ElButton: { props: ['size', 'type', 'link'], template: '<button class="el-button-stub"><slot /></button>' },
  ElTable: { props: ['data'], template: '<div class="el-table-stub"><slot /></div>' },
  ElTableColumn: { props: ['label', 'prop'], template: '<div class="el-table-column-stub" :data-prop="prop" :data-label="label" />' },
  ElMessage: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}

function mountTable(props = {}) {
  return mount(ProjectDocumentTable, {
    props: { projectId: 1, ...props },
    global: { stubs: globalStubs },
  })
}

describe('ProjectDocumentTable — uploader column field binding', () => {
  it('binds the 上传者 column to the backend DTO field "uploader"', async () => {
    getDocumentsMock.mockResolvedValue({
      success: true,
      data: [{ id: 1, name: 'test.docx', uploader: '张三', createdAt: '2026-06-17T10:00:00' }],
    })

    const wrapper = mountTable()
    await flushPromises()

    const columns = wrapper.findAll('.el-table-column-stub')
    const uploaderColumn = columns.find((c) => c.attributes('data-label') === '上传者')
    expect(uploaderColumn).toBeDefined()
    expect(uploaderColumn.attributes('data-prop')).toBe('uploader')
  })
})
