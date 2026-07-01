// Input: src/composables/usePerformanceImport.js
// Output: CO-444 业绩批量导入 composable 状态机测试
// Pos: src/composables/__tests__/ — composable 单元测试

import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() }
}))

vi.mock('@/api/modules/performance.js', () => ({
  default: {
    downloadTemplate: vi.fn(),
    batchImport: vi.fn()
  }
}))

import { usePerformanceImport } from './usePerformanceImport.js'
import performanceApi from '@/api/modules/performance.js'
import { ElMessage } from 'element-plus'

describe('CO-444 usePerformanceImport composable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('openImport 初始化弹窗状态', () => {
    const { importVisible, importFile, attachFiles, importResult, openImport } = usePerformanceImport(() => {})
    expect(importVisible.value).toBe(false)
    openImport()
    expect(importVisible.value).toBe(true)
    expect(importFile.value).toBeNull()
    expect(attachFiles.value).toEqual([])
    expect(importResult.value).toMatchObject({
      successCount: 0, failureCount: 0, attachedCount: 0, unmatchedFiles: []
    })
  })

  it('onImportFileChange 设置 importFile', () => {
    const { importFile, onImportFileChange } = usePerformanceImport(() => {})
    const raw = new File(['x'], 'a.xlsx')
    onImportFileChange({ raw })
    expect(importFile.value).toBe(raw)
  })

  it('onImportFileRemove 清空 importFile', () => {
    const { importFile, onImportFileChange, onImportFileRemove } = usePerformanceImport(() => {})
    onImportFileChange({ raw: new File(['x'], 'a.xlsx') })
    onImportFileRemove()
    expect(importFile.value).toBeNull()
  })

  it('onAttachChange 添加附件到 attachFiles', () => {
    const { attachFiles, onAttachChange } = usePerformanceImport(() => {})
    const f1 = new File(['p'], 'p.pdf')
    const f2 = new File(['i'], 'i.png')
    onAttachChange({ raw: f1 })
    onAttachChange({ raw: f2 })
    expect(attachFiles.value).toEqual([f1, f2])
  })

  it('onAttachRemove 从 attachFiles 中移除指定附件', () => {
    const { attachFiles, onAttachChange, onAttachRemove } = usePerformanceImport(() => {})
    const f1 = new File(['p'], 'p.pdf')
    const f2 = new File(['i'], 'i.png')
    onAttachChange({ raw: f1 })
    onAttachChange({ raw: f2 })
    onAttachRemove({ raw: f1 })
    expect(attachFiles.value).toEqual([f2])
  })

  it('downloadTemplate 成功时提示成功', async () => {
    performanceApi.downloadTemplate.mockResolvedValueOnce({})
    const { downloadTemplate } = usePerformanceImport(() => {})
    await downloadTemplate()
    expect(ElMessage.success).toHaveBeenCalledWith('模板下载成功')
  })

  it('downloadTemplate 失败时提示失败', async () => {
    performanceApi.downloadTemplate.mockRejectedValueOnce(new Error('network'))
    const { downloadTemplate } = usePerformanceImport(() => {})
    await downloadTemplate()
    expect(ElMessage.error).toHaveBeenCalledWith('模板下载失败')
  })

  it('confirmImport 无文件时直接返回', async () => {
    const { confirmImport } = usePerformanceImport(() => {})
    await confirmImport()
    expect(performanceApi.batchImport).not.toHaveBeenCalled()
  })

  it('confirmImport 成功时传递 file + attachments 并更新 importResult', async () => {
    const file = new File(['x'], 'a.xlsx')
    const attach = new File(['p'], 'p.pdf')
    performanceApi.batchImport.mockResolvedValueOnce({
      data: { successCount: 3, failureCount: 1, failures: [{ rowNum: 2, reason: 'err' }],
              attachedCount: 2, unmatchedFiles: ['x.txt'] }
    })
    const loadData = vi.fn()
    const { importFile, attachFiles, confirmImport, importResult } = usePerformanceImport(loadData)
    importFile.value = file
    attachFiles.value = [attach]
    await confirmImport()
    expect(performanceApi.batchImport).toHaveBeenCalledWith(file, [attach])
    expect(importResult.value.successCount).toBe(3)
    expect(importResult.value.failureCount).toBe(1)
    expect(importResult.value.attachedCount).toBe(2)
    expect(importResult.value.unmatchedFiles).toEqual(['x.txt'])
    expect(loadData).toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('导入完成：成功 3 条，失败 1 条')
  })

  it('confirmImport 失败时提示错误消息', async () => {
    const file = new File(['x'], 'a.xlsx')
    performanceApi.batchImport.mockRejectedValueOnce(new Error('服务器错误'))
    const { importFile, confirmImport } = usePerformanceImport(() => {})
    importFile.value = file
    await confirmImport()
    expect(ElMessage.error).toHaveBeenCalledWith('服务器错误')
  })

  it('closeImport 重置所有状态', () => {
    const { importVisible, importFile, attachFiles, importResult,
            openImport, onImportFileChange, onAttachChange, closeImport } = usePerformanceImport(() => {})
    openImport()
    onImportFileChange({ raw: new File(['x'], 'a.xlsx') })
    onAttachChange({ raw: new File(['p'], 'p.pdf') })
    closeImport()
    expect(importVisible.value).toBe(false)
    expect(importFile.value).toBeNull()
    expect(attachFiles.value).toEqual([])
    expect(importResult.value).toMatchObject({
      successCount: 0, failureCount: 0, attachedCount: 0, unmatchedFiles: []
    })
  })
})
