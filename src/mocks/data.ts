import { markRaw } from 'vue'
import { CircleCheck, Coin, Odometer, Warning } from '@element-plus/icons-vue'
import type { Anomaly, BillBatch, Meter, ReadingTask, StatCard } from '../types/domain'

export const initialStats: StatCard[] = [
  { label: '水表总数', value: '1,000', note: '在线 986 台', icon: markRaw(Odometer), tone: 'cyan' },
  { label: '今日抄表率', value: '98.6%', note: '较昨日 +1.2%', icon: markRaw(CircleCheck), tone: 'green' },
  { label: '本月应收', value: '¥86,420', note: '已收 91.2%', icon: markRaw(Coin), tone: 'blue' },
  { label: '待处理异常', value: '12', note: '高风险 3 条', icon: markRaw(Warning), tone: 'orange' },
]

const areas = ['滨江花园', '江南新城', '中央商务区', '高新工业园']
const users = ['张晓明', '李文静', '青禾餐饮', '澜庭酒店', '创智制造', '远航材料']
const types = ['居民', '居民', '商业', '商业', '工业', '工业'] as const

export const initialMeters: Meter[] = Array.from({ length: 36 }, (_, index) => {
  const type = types[index % types.length]
  const prefix = type === '居民' ? 'A' : type === '商业' ? 'B' : 'C'
  return {
    id: `${prefix}-${String(index + 201).padStart(5, '0')}`,
    user: index < users.length ? users[index] : `${type}用户 ${index + 1}`,
    area: areas[index % areas.length],
    type,
    reading: Number((680 + index * 437.26).toFixed(1)),
    status: index % 13 === 0 ? '异常' : index % 17 === 0 ? '离线' : '在线',
    updated: index % 17 === 0 ? '2 小时前' : `${(index % 8) + 1} 分钟前`,
  }
})

export const initialAnomalies: Anomaly[] = [
  { id: 'AL-260919-038', meter: 'A-00038', area: '滨江花园', type: '持续高流量', level: '高', time: '09-19 08:42', status: '待处理' },
  { id: 'AL-260919-112', meter: 'C-00112', area: '高新工业园', type: '阀门故障 E03', level: '高', time: '09-19 08:31', status: '处理中' },
  { id: 'AL-260919-074', meter: 'B-00074', area: '中央商务区', type: '连续零流量', level: '中', time: '09-19 07:56', status: '待处理' },
  { id: 'AL-260918-206', meter: 'A-00206', area: '江南新城', type: '夜间小流量', level: '中', time: '09-18 23:18', status: '已完成' },
  { id: 'AL-260918-091', meter: 'A-00091', area: '滨江花园', type: '数据突变', level: '低', time: '09-18 18:05', status: '已完成' },
  { id: 'AL-260918-165', meter: 'B-00165', area: '中央商务区', type: '瞬时流量突增', level: '高', time: '09-18 16:40', status: '待处理' },
  { id: 'AL-260918-147', meter: 'A-00147', area: '江南新城', type: '电池电压偏低', level: '低', time: '09-18 15:22', status: '处理中' },
]

export const initialBills: BillBatch[] = [
  { batch: '2026-09 居民用水', users: 628, amount: '¥32,680.40', paid: '94.6%', status: '收费中' },
  { batch: '2026-09 商业用水', users: 214, amount: '¥31,425.80', paid: '89.1%', status: '收费中' },
  { batch: '2026-09 工业用水', users: 158, amount: '¥22,313.80', paid: '87.5%', status: '收费中' },
]

export const initialTasks: ReadingTask[] = [
  { name: '09-21 日常抄表任务', scope: '全部区域 · 1,000 台', progress: 98.6, status: '执行完成', time: '09:00' },
  { name: '09-20 漏抄补采任务', scope: '滨江花园 · 14 台', progress: 100, status: '补采完成', time: '18:30' },
  { name: '09 月账单核算任务', scope: '居民 / 商业 / 工业', progress: 82, status: '计算中', time: '10:15' },
]
