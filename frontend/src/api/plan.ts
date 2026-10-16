import request, { unwrap } from './request'

// ── 训练计划与周安排（后端第二期）──

export interface PlanListItem {
  id: number
  name: string
  exerciseCount: number
  exerciseNames: string[]
  /** 上次练该计划的时间（从未练过缺省）。 */
  lastTrainedAt?: string | null
  /** 上次练该计划的总容量 kg（从未练过缺省）。 */
  lastVolume?: number | null
  /** 上次练时有没有力量动作——只有有氧则容量位显示「—」（ADR 0023）。 */
  lastHasStrength?: boolean | null
}

export interface PlanExerciseItem {
  exerciseId: number
  name: string
  /** 肌群码（chest/back/...，见 CONTEXT.md）。 */
  muscle: string
  /** 记录口径：`floors`＝按层数录（爬楼）；其余按重量×次数（ADR 0025）。 */
  metric?: string | null
  targetSets?: number | null
  targetReps?: number | null
  /** 目标重量 kg；未设缺省（会话页预填负荷默认值用）。 */
  targetWeight?: number | null
}

export interface PlanDetail {
  id: number
  name: string
  exercises: PlanExerciseItem[]
}

export interface PlanSavePayload {
  name: string
  exercises: {
    exerciseId: number
    targetSets?: number | null
    targetReps?: number | null
    targetWeight?: number | null
  }[]
}

/** 周表一格：后端省略 null 字段，读出来 planId 可能是 undefined——使用处做 ?? null 归一。 */
export interface ScheduleDay {
  day: number
  planId?: number | null
  isRest: boolean
}

export function listPlans() {
  return unwrap(request.get<PlanListItem[]>('/plans'))
}

export function getPlan(id: number) {
  return unwrap(request.get<PlanDetail>(`/plans/${id}`))
}

export function createPlan(payload: PlanSavePayload) {
  return unwrap(request.post<PlanDetail>('/plans', payload))
}

export function updatePlan(id: number, payload: PlanSavePayload) {
  return unwrap(request.put<PlanDetail>(`/plans/${id}`, payload))
}

export function deletePlan(id: number) {
  return unwrap(request.delete<void>(`/plans/${id}`))
}

export function getSchedule() {
  return unwrap(request.get<ScheduleDay[]>('/schedule'))
}

/** 设置某天：{isRest:true} 休息；{planId} 练计划；{} 清空为未安排。 */
export function setScheduleDay(day: number, body: { planId?: number; isRest?: boolean }) {
  return unwrap(request.put<void>(`/schedule/${day}`, body))
}
