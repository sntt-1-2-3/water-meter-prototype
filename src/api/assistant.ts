import { apiClient } from './client'
export async function askAssistant(question: string) {
  const response = await apiClient.post<{ answer: string }>('/ai/query', { question })
  return response.data.answer
}
