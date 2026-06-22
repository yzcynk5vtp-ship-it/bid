import { describe, expect, it } from 'vitest'
import detailPageSource from './DetailPage.vue?raw'

/**
 * CO-310 R1: 验证 onCrmOpportunityLinked 函数只调用 linkCrmOpportunity,
 * 不再调用 saveEvaluationDraft 和 submitEvaluationFinal(链路错配修复)。
 *
 * <p>采用源码字符串断言,与现有 DetailPage.spec.js 风格一致。
 * 函数边界通过 "async function onCrmOpportunityLinked" 起始 + 下一个 "async function" / "// ----" 终止界定。
 */
describe('CO-310 R1 — onCrmOpportunityLinked 链路修复', () => {
  // 提取 onCrmOpportunityLinked 函数体(从函数声明到下一个顶层声明)
  const functionStart = detailPageSource.indexOf('async function onCrmOpportunityLinked')
  const functionEnd = detailPageSource.indexOf('// Suppress unused-var warnings')
  const functionBody = detailPageSource.slice(functionStart, functionEnd)

  it('函数存在', () => {
    expect(functionStart).toBeGreaterThan(-1)
    expect(functionEnd).toBeGreaterThan(functionStart)
    expect(functionBody.length).toBeGreaterThan(0)
  })

  it('保留 linkCrmOpportunity 调用(关联 CRM 商机的核心 API)', () => {
    expect(functionBody).toContain('tendersApi.linkCrmOpportunity')
  })

  it('不再调用 saveEvaluationDraft(原 R1 链路错配点)', () => {
    expect(functionBody).not.toContain('tendersApi.saveEvaluationDraft')
  })

  it('不再调用 submitEvaluationFinal(原 R1 链路错配点)', () => {
    expect(functionBody).not.toContain('tendersApi.submitEvaluationFinal')
  })

  it('成功提示文案改为"CRM商机已关联"(去掉"评估表已自动提交")', () => {
    expect(functionBody).toContain("ElMessage.success('CRM商机已关联')")
    expect(functionBody).not.toContain('评估表已自动提交')
  })

  it('保留 loadTenderDetail 刷新(刷新标讯详情)', () => {
    expect(functionBody).toContain('await loadTenderDetail()')
  })

  it('保留 loadEvaluation 刷新(刷新评估表数据)', () => {
    expect(functionBody).toContain('tendersApi.loadEvaluation')
  })
})
