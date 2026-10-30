import request, { unwrap } from './request'
import { mutating, read } from './cache'
import type { MealKey } from './nutrition'

// ── 日记（ADR 0044）：一篇＝时刻 ＋ 可选标题 ＋ Markdown 正文，一天可写多篇 ──
//    图片的 multipart 上传与 blob 取图走 utils/photo.ts（不经 request 封装）。

export interface DiaryListItem {
  id: number
  writtenAt: string
  title: string
  /** 正文开头一段 Markdown 原文。 */
  preview: string
}

export interface DiaryEntry {
  id: number
  writtenAt: string
  title: string
  content: string
  updatedAt?: string
}

export interface DiaryPayload {
  writtenAt: string
  title: string
  content: string
}

export interface DiaryAlbumPhoto {
  id: number
  sizeBytes: number
  createdAt: string
}

/** 记录引用卡片：每次实时读取，记录没了 exists 为 false。 */
export interface DiaryRef {
  key: string
  kind: 'workout' | 'meal' | 'day'
  exists: boolean
  date?: string
  workoutId?: number
  title?: string
  durationSec?: number
  totalVolume?: number
  hasStrength?: boolean
  totalFloors?: number
  meal?: MealKey
  kcal?: number
  itemCount?: number
}

/** 按月列表；有 keyword 时搜全部日记。只缓存不带搜索词的首屏（ADR 0035）。 */
export function listDiary(month: string, keyword: string, limit: number, offset: number) {
  const cached = !keyword && offset === 0
  return read<DiaryListItem[]>('/diary', { month, keyword, limit, offset }, 'diary', cached)
}

export function getDiary(id: number) {
  return read<DiaryEntry>(`/diary/${id}`, undefined, 'diary')
}

export function createDiary(payload: DiaryPayload) {
  // 正文里的图片引用变了，占用不变但相册要重取；存储占用归饮食域
  return mutating(['diary', 'diet'], () => unwrap(request.post<DiaryEntry>('/diary', payload)))
}

export function updateDiary(id: number, payload: DiaryPayload) {
  return mutating(['diary', 'diet'], () => unwrap(request.put<DiaryEntry>(`/diary/${id}`, payload)))
}

export function batchDeleteDiary(ids: number[]) {
  return mutating(['diary', 'diet'], () => unwrap(request.post<void>('/diary/batch-delete', { ids })))
}

/** 相册「日记」栏。**不缓存**：照片随时会被删，逐页拉才准。 */
export function listDiaryAlbum(offset = 0, limit = 60) {
  return read<DiaryAlbumPhoto[]>('/diary/photos', { offset, limit }, 'diary', false)
}

/** 解析正文里的记录引用。**不缓存**：卡片要显示记录的最新数据。 */
export function resolveDiaryRefs(keys: string[]) {
  return read<DiaryRef[]>('/diary/refs', { keys: keys.join(',') }, 'diary', false)
}

/** 某天可引用的记录。 */
export function listRefCandidates(date: string) {
  return read<DiaryRef[]>('/diary/refs/candidates', { date }, 'diary', false)
}
