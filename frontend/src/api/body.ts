import request, { unwrap } from './request'

// ── 身体档案（体重／四围定期记录）──

export interface BodyLatest {
  date: string
  weight: number
  note: string
}

export interface BodyWeightPoint {
  date: string
  weight: number
}

export interface BodyGirth {
  date: string
  chest: number | null
  waist: number | null
  hip: number | null
  arm: number | null
}

export interface BodyOverview {
  latest: BodyLatest | null
  /** 较上周（kg，负＝降）；无从比为 null。 */
  weekDiff: number | null
  isLowest: boolean
  weightSeries: BodyWeightPoint[]
  girth: BodyGirth | null
}

export interface BodyRecordRow {
  id: number
  date: string
  weight: number
  note: string
  /** 与时间上相邻上一条之差（首条 null）。 */
  diff: number | null
}

export interface BodySavePayload {
  measuredAt?: string
  weight?: number | null
  chest?: number | null
  waist?: number | null
  hip?: number | null
  arm?: number | null
  note?: string
}

export function getBodyOverview() {
  return unwrap(request.get<BodyOverview>('/body'))
}

export function getBodyRecords(limit = 20) {
  return unwrap(request.get<BodyRecordRow[]>('/body/records', { limit }))
}

export function createBodyRecord(payload: BodySavePayload) {
  return unwrap(request.post<BodyRecordRow>('/body', payload))
}
