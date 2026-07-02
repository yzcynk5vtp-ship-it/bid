import { describe, it, expect } from 'vitest'
import { taskBackendToCard, taskFormDtoToBackend } from './project-utils.js'

describe('taskBackendToCard', () => {
  it('maps attachments from backend DTO', () => {
    const dto = {
      id: 1,
      title: '任务1',
      attachments: [
        { id: 101, name: '附件1.pdf', fileType: 'application/pdf' },
        { id: 102, name: '附件2.docx', fileType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' },
      ],
    }
    const card = taskBackendToCard(dto)
    expect(card.attachments).toHaveLength(2)
    expect(card.attachments[0].id).toBe(101)
    expect(card.attachments[1].name).toBe('附件2.docx')
  })

  it('returns empty attachments array when backend has none', () => {
    const dto = { id: 1, title: '任务1' }
    const card = taskBackendToCard(dto)
    expect(card.attachments).toEqual([])
  })

  it('returns empty attachments array when attachments is null', () => {
    const dto = { id: 1, title: '任务1', attachments: null }
    const card = taskBackendToCard(dto)
    expect(card.attachments).toEqual([])
  })

  it('preserves deliverables alongside attachments', () => {
    const dto = {
      id: 1,
      title: '任务1',
      attachments: [{ id: 101, name: '附件.pdf' }],
      deliverables: [{ id: 201, name: '交付物.pdf' }],
    }
    const card = taskBackendToCard(dto)
    expect(card.attachments).toHaveLength(1)
    expect(card.deliverables).toHaveLength(1)
    expect(card.hasDeliverable).toBe(true)
  })

  it('maps content from backend DTO', () => {
    const dto = { id: 1, title: '任务1', content: '详细描述内容' }
    const card = taskBackendToCard(dto)
    expect(card.content).toBe('详细描述内容')
  })

  it('falls back to description when content is empty (legacy deposit tasks)', () => {
    // 存量保证金任务：后端早期 createDepositTask 把详细描述写入 description（已修复为 content）。
    // 前端 normalize 必须兜底取 description，否则 UI 显示空、校验误报"请填写详细描述"。
    const dto = { id: 1, title: '缴纳投标保证金', content: '', description: '保证金金额：50万元\n保证金缴纳方式：电汇' }
    const card = taskBackendToCard(dto)
    expect(card.content).toBe('保证金金额：50万元\n保证金缴纳方式：电汇')
  })

  it('falls back to description when content is null', () => {
    const dto = { id: 1, title: '任务1', content: null, description: 'legacy描述' }
    const card = taskBackendToCard(dto)
    expect(card.content).toBe('legacy描述')
  })

  it('returns empty content when both content and description are empty', () => {
    const dto = { id: 1, title: '任务1', content: '', description: '' }
    const card = taskBackendToCard(dto)
    expect(card.content).toBe('')
  })
})

describe('taskFormDtoToBackend', () => {
  it('maps attachments field to dto', () => {
    const form = {
      name: '任务1',
      attachments: [{ id: 101, name: '附件.pdf' }],
    }
    const dto = taskFormDtoToBackend(form)
    expect(dto.attachments).toEqual([{ id: 101, name: '附件.pdf' }])
  })

  it('does not include attachments when undefined', () => {
    const form = { name: '任务1' }
    const dto = taskFormDtoToBackend(form)
    expect(dto.attachments).toBeUndefined()
  })
})
