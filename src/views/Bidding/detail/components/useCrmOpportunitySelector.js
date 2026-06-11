// Input: props, emit
// Output: crm opportunity search state/actions
// Pos: src/views/Bidding/detail/components/ - CRM opportunity selector composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { crmApi } from '@/api/modules/crm.js'

export function useCrmOpportunitySelector(props, emit) {
  const showDialog = ref(false)
  const searching = ref(false)
  const loading = ref(false)
  const searchPerformed = ref(false)
  const results = ref([])
  const selectedId = ref(null)
  const selectedChance = ref(null)
  const totalCount = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(10)

  const searchForm = reactive({ name: '', code: '', projectStatus: [] })

  const linkedOpportunity = ref(
    props.alreadyLinkedName ? { name: props.alreadyLinkedName } : null
  )

  async function openSearch() {
    showDialog.value = true
    if (!searchPerformed.value) await doSearch(1)
  }

  async function doSearch(page) {
    if (page) currentPage.value = page
    searching.value = true
    try {
      const params = {
        pageIndex: currentPage.value,
        pageSize: pageSize.value,
        body: {},
      }
      if (searchForm.name) params.body.name = searchForm.name
      if (searchForm.code) params.body.code = searchForm.code
      if (searchForm.projectStatus.length > 0) params.body.projectStatus = searchForm.projectStatus
      // 查全部商机，前端按招标主体匹配
      params.body.selectAll = true

      const res = await crmApi.searchOpportunities(params)
      const data = res?.data
      let list = data?.list || []
      // 如果标讯有招标主体，在前端按 tenderSubject 模糊匹配
      if (props.tenderer && list.length > 0) {
        const keyword = props.tenderer.trim().toLowerCase()
        list = list.filter(item => item.tenderSubject && item.tenderSubject.toLowerCase().includes(keyword))
      }
      results.value = list
      totalCount.value = list.length
      searchPerformed.value = true
      if (results.value.length === 0) ElMessage.info('未找到匹配的CRM商机')
    } catch (e) {
      ElMessage.error('商机查询失败：' + (e?.message || '未知错误'))
    } finally {
      searching.value = false
    }
  }

  function onSelect(row) {
    if (!row?.id) return
    selectedId.value = row.id
    selectedChance.value = row
  }

  function confirmLink() {
    if (!selectedChance.value) {
      ElMessage.warning('请先选择商机')
      return
    }
    const chance = selectedChance.value
    linkedOpportunity.value = { name: chance.name, code: chance.code, id: chance.id }
    emit('linked', {
      opportunityId: chance.id,
      opportunityName: chance.name,
      evaluationData: {
        opportunityId: chance.id,
        basic: {
          projectBackground: chance.remark || '',
          competitorAnalysis: chance.bidDocumentDisadvantage || '',
          contractPeriodStart: chance.evaluationTime || '',
          contractPeriodEnd: '',
          shortlistedCount: chance.planSupplierCount || 0,
          platformServiceFee: chance.ecommerceMroAmount || 0,
        },
        customerInfos: [],
        recommendation: { shouldBid: !chance.backupPlan, reason: chance.riskPrediction || '' },
      },
    })
    showDialog.value = false
    ElMessage.success('CRM商机已关联，评估表已回填')
  }

}
