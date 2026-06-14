// Input: src/views/Resource/composables/usePasswordReveal.js
// Output: unit tests for on-demand password reveal + audit logging
// Pos: src/views/Resource/composables/ — H12-frontend security regression

import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { usePasswordReveal } from './usePasswordReveal.js'

const PLAINTEXT = '<TEST-MOCK-PASSWORD-DO-NOT-USE>'

const okFetcher = vi.fn(async () => ({
  success: true,
  data: { password: PLAINTEXT, auditId: 'aud-1' }
}))

const deniedFetcher = vi.fn(async () => ({
  success: false,
  msg: '当前账号密码不可直接查看'
}))

describe('usePasswordReveal', () => {
  beforeEach(() => {
    okFetcher.mockClear()
    deniedFetcher.mockClear()
    vi.stubGlobal('navigator', {
      clipboard: { writeText: vi.fn().mockResolvedValue(undefined) }
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('displayText masks until toggled, then reveals fetched password', async () => {
    const r = usePasswordReveal(okFetcher)
    expect(r.displayText(7)).toBe('••••••')
    expect(r.isVisible(7)).toBe(false)

    await r.toggle(7)
    expect(okFetcher).toHaveBeenCalledWith(7)
    expect(r.isVisible(7)).toBe(true)
    expect(r.displayText(7)).toBe(PLAINTEXT)
  })

  it('toggle off hides without re-fetching (cached)', async () => {
    const r = usePasswordReveal(okFetcher)
    await r.toggle(7)
    await r.toggle(7)
    expect(r.isVisible(7)).toBe(false)
    expect(r.displayText(7)).toBe('••••••')

    // Reveal again — fetcher must NOT be called a second time (cached).
    await r.toggle(7)
    expect(okFetcher).toHaveBeenCalledTimes(1)
    expect(r.isVisible(7)).toBe(true)
  })

  it('records an audit entry only on successful reveal', async () => {
    const r = usePasswordReveal(okFetcher)
    await r.toggle(7)
    expect(r.auditLogs.value).toHaveLength(1)
    expect(r.auditLogs.value[0]).toMatchObject({
      accountId: 7,
      auditId: 'aud-1'
    })
    expect(r.auditLogs.value[0].viewedAt).toBeTruthy()
  })

  it('copy fetches password and writes to clipboard', async () => {
    const r = usePasswordReveal(okFetcher)
    await r.copy(9)
    expect(okFetcher).toHaveBeenCalledWith(9)
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(PLAINTEXT)
  })

  it('denied response keeps mask and records no audit entry', async () => {
    const r = usePasswordReveal(deniedFetcher)
    await r.toggle(11)
    expect(r.isVisible(11)).toBe(false)
    expect(r.displayText(11)).toBe('••••••')
    expect(r.auditLogs.value).toHaveLength(0)
    expect(r.revealed.value[11]).toBeUndefined()
  })

  it('isLoading tracks in-flight fetch', async () => {
    let resolveFetch
    const pending = new Promise((res) => { resolveFetch = res })
    const slow = vi.fn(() => pending)
    const r = usePasswordReveal(slow)
    const promise = r.toggle(3)
    expect(r.isLoading(3)).toBe(true)
    resolveFetch({ success: true, data: { password: 'x', auditId: 'a' } })
    await promise
    expect(r.isLoading(3)).toBe(false)
  })
})
