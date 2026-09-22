import { apiClient } from './client'
import type { Anomaly, BillBatch, Meter, ReadingTask, StatCard } from '../types/domain'

export interface DashboardData {
  stats: StatCard[]
  trend: number[]
  categories: Array<{ name: string; value: number }>
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export const waterApi = {
  getDashboard: () => apiClient.get<DashboardData>('/dashboard'),
  getMeters: (params: { page: number; pageSize: number; keyword?: string; type?: string }) => apiClient.get<PageResult<Meter>>('/meters', { params }),
  createMeter: (meter: Meter) => apiClient.post<Meter>('/meters', meter),
  updateMeter: (id: string, meter: Meter) => apiClient.put<Meter>(`/meters/${id}`, meter),
  startReading: () => apiClient.post<ReadingTask>('/readings/tasks'),
  getReadingTasks: () => apiClient.get<ReadingTask[]>('/readings/tasks'),
  getBills: () => apiClient.get<BillBatch[]>('/bills'),
  recalculateBills: () => apiClient.post('/bills/recalculate'),
  getAnomalies: () => apiClient.get<Anomaly[]>('/anomalies'),
  updateAnomaly: (id: string, status: Anomaly['status']) => apiClient.patch<Anomaly>(`/anomalies/${id}`, { status }),
}
