import request, { unwrap } from './request'
import { mutating, read } from './cache'
import { API_BASE } from './base'
import type { EnergyInfo } from './profile'
import type { PhotoQuality } from '@/utils/photo'

// ── 饮食记录（nutrition）：食物库 / 当日总览 / 条目 / 饮水 / 趋势 / 设置 ──
//    照片的 multipart 上传与 blob 取图走 utils/photo.ts（不经 request 封装）。

export type MealKey = 'breakfast' | 'lunch' | 'dinner' | 'snack'

/** 食物（库里一条）：营养值一律「每 100g / 100ml」；未设的字段缺省。 */
export interface FoodItem {
  id: number
  name: string
  /** 别名（ADR 0056）：搜索时和名字同等对待；没有别名时缺省。 */
  aliases?: string[] | null
  /** 食物图片的版本号（ADR 0057）：有值＝有图，换图就变；图片本身走 utils/photo.ts 的 fetchFoodImage。 */
  imageKey?: string | null
  unit: 'g' | 'ml'
  /** 分类（预置数据带《中国食物成分表》大类；用户自建无）。 */
  category?: string | null
  /** 浏览次数（详情页查看次数；不再参与排序，ADR 0056）。 */
  viewCount?: number | null
  /** 收藏（置顶标记）：同一匹配档里收藏的排最前（ADR 0056）。 */
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
  /** 预置已下架（ADR 0048）：只有用过的人看得到，带「已下架」标签，可以对自己删掉。 */
  archived?: boolean | null
}

/**
 * 新建 / 编辑食物的提交体。编辑是整体覆盖：aliases 不给就是清空。
 * copyImageFrom 只在新建时有用——「复制为自定义」把来源的图片复制过来（ADR 0057）。
 */
export type FoodPayload = Omit<FoodItem, 'id' | 'imageKey'> & { copyImageFrom?: number | null }

export interface MealItem {
  id: number
  meal: MealKey
  /** 时刻 HH:mm（ADR 0059）；老条目推断不出时为 null。 */
  time?: string | null
  foodId?: number | null
  foodName: string
  /** 临时条目：没挂食物库（AI 识别记的，或来源食物已删）；能改名，会出现在记一餐的列表里（scope #48）。 */
  temporary: boolean
  /** 实际摄入量（克；ml 单位食物为毫升）。 */
  grams: number
  kcal: number
  protein: number
  carb: number
  fat: number
  /** 钠实际摄入（mg）；老条目、临时条目没填钠时为 null。 */
  sodium?: number | null
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
  /** 饮水：ml＝当天各笔之和；drinks＝每一笔，按时刻排，没有时刻的（旧数据迁来的）在前（ADR 0059）。 */
  water: { ml: number; target?: number | null; drinks: WaterDrink[] }
  /**
   * 固定四条（早/午/晚/加餐），无条目时 items 为空数组。items 按时刻排（没有时刻的在后）；
   * firstTime＝这一餐最早的时刻。
   */
  meals: { meal: MealKey; firstTime?: string | null; items: MealItem[] }[]
  /** 有照片的餐次。 */
  photoMeals: MealKey[]
  /**
   * 当天的每日总消耗拆解（ADR 0029）。档案不全时 `complete=false`，
   * 前端据此换成「补全档案」入口，不显示缺口。
   */
  energy?: EnergyInfo | null
  /** 热量缺口 ＝ 总消耗 − 摄入（正数＝缺口，负数＝盈余）；算不出消耗时为 null。 */
  gap?: number | null
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

/**
 * 列表/搜索（预置＋自有合并；搜名字和别名，按字顺序也能命中；按匹配度 → 收藏 → 近 30 天记过几次 → 自建优先 → 名称排，
 * ADR 0056）；lite=轻量字段（列表与记录选择用，不带全套营养），limit/offset 分页。
 *
 * 只缓存**无搜索词的首屏**：带 `q` 的结果随输入变化、缓存只会占内存；续页不缓存（删改后与前页对不齐）。
 */
export function listFoods(q?: string, opts?: { lite?: boolean; limit?: number; offset?: number }) {
  const params: Record<string, unknown> = {}
  if (q) params.q = q
  if (opts?.lite) params.lite = 1
  if (opts?.limit) params.limit = opts.limit
  if (opts?.offset) params.offset = opts.offset
  return read<FoodItem[]>('/foods', Object.keys(params).length ? params : undefined, 'food', !q)
}

/** 单条（编辑回填 / 复制为自定义用）。 */
export function getFood(id: number) {
  return read<FoodItem>(`/foods/${id}`, undefined, 'food')
}

/** 食物数量统计（预置＋自有，可按关键词过滤；「我的」页显示总数、食物库分页判断到底用）。 */
export function getFoodCount(q?: string) {
  return read<{ total: number; preset: number; mine: number }>(
    '/foods/count',
    q ? { q } : undefined,
    'food',
    !q
  )
}

/** 浏览次数 +1（详情页查看时调用；预置与自有均可）。 */
export function incrementFoodView(id: number) {
  return mutating('food', () => unwrap(request.post<void>(`/foods/${id}/view`)))
}

/** 收藏 / 取消收藏（收藏＝食物库列表置顶；预置与自有均可）。 */
export function setFoodFavorite(id: number, favorite: boolean) {
  return mutating('food', () =>
    unwrap(request.put<void>(`/foods/${id}/favorite`, undefined, { favorite }))
  )
}

export function createFood(payload: FoodPayload) {
  return mutating('food', () => unwrap(request.post<FoodItem>('/foods', payload)))
}

export function updateFood(id: number, payload: FoodPayload) {
  return mutating('food', () => unwrap(request.put<FoodItem>(`/foods/${id}`, payload)))
}

export function deleteFood(id: number) {
  return mutating('food', () => unwrap(request.delete<void>(`/foods/${id}`)))
}

/** JSON 文本导入（复制粘贴）。 */
export function importFoods(text: string) {
  return mutating('food', () => unwrap(request.post<ImportResult>('/foods/import', { text })))
}

/** 导出全库（snake_case 结构，前端 pretty-print 展示）。导出不缓存——要的就是当下的全库。 */
export function exportFoods() {
  return read<Record<string, unknown>[]>('/foods/export', undefined, 'food', false)
}

// ── 营养成分表识别（ADR 0018）：照片 → 草稿（不落库）；保存走既有 createFood ──

/** 识别结果：草稿 ＋ 低置信度字段 ＋ 撞名信息。 */
export interface FoodDraft {
  draft: FoodPayload
  /** 低置信度字段 key（前端在这些字段旁标「待核对」）。 */
  uncertain: string[]
  /** 标签上没找到的必填字段 key（4+1 中的；草稿里对应值为空，前端标「待补」）。 */
  missing?: string[]
  /** 撞名：自有库里已有同名食物（null＝不撞）。 */
  duplicateFoodId?: number | null
  duplicateFoodName?: string | null
}

/**
 * 识别一张照片（multipart）。走裸 fetch——与餐照同套路：request 封装只处理 JSON，
 * multipart 与鉴权参照 utils/photo.ts。失败抛 Error（message 即后端给的降级原因）。
 */
export async function recognizeFood(blob: Blob): Promise<FoodDraft> {
  const token = (uni.getStorageSync('fitapp_token') as string) || ''
  const fd = new FormData()
  fd.append('file', blob, blob.type === 'image/png' ? 'label.png' : 'label.jpg')
  const res = await fetch(`${API_BASE}/food-recognition`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: fd
  })
  let body: { code?: number; message?: string; data?: FoodDraft } | null = null
  try {
    body = await res.json()
  } catch {
    /* 非 JSON（如 nginx 错误页） */
  }
  if (res.ok && body && body.code === 0 && body.data) return body.data
  throw new Error((body && body.message) || '识别失败，请重试')
}

// ── 餐次 AI 识别（ADR 0052）：饭菜照片估算 / 营养成分表换算 → 临时食物草稿（不落库）──

export type MealDraftKey = 'name' | 'grams' | 'kcal' | 'protein' | 'fat' | 'carb' | 'sodium'

/** 临时食物草稿：营养为每 100（g / ml），grams 是这一份的量；认不出的为 null。 */
export interface MealDraft {
  /** photo＝饭菜照片（估算）；label＝营养成分表（照表换算）。 */
  kind: 'photo' | 'label'
  name: string
  unit: 'g' | 'ml'
  grams: number | null
  /** grams 是服务端兜底的 100（照片没估出重量）：营养其实是整份总量，改份量时总量不变。 */
  gramsGuessed?: boolean
  kcal: number | null
  protein: number | null
  fat: number | null
  carb: number | null
  sodium: number | null
  /** 拿不准的字段（标「待核对」）。 */
  uncertain: MealDraftKey[]
  /** 没给出的营养字段（标「待补」）。 */
  missing: MealDraftKey[]
}

/** 识别一张照片为临时食物草稿（multipart，裸 fetch，同 recognizeFood）。失败抛 Error。 */
export async function recognizeMeal(blob: Blob): Promise<MealDraft> {
  const token = (uni.getStorageSync('fitapp_token') as string) || ''
  const fd = new FormData()
  fd.append('file', blob, blob.type === 'image/png' ? 'meal.png' : 'meal.jpg')
  const res = await fetch(`${API_BASE}/food-recognition/meal`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: fd
  })
  let body: { code?: number; message?: string; data?: MealDraft } | null = null
  try {
    body = await res.json()
  } catch {
    /* 非 JSON（如 nginx 错误页） */
  }
  if (res.ok && body && body.code === 0 && body.data) return body.data
  throw new Error((body && body.message) || '识别失败，请重试')
}

/** 识别用量汇总（「我的」页外卡两值；不显示费用）。 */
export interface RecognitionUsage {
  /** 累计 token（输入＋输出，含失败调用）。 */
  totalTokens: number
  /** 请求次数（含失败）。 */
  totalRequests: number
}

export function getRecognitionUsage() {
  return read<RecognitionUsage>('/food-recognition/usage', undefined, 'food')
}

/** 用量明细的一条。 */
export interface RecognitionLogItem {
  id: number
  model: string
  tokensIn: number
  tokensOut: number
  cachedTokens: number
  durationMs: number
  success: boolean
  createdAt: string
  /** 以下为 V40 起多记的（ADR 0053），老记录为 null；列表不带 rawOutput。 */
  /** label＝食物库营养成分表入库；meal＝记一餐 AI 识别。 */
  kind?: 'label' | 'meal' | null
  endpoint?: string | null
  thinkingMode?: string | null
  reasoningEffort?: string | null
  reasoningTokens?: number | null
  imageBytes?: number | null
  imageMime?: string | null
  finishReason?: string | null
  upstreamId?: string | null
  resultName?: string | null
  errorMessage?: string | null
  rawOutput?: string | null
  /** 发给服务商的请求体（图片 base64 已换成占位）。V41 起；列表不带。 */
  requestJson?: string | null
  /** 服务商返回的完整响应；失败时是「HTTP 状态 ＋ 报错正文」。V41 起；列表不带。 */
  responseJson?: string | null
}

/** 一次识别调用的价格估算（ADR 0054）：目前只算 DeepSeek，按调用时刻分高峰 / 空闲。 */
export interface RecognitionPrice {
  supported: boolean
  /** 不能算时的原因。 */
  note?: string | null
  currency?: string
  modelLabel?: string
  source?: string
  /** 判断峰谷用的时刻（北京时间，请求发出时）。 */
  callTime?: string
  peak?: boolean
  periodReason?: string
  /** 这一年的放假安排在不在表里。 */
  holidayCovered?: boolean
  items?: { label: string; tokens: number; unitPrice: number; cost: number }[]
  total?: number
}

export interface RecognitionLogDetail extends RecognitionLogItem {
  price?: RecognitionPrice | null
}

/** 一条识别流水的全部信息（详情页；含模型原始输出与价格估算）。不缓存。 */
export function getRecognitionLog(id: number) {
  return read<RecognitionLogDetail>(`/food-recognition/logs/${id}`, undefined, 'food', false)
}

/** 用量明细分页（新的在前）。**不缓存**：这是逐条流水，每次进页都该是当下的。 */
export function listRecognitionLogs(offset = 0, limit = 50) {
  return read<RecognitionLogItem[]>(
    '/food-recognition/logs',
    { offset, limit },
    'food',
    false
  )
}

// ── 当日 / 条目 / 饮水 / 趋势 ──

export function getDay(date: string) {
  return read<DayOverview>('/diet', { date }, 'diet')
}

/** 一笔饮水（ADR 0059）。 */
export interface WaterDrink {
  id: number
  ml: number
  /** HH:mm；旧的每日累计迁来的为 null。 */
  time?: string | null
}

/** `time`（HH:mm）不给时服务端按默认规则补：记今天用现在的钟点，补记按餐次（ADR 0059）。 */
export function addMeal(payload: { date: string; meal: MealKey; foodId: number; grams: number; time?: string }) {
  return mutating('diet', () => unwrap(request.post<MealItem>('/diet/meals', payload)))
}

/**
 * 记一条临时条目（ADR 0052）：AI 识别后核对过的「临时食物」，不进食物库。
 * 营养按每 100（g / ml）传；钠选填。
 */
export function addCustomMeal(payload: {
  date: string
  meal: MealKey
  name: string
  grams: number
  kcal: number
  protein: number
  fat: number
  carb: number
  sodium: number | null
  time?: string
}) {
  return mutating('diet', () => unwrap(request.post<MealItem>('/diet/meals/custom', payload)))
}

/** 改一条条目：给了哪项改哪项；名称只有临时条目能改。 */
export function updateMeal(id: number, patch: { grams?: number; name?: string; time?: string }) {
  return mutating('diet', () => unwrap(request.put<MealItem>(`/diet/meals/${id}`, patch)))
}

/** 吃过的一种临时条目（名字和营养一样的合成一种）；营养是每 100。 */
export interface TempPick {
  name: string
  /** 上一次记的份量。 */
  grams: number
  kcal: number
  protein: number
  fat: number
  carb: number
  sodium?: number | null
  /** 近 30 天记过几次。 */
  count: number
  lastDate: string
}

export type MealPick = { type: 'food'; food: FoodItem } | { type: 'temp'; temp: TempPick }

/**
 * 记一餐弹层的列表（scope #48）：食物库的食物和近 30 天吃过的临时条目，由服务端按同一套规则排好。
 * 依赖饮食和食物库两个域：记了一餐、改了食物都要重取。
 */
export function listMealPicks(q?: string, limit = 200) {
  const params: Record<string, unknown> = { limit }
  if (q) params.q = q
  return read<MealPick[]>('/diet/picks', params, ['diet', 'food'], !q)
}

export function deleteMeal(id: number) {
  return mutating('diet', () => unwrap(request.delete<void>(`/diet/meals/${id}`)))
}

/** 记一笔饮水；时刻不给就用现在的钟点（ADR 0059）。 */
export function addDrink(payload: { date: string; ml: number; time?: string }) {
  return mutating('diet', () => unwrap(request.post<WaterDrink>('/diet/water/drinks', payload)))
}

/** 改一笔饮水的毫升数或时刻。 */
export function updateDrink(id: number, patch: { ml?: number; time?: string }) {
  return mutating('diet', () => unwrap(request.put<WaterDrink>(`/diet/water/drinks/${id}`, patch)))
}

/** 删一笔饮水（不进回收站）。 */
export function deleteDrink(id: number) {
  return mutating('diet', () => unwrap(request.delete<void>(`/diet/water/drinks/${id}`)))
}

/** 饮水加减一笔（首页卡用）：正数＝按现在的钟点记一笔，负数＝撤掉当天最后记的那一笔；返回该日最新合计（ml）。 */
export function addWater(date: string, delta: number) {
  return mutating('diet', () => unwrap(request.post<number>('/diet/water', { date, delta })))
}

/**
 * 趋势。不传参时由后端从**最早一条记录**起返回全部历史（ADR 0015：前端一次拉全量、
 * 本地缩放平移）；也可显式给起止日期或天数（兼容旧调用）。
 */
export function getTrend(params?: { from?: string; to?: string; days?: number }) {
  return read<{ days: TrendPoint[] }>('/diet/trend', params, 'diet')
}

// ── 餐照的存储占用（ADR 0013）──

export interface StorageInfo {
  /** 图片已用（字节，原图 ＋ 缩略图）。 */
  photoBytes: number
  /** 其中回收站里的照片占多少（字节）。 */
  trashBytes: number
  /** 照片张数。 */
  photoCount: number
  /** 图片上限（字节）。 */
  photoQuotaBytes: number
}

export function getStorage() {
  return read<StorageInfo>('/diet/storage', undefined, 'diet')
}

/** 相册里的一条（只有元数据；图片数据走缩略图 / 原图接口）。 */
export interface AlbumPhoto {
  id: number
  logDate: string
  meal: MealKey
  sizeBytes: number
}

/** 相册列表（按日期倒序、同日早→晚）。**不缓存**：照片随时会被换／删，逐页拉才准。 */
export function listAlbum(offset = 0, limit = 60) {
  return read<AlbumPhoto[]>('/diet/photos', { offset, limit }, 'diet', false)
}

/** 某一餐的全部照片（只有元数据），按上传先后。 */
export function listMealPhotos(date: string, meal: string) {
  return read<AlbumPhoto[]>('/diet/photos/of-meal', { date, meal }, 'diet')
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
  return read<FoodComboVO[]>('/combos', undefined, 'food')
}

export function createCombo(payload: ComboPayload) {
  return mutating('food', () => unwrap(request.post<FoodComboVO>('/combos', payload)))
}

export function updateCombo(id: number, payload: ComboPayload) {
  return mutating('food', () => unwrap(request.put<FoodComboVO>(`/combos/${id}`, payload)))
}

export function deleteCombo(id: number) {
  return mutating('food', () => unwrap(request.delete<void>(`/combos/${id}`)))
}

/** 整份加入某一餐：一次事务落多条普通条目（不新开记录类型）。 */
export function addMealsBatch(payload: {
  date: string
  meal: MealKey
  items: { foodId: number; grams: number }[]
  time?: string
}) {
  return mutating('diet', () => unwrap(request.post<MealItem[]>('/diet/meals/batch', payload)))
}

// ── 设置 ──

export function getSettings() {
  return read<DietSetting>('/settings', undefined, 'diet')
}

export function updateSettings(payload: DietSetting) {
  return mutating('diet', () => unwrap(request.put<DietSetting>('/settings', payload)))
}

/** 用量统计的时间范围：全部 / 本月 / 近 7 天（北京时间，按请求发出的时刻划）。 */
export type RecognitionStatsRange = 'all' | 'month' | 'week'

/** 用量统计里按模型或按入口的一组。 */
export interface RecognitionStatsGroup {
  /** 模型名或入口（label / meal）；null＝老记录没记。 */
  key: string | null
  requests: number
  /** token 合计（输入＋输出）。 */
  tokens: number
  /** 能计价部分的金额（元）。 */
  cost: number
  /** 其中算不了价的次数；等于 requests 时整组「未计价」。 */
  unpricedRequests: number
}

/** 用量统计的一天。 */
export interface RecognitionStatsDay {
  date: string
  requests: number
  tokens: number
  cost: number
}

/**
 * 识别用量统计（「用量统计」页，ADR 0054 后续注记）。金额只合计能计价的记录（目前只有 DeepSeek），
 * 由后端逐条估算后合计——前端不存价目。失败的调用照算 token 与钱。
 */
export interface RecognitionStats {
  range: RecognitionStatsRange
  /** 范围起止日；「全部」且没有记录时为 null。 */
  from: string | null
  to: string | null
  requests: number
  successes: number
  failures: number
  /** 输入（含缓存命中）、其中缓存命中、输出、其中思考、合计（输入＋输出）。 */
  tokensIn: number
  cachedTokens: number
  tokensOut: number
  reasoningTokens: number
  totalTokens: number
  cacheHitRate: number | null
  cost: number
  peakCost: number
  offPeakCost: number
  pricedRequests: number
  unpricedRequests: number
  unpricedTokens: number
  avgTokens: number | null
  avgCost: number | null
  avgDurationMs: number | null
  /** 能计价的记录落在放假表没覆盖的年份（这些年节假日只按周末判断）。 */
  uncoveredHolidayYears: number[]
  byModel: RecognitionStatsGroup[]
  byKind: RecognitionStatsGroup[]
  /** 从 from 到 to 每天一点，没记录的天为 0。 */
  daily: RecognitionStatsDay[]
}

/** 用量统计。**不缓存**：识别一次就变。 */
export function getRecognitionStats(range: RecognitionStatsRange = 'all') {
  return read<RecognitionStats>('/food-recognition/stats', { range }, 'food', false)
}
