import { ref, computed } from 'vue'
import { useProjectStore } from '@/stores/project'
import { useProjectPagination } from './useProjectPagination.js'

function byStringField(a, b, field, order) {
  const va = (a[field] || '').toLowerCase()
  const vb = (b[field] || '').toLowerCase()
  return order === 'ascending' ? va.localeCompare(vb) : vb.localeCompare(va)
}
function byNumberField(a, b, field, order) {
  const va = Number(a[field]) || 0
  const vb = Number(b[field]) || 0
  return order === 'ascending' ? va - vb : vb - va
}
function byDateField(a, b, field, order) {
  const va = a[field] ? new Date(a[field]).getTime() : 0
  const vb = b[field] ? new Date(b[field]).getTime() : 0
  return order === 'ascending' ? va - vb : vb - va
}

/**
 * Match a filter value against a field value.
 * Handles both string (single-select) and array (multi-select) filter values.
 * Empty arrays are treated as "no filter" (always match).
 */
function matchFilter(filterVal, fieldVal) {
  if (!filterVal) return true
  if (Array.isArray(filterVal)) {
    return filterVal.length === 0 || filterVal.includes(fieldVal)
  }
  return fieldVal === filterVal
}

const SORTERS = {
  createdAt: byDateField,
  bidOpenTime: byDateField,
  revenue: byNumberField,
}

/**
 * Composable for project list filtering and pagination.
 * Extracted from List.vue to keep template under line budget.
 */
export function useProjectFilter(searchForm) {
  const projectStore = useProjectStore()
  const loading = ref(false)
  const error = ref(null)
  const sortProp = ref(null)
  const sortOrder = ref(null)

  const matchedProjects = computed(() => {
    const f = searchForm.value
    return (projectStore.projects || []).filter((p) => {
      if (f.name && !(p.name || '').includes(f.name) && !(p.projectName || '').includes(f.name)) return false
      if (f.ownerUnit && !(p.ownerUnit || '').includes(f.ownerUnit)) return false
      if (!matchFilter(f.projectType, p.projectType)) return false
      if (!matchFilter(f.customerType, p.customerType)) return false
      if (!matchFilter(f.sourceModule, p.sourceModule)) return false
      if (!matchFilter(f.bidStatus, p.bidStatus)) return false
      if (!matchFilter(f.stage, p.stage)) return false
      if (f.projectLeaderName && !(p.projectLeaderName || '').includes(f.projectLeaderName)) return false
      if (f.biddingLeaderName && !(p.biddingLeaderName || '').includes(f.biddingLeaderName)) return false
      if (f.leaderDepartment && p.leaderDepartment !== f.leaderDepartment) return false
      if (f.region && !(p.region || '').includes(f.region)) return false
      if (f.biddingPlatform && !(p.biddingPlatform || '').includes(f.biddingPlatform)) return false
      if (f.bidMonth && p.bidMonth !== f.bidMonth) return false
      if (!matchFilter(f.priority, p.priority)) return false
      if (f.shortlistedCountMin != null && (p.shortlistedCount == null || p.shortlistedCount < f.shortlistedCountMin)) return false
      if (f.shortlistedCountMax != null && (p.shortlistedCount == null || p.shortlistedCount > f.shortlistedCountMax)) return false
      if (f.revenueMin != null) {
        const rev = Number(p.budget || 0)
        if (rev < f.revenueMin) return false
      }
      if (f.revenueMax != null) {
        const rev = Number(p.budget || 0)
        if (rev > f.revenueMax) return false
      }
      if (f.bidOpenTimeRange && f.bidOpenTimeRange.length === 2) {
        const bt = p.bidOpenTime ? new Date(p.bidOpenTime) : null
        if (bt) {
          const end = new Date(f.bidOpenTimeRange[1])
          end.setHours(23, 59, 59)
          if (bt < new Date(f.bidOpenTimeRange[0]) || bt > end) return false
        }
      }
      if (f.createTimeRange && f.createTimeRange.length === 2) {
        const ct = p.createdAt ? new Date(p.createdAt) : null
        if (ct) {
          const end = new Date(f.createTimeRange[1])
          end.setHours(23, 59, 59)
          if (ct < new Date(f.createTimeRange[0]) || ct > end) return false
        }
      }
      return true
    })
  })

  const sortedProjects = computed(() => {
    const prop = sortProp.value
    const order = sortOrder.value
    if (!prop || !order || !SORTERS[prop]) return matchedProjects.value
    const sorter = SORTERS[prop]
    const sorted = [...matchedProjects.value]
    sorted.sort((a, b) => sorter(a, b, prop, order))
    return sorted
  })

  const { pagination, filteredProjects, handleSizeChange, handlePageChange, resetPage } =
    useProjectPagination(sortedProjects)

  const handleSortChange = ({ prop, order }) => {
    sortProp.value = prop || null
    sortOrder.value = order || null
    resetPage()
  }

  return {
    loading,
    error,
    matchedProjects,
    pagination,
    filteredProjects,
    handleSizeChange,
    handlePageChange,
    resetPage,
    handleSortChange,
  }
}
