// Input: picker mode (search|candidates) + optional context/deptCode/roleCode
// Output: reactive options list, loading flag, debounced search, loadCandidates, formatLabel
// Pos: src/composables/ - shared user picker composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { ref } from 'vue'
import { usersApi } from '@/api/modules/users.js'
import { formatDisplayName } from '@/utils/formatDisplayName.js'

const DEBOUNCE_MS = 300

export function useUserPicker(options = {}) {
  const {
    mode = 'search',
    context,
    deptCode,
    roleCode,
  } = options

  const userOptions = ref([])
  const loading = ref(false)

  let debounceTimer = null

  function debounce(fn, delay) {
    return function debounced(...args) {
      if (debounceTimer !== null) {
        clearTimeout(debounceTimer)
      }
      debounceTimer = setTimeout(() => {
        debounceTimer = null
        fn(...args)
      }, delay)
    }
  }

  async function searchUsers(query) {
    const trimmed = query == null ? '' : String(query).trim()
    if (trimmed.length === 0) {
      userOptions.value = []
      return
    }
    loading.value = true
    try {
      const results = await usersApi.search(trimmed, 10)
      userOptions.value = Array.isArray(results) ? results : []
    } catch (err) {
      console.error('[useUserPicker] search failed', err)
      userOptions.value = []
    } finally {
      loading.value = false
    }
  }

  const search = debounce(searchUsers, DEBOUNCE_MS)

  async function loadCandidates() {
    loading.value = true
    try {
      const results = await usersApi.getAssignableCandidates({
        context,
        deptCode,
        roleCode,
      })
      userOptions.value = Array.isArray(results) ? results : []
    } catch (err) {
      console.error('[useUserPicker] loadCandidates failed', err)
      userOptions.value = []
    } finally {
      loading.value = false
    }
  }

  function formatLabel(user) {
    if (!user) return ''
    const name = user.name || user.fullName || ''
    if (!name) return ''
    const parts = [user.deptName, user.roleName].filter(Boolean)
    if (parts.length === 0) return name
    return `${name}（${parts.join('·')}）`
  }

  return {
    options: userOptions,
    loading,
    search,
    loadCandidates,
    formatLabel,
  }
}
