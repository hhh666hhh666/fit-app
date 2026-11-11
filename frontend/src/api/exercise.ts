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
  /** 只出现在预置上：已下架、但这个用户用过（ADR 0048）。只读，可以对自己删掉。 */
  archived?: boolean
  /** 英文名：只有导入过开源动作数据集的预置才有（ADR 0055），搜索也匹配它。 */
  nameEn?: string | null
}

/** 动作详情（ADR 0055）：资料只有导入过数据集的预置才有，自定义动作全为空。 */
export interface ExerciseDetail extends ExerciseItem {
  equipment: string | null
  target: string | null
  secondary: string[]
  steps: string[]
  /** 有动图可取：`fetchExerciseMedia(id)` */
  media: boolean
}

/** 动作库里看得到的动作数（「我的」页用）：导入数据集后一千多条，别为一个数字拉全量列表。 */
export function getExerciseCount() {
  return read<number>('/exercises/count', undefined, 'plan')
}

export function getExercise(id: number) {
  return read<ExerciseDetail>(`/exercises/${id}`, undefined, 'plan')
}

export function listExercises(params?: { muscle?: string; keyword?: string }) {
  return read<ExerciseItem[]>('/exercises', params, 'plan')
}

/** 新增自定义动作（预设不可改；删除 = 下架，走 DELETE /exercises/{id}）。 */
export function createExercise(payload: { name: string; muscle: string }) {
  return mutating('plan', () => unwrap(request.post<ExerciseItem>('/exercises', payload)))
}

/** 批量删除：自定义动作下架；已下架的预置只对自己隐藏（ADR 0048）；上架的预设由后端拒绝。 */
export function batchDeleteExercises(ids: number[]) {
  return mutating('plan', () => unwrap(request.post<void>('/exercises/batch-delete', { ids })))
}
