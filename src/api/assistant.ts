import { apiClient, useRealApi } from './client'

function mockAnswer(prompt: string) {
  if (prompt.includes('小区') || prompt.includes('区域')) return '本月用水量最高的是中央商务区，累计 42,680 m³，占全部用水量的 34%。主要来自澜庭酒店和青禾餐饮两户商业用户。'
  if (prompt.includes('异常')) return '当前有 3 条高风险异常：A-00038 持续高流量、C-00112 阀门故障、B-00165 瞬时流量突增。建议优先派单检查前两项。'
  if (prompt.includes('账单') || prompt.includes('收费')) return '本月应收 ¥86,420，已收 ¥78,816，综合收费率 91.2%。商业与工业用户是当前催缴重点。'
  return '本月累计用水 125,420 m³，抄表覆盖率 98.6%，待处理异常 12 条，整体运营稳定。建议继续关注夜间小流量和离线设备。'
}

export async function askAssistant(prompt: string) {
  if (useRealApi) {
    const response = await apiClient.post<{ answer: string }>('/ai/query', { question: prompt })
    return response.data.answer
  }
  await new Promise((resolve) => window.setTimeout(resolve, 650))
  return mockAnswer(prompt)
}
