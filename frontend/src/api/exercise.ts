import request, { unwrap } from './request'

export interface ExerciseItem {
  id: number
  name: string
  muscle: string
  preset: boolean
}

export function listExercises(params?: { muscle?: string; keyword?: string }) {
  return unwrap(request.get<ExerciseItem[]>('/exercises', params))
}

/** 新增自定义动作（预设不可改；删除 = 下架，走 DELETE /exercises/{id}）。 */
export function createExercise(payload: { name: string; muscle: string }) {
  return unwrap(request.post<ExerciseItem>('/exercises', payload))
}
