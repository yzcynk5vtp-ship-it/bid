<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="批量导入结果"
    width="640px"
    data-testid="qual-import-result-dialog"
  >
    <div v-if="result">
      <el-alert
        :title="`导入完成：共 ${result.total} 条，成功 ${result.success} 条，失败 ${result.failed} 条`"
        :type="result.failed === 0 ? 'success' : (result.success === 0 ? 'error' : 'warning')"
        :closable="false"
        show-icon
        class="qual-import-summary"
      />
      <div v-if="failedRows.length > 0" class="qual-import-failed">
        <h4>失败明细（{{ failedRows.length }} 条）</h4>
        <el-table :data="failedRows" max-height="320" data-testid="qual-import-failed-table">
          <el-table-column prop="rowNumber" label="行号" width="80" />
          <el-table-column prop="certificateNo" label="证书编号" min-width="160" show-overflow-tooltip />
          <el-table-column prop="failureReason" label="失败原因" min-width="280" show-overflow-tooltip />
        </el-table>
      </div>
      <el-empty v-else description="全部导入成功" />
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)" data-testid="qual-import-result-close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  result: { type: Object, default: null }
})
defineEmits(['update:modelValue'])

const failedRows = computed(() => (props.result?.results || []).filter(r => !r.success))
</script>

<style scoped>
.qual-import-summary { margin-bottom: 16px; }
.qual-import-failed h4 { margin: 16px 0 12px; color: var(--color-danger-dark); }
</style>
