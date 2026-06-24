import { describe, it, expect, vi, beforeEach } from 'vitest'

// mock API modules before import
vi.mock('@/api/modules/dashboard', () => ({
  tasksApi: { getBoardItems: vi.fn(), updateStatus: vi.fn() }
}))
vi.mock('@/api/modules/projects', () => ({
  projectsApi: { getTaskDeliverables: vi.fn() }
}))

import { tasksApi } from '@/api/modules/dashboard'

describe('useTaskBoard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('COLUMNS should have exactly 3 columns without IN_PROGRESS', async () => {
    const { useTaskBoard } = await import('./useTaskBoard.js')
    const { columns } = useTaskBoard()
    expect(columns).toHaveLength(3)
    const keys = columns.map(c => c.key)
    expect(keys).toEqual(['TODO', 'REVIEW', 'COMPLETED'])
    expect(keys).not.toContain('IN_PROGRESS')
  })

  it('AVAILABLE_STATUSES should have exactly 3 statuses without IN_PROGRESS', async () => {
    const { useTaskBoard } = await import('./useTaskBoard.js')
    const { availableStatuses } = useTaskBoard()
    const codes = availableStatuses.map(s => s.code)
    expect(codes).toEqual(['TODO', 'REVIEW', 'COMPLETED'])
    expect(codes).not.toContain('IN_PROGRESS')
  })
})
