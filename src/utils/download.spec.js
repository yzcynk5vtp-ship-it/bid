import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest'
import { parseFilenameFromDisposition, triggerBlobDownload, downloadWithFilename } from './download.js'

// Mock URL API for jsdom
beforeAll(() => {
  if (!URL.createObjectURL) {
    URL.createObjectURL = vi.fn(() => 'blob:mock-url')
  }
  if (!URL.revokeObjectURL) {
    URL.revokeObjectURL = vi.fn()
  }
})

describe('parseFilenameFromDisposition', () => {
  it('应解析 RFC 5987 格式的文件名', () => {
    const disposition = "attachment; filename*=UTF-8''%E6%8B%9B%E6%A0%87%E5%85%AC%E5%91%8A.pdf"
    expect(parseFilenameFromDisposition(disposition, 'fallback.pdf')).toBe('招标公告.pdf')
  })

  it('应解析普通格式的文件名（带引号）', () => {
    const disposition = 'attachment; filename="test file.pdf"'
    expect(parseFilenameFromDisposition(disposition, 'fallback.pdf')).toBe('test file.pdf')
  })

  it('应解析普通格式的文件名（不带引号）', () => {
    const disposition = 'attachment; filename=test.pdf'
    expect(parseFilenameFromDisposition(disposition, 'fallback.pdf')).toBe('test.pdf')
  })

  it('当 disposition 为空时应返回 fallback', () => {
    expect(parseFilenameFromDisposition('', 'fallback.pdf')).toBe('fallback.pdf')
    expect(parseFilenameFromDisposition(null, 'fallback.pdf')).toBe('fallback.pdf')
    expect(parseFilenameFromDisposition(undefined, 'fallback.pdf')).toBe('fallback.pdf')
  })

  it('当 disposition 不包含 filename 时应返回 fallback', () => {
    expect(parseFilenameFromDisposition('attachment', 'fallback.pdf')).toBe('fallback.pdf')
  })

  it('应优先使用 filename* 而非 filename', () => {
    const disposition = "attachment; filename=\"old.pdf\"; filename*=UTF-8''%E6%96%B0.pdf"
    expect(parseFilenameFromDisposition(disposition, 'fallback.pdf')).toBe('新.pdf')
  })
})

describe('triggerBlobDownload', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('应创建 <a> 标签并触发点击', () => {
    const clickSpy = vi.fn()
    const appendSpy = vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
    const removeSpy = vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})

    // mock createElement
    const originalCreateElement = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation((tag) => {
      if (tag === 'a') {
        return { href: '', download: '', click: clickSpy }
      }
      return originalCreateElement(tag)
    })

    // mock URL.createObjectURL
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})

    const blob = new Blob(['test'], { type: 'text/plain' })
    triggerBlobDownload(blob, 'test.txt')

    expect(clickSpy).toHaveBeenCalled()
    expect(appendSpy).toHaveBeenCalled()
    expect(removeSpy).toHaveBeenCalled()
  })

  it('当 blob 为 null 时不应触发下载', () => {
    const clickSpy = vi.fn()
    vi.spyOn(document, 'createElement').mockReturnValue({ click: clickSpy })

    triggerBlobDownload(null, 'test.txt')
    expect(clickSpy).not.toHaveBeenCalled()
  })
})

describe('downloadWithFilename', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('当 url 为空时不应发起请求', async () => {
    const fetchSpy = vi.spyOn(global, 'fetch')
    await downloadWithFilename('', 'test.txt')
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('当 fetch 失败时应 fallback 到 window.open', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValue({
      ok: false,
      status: 404,
    })
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => {})

    await downloadWithFilename('http://example.com/file', 'test.txt')
    expect(openSpy).toHaveBeenCalledWith('http://example.com/file', '_blank')
  })

  it('当 fetch 成功时应从 Content-Disposition 解析文件名并下载', async () => {
    const blob = new Blob(['test'], { type: 'text/plain' })
    vi.spyOn(global, 'fetch').mockResolvedValue({
      ok: true,
      headers: {
        get: (name) => {
          if (name === 'Content-Disposition') return "attachment; filename*=UTF-8''%E6%B5%8B%E8%AF%95.txt"
          return null
        }
      },
      blob: () => Promise.resolve(blob)
    })

    const clickSpy = vi.fn()
    vi.spyOn(document, 'createElement').mockReturnValue({
      href: '', download: '', click: clickSpy
    })
    vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
    vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})

    await downloadWithFilename('http://example.com/file', 'fallback.txt')
    expect(clickSpy).toHaveBeenCalled()
  })
})
