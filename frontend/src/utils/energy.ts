/**
 * 每日消耗与热量缺口的**展示口径**（[ADR 0029]）。
 *
 * 计算只在后端 `EnergyService` 一处；这里只管「怎么念」——标签、缺口文案。
 * 身体档案页与饮食页共用，避免同一个「1.35」被两个页面说成两种话。
 */

export type ActivityLevelKey = 'sedentary' | 'light' | 'active'
export type SexKey = 'male' | 'female'

export const SEX_OPTIONS: { key: SexKey; label: string }[] = [
  { key: 'male', label: '男' },
  { key: 'female', label: '女' }
]

/** 生活活动档：**只含日常生活，不含专门训练**（系数与后端 `EnergyService` 一一对应）。 */
export const ACTIVITY_OPTIONS: {
  key: ActivityLevelKey
  label: string
  desc: string
  factor: number
}[] = [
  { key: 'sedentary', label: '久坐', desc: '办公室、几乎不走动（×1.2）', factor: 1.2 },
  { key: 'light', label: '经常走动', desc: '时不时起身、通勤走路（×1.35）', factor: 1.35 },
  { key: 'active', label: '体力工作', desc: '整天站着或搬东西（×1.5）', factor: 1.5 }
]

export function sexLabel(v?: string | null): string {
  if (v === 'female') return '女'
  if (v === 'male') return '男'
  return '–'
}

export function activityLabel(v?: string | null): string {
  return ACTIVITY_OPTIONS.find((o) => o.key === v)?.label ?? '–'
}

/**
 * 缺口文案：**不带正负号**——消耗大于摄入说「缺口」，反过来「盈余」，两个词都按正数念。
 * 红涨绿跌那套符号语义用在热量上会被误读成好坏。
 */
export function gapText(gap?: number | null): string {
  if (gap == null || !Number.isFinite(gap)) return ''
  return gap >= 0 ? `缺口 ${Math.round(gap)}` : `盈余 ${Math.abs(Math.round(gap))}`
}
