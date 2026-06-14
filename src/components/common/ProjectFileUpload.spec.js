// Input: ProjectFileUpload.vue 组件
// Output: H13 with-credentials 契约 mount 测试
// Pos: src/components/common/ — 与组件同目录
//
// H13 根治 (2026-06-14): access token 迁移到 HttpOnly cookie 后,
// 所有打到 /api/** 认证端点的 <el-upload> 必须声明 :with-credentials="true",
// 否则浏览器不会自动携带 access_token cookie → 上传被 401 拒绝。
// 本测试 mount 组件并断言其根 <el-upload> 收到 withCredentials=true (H13 安全契约)。
// element-plus / 图标 / 上传 API 全量 mock 以避免 SFC 重编译拖慢 test:unit (DynamicWorkflowForm.spec.js 同策略)。

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('@element-plus/icons-vue', () => ({
  Upload: { name: 'Upload', template: '<i />' },
  UploadFilled: { name: 'UploadFilled', template: '<i />' },
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), warning: vi.fn(), success: vi.fn() },
}))

vi.mock('@/api/upload.js', () => ({
  getUploadUrl: () => '/api/upload',
  getUploadHeaders: () => ({}),
}))

import ProjectFileUpload from './ProjectFileUpload.vue'

const elUploadStub = {
  name: 'ElUpload',
  props: ['withCredentials', 'action', 'headers', 'accept', 'multiple', 'limit', 'drag', 'disabled', 'fileList', 'autoUpload', 'showFileList'],
  template: '<div class="upload" />',
}

const elementStubs = {
  'el-upload': elUploadStub,
  'el-icon': { template: '<i />' },
  'el-button': { template: '<button><slot /></button>' },
}

describe('ProjectFileUpload (H13 cookie 契约)', () => {
  it('H13: el-upload 声明 :with-credentials="true" 以走 HttpOnly cookie', () => {
    const wrapper = mount(ProjectFileUpload, {
      props: { businessType: 'bid' },
      global: { stubs: elementStubs },
    })
    const upload = wrapper.findComponent({ name: 'ElUpload' })
    expect(upload.exists()).toBe(true)
    expect(upload.props('withCredentials')).toBe(true)
  })
})
