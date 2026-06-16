// @ui-cover:bidding
// CRM 商机选择器回归测试。
// 验证标讯详情页打开「关联 CRM 商机」时，按产品蓝图
// 「招标主体 + 报名截止时间 + 开标时间」调用 search-by-tender，并能展示匹配商机。
//
// 运行依赖：
//   后端必须能访问真实 CRM，且存在与测试标讯匹配的商机。
//   通过环境变量配置：
//     CRM_E2E_TENDER_ID      已存在的测试标讯 ID（若未设置则尝试新建标讯）
//     CRM_E2E_GROUP_NAME     招标主体/CRM groupName，如 "山东海化集团有限公司"
//     CRM_E2E_EVALUATION_DATE 评标日期 yyyy-MM-dd，需与 CRM 商机 evaluationTime 一致
//     CRM_E2E_EXPECTED_OPPORTUNITY_NAME  期望在选择器看到的商机名称（可选）
//   本地无 CRM 环境时测试会被跳过。

import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

const crmGroupName = process.env.CRM_E2E_GROUP_NAME
const crmEvaluationDate = process.env.CRM_E2E_EVALUATION_DATE
const expectedOpportunityName = process.env.CRM_E2E_EXPECTED_OPPORTUNITY_NAME
const existingTenderId = process.env.CRM_E2E_TENDER_ID

async function apiFetch(token, path, options = {}) {
  const url = `${apiBaseUrl}${path}`
  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
    ...(options.headers || {}),
  }
  const response = await fetch(url, { ...options, headers })
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${url} failed ${response.status}: ${JSON.stringify(payload)}`)
  }
  return payload
}

async function createTender(token, { title, purchaserName, deadline, registrationDeadline, bidOpeningTime }) {
  const payload = await apiFetch(token, '/api/tenders', {
    method: 'POST',
    body: JSON.stringify({
      title,
      purchaserName,
      deadline,
      registrationDeadline,
      bidOpeningTime,
    }),
  })
  return payload?.data
}

async function assignTender(token, tenderId, assigneeId) {
  const payload = await apiFetch(token, '/api/batch/tenders/assign', {
    method: 'POST',
    body: JSON.stringify({ tenderIds: [Number(tenderId)], assigneeId: Number(assigneeId) }),
  })
  return payload?.data
}

test.describe('bidding CRM opportunity', () => {
  test('opportunity center is enabled in bidding page', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_crm_${suffix}`,
      role: 'ADMIN',
      fullName: 'E2E CRM Admin'
    })

    await injectSession(page, session)
    await page.goto('/bidding')
    await expect(page.getByText('标讯列表').first()).toBeVisible()
  })

  test('selector matches CRM opportunities by blueprint criteria', async ({ page }) => {
    test.skip(
      !crmGroupName || !crmEvaluationDate,
      '跳过：未设置 CRM_E2E_GROUP_NAME / CRM_E2E_EVALUATION_DATE，无法匹配真实 CRM 数据'
    )

    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_crm_blueprint_${suffix}`,
      role: 'ADMIN',
      fullName: 'E2E CRM Blueprint Admin'
    })
    await injectSession(page, session)

    let tenderId = existingTenderId ? Number(existingTenderId) : null

    if (!tenderId) {
      // 新建标讯：日期使用 CRM 评标日期，且必须满足 @Future 校验
      const registrationDeadline = `${crmEvaluationDate}T23:59:00`
      const bidOpeningTime = `${crmEvaluationDate}T10:00:00`
      const deadline = '2030-12-31T23:59:00'
      const tender = await createTender(session.token, {
        title: `E2E-CRM-Blueprint-${suffix}`,
        purchaserName: crmGroupName,
        deadline,
        registrationDeadline,
        bidOpeningTime,
      })
      tenderId = tender?.id
      if (!tenderId) {
        throw new Error('创建测试标讯失败，无法继续 E2E 验证')
      }
    }

    // 将标讯分配给当前测试用户，使选择器可用
    await assignTender(session.token, tenderId, session.user.id)

    // 打开标讯详情
    await page.goto(`/bidding/detail/${tenderId}`)
    await expect(page.getByText('基本信息').first()).toBeVisible()
    await page.getByRole('tab', { name: '基本信息' }).click()

    // 监听 search-by-tender 请求并断言参数
    const searchByTenderPromise = page.waitForRequest((req) =>
      req.url().includes('/api/xiyu/crm/chances/search-by-tender') && req.method() === 'POST'
    )

    // 打开选择器
    await page.getByRole('button', { name: '点击关联CRM商机' }).click()
    await expect(page.getByText('选择关联的CRM商机').first()).toBeVisible()

    const request = await searchByTenderPromise
    const requestBody = JSON.parse(request.postData() || '{}')
    expect(requestBody.tenderer?.trim()).toBe(crmGroupName)
    expect(requestBody.registrationDeadline).toContain(crmEvaluationDate)
    expect(requestBody.bidOpeningTime).toContain(crmEvaluationDate)

    // 等待结果表格渲染
    const table = page.locator('.crm-opportunity-selector .el-table__body-wrapper table')
    await expect(table.locator('tr').first()).toBeVisible({ timeout: 15000 })

    if (expectedOpportunityName) {
      await expect(page.locator('.crm-opportunity-selector').getByText(expectedOpportunityName).first()).toBeVisible()
    } else {
      const rows = await table.locator('tr').count()
      expect(rows, '选择器应按蓝图条件返回至少一条 CRM 商机').toBeGreaterThan(0)
    }
  })
})
