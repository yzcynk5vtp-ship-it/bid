import { describe, expect, it } from 'vitest'
import { resolveAccountActions } from './accountActions.js'

describe('resolveAccountActions', () => {
  const adminCases = ['admin', '/bidAdmin', 'bid-TeamLeader']

  adminCases.forEach((role) => {
    it(`returns edit/return/takeDown for ${role}`, () => {
      expect(resolveAccountActions(role, 1, { contactPerson: '2' }))
        .toEqual(['edit', 'return', 'takeDown'])
    })
  })

  it('returns edit/return/takeDown for bid-Team when they are the contact person', () => {
    expect(resolveAccountActions('bid-Team', 42, { contactPerson: '42' }))
      .toEqual(['edit', 'return', 'takeDown'])
  })

  it('returns borrow for bid-Team when they are not the contact person', () => {
    expect(resolveAccountActions('bid-Team', 42, { contactPerson: '99' }))
      .toEqual(['borrow'])
  })

  it('returns apply for project leader', () => {
    expect(resolveAccountActions('bid-projectLeader', 42, { contactPerson: '99' }))
      .toEqual(['apply'])
  })

  it('returns apply for sales', () => {
    expect(resolveAccountActions('sales', 42, { contactPerson: '99' }))
      .toEqual(['apply'])
  })

  it('treats missing contactPerson as empty string', () => {
    expect(resolveAccountActions('bid-Team', 42, {}))
      .toEqual(['borrow'])
  })

  it('compares contactPerson and currentUserId as strings', () => {
    expect(resolveAccountActions('bid-Team', '42', { contactPerson: 42 }))
      .toEqual(['edit', 'return', 'takeDown'])
  })
})
