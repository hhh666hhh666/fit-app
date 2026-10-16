import { formatThousands } from './format'

/**
 * 一次训练「成绩位」的口径（[ADR 0023]／[ADR 0025]）：只有三种情形——
 * 有力量动作 → 容量；只有层数口径（爬楼）→ 总层数；都没有 → 「—」。
 * 三个页面（会话页、汇总页、详情页）共用这一套判断，免得某页把纯有氧写成「0 kg」、另一页写成「—」。
 */
export type MetricKind = 'lift' | 'floors' | 'none'

export function metricKindOf(hasLift: boolean, floors: number): MetricKind {
  if (hasLift) return 'lift'
  return floors > 0 ? 'floors' : 'none'
}

/** 指标名。 */
export function metricLabel(kind: MetricKind): string {
  return kind === 'floors' ? '总层数' : '总容量'
}

/** 数字部分（不含单位）：都没练到就「—」。 */
export function metricValue(kind: MetricKind, volumeKg: number, floors: number): string {
  if (kind === 'lift') return formatThousands(Math.round(volumeKg))
  if (kind === 'floors') return formatThousands(floors)
  return '—'
}

/** 单位（`—` 时为空串，页面据此决定要不要画单位那截小字）。 */
export function metricUnit(kind: MetricKind): string {
  if (kind === 'lift') return 'kg'
  if (kind === 'floors') return '层'
  return ''
}

/** 数字＋单位（会话页底部那种一处显示的用法）。 */
export function metricText(kind: MetricKind, volumeKg: number, floors: number): string {
  const unit = metricUnit(kind)
  return unit ? `${metricValue(kind, volumeKg, floors)} ${unit}` : '—'
}
