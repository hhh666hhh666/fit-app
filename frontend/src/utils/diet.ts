import type { DayOverview, MealKey } from '@/api/nutrition'
import { nowTime, todayKey } from '@/utils/format'

// ── 饮食展示工具的共用层（首页卡与「饮食」页共用，避免两处各写一份）──

/** 营养素条形的一行（晴窗：「名称 ＋ 进度条 ＋ 现值／目标」，ADR 0041）。 */
export interface NutrientBar {
  key: 'protein' | 'carb' | 'fat' | 'water'
  name: string
  /** 现值／目标＋单位，如「82/140g」；饮水按升显示（「1.2/2L」），毫升在窄列里放不下。 */
  text: string
  /** 进度条填充比例 0–1；没设目标为 null（只画轨道）。 */
  ratio: number | null
}

/** 由当日总览拼四条营养素条形。 */
export function buildNutrientBars(day: DayOverview | null | undefined): NutrientBar[] {
  const t = day?.totals
  const g = day?.targets
  const ratioOf = (v: number, target: number | null | undefined): number | null =>
    target == null || Number(target) <= 0 ? null : Math.min(1, v / Number(target))
  const gram = (key: NutrientBar['key'], name: string, value: number, target: number | null | undefined): NutrientBar => {
    const v = Math.round(Number(value || 0))
    const tt = target == null ? '--' : String(Math.round(Number(target)))
    return { key, name, text: `${v}/${tt}g`, ratio: ratioOf(v, target) }
  }
  const litre = (ml: number) => String(Math.round(ml / 100) / 10)
  const waterMl = Number(day?.water.ml ?? 0)
  const waterTarget = day?.water.target
  return [
    gram('protein', '蛋白', t?.protein ?? 0, g?.protein),
    gram('carb', '碳水', t?.carb ?? 0, g?.carb),
    gram('fat', '脂肪', t?.fat ?? 0, g?.fat),
    {
      key: 'water',
      name: '饮水',
      text: `${litre(waterMl)}/${waterTarget == null ? '--' : litre(Number(waterTarget))}L`,
      ratio: ratioOf(waterMl, waterTarget)
    }
  ]
}

// ── 时刻（ADR 0059）──

/** HH:mm 的校验：00:00–23:59。 */
export const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/

const MEAL_DEFAULT_TIME: Partial<Record<MealKey, string>> = { breakfast: '08:00', lunch: '12:00', dinner: '18:00' }

/**
 * 记一餐时默认的时刻：记今天用现在的钟点；补记别的日子按餐次（早 8、午 12、晚 18，加餐用现在的钟点）。
 * 和服务端 EntryTime 同一口径。
 */
export function defaultEntryTime(date: string, meal: MealKey): string {
  if (date === todayKey()) return nowTime()
  return MEAL_DEFAULT_TIME[meal] ?? nowTime()
}

/** 条目按时刻排：没有时刻的老条目在后，同一时刻保持原来的先后。 */
export function byTime<T extends { time?: string | null }>(a: T, b: T): number {
  if (a.time && b.time) return a.time < b.time ? -1 : a.time > b.time ? 1 : 0
  if (a.time) return -1
  if (b.time) return 1
  return 0
}

// ── 食物列表与份量（ADR 0063）──

/** 列表里三大营养素的克数：10 以上取整，不到 10 留一位小数（0.4g 写成 0 会误导）。 */
export function macroGram(v: number): string {
  if (!Number.isFinite(v) || v <= 0) return '0'
  return v >= 10 ? String(Math.round(v)) : String(Math.round(v * 10) / 10)
}

/** 两位小数、去尾零。 */
export function trim2(n: number): string {
  return String(Math.round(n * 100) / 100)
}

/**
 * 份量输入的「份 / g」两种单位（ADR 0063）：有常用份才能按份。
 * 按份：填的是份数，总量＝份数×一份；按 g：填的是克数，合多少份＝克数÷一份。
 */
export type AmountMode = 'portion' | 'gram'

/** 把输入换成克数；输入不是正数时为 null。 */
export function amountToGrams(raw: string, mode: AmountMode, portionGrams: number | null | undefined): number | null {
  const n = Number(raw.trim())
  if (!raw.trim() || !Number.isFinite(n) || n <= 0) return null
  if (mode === 'portion') return portionGrams && portionGrams > 0 ? Math.round(n * portionGrams * 100) / 100 : null
  return n
}

/** 输入框下的小字：按份时写总量，按 g 时写合几份；没有常用份或输入无效时为空。 */
export function amountHint(
  raw: string,
  mode: AmountMode,
  portionGrams: number | null | undefined,
  unit: string
): string {
  const g = amountToGrams(raw, mode, portionGrams)
  if (g == null || !portionGrams || portionGrams <= 0) return ''
  return mode === 'portion' ? `共 ${trim2(g)}${unit}` : `约 ${trim2(g / portionGrams)} 份`
}
