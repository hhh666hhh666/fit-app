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
  /** 分类（预置数据带《中国食物成分表》大类；用户自建无）。 */
  category?: string | null
  /** 浏览次数（详情页查看次数；食物库列表按此降序）。 */
  viewCount?: number | null
  /** 收藏（置顶标记）：收藏者排在食物库列表最前，同为收藏再按浏览次数降序。 */
  favorite?: boolean | null
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
  /** 升糖指数 GI（葡萄糖＝100 基准；扩展字段，不在国标营养标签内）。 */
  gi?: number | null
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
  /** 系统预置（只读，可「复制为自定义」）；lite 列表也会带此标志。 */
  preset?: boolean | null
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

/** 列表/搜索（预置＋自有合并，按浏览次数降序）；lite=轻量字段（列表与记录选择用，不带全套营养），limit/offset 分页。 */
export function listFoods(q?: string, opts?: { lite?: boolean; limit?: number; offset?: number }) {
  const params: Record<string, unknown> = {}
  if (q) params.q = q
  if (opts?.lite) params.lite = 1
  if (opts?.limit) params.limit = opts.limit
  if (opts?.offset) params.offset = opts.offset
  return unwrap(request.get<FoodItem[]>('/foods', Object.keys(params).length ? params : undefined))
}

/** 单条（编辑回填 / 复制为自定义用）。 */
export function getFood(id: number) {
  return unwrap(request.get<FoodItem>(`/foods/${id}`))
}

/** 食物数量统计（预置＋自有，可按关键词过滤；「我的」页显示总数、食物库分页判断到底用）。 */
export function getFoodCount(q?: string) {
  return unwrap(
    request.get<{ total: number; preset: number; mine: number }>(
      '/foods/count',
      q ? { q } : undefined
    )
  )
}

/** 浏览次数 +1（详情页查看时调用；预置与自有均可）。 */
export function incrementFoodView(id: number) {
  return unwrap(request.post<void>(`/foods/${id}/view`))
}

/** 收藏 / 取消收藏（收藏＝食物库列表置顶；预置与自有均可）。 */
export function setFoodFavorite(id: number, favorite: boolean) {
  return unwrap(request.put<void>(`/foods/${id}/favorite`, undefined, { favorite }))
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

// ── 餐照的存储占用（ADR 0013）──

export interface StorageInfo {
  /** 图片已用（字节，原图 ＋ 缩略图）。 */
  photoBytes: number
  /** 照片张数。 */
  photoCount: number
  /** 图片上限（字节）。 */
  photoQuotaBytes: number
}

export function getStorage() {
  return unwrap(request.get<StorageInfo>('/diet/storage'))
}

/** 相册里的一条（只有元数据；图片数据走缩略图 / 原图接口）。 */
export interface AlbumPhoto {
  id: number
  logDate: string
  meal: MealKey
  sizeBytes: number
}

/** 相册列表（按日期倒序、同日早→晚）。 */
export function listAlbum(offset = 0, limit = 60) {
  return unwrap(request.get<AlbumPhoto[]>('/diet/photos', { offset, limit }))
}

// ── 食物组合（一串食物 ＋ 固定克数；ADR 0012）──

/** 组合里的一条：foodId 为 null 表示那样食物已被删（仍列出名字，但无单位与热量、不计入合计）。 */
export interface ComboItem {
  foodId?: number | null
  foodName: string
  grams: number
  unit?: 'g' | 'ml' | null
  kcal?: number | null
}

export interface FoodComboVO {
  id: number
  name: string
  /** 整份热量合计（大卡）。 */
  totalKcal: number
  items: ComboItem[]
}

/** 组合的提交体：明细整体覆盖。 */
export interface ComboPayload {
  name: string
  items: { foodId: number; grams: number }[]
}

export function listCombos() {
  return unwrap(request.get<FoodComboVO[]>('/combos'))
}

export function createCombo(payload: ComboPayload) {
  return unwrap(request.post<FoodComboVO>('/combos', payload))
}

export function updateCombo(id: number, payload: ComboPayload) {
  return unwrap(request.put<FoodComboVO>(`/combos/${id}`, payload))
}

export function deleteCombo(id: number) {
  return unwrap(request.delete<void>(`/combos/${id}`))
}

/** 整份加入某一餐：一次事务落多条普通条目（不新开记录类型）。 */
export function addMealsBatch(payload: {
  date: string
  meal: MealKey
  items: { foodId: number; grams: number }[]
}) {
  return unwrap(request.post<MealItem[]>('/diet/meals/batch', payload))
}

// ── 设置 ──

export function getSettings() {
  return unwrap(request.get<DietSetting>('/settings'))
}

export function updateSettings(payload: DietSetting) {
  return unwrap(request.put<DietSetting>('/settings', payload))
}
