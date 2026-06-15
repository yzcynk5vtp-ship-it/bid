/**
 * API 配置文件
 * 真实 API 为唯一数据源
 */

const viteEnv = typeof import.meta !== 'undefined' && import.meta.env ? import.meta.env : {}
const DEFAULT_API_HOST = '127.0.0.1'
const DEFAULT_API_PORT = 18080

const trimTrailingSlash = (value) => value.replace(/\/+$/, '')

const normalizeApiBaseUrl = (rawValue) => {
  const fallback = `http://${DEFAULT_API_HOST}:${DEFAULT_API_PORT}`
  const value = String(rawValue || '').trim()

  if (!value) {
    return ''
  }

  // 支持相对路径（如 /api），让浏览器通过当前 host + nginx 反向代理访问后端
  if (value.startsWith('/')) {
    return trimTrailingSlash(value)
  }

  if (/^https?:\/\//i.test(value)) {
    return trimTrailingSlash(value)
  }

  const missingHostMatch = value.match(/^\/{0,2}:([0-9]{2,5})$/)
  if (missingHostMatch) {
    return `http://${DEFAULT_API_HOST}:${missingHostMatch[1]}`
  }

  if (/^[a-z0-9.-]+:[0-9]{2,5}$/i.test(value)) {
    return `http://${value}`
  }

  console.warn(`[api/config] Invalid VITE_API_BASE_URL "${value}", fallback to ${fallback}`)
  return fallback
}

export const API_BASE_URL = normalizeApiBaseUrl(viteEnv.VITE_API_BASE_URL)

export const API_CONFIG = {
  mode: 'api',
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
}

export const isCommercialMode = () => true

export const getApiUrl = (path) => `${API_BASE_URL}${path}`

export default API_CONFIG
