import request, { unwrap } from './request'
import { mutating, read } from './cache'

export interface WorkoutSetItem {
  weight: number
  reps: number
  /** 层数：`metric = floors` 的动作（爬楼）专用；其余组为缺省。 */
  floors?: number | null
  /** 提交时是否刷新该动作历史最佳（未挂动作库的组为 false）。 */
  pr?: boolean
  /** 该组净用时（秒，剔除暂停段）；缺省＝无用时（历史数据与编辑时新增的组）。 */
  durationSec?: number | null
}

export interface WorkoutBlockItem {
  /** 关联动作库的 id（未挂库的临场动作为空）；编辑保存时回传。 */
  exerciseId?: number | null
  name: string
  /** 有氧动作（肌群 cardio）——不参与容量与 PR（ADR 0023／0024），容量位显示「—」。 */
  cardio?: boolean
  /** 记录口径：`floors`＝按层数录（爬楼）；其余按重量×次数（ADR 0025）。 */
  metric?: string | null
  sets: WorkoutSetItem[]
}

export interface WorkoutDetail {
  id: number
  title: string
  createdAt: string
  durationSec: number
  /** 本次手填消耗（大卡）；null/缺省＝未填（ADR 0029）。 */
  calories?: number | null
  totalVolume: number
  note: string
  blocks: WorkoutBlockItem[]
}

export interface WorkoutListItem {
  id: number
  title: string
  firstExercise: string | null
  durationSec: number
  /** 本次手填的消耗（大卡）；null/缺省＝未填（ADR 0029）。 */
  calories?: number | null
  totalVolume: number
  /** 该次训练是否含力量动作——纯有氧场次的容量位显示「—」（ADR 0023）。 */
  hasStrength?: boolean
  /**
   * 该次训练各组的层数合计（爬楼梯等 `metric = floors` 的动作；没有则 0）。
   * 纯有氧场次的成绩位改显「总层数」（ADR 0025 的成绩位口径，日志页是后补的那处）。
   */
  totalFloors?: number
  createdAt: string
}

export interface LastRecord {
  weight: number
  reps: number
  /** 层数（`metric = floors` 的动作有值，「上次数据」提示要按它显示）。 */
  floors?: number | null
  performedAt: string
}

export interface CreateWorkoutPayload {
  /** 来源计划（自由训练不传）。 */
  planId?: number | null
  title: string
  durationSec: number
  /** 本次手填消耗（大卡）；不传＝未填，不计入当日总消耗（ADR 0029）。 */
  calories?: number | null
  note?: string
  blocks: {
    exerciseId?: number | null
    name: string
    /** `floors` 口径的动作：weight／reps 传占位值 0／1，只传 floors（ADR 0025）。 */
    sets: { weight: number; reps: number; floors?: number; durationSec?: number }[]
  }[]
}

export interface UpdateWorkoutPayload {
  title: string
  note?: string
  /** 本次消耗（大卡）；null/不传＝清空。练完随手填、事后可改（ADR 0029 后续）。 */
  calories?: number | null
  blocks: {
    exerciseId?: number | null
    name: string
    /** `floors` 口径的动作：weight／reps 传占位值 0／1，只传 floors（ADR 0025）。 */
    sets: { weight: number; reps: number; floors?: number; durationSec?: number }[]
  }[]
}

export function createWorkout(payload: CreateWorkoutPayload) {
  return mutating('workout', () => unwrap(request.post<WorkoutDetail>('/workouts', payload)))
}

/** 修改整条记录（详情页「编辑 → ✓」）：标题/备注/动作组次整体覆盖，返回更新后的详情。 */
export function updateWorkout(id: number, payload: UpdateWorkoutPayload) {
  return mutating('workout', () => unwrap(request.put<WorkoutDetail>(`/workouts/${id}`, payload)))
}

/** 删除单条训练记录。 */
export function deleteWorkout(id: number) {
  return mutating('workout', () => unwrap(request.delete<void>(`/workouts/${id}`)))
}

/** 批量删除（日志页长按多选删除）：一次事务，任一条不归属则整批不动。 */
export function batchDeleteWorkouts(ids: number[]) {
  return mutating('workout', () => unwrap(request.post<void>('/workouts/batch-delete', { ids })))
}

/**
 * 日志列表：`month = 'YYYY-MM'` 按月过滤（不传＝不过滤，首页「上次训练」用它取最近一条）；
 * `offset` 供触底分页。
 *
 * 只缓存首屏（`offset === 0`）：续页缓存下来，第 3 页被删过一条就会与前面的页对不齐。
 */
export function listWorkouts(limit = 50, offset = 0, month?: string) {
  return read<WorkoutListItem[]>(
    '/workouts',
    { limit, offset, ...(month ? { month } : {}) },
    'workout',
    offset === 0
  )
}

/** 按月（＆本周）的轻量概览：当月次数与总容量 ＋ 上月同口径（环比基准）＋ 本周次数。 */
export interface MonthSummary {
  /** `YYYY-MM`（服务端回显）。 */
  month: string
  count: number
  totalVolume: number
  /** 本周（周一 00:00 起）次数——首页统计条用。 */
  weekCount: number
  /** 0 ＝ 上月没有记录，此时不显示环比。 */
  prevCount: number
  prevTotalVolume: number
}

/** 不传 month ＝ 当月（首页取本周／本月次数就是这么调的）。 */
export function getMonthSummary(month?: string) {
  return read<MonthSummary>(
    '/workouts/month-summary',
    month ? { month } : undefined,
    'workout'
  )
}

/** 「我的」页累计口径：**全量**次数与总容量（列表接口有 limit 封顶，不能拿它求和冒充「累计」）。 */
export interface WorkoutTotals {
  count: number
  totalVolume: number
}

export function getWorkoutTotals() {
  return read<WorkoutTotals>('/workouts/totals', undefined, 'workout')
}

export function getWorkout(id: number) {
  return read<WorkoutDetail>(`/workouts/${id}`, undefined, 'workout')
}

export function getLastRecord(exerciseId: number) {
  return read<LastRecord | null>('/workouts/last-record', { exerciseId }, 'workout')
}

export function updateWorkoutNote(id: number, note: string) {
  return mutating('workout', () => unwrap(request.put<void>(`/workouts/${id}/note`, { note })))
}
