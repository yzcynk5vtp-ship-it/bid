<template>
  <el-dialog
    v-model="visible"
    title="导入结果报告"
    width="680px"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <div v-if="data" class="import-result-dialog">
      <div class="stat-cards">
        <div class="stat-card total">
          <div class="stat-number">{{ data.total }}</div>
          <div class="stat-label">总条数</div>
        </div>
        <div class="stat-card success">
          <div class="stat-number">{{ data.success }}</div>
          <div class="stat-label">成功</div>
        </div>
        <div class="stat-card failed">
          <div class="stat-number">{{ data.failed }}</div>
          <div class="stat-label">失败</div>
        </div>
      </div>

      <div v-if="data.errors && data.errors.length" class="error-section">
        <div class="section-title">
          <el-icon><Warning /></el-icon>
          失败明细（共 {{ data.errors.length }} 条）
        </div>
        <el-table :data="data.errors" size="small" border>
          <el-table-column prop="row" label="行号" width="70" />
          <el-table-column prop="certificateNo" label="证书编号" />
          <el-table-column prop="reason" label="失败原因" />
        </el-table>
        <el-button
          v-if="data.errors.length"
          type="primary"
          size="small"
          class="download-btn"
          @click="handleDownloadErrorFile"
        >
          <el-icon><Download /></el-icon>
          下载修正文件
        </el-button>
      </div>

      <div v-else class="all-success">
        <el-icon class="success-icon"><CircleCheck /></el-icon>
        <span>全部导入成功</span>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { Warning, Download, CircleCheck } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: Boolean,
  data: { type: Object, default: () => ({ total: 0, success: 0, failed: 0, errors: [] }) }
})
const emit = defineEmits(['update:modelValue', 'closed'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const handleClosed = () => {
  emit('closed')
}

const handleDownloadErrorFile = () => {
  if (!props.data?.errors?.length) return
  const headers = ['证书名称', '等级', '认证机构', '证书编号', '发证日期', '证书有效期', '代理机构', '代理联系方式', '认证范围', '证书审核提醒', '附件文件名']
  const csv = [headers.join(','), ...props.data.errors.map(e => `,,,"${e.certificateNo || ''}",,,,,,,,"${e.reason}"`)]
  const blob = new Blob(['\uFEFF' + csv.join('\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `导入失败明细_${new Date().toISOString().slice(0, 10)}.csv`)
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
</script>

<style scoped lang="scss">
.stat-cards {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  flex: 1;
  text-align: center;
  padding: 16px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}
.stat-card.total { border-top: 3px solid var(--el-color-primary); }
.stat-card.success { border-top: 3px solid var(--el-color-success); }
.stat-card.failed { border-top: 3px solid var(--el-color-danger); }
.stat-number {
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 4px;
}
.stat-card.total .stat-number { color: var(--el-color-primary); }
.stat-card.success .stat-number { color: var(--el-color-success); }
.stat-card.failed .stat-number { color: var(--el-color-danger); }
.stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.error-section {
  margin-top: 8px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  margin-bottom: 12px;
  color: var(--el-color-warning);
}
.download-btn {
  margin-top: 12px;
}
.all-success {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: var(--el-color-success);
  font-size: 16px;
}
.success-icon {
  font-size: 24px;
}
</style>
