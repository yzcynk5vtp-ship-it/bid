import { ref } from 'vue'
import { ElMessage } from 'element-plus'

/**
 * Composable for on-demand password reveal with audit logging.
 *
 * <p>The list API no longer returns plaintext passwords. This composable
 * fetches a single account password only when the user explicitly asks to
 * view or copy it, records an audit entry, and caches the result for the
 * current page session.</p>
 *
 * @param {function(number): Promise<{success:boolean, data?:{password?:string, auditId?:string}, msg?:string}>} fetcher
 */
export function usePasswordReveal(fetcher) {
  const visible = ref({})
  const revealed = ref({})
  const auditLogs = ref([])
  const loading = ref({})

  const ensurePassword = async (accountId) => {
    if (revealed.value[accountId] !== undefined) {
      return revealed.value[accountId]
    }
    loading.value[accountId] = true
    try {
      const res = await fetcher(accountId)
      if (!res?.success || !res?.data?.password) {
        ElMessage.info(res?.msg || '当前账号密码不可直接查看')
        return null
      }
      revealed.value[accountId] = res.data.password
      if (res.data.auditId) {
        auditLogs.value.push({
          accountId,
          auditId: res.data.auditId,
          viewedAt: new Date().toISOString()
        })
      }
      return res.data.password
    } catch (e) {
      console.error('Failed to reveal password:', e)
      ElMessage.error('密码获取失败')
      return null
    } finally {
      loading.value[accountId] = false
    }
  }

  const toggle = async (accountId) => {
    if (visible.value[accountId]) {
      visible.value[accountId] = false
      return
    }
    const password = await ensurePassword(accountId)
    if (password) {
      visible.value[accountId] = true
    }
  }

  const copy = async (accountId) => {
    const password = await ensurePassword(accountId)
    if (!password) return
    try {
      await navigator.clipboard.writeText(password)
      ElMessage.success('密码已复制到剪贴板')
    } catch {
      ElMessage.error('复制失败')
    }
  }

  const displayText = (accountId) => {
    return visible.value[accountId] ? revealed.value[accountId] : '••••••'
  }

  const isVisible = (accountId) => !!visible.value[accountId]
  const isLoading = (accountId) => !!loading.value[accountId]

  return {
    visible,
    revealed,
    auditLogs,
    loading,
    ensurePassword,
    toggle,
    copy,
    displayText,
    isVisible,
    isLoading
  }
}
