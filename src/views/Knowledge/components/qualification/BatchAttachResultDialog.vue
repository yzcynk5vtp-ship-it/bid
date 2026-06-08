<template>
  <el-dialog
    v-model="visible"
    title="批量关联附件结果"
    width="680px"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <div v-if="data" class="attach-result-dialog">
      <div class="stat-cards">
        <div class="stat-card total">
          <div class="stat-number">{{ data.total }}</div>
          <div class="stat-label">总文件数</div>
        </div>
        <div class="stat-card success">
          <div class="stat-number">{{ data.success }}</div>
          <div class="stat-label">关联成功</div>
        </div>
        <div class="stat-card failed">
          <div class="stat-number">{{ data.failed }}</div>
          <div class="stat-label">未匹配</div>
        </div>
      </div>

      <div v-if="data.matched && data.matched.length" class="matched-section">
        <div class="section-title success-title">
          <el-icon><CircleCheck /></el-icon>
          关联成功（共 {{ data.matched.length }} 个）
        </div>
        <el-table :data="data.matched" size="small" border>
          <el-table-column prop="fileName" label="文件名" show-overflow-tooltip />
          <el-table-column prop="certificateNo" label="证书编号" />
          <el-table-column prop="qualificationName" label="证书名称" show-overflow-tooltip />
        </el-table>
      </div>

      <div v-if="data.unmatched && data.unmatched.length" class="unmatched-section">
        <div class="section-title warning-title">
          <el-icon><Warning /></el-icon>
          未匹配文件（共 {{ data.unmatched.length }} 个）
        </div>
        <el-table :data="data.unmatched" size="small" border>
          <el-table-column prop="fileName" label="文件名" show-overflow-tooltip />
          <el-table-column prop="reason" label="原因" show-overflow-tooltip />
        </el-table>
      </div>

      <div v-if="!data.matched?.length && !data.unmatched?.length" class="all-success">
        <el-icon class="success-icon"><CircleCheck /></el-icon>
        <span>未检测到有效文件</span>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { Warning, CircleCheck } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: Boolean,
  data: { type: Object, default: () => ({ total: 0, success: 0, failed: 0, matched: [], unmatched: [] }) }
})
const emit = defineEmits(['update:modelValue', 'closed'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const handleClosed = () => {
  emit('closed')
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
.matched-section,
.unmatched-section {
  margin-top: 16px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  margin-bottom: 12px;
}
.success-title {
  color: var(--el-color-success);
}
.warning-title {
  color: var(--el-color-warning);
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
