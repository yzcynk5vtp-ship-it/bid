import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useTenderAiParse } from './useTenderAiParse.js'

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
}))

vi.mock('@/api/modules/tenders.js', () => ({
  tendersApi: {
    parseTenderIntakeDocument: vi.fn(),
    parseTenderIntakeText: vi.fn(),
  },
}))

import { tendersApi } from '@/api/modules/tenders.js'

describe('useTenderAiParse', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function makeUploadFile(name, type = 'application/pdf', uid) {
    const raw = new File(['content'], name, { type })
    return { name, raw, uid, type }
  }

  function mockParseResponse(documentId, overrides = {}) {
    return {
      success: true,
      data: {
        documentId,
        extractedData: {},
        requirements: [],
        rawMarkdown: '',
        ...overrides,
      },
    }
  }

  it('backfills attachment url/fileUrl from parsed documentId', async () => {
    const form = ref({ attachments: [], pastedText: '' })
    const { handleFileChange } = useTenderAiParse(form)
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    tendersApi.parseTenderIntakeDocument.mockResolvedValue(
      mockParseResponse('doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf')
    )

    await handleFileChange(file, [file])

    expect(form.value.attachments).toHaveLength(1)
    expect(form.value.attachments[0]).toMatchObject({
      name: 'tender.pdf',
      type: 'application/pdf',
      fileName: 'tender.pdf',
      fileType: 'application/pdf',
      url: 'doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf',
      fileUrl: 'doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf',
    })
  })

  it('backfills attachment url/fileUrl even when document is scanned', async () => {
    const form = ref({ attachments: [], pastedText: '' })
    const { handleFileChange } = useTenderAiParse(form)
    const file = makeUploadFile('scanned.pdf', 'application/pdf', 2)

    tendersApi.parseTenderIntakeDocument.mockResolvedValue(
      mockParseResponse('doc-insight://TENDER_INTAKE/create-tender/hash-scanned.pdf', {
        warnings: ['SCANNED_DOCUMENT: 该文件可能是扫描件'],
      })
    )

    await handleFileChange(file, [file])

    expect(form.value.attachments[0]).toMatchObject({
      url: 'doc-insight://TENDER_INTAKE/create-tender/hash-scanned.pdf',
      fileUrl: 'doc-insight://TENDER_INTAKE/create-tender/hash-scanned.pdf',
    })
  })

  it('updates the correct attachment by index when multiple files share the same name', async () => {
    const form = ref({ attachments: [], pastedText: '' })
    const { handleFileChange } = useTenderAiParse(form)

    const first = makeUploadFile('same-name.pdf', 'application/pdf', 10)
    const second = makeUploadFile('same-name.pdf', 'application/pdf', 20)
    const fileList = [first, second]

    tendersApi.parseTenderIntakeDocument.mockResolvedValue(
      mockParseResponse('doc-insight://TENDER_INTAKE/create-tender/hash-second.pdf')
    )

    await handleFileChange(second, fileList)

    expect(form.value.attachments).toHaveLength(2)
    expect(form.value.attachments[0].url).toBeUndefined()
    expect(form.value.attachments[0].fileUrl).toBeUndefined()
    expect(form.value.attachments[1].url).toBe('doc-insight://TENDER_INTAKE/create-tender/hash-second.pdf')
    expect(form.value.attachments[1].fileUrl).toBe('doc-insight://TENDER_INTAKE/create-tender/hash-second.pdf')
  })
})
