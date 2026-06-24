// Input: project detail DTO
// Output: pure project-created activity timeline helper for project detail sidebar
// Pos: src/composables/projectDetail/ - Project detail composition helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md.

function pad(value) {
  return String(value).padStart(2, '0')
}

export function formatProjectActivityTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function isBlank(value) {
  return value == null || String(value).trim() === ''
}

export function resolveProjectCreatorName(project) {
  const candidates = [
    project?.createdByName,
    project?.creatorName,
    project?.ownerName,
    project?.managerName,
  ]
  const found = candidates.find((value) => !isBlank(value))
  return found == null ? '系统' : String(found).trim()
}

export function buildProjectCreatedActivity(project = {}) {
  if (!project?.id) return []
  return [{
    id: `project-created-${project.id}`,
    user: resolveProjectCreatorName(project),
    action: '创建了项目',
    time: formatProjectActivityTime(project.createdAt || project.createTime || project.updatedAt || project.updateTime)
  }]
}
