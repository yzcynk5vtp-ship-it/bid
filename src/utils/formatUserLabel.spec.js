import { describe, it, expect } from 'vitest'
import { formatUserLabel } from './formatUserLabel.js'

describe('formatUserLabel', () => {
  it('returns name with employeeNumber', () => {
    expect(formatUserLabel({ name: '张三', employeeNumber: '20260509' })).toBe('张三（20260509）')
  })

  it('falls back to username when no employeeNumber', () => {
    expect(formatUserLabel({ name: '李四', username: '03645' })).toBe('李四（03645）')
  })

  it('prefers fullName over name', () => {
    expect(formatUserLabel({ name: 'zs', fullName: '张三', employeeNumber: '1' })).toBe('张三（1）')
  })

  it('falls back to plain name when no employee id', () => {
    expect(formatUserLabel({ name: '王五' })).toBe('王五')
  })

  it('returns em dash for null user', () => {
    expect(formatUserLabel(null)).toBe('—')
  })
})
