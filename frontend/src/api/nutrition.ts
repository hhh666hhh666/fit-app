import request, { unwrap } from './request'
import type { PhotoQuality } from '@/utils/photo'

// ── 饮食记录（nutrition）：食物库 / 当日总览 / 条目 / 饮水 / 趋势 / 设置 ──
//    照片的 multipart 上传与 blob 取图走 utils/photo.ts（不经 request 封装）。

export type MealKey = 'breakfast' | 'lunch' | 'dinner' | 'snack'

/** 食物（库里一条）：营养值一律「每 100g / 100ml」；未设的字段缺省。 */
export interface FoodItem {
  id: number
  name: string
  unit: 'g' | 'ml'
  kcal: number
  protein: number
  fat: number
  carb: number
  sodium: number
  saturatedFat?: number | null
  sugar?: number | null
  transFat?: number | null
  monoFat?: number | null
  polyFat?: number | null
  cholesterol?: number | null
  fiber?: number | null
  solubleFiber?: number | null
  insolubleFiber?: number | null
  starch?: number | null
  lactose?: number | null
  va?: number | null
  vd?: number | null
  ve?: number | null
  vb1?: number | null
  vb2?: number | null
  vb6?: number | null
  vb12?: number | null
  vc?: number | null
  niacin?: number | null
  folate?: number | null
  pantothenic?: number | null
  biotin?: number | null
  calcium?: number | null
  phosphorus?: number | null
  potassium?: number | null
  magnesium?: number | null
  iron?: number | null
  zinc?: number | null
  iodine?: number | null
  selenium?: number | null
  copper?: number | null
  manganese?: number | null
  fluoride?: number | null
  chromium?: number | null
  molybdenum?: number | null
  water?: number | null
  ash?: number | null
  alcohol?: number | null
  organicAcid?: number | null
  portionLabel?: string | null
  portionGrams?: number | null
}

/** 新建 / 编辑食物的提交体。 */
export type FoodPayload = Omit<FoodItem, 'id'>

export interface MealItem {
  id: number
  meal: MealKey
  foodId?: number | null
  foodName: string
  /** 实际摄入量（克；ml 单位食物为毫升）。 */
  grams: number
  kcal: number
  protein: number
  carb: number
  fat: number
}

export interface Targets {
  kcal?: number | null
  protein?: number | null
  carb?: number | null
  fat?: number | null
  water?: number | null
}

export interface DayOverview {
  date: string
  totals: { kcal: number; protein: number; carb: number; fat: number }
  targets: Targets
  water: { ml: number; target?: number | null }
  /** 固定四条（早/午/晚/加餐），无条目时 items 为空数组。 */
  meals: { meal: MealKey; items: MealItem[] }[]
  /** 有照片的餐次。 */
  photoMeals: MealKey[]
}

export interface TrendPoint {
  date: string
  kcal: number
  protein: number
  carb: number
  fat: number
  water: number
}

export interface DietSetting {
  photoQuality: PhotoQuality
  waterStepMl: number
  targetKcal?: number | null
  targetProtein?: number | null
  targetCarb?: number | null
  targetFat?: number | null
  targetWater?: number | null
}

export interface ImportResult {
  imported: number
  skipped: string[]
  failed: { name: string; reason: string }[]
}

// ── 食物库 ──

export function listFoods(q?: string) {
  return unwrap(request.get<FoodItem[]>('/foods', q ? { q } : undefined))
}

export function createFood(payload: FoodPayload) {
  return unwrap(request.post<FoodItem>('/foods', payload))
}

export function updateFood(id: number, payload: FoodPayload) {
  return unwrap(request.put<FoodItem>(`/foods/${id}`, payload))
}

export function deleteFood(id: number) {
  return unwrap(request.delete<void>(`/foods/${id}`))
}

/** JSON 文本导入（复制粘贴）。 */
export function importFoods(text: string) {
  return unwrap(request.post<ImportResult>('/foods/import', { text }))
}

/** 导出全库（snake_case 结构，前端 pretty-print 展示）。 */
export function exportFoods() {
  return unwrap(request.get<Record<string, unknown>[]>('/foods/export'))
}

// ── 当日 / 条目 / 饮水 / 趋势 ──

export function getDay(date: string) {
  return unwrap(request.get<DayOverview>('/diet', { date }))
}

export function addMeal(payload: { date: string; meal: MealKey; foodId: number; grams: number }) {
  return unwrap(request.post<MealItem>('/diet/meals', payload))
}

export function updateMeal(id: number, grams: number) {
  return unwrap(request.put<MealItem>(`/diet/meals/${id}`, { grams }))
}

export function deleteMeal(id: number) {
  return unwrap(request.delete<void>(`/diet/meals/${id}`))
}

/** 饮水加减一笔，返回该日最新累计（ml）。 */
export function addWater(date: string, delta: number) {
  return unwrap(request.post<number>('/diet/water', { date, delta }))
}

export function getTrend(days = 30) {
  return unwrap(request.get<{ days: TrendPoint[] }>('/diet/trend', { days }))
}

// ── 设置 ──

export function getSettings() {
  return unwrap(request.get<DietSetting>('/settings'))
}

export function updateSettings(payload: DietSetting) {
  return unwrap(request.put<DietSetting>('/settings', payload))
}
