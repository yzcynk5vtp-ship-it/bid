import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockLogs = [
  {
    id: 1,
    time: '2026-06-24 10:00:00',
    operator: '郑蓉蓉（06234）',
    department: '投标部',
    role: '/bidadmin',
    actionType: 'create',
    module: 'bidding',
    target: '1',
    detail: '标讯已创建',
    ip: '127.0.0.1',
    status: 'success',
  },
  {
    id: 2,
    time: '2026-06-24 11:00:00',
    operator: '李明（06235）',
    department: '投标部',
    role: 'bid-teamleader',
    actionType: 'abandon',
    module: 'bidding',
    target: '1',
    detail: '放弃投标, 原因: 预算不足',
    ip: '127.0.0.1',
    status: 'success',
  },
]

vi.mock('@/api/modules/audit.js', () => ({
  auditApi: {
    getTenderAuditLogs: vi.fn(() => Promise.resolve({ data: { data: mockLogs } })),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: { info: vi.fn(), warning: vi.fn(), error: vi.fn(), success: vi.fn() },
}))

const stubs = {
  ElTimeline: { template: '<div class="el-timeline"><slot /></div>' },
  ElTimelineItem: {
    props: ['timestamp', 'type', 'placement'],
    template: '<div class="el-timeline-item"><span class="timeline-ts">{{ timestamp }}</span><slot /></div>',
  },
  ElTag: { props: ['type', 'size'], template: '<span class="el-tag"><slot /></span>' },
  ElSkeleton: { props: ['rows', 'animated'], template: '<div class="el-skeleton" />' },
  ElEmpty: { props: ['description'], template: '<div class="el-empty">{{ description }}</div>' },
  ElIcon: { template: '<span class="el-icon"><slot /></span>' },
}

async function mountComponent() {
  const { default: OperationLogTimeline } = await import('./OperationLogTimeline.vue')
  const wrapper = mount(OperationLogTimeline, {
    props: { tenderId: 1 },
    global: { stubs },
  })
  await flushPromises()
  await nextTick()
  return wrapper
}

describe('OperationLogTimeline', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('调用 auditApi.getTenderAuditLogs 获取日志', async () => {
    const { auditApi } = await import('@/api/modules/audit.js')
    await mountComponent()
    expect(auditApi.getTenderAuditLogs).toHaveBeenCalledWith(1)
  })

  it('展示操作人姓名（工号）', async () => {
    const wrapper = await mountComponent()
    const html = wrapper.html()
    expect(html).toContain('郑蓉蓉（06234）')
    expect(html).toContain('李明（06235）')
  })

  it('展示操作时间', async () => {
    const wrapper = await mountComponent()
    const html = wrapper.html()
    expect(html).toContain('2026-06-24 10:00:00')
    expect(html).toContain('2026-06-24 11:00:00')
  })

  it('展示操作内容', async () => {
    const wrapper = await mountComponent()
    const html = wrapper.html()
    expect(html).toContain('标讯已创建')
    expect(html).toContain('放弃投标, 原因: 预算不足')
  })

  it('无日志时显示空状态', async () => {
    const { auditApi } = await import('@/api/modules/audit.js')
    auditApi.getTenderAuditLogs.mockResolvedValueOnce({ data: { data: [] } })
    const wrapper = await mountComponent()
    expect(wrapper.find('.el-empty').exists()).toBe(true)
  })
})
