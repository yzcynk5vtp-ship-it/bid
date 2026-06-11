<template>
  <div class="crm-opportunity-selector">
    <div class="crm-field-row">
      <el-text class="label">CRM商机关联</el-text>
      <template v-if="linkedOpportunity">
        <el-tag type="success" size="large">{{ linkedOpportunity.name }}</el-tag>
        <el-tag v-if="linkedOpportunity.code" type="info" size="small">{{ linkedOpportunity.code }}</el-tag>
        <el-button text type="primary" size="small" @click="openSearch">更换</el-button>
      </template>
      <template v-else>
        <el-button type="primary" :disabled="!enabled" :loading="searching" @click="openSearch">
          {{ enabled ? '点击关联CRM商机' : '分配后由项目负责人关联' }}
        </el-button>
      </template>
    </div>

    <el-dialog v-model="showDialog" title="选择关联的CRM商机" width="860px">
      <div class="search-filters">
        <el-descriptions :column="4" size="small" border :labelStyle="{ fontWeight: 600 }">
          <el-descriptions-item label="招标主体">{{ tenderer }}</el-descriptions-item>
          <el-descriptions-item label="报名截止">{{ registrationDeadline }}</el-descriptions-item>
          <el-descriptions-item label="开标时间">{{ bidOpeningTime }}</el-descriptions-item>
        </el-descriptions>
        <div class="filter-row">
          <el-input v-model="searchForm.name" placeholder="商机名称（精确匹配）" clearable size="small" class="filter-input" />
          <el-input v-model="searchForm.code" placeholder="商机编号（CRM暂不支持按编号搜索）" clearable size="small" class="filter-input" />
          <el-select v-model="searchForm.projectStatus" multiple placeholder="项目状态" clearable size="small" class="filter-input">
            <el-option label="跟踪中" :value="1" /><el-option label="已投标" :value="2" />
            <el-option label="已中标" :value="3" /><el-option label="已丢标" :value="4" /><el-option label="已流标" :value="5" />
          </el-select>
          <el-button size="small" type="primary" :loading="searching" @click="doSearch(1)">
            <el-icon><Search /></el-icon> 搜索
          </el-button>
        </div>
      </div>

      <CrmOpportunityTable :results="results" :total-count="totalCount" :current-page="currentPage"
        :page-size="pageSize" :selected-id="selectedId" @select="onSelect" @page-change="doSearch" />

      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedChance" :loading="loading" @click="confirmLink">确认关联并回填评估表</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { crmApi } from '@/api/modules/crm.js'
import CrmOpportunityTable from './CrmOpportunityTable.vue'

const props = defineProps({
  enabled: { type: Boolean, default: false },
  tenderer: { type: String, default: '' },
  registrationDeadline: { type: String, default: '' },
  bidOpeningTime: { type: String, default: '' },
  alreadyLinkedName: { type: String, default: '' },
})
const emit = defineEmits(['linked'])

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
const linkedOpportunity = ref(props.alreadyLinkedName ? { name: props.alreadyLinkedName } : null)

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
    params.body.selectAll = true

    const res = await crmApi.searchOpportunities(params)
    const data = res?.data
    let list = data?.list || []
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
</script>

<style scoped>
.crm-opportunity-selector { padding: 12px 0; }
.crm-field-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.label { font-weight: 600; min-width: 110px; }
.search-filters { margin-bottom: 16px; }
.filter-row { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; align-items: center; }
.filter-input { width: 180px; }
</style>
