import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useQualificationImportExport } from './useQualificationImportExport.js'

describe('useQualificationImportExport', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('beforeImportUpload rejects non-xlsx files', () => {
    const { beforeImportUpload } = useQualificationImportExport({})
    const mockFile = { name: 'test.pdf', size: 100 }
    expect(beforeImportUpload(mockFile)).toBe(false)
  })

  it('beforeImportUpload rejects files larger than 10MB', () => {
    const { beforeImportUpload } = useQualificationImportExport({})
    const mockFile = { name: 'test.xlsx', size: 11 * 1024 * 1024 }
    expect(beforeImportUpload(mockFile)).toBe(false)
  })

  it('beforeImportUpload accepts valid xlsx files', () => {
    const { beforeImportUpload } = useQualificationImportExport({})
    const mockFile = { name: 'test.xlsx', size: 1 * 1024 * 1024 }
    expect(beforeImportUpload(mockFile)).toBe(true)
  })
})