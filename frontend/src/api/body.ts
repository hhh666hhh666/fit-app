import request, { unwrap } from './request'

// ── 身体档案（体重／四围定期记录；一条＝一次测定，ADR 0017）──

export interface BodyLatest {
  date: string
  weight: number
  note: string
}

export interface BodyWeightPoint {
  date: string
  weight: number
}

/** 围度序列的点：四个部位各取当天最小，当天没量的是 null。 */
export interface BodyGirthPoint {
  date: string
  chest: number | null
  waist: number | null
  hip: number | null
  arm: number | null
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
  /** 按日聚合，同一天取最小。 */
  weightSeries: BodyWeightPoint[]
  /** 按日聚合，各部位分别取当天最小。 */
  girthSeries: BodyGirthPoint[]
  girth: BodyGirth | null
  /** 最新体脂率（体脂非空的最近一条）；从未测过为 null。 */
  bodyFat?: { date: string; value: number } | null
  /** 体脂序列（按日聚合取最小）。 */
  bodyFatSeries?: BodyFatPoint[]
}

export interface BodyFatPoint {
  date: string
  bodyFat: number
}

/** 明细行：体重与围度同一条时间线（体重可空——只量了围度的那条也在）。 */
export interface BodyRecordRow {
  id: number
  /** 测定时刻，ISO 带 T：`2026-10-13T23:51:00`。 */
  date: string
  weight: number | null
  /** 体脂率 %（只记录，不参与计算）。 */
  bodyFat?: number | null
  chest: number | null
  waist: number | null
  hip: number | null
  arm: number | null
  note: string
  /** 与时间上相邻「上一条有体重」的记录之差（首条或本条无体重时 null）。 */
  diff: number | null
}

export interface BodySavePayload {
  /** 测定时刻，ISO 带 T：`2026-10-13T23:51:00`（后端只认这一种，空格分隔会 400）。 */
  measuredAt?: string
  weight?: number | null
  /** 体脂率 %（3–75，1 位小数；只记录、不参与计算）。 */
  bodyFat?: number | null
  chest?: number | null
  waist?: number | null
  hip?: number | null
  arm?: number | null
  note?: string
}

export function getBodyOverview() {
  return unwrap(request.get<BodyOverview>('/body'))
}

export function getBodyRecords(limit = 20, offset = 0) {
  return unwrap(request.get<BodyRecordRow[]>('/body/records', { limit, offset }))
}

export function createBodyRecord(payload: BodySavePayload) {
  return unwrap(request.post<BodyRecordRow>('/body', payload))
}

/** 修改一次测定（点明细行进编辑弹层）：时刻与五项数值整体覆盖。 */
export function updateBodyRecord(id: number, payload: BodySavePayload) {
  return unwrap(request.put<BodyRecordRow>(`/body/${id}`, payload))
}

export function deleteBodyRecord(id: number) {
  return unwrap(request.delete<void>(`/body/${id}`))
}

/** 批量删除（长按多选）：一次事务删多条，任一条不归属则整批不动。 */
export function batchDeleteBodyRecords(ids: number[]) {
  return unwrap(request.post<void>('/body/batch-delete', { ids }))
}
