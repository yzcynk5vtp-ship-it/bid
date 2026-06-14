import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/client'

export function useQualificationBatch({ fetchQualifications }) {
  const tableRef = ref(null)
  const selectedRows = ref([])
  const selectedCount = computed(() => selectedRows.value.length)
  const hasSelection = computed(() => selectedCount.value > 0)

  const handleSelectionChange = (rows) => { selectedRows.value = rows || [] }

  const handleBatchExport = () => {
    const ids = selectedRows.value.map(r => r.id)
    http.post('/api/knowledge/qualifications/batch-export', { ids }, { responseType: 'blob' })
      .then(res => {
        const url = window.URL.createObjectURL(new Blob([res.data]))
        const link = document.createElement('a')
        link.href = url
        link.setAttribute('download', `资质证书台账批量导出_${new Date().toISOString().slice(0, 10)}.xlsx`)
        document.body.appendChild(link)
        link.click()
        link.remove()
        window.URL.revokeObjectURL(url)
      })
      .catch(() => ElMessage.error('批量导出失败'))
  }

  const handleBatchDownload = () => {
    const ids = selectedRows.value.map(r => r.id)
    http.post('/api/knowledge/qualifications/batch-download', { ids }, { responseType: 'blob' })
      .then(res => {
        const url = window.URL.createObjectURL(new Blob([res.data]))
        const link = document.createElement('a')
        link.href = url
        link.setAttribute('download', `资质附件批量下载_${new Date().toISOString().slice(0, 10)}.zip`)
        document.body.appendChild(link)
        link.click()
        link.remove()
        window.URL.revokeObjectURL(url)
      })
      .catch(() => ElMessage.error('批量下载失败'))
  }

  return {
    tableRef,
    selectedRows,
    selectedCount,
    hasSelection,
    handleSelectionChange,
    handleBatchExport,
    handleBatchDownload
  }
}
