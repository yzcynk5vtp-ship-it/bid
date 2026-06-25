import { describe, expect, it } from 'vitest'
import { firstNonBlank } from './firstNonBlank.js'

describe('firstNonBlank', () => {
  it('returns the first non-blank value', () => {
    expect(firstNonBlank(null, undefined, '', '  ', '03645', 'fallback')).toBe('03645')
  })

  it('keeps numeric zero when present', () => {
    expect(firstNonBlank(null, 0, 'fallback')).toBe(0)
  })

  it('returns empty string when all values are blank', () => {
    expect(firstNonBlank(null, undefined, '', '   ')).toBe('')
  })
})
