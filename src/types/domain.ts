import type { Component } from 'vue'

export type Role = 'admin' | 'user'
export type MeterType = '居民' | '商业' | '工业'
export type MeterStatus = '在线' | '异常' | '离线'
export type RiskLevel = '高' | '中' | '低'
export type AnomalyStatus = '待处理' | '处理中' | '已完成'

export interface NavigationItem {
  path: string
  label: string
  icon: Component
}

export interface StatCard {
  label: string
  value: string
  note: string
  icon: Component
  tone: string
}

export interface Meter {
  id: string
  user: string
  area: string
  type: MeterType
  reading: number
  status: MeterStatus
  updated: string
}

export interface Anomaly {
  id: string
  meter: string
  area: string
  type: string
  level: RiskLevel
  time: string
  status: AnomalyStatus
}

export interface BillBatch {
  batch: string
  users: number
  amount: string
  paid: string
  status: string
}

export interface ReadingTask {
  name: string
  scope: string
  progress: number
  status: string
  time: string
}
