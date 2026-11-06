/** 后端统一信封（HTTP 恒为 200，看 code）。 */
export interface Result<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface FoodRow {
  id: number
  name: string
  category: string | null
  unit: 'g' | 'ml'
  kcal: number
  protein: number
  fat: number
  carb: number
  sodium: number | null
  archived: boolean
  updatedAt: string
}

/** 单条预置食物（营养字段键名与后端 camelCase 一致，完整清单见 @shared/food-fields）。 */
export type FoodDetail = {
  id: number
  name: string
  unit: 'g' | 'ml'
  category: string | null
  archived: boolean
  gi?: number | null
  portionLabel?: string | null
  portionGrams?: number | null
  updatedAt?: string
} & Record<string, unknown>

export interface ImportPreview {
  create: number
  update: number
  invalid: { index: number; name: string; reason: string }[]
}

export interface ExerciseRow {
  id: number
  name: string
  muscle: string
  metric: 'weight_reps' | 'floors'
  sortOrder: number
  archived: boolean
  used: boolean
  updatedAt: string
}

export interface UserRow {
  id: number
  username: string
  /** 昵称（ADR 0050），没设为 null */
  nickname: string | null
  admin: boolean
  createdAt: string
  workoutCount: number
  mealCount: number
  lastRecordAt: string | null
}

export const MUSCLES = [
  { code: 'chest', label: '胸' },
  { code: 'back', label: '背' },
  { code: 'leg', label: '腿' },
  { code: 'shoulder', label: '肩' },
  { code: 'arm', label: '手臂' },
  { code: 'core', label: '腹部' },
  { code: 'cardio', label: '有氧' }
] as const

export const muscleLabel = (code: string) => MUSCLES.find((m) => m.code === code)?.label ?? code

export const METRICS = [
  { code: 'weight_reps', label: '重量×次数' },
  { code: 'floors', label: '层数' }
] as const

export const metricLabel = (code: string) => METRICS.find((m) => m.code === code)?.label ?? code

/** 后端给的是 `2026-11-04T08:10:00` 这种本地时间（Asia/Shanghai），只做显示截取。 */
export const timeText = (v: string | null | undefined) => (v ? v.replace('T', ' ').slice(0, 16) : '—')
