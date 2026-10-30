import type { DiaryRef } from '@/api/diary'
import type { MealKey } from '@/api/nutrition'
import { formatDuration, formatThousands } from './format'
import { metricKindOf, metricText } from './metric'

// ── 记录引用（ADR 0044）的文案：卡片和选择弹层共用 ──

const MEAL_LABEL: Record<MealKey, string> = {
  breakfast: '早餐',
  lunch: '午餐',
  dinner: '晚餐',
  snack: '加餐'
}

/** 2026-10-30 → 10月30日 */
function dayLabel(iso?: string): string {
  if (!iso) return ''
  const [, m, d] = iso.split('-').map(Number)
  return `${m}月${d}日`
}

/** 卡片第一行。 */
export function refTitle(r: DiaryRef): string {
  const day = dayLabel(r.date)
  if (r.kind === 'workout') return `${r.title || '训练'} · ${day}`
  if (r.kind === 'meal' && r.meal) return `${MEAL_LABEL[r.meal]} · ${day}`
  return `全天饮食 · ${day}`
}

/** 卡片第二行：训练看时长与成绩（口径见 ADR 0025），饮食看热量与条数。 */
export function refSub(r: DiaryRef): string {
  if (r.kind === 'workout') {
    const floors = r.totalFloors ?? 0
    const kind = metricKindOf(r.hasStrength !== false, floors)
    return `${formatDuration(r.durationSec ?? 0)} · ${metricText(kind, Number(r.totalVolume ?? 0), floors)}`
  }
  return `${formatThousands(Math.round(Number(r.kcal ?? 0)))} 大卡 · ${r.itemCount ?? 0} 条`
}

/** 写进正文的一行：说明文字让原文也读得懂，真正定位靠键。 */
export function refMarkdown(r: DiaryRef): string {
  return `[${refTitle(r).replace(/[\[\]]/g, '')}](fit-ref:${r.key})`
}

/** 点卡片去哪；记录没了返回 null。 */
export function refUrl(r: DiaryRef): string | null {
  if (!r.exists) return null
  if (r.kind === 'workout') return `/pages/detail/index?id=${r.workoutId}`
  return `/pages/diet/index?date=${r.date}`
}
