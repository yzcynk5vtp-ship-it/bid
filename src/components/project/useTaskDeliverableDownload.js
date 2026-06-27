import { ElMessage } from 'element-plus'
import { getTaskDeliverableDownloadUrl } from '@/api/modules/taskDeliverables.js'
import { downloadWithFilename } from '@/utils/download.js'

/**
 * 任务交付物下载链接逻辑。
 * 负责构造交付物的后端下载 API URL，避免直接使用 doc-insight:// 协议 URL。
 *
 * @param {Object} localValue - 任务表单数据（含 deliverables 数组）
 * @returns {Object} { getDeliverableDownloadUrl, downloadDeliverable } - 交付物下载相关函数
 */
export function useTaskDeliverableDownload(localValue) {
  /**
   * 构造单个交付物的后端下载 API URL。
   * @param {Object} deliverable - 交付物对象
   * @returns {string} 下载 API URL
   */
  function getDeliverableDownloadUrl(deliverable) {
    if (!deliverable?.id) return ''
    const projectId = deliverable.projectId || localValue.projectId
    const taskId = deliverable.taskId || localValue.id
    return getTaskDeliverableDownloadUrl(projectId, taskId, deliverable.id)
  }

  /**
   * 通过 axios 下载交付物文件，携带认证 token 并处理 403/401 错误。
   * 避免使用 <a :href> 直接导航导致会话过期时显示空白页。
   * @param {Object} deliverable - 交付物对象
   */
  async function downloadDeliverable(deliverable) {
    const url = getDeliverableDownloadUrl(deliverable)
    if (!url) {
      ElMessage.info('文件地址不可用')
      return
    }
    await downloadWithFilename(url, deliverable?.name || 'download')
  }

  return {
    getDeliverableDownloadUrl,
    downloadDeliverable,
  }
}
