import request, { unwrap } from './request'

export interface WorkoutSetItem {
  weight: number
  reps: number
  /** 提交时是否刷新该动作历史最佳（未挂动作库的组为 false）。 */
  pr?: boolean
}

export interface WorkoutBlockItem {
  /** 关联动作库的 id（未挂库的临场动作为空）；编辑保存时回传。 */
  exerciseId?: number | null
  name: string
  sets: WorkoutSetItem[]
}

export interface WorkoutDetail {
  id: number
  title: string
  createdAt: string
  durationSec: number
  totalVolume: number
  note: string
  blocks: WorkoutBlockItem[]
}

export interface WorkoutListItem {
  id: number
  title: string
  firstExercise: string | null
  durationSec: number
  totalVolume: number
  createdAt: string
}

export interface LastRecord {
  weight: number
  reps: number
  performedAt: string
}

export interface CreateWorkoutPayload {
  /** 来源计划（自由训练不传）。 */
  planId?: number | null
  title: string
  durationSec: number
  note?: string
  blocks: {
    exerciseId?: number | null
    name: string
    sets: { weight: number; reps: number }[]
  }[]
}

export interface UpdateWorkoutPayload {
  title: string
  note?: string
  blocks: {
    exerciseId?: number | null
    name: string
    sets: { weight: number; reps: number }[]
  }[]
}

export function createWorkout(payload: CreateWorkoutPayload) {
  return unwrap(request.post<WorkoutDetail>('/workouts', payload))
}

/** 修改整条记录（详情页「编辑 → ✓」）：标题/备注/动作组次整体覆盖，返回更新后的详情。 */
export function updateWorkout(id: number, payload: UpdateWorkoutPayload) {
  return unwrap(request.put<WorkoutDetail>(`/workouts/${id}`, payload))
}

/** 删除单条训练记录。 */
export function deleteWorkout(id: number) {
  return unwrap(request.delete<void>(`/workouts/${id}`))
}

/** 批量删除（日志页长按多选删除）：一次事务，任一条不归属则整批不动。 */
export function batchDeleteWorkouts(ids: number[]) {
  return unwrap(request.post<void>('/workouts/batch-delete', { ids }))
}

export function listWorkouts(limit = 50) {
  return unwrap(request.get<WorkoutListItem[]>('/workouts', { limit }))
}

export function getWorkout(id: number) {
  return unwrap(request.get<WorkoutDetail>(`/workouts/${id}`))
}

export function getLastRecord(exerciseId: number) {
  return unwrap(request.get<LastRecord | null>('/workouts/last-record', { exerciseId }))
}

export function updateWorkoutNote(id: number, note: string) {
  return unwrap(request.put<void>(`/workouts/${id}/note`, { note }))
}
