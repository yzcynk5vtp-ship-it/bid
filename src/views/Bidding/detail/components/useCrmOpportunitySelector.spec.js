import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const searchOpportunities = vi.fn()
const searchOpportunitiesByTender = vi.fn()

vi.mock('@/api/modules/crm.js', () => ({
  crmApi: {
    searchOpportunities,
    searchOpportunitiesByTender,
  },
}))

const elMessage = {
  info: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
  success: vi.fn(),
}

vi.mock('element-plus', () => ({
  ElMessage: elMessage,
}))

const { useCrmOpportunitySelector } = await import('./useCrmOpportunitySelector.js')

// ---------------------------------------------------------------------------
// Harness
// ---------------------------------------------------------------------------

function createHarness(props) {
  return defineComponent({
    template: '<div />',
    setup() {
      return useCrmOpportunitySelector(props, vi.fn())
    },
  })
}

// ===========================================================================
// Tests
// ===========================================================================

describe('useCrmOpportunitySelector', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初始打开且无手动搜索条件时，按招标主体（CRM groupName）查询同集团商机', async () => {
    searchOpportunities.mockResolvedValue({
      data: { list: [{ id: 1, name: '商机A' }], totalCount: 1 },
    })

    const props = {
      tenderer: '山东海化集团有限公司',
      registrationDeadline: '2026-06-03 23:59:00',
      bidOpeningTime: '2026-06-04 23:59:00',
      alreadyLinkedName: '',
    }
    const wrapper = mount(createHarness(props))
    await wrapper.vm.openSearch()
    await flushPromises()

    expect(searchOpportunities).toHaveBeenCalledWith({
      pageIndex: 1,
      pageSize: 10,
      body: { groupName: ['山东海化集团有限公司'] },
    })
    expect(searchOpportunitiesByTender).not.toHaveBeenCalled()
    expect(wrapper.vm.results).toEqual([{ id: 1, name: '商机A' }])
  })

  it('按招标主体查不到时，兜底拉取全量商机', async () => {
    searchOpportunities
      .mockResolvedValueOnce({ data: { list: [], totalCount: 0 } })
      .mockResolvedValueOnce({ data: { list: [{ id: 4, name: '兜底商机' }], totalCount: 1 } })

    const props = {
      tenderer: '山东海化集团有限公司',
      registrationDeadline: '2026-06-03 23:59:00',
      bidOpeningTime: '2026-06-04 23:59:00',
      alreadyLinkedName: '',
    }
    const wrapper = mount(createHarness(props))
    await wrapper.vm.openSearch()
    await flushPromises()

    expect(searchOpportunities).toHaveBeenCalledTimes(2)
    expect(searchOpportunities).toHaveBeenNthCalledWith(1, {
      pageIndex: 1,
      pageSize: 10,
      body: { groupName: ['山东海化集团有限公司'] },
    })
    expect(searchOpportunities).toHaveBeenNthCalledWith(2, {
      pageIndex: 1,
      pageSize: 10,
      body: { selectAll: true },
    })
    expect(wrapper.vm.results).toEqual([{ id: 4, name: '兜底商机' }])
  })

  it('用户输入商机名称后，使用通用分页查询', async () => {
    searchOpportunities.mockResolvedValue({
      data: { list: [{ id: 2, name: '搜索结果' }], totalCount: 1 },
    })

    const props = {
      tenderer: '山东海化集团有限公司',
      registrationDeadline: '2026-06-03 23:59:00',
      bidOpeningTime: '2026-06-04 23:59:00',
      alreadyLinkedName: '',
    }
    const wrapper = mount(createHarness(props))
    wrapper.vm.searchForm.name = '搜索'
    await wrapper.vm.doSearch(1)
    await flushPromises()

    expect(searchOpportunities).toHaveBeenCalledWith({
      pageIndex: 1,
      pageSize: 10,
      body: { name: '搜索' },
    })
    expect(searchOpportunitiesByTender).not.toHaveBeenCalled()
  })

  it('无蓝图条件时兜底拉取全量商机', async () => {
    searchOpportunities.mockResolvedValue({
      data: { list: [{ id: 3, name: '全量商机' }], totalCount: 1 },
    })

    const props = { tenderer: '', registrationDeadline: '', bidOpeningTime: '', alreadyLinkedName: '' }
    const wrapper = mount(createHarness(props))
    await wrapper.vm.openSearch()
    await flushPromises()

    expect(searchOpportunities).toHaveBeenCalledWith({
      pageIndex: 1,
      pageSize: 10,
      body: { selectAll: true },
    })
  })
})
