// Input: tenders API（导入模板下载、批量导入接口）+ refreshTenderList + canCreateTender
// Output: 批量导入对话框状态、模板下载、上传提交动作
// Pos: src/views/Bidding/list/ - Tender bulk import composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { triggerBlobDownload } from '@/utils/download.js'

const MAX_FILE_BYTES = 5 * 1024 * 1024
const ACCEPTED_EXT = '.xlsx'

function isXlsxFile(file) {
  const name = String(file?.name || '').toLowerCase()
  return name.endsWith(ACCEPTED_EXT)
}

export function useTenderBulkImport({ tendersApi, refreshTenderList, canCreateTender }) {
  const showBulkImport = ref(false)
  const templateDownloading = ref(false)
  const importing = ref(false)
  const importResult = ref(null)
  const selectedFile = ref(null)

  const resetImport = () => {
    selectedFile.value = null
    importResult.value = null
  }

  const closeDialog = () => {
    showBulkImport.value = false
    resetImport()
  }

  const openBulkImport = () => {
    if (!canCreateTender.value) {
      ElMessage.error('当前账号无权批量导入标讯')
      return
    }
    resetImport()
    showBulkImport.value = true
  }

  const downloadImportTemplate = async () => {
    if (!canCreateTender.value) {
      ElMessage.error('当前账号无权下载导入模板')
      return false
    }
    templateDownloading.value = true
    try {
      const blob = await tendersApi.downloadImportTemplate()
      if (!(blob instanceof Blob)) {
        throw new Error('模板下载响应异常')
      }
      triggerBlobDownload(blob, '标讯批量导入模板.xlsx')
      ElMessage.success('模板已下载，请在 Excel 中填写后回传')
      return true
    } catch (error) {
      ElMessage.error(error?.message || '模板下载失败，请稍后重试')
      return false
    } finally {
      templateDownloading.value = false
    }
  }

  const handleFileChange = (file) => {
    const raw = file?.raw instanceof File ? file.raw : (file instanceof File ? file : null)
    if (!raw) {
      selectedFile.value = null
      return
    }
    if (!isXlsxFile(raw)) {
      ElMessage.error('仅支持 .xlsx 模板，请重新选择')
      selectedFile.value = null
      return
    }
    if (raw.size > MAX_FILE_BYTES) {
      ElMessage.error('文件大小不能超过 5MB')
      selectedFile.value = null
      return
    }
    selectedFile.value = raw
    importResult.value = null
  }

  const submitBulkImport = async () => {
    if (!canCreateTender.value) {
      ElMessage.error('当前账号无权批量导入标讯')
      return false
    }
    if (!selectedFile.value) {
      ElMessage.warning('请先选择 .xlsx 导入文件')
      return false
    }
    importing.value = true
    try {
      const response = await tendersApi.bulkImport(selectedFile.value)
      const data = response?.data || null
      importResult.value = data
      if (data && data.failureCount > 0) {
        ElMessage.warning(`导入未通过：共 ${data.totalRows} 行，失败 ${data.failureCount} 行（已整批回滚，未写入数据），请查看下方错误明细逐行修正后重新上传`)
        return false
      }
      const successCount = data?.successCount ?? 0
      ElMessage.success(`成功导入 ${successCount} 条标讯`)

      // CRM 自动分配功能未实现（后端 autoAssign API 未提供），
      // 待后端就绪后再在此处或独立 composable 中接入。
      await refreshTenderList()
      closeDialog()
      return true
    } catch (error) {
      ElMessage.error(error?.response?.data?.msg || error?.message || '批量导入失败，请稍后重试')
      return false
    } finally {
      importing.value = false
    }
  }

  return {
    showBulkImport,
    templateDownloading,
    importing,
    importResult,
    selectedFile,
    openBulkImport,
    closeDialog,
    resetImport,
    downloadImportTemplate,
    handleFileChange,
    submitBulkImport,
  }
}
