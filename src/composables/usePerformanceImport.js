import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import performanceApi from '@/api/modules/performance.js'

export function usePerformanceImport(loadData) {
  const importVisible = ref(false)
  const importFile = ref(null)
  const attachFiles = ref([])
  const importLoading = ref(false)
  const importResult = ref({ successCount: 0, failureCount: 0, failures: [], attachedCount: 0, unmatchedFiles: [] })

  const openImport = () => {
    importVisible.value = true
    importFile.value = null
    attachFiles.value = []
    importResult.value = { successCount: 0, failureCount: 0, failures: [], attachedCount: 0, unmatchedFiles: [] }
  }

  const downloadTemplate = async () => {
    try {
      await performanceApi.downloadTemplate()
      ElMessage.success('模板下载成功')
    } catch {
      ElMessage.error('模板下载失败')
    }
  }

  const onImportFileChange = (uploadFile) => {
    if (uploadFile && uploadFile.raw) importFile.value = uploadFile.raw
  }

  const onImportFileRemove = () => {
    importFile.value = null
  }

  const onAttachChange = (uploadFile) => {
    if (uploadFile && uploadFile.raw) attachFiles.value.push(uploadFile.raw)
  }

  const onAttachRemove = (uploadFile) => {
    if (uploadFile && uploadFile.raw) {
      attachFiles.value = attachFiles.value.filter(f => f !== uploadFile.raw)
    }
  }

  const confirmImport = async () => {
    if (!importFile.value) return
    importLoading.value = true
    try {
      const res = await performanceApi.batchImport(importFile.value, attachFiles.value)
      importResult.value = {
        successCount: res.data?.successCount || 0,
        failureCount: res.data?.failureCount || 0,
        failures: res.data?.failures || [],
        attachedCount: res.data?.attachedCount || 0,
        unmatchedFiles: res.data?.unmatchedFiles || []
      }
      ElMessage.success(`导入完成：成功 ${importResult.value.successCount} 条，失败 ${importResult.value.failureCount} 条`)
      loadData()
    } catch (e) {
      ElMessage.error(e.message || '导入失败')
    } finally {
      importLoading.value = false
    }
  }

  const closeImport = () => {
    importVisible.value = false
    importFile.value = null
    attachFiles.value = []
    importResult.value = { successCount: 0, failureCount: 0, failures: [], attachedCount: 0, unmatchedFiles: [] }
  }

  return {
    importVisible, importFile, attachFiles, importLoading, importResult,
    openImport, downloadTemplate, onImportFileChange, onImportFileRemove,
    onAttachChange, onAttachRemove, confirmImport, closeImport
  }
}
