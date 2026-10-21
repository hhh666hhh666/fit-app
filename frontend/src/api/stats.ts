import { read } from './cache'

// ── 统计页（period 只影响 count/duration；weekly 与 prs 固定随屏返回）──
//    数据全部来自训练记录，所以归「训练」域：改训练即作废。
//    ⚠️ 「本周／本月／全年」是**时间窗口**，跨零点算的不是同一段区间——缓存靠按自然日分区兜住（ADR 0035）。

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
  return read<StatsData>('/stats', { period }, 'workout')
}
