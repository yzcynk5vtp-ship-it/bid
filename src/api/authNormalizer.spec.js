import { describe, it, expect } from 'vitest'
import { normalizeUser } from './authNormalizer'

describe('normalizeUser roleCode', () => {
  it('preserves OSS role code with leading slash (/bidAdmin)', () => {
    const user = normalizeUser({ roleCode: '/bidAdmin' })
    expect(user.roleCode).toBe('/bidAdmin')
    expect(user.role).toBe('/bidAdmin')
  })

  it('preserves OSS role code case (bid-TeamLeader)', () => {
    const user = normalizeUser({ roleCode: 'bid-TeamLeader' })
    expect(user.roleCode).toBe('bid-TeamLeader')
  })

  it('preserves OSS role code case (bid-Team)', () => {
    const user = normalizeUser({ roleCode: 'bid-Team' })
    expect(user.roleCode).toBe('bid-Team')
  })

  it('falls back to role field when roleCode is missing', () => {
    const user = normalizeUser({ role: 'admin' })
    expect(user.roleCode).toBe('admin')
  })

  it('returns empty string when both roleCode and role are missing', () => {
    const user = normalizeUser({})
    expect(user.roleCode).toBe('')
  })
})

describe('normalizeUser menuPermissions', () => {
  it('adds knowledge parent permission when knowledge qualification child exists', () => {
    const user = normalizeUser({ menuPermissions: ['dashboard', 'knowledge-qualification'] })

    expect(user.menuPermissions).toContain('dashboard')
    expect(user.menuPermissions).toContain('knowledge-qualification')
    expect(user.menuPermissions).toContain('knowledge')
  })

  it('does not add knowledge parent permission for unrelated permissions', () => {
    const user = normalizeUser({ menuPermissions: ['dashboard'] })

    expect(user.menuPermissions).toEqual(['dashboard'])
  })

  it('adds knowledge parent permission for other knowledge child permissions', () => {
    const user = normalizeUser({ menuPermissions: ['knowledge-case'] })

    expect(user.menuPermissions).toContain('knowledge-case')
    expect(user.menuPermissions).toContain('knowledge')
  })

  it('preserves all permission without adding knowledge parent permission', () => {
    const user = normalizeUser({ menuPermissions: ['all'] })

    expect(user.menuPermissions).toEqual(['all'])
  })
})
