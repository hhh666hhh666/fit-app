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
