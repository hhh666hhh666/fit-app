import request, { unwrap } from './request'
import { mutating, read } from './cache'

// ── 动作库（预设只读 ＋ 自定义可增删；归「计划」域——它与计划编辑、动作库页同源）──

export interface ExerciseItem {
  id: number
  name: string
  muscle: string
  /** 记录口径：`floors`＝按层数录（爬楼）；其余按重量×次数（ADR 0025）。 */
  metric?: string | null
  preset: boolean
}

export function listExercises(params?: { muscle?: string; keyword?: string }) {
  return read<ExerciseItem[]>('/exercises', params, 'plan')
}

/** 新增自定义动作（预设不可改；删除 = 下架，走 DELETE /exercises/{id}）。 */
export function createExercise(payload: { name: string; muscle: string }) {
  return mutating('plan', () => unwrap(request.post<ExerciseItem>('/exercises', payload)))
}

/** 批量下架自定义动作；预设动作由后端拒绝。 */
export function batchDeleteExercises(ids: number[]) {
  return mutating('plan', () => unwrap(request.post<void>('/exercises/batch-delete', { ids })))
}
