import { describe, expect, it } from 'vitest'
import {
  buildProjectCreatedActivity,
  formatProjectActivityTime,
  resolveProjectCreatorName,
} from './useProjectDetailActivities.js'

describe('project detail activities', () => {
  it('does not seed the legacy fixed mock timeline', () => {
    const activities = buildProjectCreatedActivity({
      id: 108,
      createdAt: '2026-04-27T17:38:12',
      managerName: '小王',
    })

    expect(activities).toEqual([{
      id: 'project-created-108',
      user: '小王',
      action: '创建了项目',
      time: '2026-04-27 17:38',
    }])
    expect(activities.map((item) => item.time)).not.toContain('2025-02-20 10:00')
  })

  it('resolves creator name by priority order', () => {
    expect(resolveProjectCreatorName({ createdByName: '张三' })).toBe('张三')
    expect(resolveProjectCreatorName({ creatorName: '李四' })).toBe('李四')
    expect(resolveProjectCreatorName({ ownerName: '负责人' })).toBe('负责人')
    expect(resolveProjectCreatorName({ managerName: '小王' })).toBe('小王')
  })

  it('falls back to system when project creator fields are absent', () => {
    expect(resolveProjectCreatorName({})).toBe('系统')
    expect(resolveProjectCreatorName()).toBe('系统')
    expect(resolveProjectCreatorName(null)).toBe('系统')
  })

  it('does not falsely attribute project creation to the current viewer', () => {
    expect(resolveProjectCreatorName({ id: 48, name: '某项目' })).toBe('系统')
  })

  it('trims creator name and skips blank or dirty values', () => {
    expect(resolveProjectCreatorName({ createdByName: '  张三  ' })).toBe('张三')
    expect(resolveProjectCreatorName({ createdByName: '', managerName: '小王' })).toBe('小王')
    expect(resolveProjectCreatorName({ createdByName: '   ', creatorName: '李四' })).toBe('李四')
    expect(resolveProjectCreatorName({ createdByName: null, ownerName: '负责人' })).toBe('负责人')
    expect(resolveProjectCreatorName({ createdByName: 'null', managerName: '小王' })).toBe('null')
  })

  it('keeps unknown date values visible instead of inventing a time', () => {
    expect(formatProjectActivityTime('待确认')).toBe('待确认')
    expect(formatProjectActivityTime('')).toBe('')
  })

  it('returns no activity when project is missing', () => {
    expect(buildProjectCreatedActivity(null)).toEqual([])
    expect(buildProjectCreatedActivity({})).toEqual([])
  })
})
