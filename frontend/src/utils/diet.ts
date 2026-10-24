import type { DayOverview } from '@/api/nutrition'

// ── 饮食展示工具的共用层（首页卡与「饮食」页共用，避免两处各写一份）──

interface DietRingData {
  key: 'protein' | 'carb' | 'fat' | 'water'
  label: string
  /** 环内大数字（取整）。 */
  value: string
  /** 环内小字（现值 / 目标）。 */
  sub: string
  /** 完成度（无目标给「--」）。 */
  percent: string
}

/** 由当日总览拼四环展示数据。 */
export function buildDietRings(day: DayOverview | null | undefined): DietRingData[] {
  const t = day?.totals
  const g = day?.targets
  const mk = (
    key: DietRingData['key'],
    label: string,
    value: number,
    target: number | null | undefined
  ): DietRingData => {
    const v = Math.round(Number(value || 0))
    const tt = target == null ? null : Math.round(Number(target))
    return {
      key,
      label,
      value: String(v),
      sub: tt == null ? `${v} / --` : `${v} / ${tt}`,
      percent: tt == null || tt === 0 ? '--' : `${Math.min(999, Math.round((v / tt) * 100))}%`
    }
  }
  return [
    mk('protein', '蛋白(g)', t?.protein ?? 0, g?.protein),
    mk('carb', '碳水(g)', t?.carb ?? 0, g?.carb),
    mk('fat', '脂肪(g)', t?.fat ?? 0, g?.fat),
    mk('water', '饮水(ml)', day?.water.ml ?? 0, day?.water.target)
  ]
}

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
