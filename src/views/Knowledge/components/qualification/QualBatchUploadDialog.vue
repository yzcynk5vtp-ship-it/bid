<template>
  <el-dialog
    v-model="visible"
    title="批量上传附件"
    width="520px"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <p class="dialog-desc">选择多个附件文件，系统将自动按文件名匹配对应资质证书。</p>
    <el-upload
      ref="uploadRef"
      class="batch-upload-area"
      drag
      multiple
      :auto-upload="false"
      :on-change="handleFileChange"
      accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png,.zip,.rar"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
    </el-upload>
    <div v-if="fileList.length" class="file-preview">
      <p class="file-count">已选择 {{ fileList.length }} 个文件</p>
      <ul>
        <li v-for="(f, i) in fileList" :key="i">{{ f.name }}</li>
      </ul>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="uploading" :disabled="!fileList.length" @click="handleUpload">
        开始上传
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import http from '@/api/client'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'uploaded'])

const visible = ref(props.modelValue)
watch(() => props.modelValue, v => { visible.value = v })
watch(visible, v => { emit('update:modelValue', v) })

const uploadRef = ref(null)
const fileList = ref([])
const uploading = ref(false)

const handleFileChange = (_file, files) => {
  fileList.value = files.map(f => f)
}

const handleUpload = async () => {
  if (!fileList.value.length) return
  uploading.value = true
  try {
    const formData = new FormData()
    fileList.value.forEach(f => {
      formData.append('files', f.raw)
    })
    await http.post('/api/knowledge/qualifications/batch-attach', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    ElMessage.success('附件上传完成，系统已自动匹配关联')
    emit('uploaded')
    visible.value = false
  } catch {
    ElMessage.error('批量上传附件失败')
  } finally {
    uploading.value = false
  }
}

const handleClosed = () => {
  fileList.value = []
}
</script>

<style scoped>
.dialog-desc {
  color: var(--gray-650, #6b7280);
  font-size: 13px;
  margin: 0 0 16px;
}
.batch-upload-area {
  margin-bottom: 12px;
}
.file-preview {
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 6px;
  padding: 12px 16px;
}
.file-count {
  font-weight: 600;
  margin: 0 0 8px;
}
.file-preview ul {
  margin: 0;
  padding-left: 18px;
}
.file-preview li {
  font-size: 13px;
  color: var(--gray-650);
  line-height: 1.8;
}
</style>
