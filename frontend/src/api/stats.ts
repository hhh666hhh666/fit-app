import request, { unwrap } from './request'

// ── 统计页（period 只影响 count/duration；weekly 与 prs 固定随屏返回）──

export type StatsPeriod = 'week' | 'month' | 'year'

export interface StatsWeekBar {
  label: string
  count: number
}

export interface StatsPrItem {
  exerciseId: number
  /** 动作名快照。 */
  name: string
  weight: number
  reps: number
  performedAt: string
}

export interface StatsData {
  count: number
  durationSec: number
  weekly: StatsWeekBar[]
  prs: StatsPrItem[]
}

export function getStats(period: StatsPeriod) {
  return unwrap(request.get<StatsData>('/stats', { period }))
}
