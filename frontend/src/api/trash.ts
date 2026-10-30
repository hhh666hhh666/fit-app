import request, { unwrap } from './request'
import { clearAll, read } from './cache'
import { clearEmptyMarks } from '@/utils/photo-cache'
import type { Session } from '@/utils/session'

// ── 回收站（ADR 0047）──

export type TrashType =
  | 'workout'
  | 'session'
  | 'diary'
  | 'meal_photo'
  | 'diary_photo'
  | 'body'
  | 'plan'
  | 'exercise'
  | 'food'

export interface TrashItem {
  id: number
  type: TrashType
  title: string
  summary: string
  deletedAt: string
  remainingDays: number
  hasThumb: boolean
}

export interface TrashRestored {
  id: number
  type: TrashType
  /** 有话要告诉主人（比如重名改了名）才有。 */
  note?: string
  /** 只有丢掉的训练才有，由前端写回本机。 */
  session?: { abandonedAt: number; session: Session }
}

/** 列表。**不缓存**：别的页面删东西不会经过这里，逐页拉才准。 */
export function listTrash(types: TrashType[], offset = 0, limit = 30) {
  return read<TrashItem[]>('/trash', { types: types.join(','), offset, limit }, 'diet', false)
}

/** 恢复可能碰到任何一个域，还可能让「图片已删除」的图回来，所以全部作废。 */
export async function restoreTrash(ids: number[]) {
  const out = await unwrap(request.post<TrashRestored[]>('/trash/restore', { ids }))
  clearAll()
  clearEmptyMarks()
  return out
}

/** 彻底删除会释放配额，存储占用要重取。 */
export async function purgeTrash(ids: number[]) {
  await unwrap(request.post<void>('/trash/purge', { ids }))
  clearAll()
}

export async function clearTrash() {
  await unwrap(request.post<void>('/trash/clear'))
  clearAll()
}
