<template>
  <el-dialog v-model="visible" title="登记账号归还" width="480px">
    <el-form :model="form" label-width="120px">
      <el-form-item label="借用人">
        <span>用户#{{ application?.applicantId }}</span>
      </el-form-item>
      <el-form-item label="平台">
        <span>{{ accountName }}</span>
      </el-form-item>
      <el-form-item label="借用时间">
        <span>{{ formatDate(application?.createdAt) }}</span>
      </el-form-item>
      <el-form-item>
        <el-alert type="warning" :closable="false" show-icon>
          <template #title>账号归还时必须修改密码，否则无法完成归还登记</template>
        </el-alert>
      </el-form-item>
      <el-form-item label="实际归还时间" required>
        <el-date-picker v-model="form.actualReturnedAt" type="datetime"
          placeholder="选择日期时间" style="width:100%" />
      </el-form-item>
      <el-form-item label="新密码" required>
        <el-input v-model="form.newPassword" type="password" show-password placeholder="至少6位" />
      </el-form-item>
      <el-form-item label="确认新密码" required>
        <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
      </el-form-item>
      <el-form-item label="归还备注">
        <el-input v-model="form.remarks" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="submit">确认归还</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { resourcesApi } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  application: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'submitted'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const empty = () => ({ actualReturnedAt: '', newPassword: '', confirmPassword: '', remarks: '' })
const form = ref(empty())

watch(() => props.application, () => { form.value = empty() })

const accountName = computed(() => {
  if (!props.application) return '-'
  return `平台#${props.application.accountId}`
})

const formatDate = (value) => {
  if (!value) return '-'
  const d = new Date(value)
  return isNaN(d.getTime()) ? value : d.toLocaleString('zh-CN')
}

const formatDateTime = (value) => {
  if (!value) return undefined
  const d = value instanceof Date ? value : new Date(value)
  return isNaN(d.getTime()) ? undefined : d.toISOString()
}

const submit = async () => {
  if (!form.value.actualReturnedAt) { ElMessage.warning('请选择实际归还时间'); return }
  if (!form.value.newPassword || form.value.newPassword.length < 6) { ElMessage.warning('新密码长度不能少于6位'); return }
  if (form.value.newPassword !== form.value.confirmPassword) { ElMessage.warning('两次输入的密码不一致'); return }

  const res = await resourcesApi.accounts.returnBorrowApplication(props.application.id, {
    newPassword: form.value.newPassword,
    actualReturnedAt: formatDateTime(form.value.actualReturnedAt),
    remarks: form.value.remarks
  })
  if (!res?.success) {
    ElMessage.error(res?.msg || '归还登记失败'); return
  }
  ElMessage.success('账号已归还，密码已更新')
  visible.value = false
  emit('submitted')
}
</script>
