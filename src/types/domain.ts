import type { Component } from 'vue'

export type Role = 'ADMIN' | 'OPERATOR' | 'FINANCE' | 'AUDITOR' | 'USER'
export type MeterType = '居民' | '商业' | '工业'
export type MeterStatus = '在线' | '异常' | '离线'
export type RiskLevel = '高' | '中' | '低'
export type AnomalyStatus = '待处理' | '处理中' | '已完成'
export interface UserSession {
  id: number
  username: string
  name: string
  phone?: string
  role: Role
  areaScope?: string | null
  boundMeterId?: string | null
  enabled?: boolean
}
export interface LoginResponse extends UserSession { token: string }
export interface RegistrationPayload { username: string; name: string; phone: string; password: string }
export interface SystemNotification {
  id: number
  title: string
  content: string
  time: string
  type: string
  route?: string | null
  read: boolean
  readAt?: string | null
}
export interface Feedback {
  id: number
  username: string
  userName: string
  meterId: string
  area: string
  type: string
  description: string
  status: AnomalyStatus
  resolution?: string | null
  handledBy?: string | null
  createdAt: string
  updatedAt: string
}
export interface NavigationItem { path: string; label: string; icon: Component }
export interface StatCard { label: string; value: string; note: string; icon?: Component; tone: string }
export interface Meter { id: string; user: string; area: string; type: MeterType; reading: number; status: MeterStatus; updated: string | null }
export interface MeterRequest { meterId: string; userName: string; area: string; type: MeterType; reading: number; lastReading?: number; status: MeterStatus }
export interface Anomaly { id: string; meter: string; area: string; type: string; level: RiskLevel; time: string; status: AnomalyStatus; alarmCode?: string; suggestion?: string; confirmedBy?: string; confirmedAt?: string }
export interface BillBatch { batch: string; users: number; amount: number | string; paid: string; status: string; period?: string }
export interface BillItem {
  id: number
  batch: string
  period: string
  meterId: string
  userName: string
  area: string
  meterType: MeterType
  usage: number
  openingReading: number
  closingReading: number
  waterFee: number
  penalty: number
  totalDue: number
  paidAmount: number
  outstanding: number
  overdueDays: number
  status: string
  dueDate: string
  paidAt?: string | null
}
export interface Payment {
  id: number
  transactionNo: string
  billItemId: number
  meterId: string
  amount: number
  paidAt: string
  payer: string
  channel: string
  idempotencyKey?: string
}
export interface ReadingTask {
  id?: number
  name: string
  scope: string
  progress: number
  status: string
  time: string
  period?: string
  total?: number
  success?: number
  missed?: number
  failed?: number
  retryCount?: number
  errorMessage?: string | null
  endTime?: string | null
}
export interface PriceRule { meterType: MeterType; tier: number; fromUsage: number; toUsage: number | null; unitPrice: number; penaltyRate: number }
export interface UserSummary { name: string; meterId: string | null; address: string | null; usage: number; amount: number; paid: number; outstanding: number; status: string; reading: number; trend: Array<{ date: string; usage: number }> }
export interface DashboardData {
  stats: StatCard[]
  trend: number[]
  categories: Array<{ name: string; value: number }>
  trendDates?: string[]
}
export interface PageResult<T> { items: T[]; total: number; page: number; pageSize: number }
