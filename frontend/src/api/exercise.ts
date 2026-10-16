import request, { unwrap } from './request'

export interface ExerciseItem {
  id: number
  name: string
  muscle: string
  /** 记录口径：`floors`＝按层数录（爬楼）；其余按重量×次数（ADR 0025）。 */
  metric?: string | null
  preset: boolean
}

export function listExercises(params?: { muscle?: string; keyword?: string }) {
  return unwrap(request.get<ExerciseItem[]>('/exercises', params))
}

/** 新增自定义动作（预设不可改；删除 = 下架，走 DELETE /exercises/{id}）。 */
export function createExercise(payload: { name: string; muscle: string }) {
  return unwrap(request.post<ExerciseItem>('/exercises', payload))
}

/** 批量下架自定义动作；预设动作由后端拒绝。 */
export function batchDeleteExercises(ids: number[]) {
  return unwrap(request.post<void>('/exercises/batch-delete', { ids }))
}
