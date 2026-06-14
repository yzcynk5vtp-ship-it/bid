// Input: Playwright E2E env vars and backend auth endpoints
// Output: shared helpers for authenticated API-backed Playwright sessions
// Pos: e2e/ - Playwright end-to-end coverage
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// H13 根治 (2026-06-14): access token 走 HttpOnly cookie (不再放 response body).
// E2E 从 login 响应的 Set-Cookie 提取 access_token (供浏览器外 Bearer header),
// 浏览器内注入 access_token cookie + user hint (前端走 cookie 认证, 读 storage user).

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'
const frontendBaseUrl = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:1314'
const defaultPassword = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${url} failed with status ${response.status}: ${JSON.stringify(payload)}`)
  }

  return payload
}

// H13 根治: 从 Set-Cookie 提取 access_token (body 不再含 token)
function extractAccessTokenFromResponse(response) {
  const setCookie = response.headers.get('set-cookie') || ''
  const match = setCookie.match(/access_token=([^;]+)/)
  if (!match) {
    throw new Error('Login response missing access_token cookie (H13)')
  }
  return match[1]
}

function extractUser(data) {
  if (!data?.id) {
    throw new Error('Backend response missing user identity')
  }
  return {
    id: data.id,
    name: data.fullName || data.username,
    username: data.username,
    email: data.email,
    role: String(data.role || '').toLowerCase()
  }
}

async function loginForSession(username, password) {
  const response = await fetch(`${apiBaseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(`login failed ${response.status}: ${JSON.stringify(payload)}`)
  }
  return { token: extractAccessTokenFromResponse(response), data: payload?.data }
}

export async function ensureApiSession({ username, role = 'ADMIN', fullName, password = defaultPassword }) {
  const email = `${username}@example.com`

  // 注册新用户 (H13: register 不签 token, 仅创建账号; 409 已存在走 login)
  try {
    await requestJson(`${apiBaseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username,
        password,
        email,
        fullName: fullName || username,
        role
      })
    })
  } catch (error) {
    const message = String(error.message)
    // 409 表示用户已存在，需要走登录流程
    if (!message.includes('409') && !message.includes('already exists')) {
      throw error
    }
  }

  // login: H13 access token 走 Set-Cookie (body.token=null)
  const { token, data } = await loginForSession(username, password)
  return { token, user: extractUser(data) }
}

export async function injectSession(page, session) {
  // 给浏览器 context 授权剪贴板读写，避免 E2E 中 navigator.clipboard 抛 NotAllowedError
  try {
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write'])
  } catch (permErr) {
    // 某些浏览器实现不支持该 API；忽略即可
  }

  await page.addInitScript(({ apiBaseUrl: browserApiBaseUrl }) => {
    const existingProcess = globalThis.process || { env: {} }
    existingProcess.env = existingProcess.env || {}
    existingProcess.env.VITE_API_BASE_URL = existingProcess.env.VITE_API_BASE_URL || browserApiBaseUrl
    globalThis.process = existingProcess
  }, { apiBaseUrl })

  // H13 根治: 注入 access_token cookie (前端+后端域, 浏览器自动带) + user hint (前端读 storage user)
  await page.context().addCookies([
    { name: 'access_token', value: session.token, url: apiBaseUrl, httpOnly: true, sameSite: 'Lax' },
    { name: 'access_token', value: session.token, url: frontendBaseUrl, httpOnly: true, sameSite: 'Lax' }
  ])

  await page.addInitScript(({ currentUser }) => {
    sessionStorage.setItem('user', JSON.stringify(currentUser))
  }, { currentUser: session.user })
}

export { apiBaseUrl, frontendBaseUrl, defaultPassword }

export async function injectAuthToken(page, { role = 'ADMIN', fullName = role, password = defaultPassword }) {
  const username = role.toLowerCase() + '_e2e_' + Date.now()
  const session = await ensureApiSession({ username, role, fullName, password })
  await injectSession(page, session)
}
