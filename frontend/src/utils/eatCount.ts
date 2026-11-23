/**
 * 边吃边数（ADR 0067）：吃一个点一下的进行中计数，只存在这台手机上。
 *
 * - 开始时就定好记进哪一餐（中途可换），同一时间只有一场；
 * - 任何食物都能数：食物库的食物和吃过的临时条目；每种都要有「一个多少 g」，随时可补，吃完前必须补齐；
 * - 存本机（uni storage），App 被杀掉重启还在；记下或放弃之前不算吃过。
 *
 * 页面、首页 / 饮食页的常驻条都读同一份 `eatCount`（模块级 ref），改了就落盘。
 */
import { ref } from 'vue'
import type { FoodItem, MealKey, TempPick } from '@/api/nutrition'
import { nowTime, todayKey } from '@/utils/format'
import { portionUnit } from '@/utils/diet'

const STORAGE_KEY = 'ft.eatCount'

/** 每 100（g / ml）的营养；临时条目的营养只在这里。 */
export interface Per100 {
  kcal: number
  protein: number
  fat: number
  carb: number
  sodium: number | null
}

export interface CountItem {
  /** 列表里的唯一键：食物用 `f<id>`，临时条目用名字＋营养拼出来。 */
  key: string
  /** 食物库的食物 id；临时条目为 null。 */
  foodId: number | null
  name: string
  /** 食物图片版本号（有图才有）。 */
  imageKey: string | null
  /** 这种食物是不是用户自己建的（只有自己建的能把「一个多少 g」存回常用份）。 */
  own: boolean
  /** 克还是毫升。 */
  base: 'g' | 'ml'
  per100: Per100
  /** 按什么单位数（「个」「片」…）。 */
  unit: string
  /** 一个多少 g；还没填为 null。 */
  pieceGrams: number | null
  /** 补的克数要不要存回这条食物的常用份（只对自己建的食物有意义，默认勾）。 */
  savePortion: boolean
  /** 食物原本就有常用份（这时不用再问存不存）。 */
  hadPortion: boolean
  count: number
}

export interface EatCount {
  v: 1
  meal: MealKey
  /** 开始那一刻的日期与钟点：记进去的日期和时刻都按它（三餐已有时刻的由服务端沿用）。 */
  date: string
  time: string
  startedAt: number
  items: CountItem[]
}

function read(): EatCount | null {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY)
    if (!raw) return null
    const s = (typeof raw === 'string' ? JSON.parse(raw) : raw) as EatCount
    if (!s || s.v !== 1 || !Array.isArray(s.items)) return null
    return s
  } catch {
    return null
  }
}

/** 进行中的那一场；没有为 null。 */
export const eatCount = ref<EatCount | null>(read())

/** 改完就调：落盘（失败就算了，至少这次打开还在内存里）。 */
export function persist() {
  try {
    if (eatCount.value) uni.setStorageSync(STORAGE_KEY, JSON.stringify(eatCount.value))
    else uni.removeStorageSync(STORAGE_KEY)
  } catch {
    // 存储满了 / 被拦：不影响这次计数
  }
}

/** 开一场新的；已经有一场时原样返回那一场（同一时间只有一场）。 */
export function startCount(meal: MealKey): EatCount {
  if (eatCount.value) return eatCount.value
  eatCount.value = { v: 1, meal, date: todayKey(), time: nowTime(), startedAt: Date.now(), items: [] }
  persist()
  return eatCount.value
}

export function clearCount() {
  eatCount.value = null
  persist()
}

export function foodKey(f: FoodItem): string {
  return `f${f.id}`
}

export function tempKey(t: TempPick): string {
  return `t${t.name}|${t.kcal}|${t.protein}|${t.fat}|${t.carb}|${t.sodium ?? ''}`
}

/** 由食物库的一条食物做一张计数卡：有常用份就按它的单位和克数，没有就按「个」、克数待补。 */
export function itemFromFood(f: FoodItem): CountItem {
  const pg = f.portionGrams && Number(f.portionGrams) > 0 ? Number(f.portionGrams) : null
  return {
    key: foodKey(f),
    foodId: f.id,
    name: f.name,
    imageKey: f.imageKey ?? null,
    own: !f.preset,
    base: f.unit === 'ml' ? 'ml' : 'g',
    per100: { kcal: Number(f.kcal), protein: Number(f.protein), fat: Number(f.fat), carb: Number(f.carb), sodium: f.sodium ?? null },
    unit: pg ? portionUnit(f.portionLabel) : '个',
    pieceGrams: pg,
    savePortion: !f.preset && !pg,
    hadPortion: !!pg,
    count: 0
  }
}

/** 由吃过的临时条目做一张计数卡：没有常用份，按「个」、克数待补，不能存回。 */
export function itemFromTemp(t: TempPick): CountItem {
  return {
    key: tempKey(t),
    foodId: null,
    name: t.name,
    imageKey: null,
    own: false,
    base: 'g',
    per100: { kcal: Number(t.kcal), protein: Number(t.protein), fat: Number(t.fat), carb: Number(t.carb), sodium: t.sodium ?? null },
    unit: '个',
    pieceGrams: null,
    savePortion: false,
    hadPortion: false,
    count: 0
  }
}

/** 这一种吃了多少克（没填一个多少 g 时为 null）。 */
export function itemGrams(it: CountItem): number | null {
  return it.pieceGrams && it.pieceGrams > 0 ? Math.round(it.count * it.pieceGrams * 100) / 100 : null
}

/** 这一种的某项营养合计（没填克数时按 0 算）。 */
export function itemNutrient(it: CountItem, k: 'kcal' | 'protein' | 'fat' | 'carb'): number {
  const g = itemGrams(it)
  return g == null ? 0 : (Number(it.per100[k] || 0) * g) / 100
}

/** 一句话摘要：「饺子 12 个 · 猪肉丸 3 个」（常驻条用）。 */
export function countSummary(s: EatCount): string {
  const parts = s.items.filter((it) => it.count > 0).map((it) => `${it.name} ${it.count} ${it.unit}`)
  return parts.length ? parts.join(' · ') : '还没开始数'
}

const MEAL_NAMES: Record<MealKey, string> = { breakfast: '早餐', lunch: '午餐', dinner: '晚餐', snack: '加餐' }
const MEAL_ORDER: MealKey[] = ['breakfast', 'lunch', 'dinner', 'snack']

/** 按现在的钟点猜是哪一餐（入口选餐次时预选）：10:30 前早餐、14:30 前午餐、17–21 点晚餐，其余加餐。 */
export function mealByNow(d = new Date()): MealKey {
  const m = d.getHours() * 60 + d.getMinutes()
  if (m >= 5 * 60 && m < 10 * 60 + 30) return 'breakfast'
  if (m >= 10 * 60 + 30 && m < 14 * 60 + 30) return 'lunch'
  if (m >= 17 * 60 && m < 21 * 60) return 'dinner'
  return 'snack'
}

function go(meal: MealKey) {
  uni.navigateTo({ url: `/pages/eat-count/index?meal=${meal}` })
}

/**
 * 进边吃边数（ADR 0067）：
 * - 给了餐次（长按餐次按钮）：没有进行中的就开这一餐；有进行中的、餐次一样就回到它；不一样先问一句再去那一场；
 * - 没给餐次（首页第二页的入口）：有进行中的直接回去；没有就先选餐次，按现在的钟点猜的那一餐排第一。
 */
export function openEatCount(meal: MealKey | null) {
  const cur = eatCount.value
  if (cur) {
    if (meal == null || meal === cur.meal) {
      go(cur.meal)
      return
    }
    uni.showModal({
      title: `正在数${MEAL_NAMES[cur.meal]}`,
      content: '同一时间只能数一场，先去看看正在数的那一场？',
      confirmText: '去看看',
      success: ({ confirm }) => {
        if (confirm) go(cur.meal)
      }
    })
    return
  }
  if (meal) {
    go(meal)
    return
  }
  const guess = mealByNow()
  const order = [guess, ...MEAL_ORDER.filter((m) => m !== guess)]
  uni.showActionSheet({
    itemList: order.map((m, i) => (i === 0 ? `${MEAL_NAMES[m]}（现在）` : MEAL_NAMES[m])),
    success: ({ tapIndex }) => go(order[tapIndex])
  })
}
