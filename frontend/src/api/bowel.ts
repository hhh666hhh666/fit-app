import request, { unwrap } from './request'
import { mutating, read } from './cache'

// ── 排便记录（ADR 0061）：一次大便或小便；入口在首页餐次入口的第二页 ──

export type BowelKind = 'stool' | 'urine'

export interface BowelRecord {
  id: number
  kind: BowelKind
  /** 时刻 `YYYY-MM-DDTHH:mm:ss`（只到分）。 */
  occurredAt: string
  /** 布里斯托形态 1–7：只有大便可能有。 */
  bristol?: number | null
  /** 小便颜色 1–7（见 URINE_COLORS）：只有小便可能有。 */
  color?: number | null
  note?: string | null
}

export interface BowelSave {
  kind?: BowelKind
  occurredAt?: string
  bristol?: number | null
  color?: number | null
  note?: string | null
}

export interface BowelKindStats {
  count: number
  perDay: number
  /** 相邻两次的平均间隔（小时）；不到两次为 null。 */
  avgIntervalHours: number | null
  /** 下标＝几点。 */
  hours: number[]
}

export interface BowelStats {
  from: string
  to: string
  days: { date: string; stool: number; urine: number }[]
  stool: BowelKindStats
  urine: BowelKindStats
  /** 下标 0–6 对应 1–7 型。 */
  bristol: number[]
  /** 小便颜色分布：下标 0–6 对应 1–7 档。 */
  color: number[]
}

/** 历史：最近的在前。 */
export function listBowel(limit = 30, offset = 0) {
  return read<BowelRecord[]>('/bowel', { limit, offset }, 'bowel', offset === 0)
}

/** 统计：最近 days 天（含今天）；0＝从第一条记录起。 */
export function getBowelStats(days: number) {
  return read<BowelStats>('/bowel/stats', { days }, 'bowel')
}

/** 记一次；时刻不给就是现在。 */
export function addBowel(payload: BowelSave & { kind: BowelKind }) {
  return mutating('bowel', () => unwrap(request.post<BowelRecord>('/bowel', payload)))
}

/** 改一次：时刻、形态、备注整体覆盖。 */
export function updateBowel(id: number, payload: BowelSave) {
  return mutating('bowel', () => unwrap(request.put<BowelRecord>(`/bowel/${id}`, payload)))
}

/** 删一次：进回收站。 */
export function deleteBowel(id: number) {
  return mutating('bowel', () => unwrap(request.delete<void>(`/bowel/${id}`)))
}
