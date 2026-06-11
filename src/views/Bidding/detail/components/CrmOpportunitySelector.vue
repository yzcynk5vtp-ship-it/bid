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
import { Search } from '@element-plus/icons-vue'
import { useCrmOpportunitySelector } from './useCrmOpportunitySelector.js'
import CrmOpportunityTable from './CrmOpportunityTable.vue'

const props = defineProps({
  enabled: { type: Boolean, default: false },
  tenderer: { type: String, default: '' },
  registrationDeadline: { type: String, default: '' },
  bidOpeningTime: { type: String, default: '' },
  alreadyLinkedName: { type: String, default: '' },
})
const emit = defineEmits(['linked'])

const {
  showDialog, searching, loading, searchPerformed, results, selectedId,
  selectedChance, totalCount, currentPage, pageSize,
  searchForm, linkedOpportunity,
  openSearch, doSearch, onSelect, confirmLink,
} = useCrmOpportunitySelector(props, emit)
</script>

<style scoped>
.crm-opportunity-selector { padding: 12px 0; }
.crm-field-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.label { font-weight: 600; min-width: 110px; }
.search-filters { margin-bottom: 16px; }
.filter-row { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; align-items: center; }
.filter-input { width: 180px; }
</style>
