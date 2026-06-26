import httpClient from '../client.js'

export const tasksApi = {
  async getTaskById(taskId) {
    return httpClient.get(`/api/tasks/${taskId}`)
  },
}
