<template>
  <el-tooltip v-if="actions.includes('edit')" content="编辑" placement="top">
    <el-button :icon="Edit" circle size="small" @click.stop="$emit('edit', row)" />
  </el-tooltip>
  <el-tooltip v-if="actions.includes('return')" content="登记归还" placement="top">
    <el-button :icon="CircleCheck" circle size="small" type="success" @click.stop="$emit('return', row)" />
  </el-tooltip>
  <el-tooltip v-if="actions.includes('takeDown')" content="下架" placement="top">
    <el-button :icon="Delete" circle size="small" type="danger" @click.stop="takeDown" />
  </el-tooltip>
  <el-tooltip v-if="actions.includes('borrow') || actions.includes('apply')" :content="actions.includes('apply') ? '申请使用' : '借用'" placement="top">
    <el-button :icon="Key" circle size="small" type="primary" :disabled="row.status !== 'available'" @click.stop="$emit('borrow', row)" />
  </el-tooltip>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, CircleCheck, Delete, Key } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { resourcesApi } from '@/api'
import { resolveAccountActions } from './accountActions.js'

const props = defineProps({
  row: { type: Object, required: true }
})
const emit = defineEmits(['edit', 'return', 'borrow', 'taken-down'])

const userStore = useUserStore()
const roleCode = computed(() => userStore.currentUser?.roleCode || userStore.currentUser?.role || '')
const currentUserId = computed(() => userStore.currentUser?.id)
const actions = computed(() => resolveAccountActions(roleCode.value, currentUserId.value, props.row))

const takeDown = async () => {
  try {
    await ElMessageBox.confirm(`确定下架平台「${props.row.platform}」吗？`, '确认下架', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    const res = await resourcesApi.accounts.delete(props.row.id)
    if (!res?.success) {
      ElMessage.error(res?.msg || '下架失败')
      return
    }
    ElMessage.success('下架成功')
    emit('taken-down')
  } catch (e) {
    console.error('Failed to take down account:', e)
    ElMessage.error('下架失败')
  }
}
</script>
