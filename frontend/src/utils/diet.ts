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
