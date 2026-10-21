import request, { unwrap } from './request'
import { mutating, read } from './cache'

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

/** 某一组的目标（ADR 0027／0031）：三项都可为空，组数由数组长度表达。 */
export interface PlanSetTarget {
  setIndex?: number
  reps?: number | null
  /** 该组目标重量 kg。 */
  weight?: number | null
  /** 该组目标层数：仅 `metric = floors` 的动作（爬楼）有值。 */
  floors?: number | null
}

export interface PlanExerciseItem {
  exerciseId: number
  name: string
  /** 肌群码（chest/back/...，见 CONTEXT.md）。 */
  muscle: string
  /** 记录口径：`floors`＝按层数录（爬楼）；其余按重量×次数（ADR 0025）。 */
  metric?: string | null
  /** 逐组目标；**长度即组数**（ADR 0027）。空数组＝不设目标。 */
  sets?: PlanSetTarget[] | null
  /** 冗余的组数（旧口径残留，读 sets 长度即可）。 */
  targetSets?: number | null
  targetReps?: number | null
  /** 目标重量 kg；未设缺省（新加一组时的默认值）。 */
  targetWeight?: number | null
  /** 循环组号；null/缺省＝不参与循环（ADR 0028）。 */
  circuitNo?: number | null
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
    /** 逐组目标；数组顺序即组序，空数组＝不设目标。 */
    sets?: { reps?: number | null; weight?: number | null; floors?: number | null }[]
    /** 循环组号；null/不传＝不参与循环。 */
    circuitNo?: number | null
  }[]
}

/** 周表一格：后端省略 null 字段，读出来 planId 可能是 undefined——使用处做 ?? null 归一。 */
export interface ScheduleDay {
  day: number
  planId?: number | null
  isRest: boolean
}

export function listPlans() {
  return read<PlanListItem[]>('/plans', undefined, 'plan')
}

export function getPlan(id: number) {
  return read<PlanDetail>(`/plans/${id}`, undefined, 'plan')
}

export function createPlan(payload: PlanSavePayload) {
  return mutating('plan', () => unwrap(request.post<PlanDetail>('/plans', payload)))
}

export function updatePlan(id: number, payload: PlanSavePayload) {
  return mutating('plan', () => unwrap(request.put<PlanDetail>(`/plans/${id}`, payload)))
}

export function deletePlan(id: number) {
  return mutating('plan', () => unwrap(request.delete<void>(`/plans/${id}`)))
}

export function getSchedule() {
  return read<ScheduleDay[]>('/schedule', undefined, 'plan')
}

/** 设置某天：{isRest:true} 休息；{planId} 练计划；{} 清空为未安排。 */
export function setScheduleDay(day: number, body: { planId?: number; isRest?: boolean }) {
  return mutating('plan', () => unwrap(request.put<void>(`/schedule/${day}`, body)))
}
