/**
 * 计划目标的文案（[ADR 0027]／[ADR 0031]）：一个动作的逐组目标怎么念。
 *
 * 口径（mode）决定念哪一栏：
 * - `strength`：次数／重量。各组同值折叠成人话（「目标 3 组 × 12 次 · 60 kg」），不同则按斜杠列出
 *   （「目标 3 组 · 12/10/8 次 · 60/65/65 kg」）。
 * - `floors`：爬楼只念层数（「目标 3 组 × 20 层」／「目标 3 组 · 20/20/15 层」）。
 * - `cardio`：有专属口径之外的有氧（未来跑步之类）只念组数。
 *
 * 计划编辑页、计划预览弹层与会话页共用这一处，避免三个地方把同一份目标念成三种样子。
 */

export interface SetTargetLike {
  reps?: number | null
  weight?: number | null
  /** 逐组层数：仅 `metric = floors` 的动作有值（ADR 0031）。 */
  floors?: number | null
}

export type TargetMode = 'strength' | 'floors' | 'cardio'

/**
 * 由动作的记录口径与肌群码判该念哪一栏。
 * `metric === 'floors'`（爬楼）优先于肌群；只有肌群是有氧、口径不是 floors 的动作归 `cardio`。
 */
export function targetModeOf(metric?: string | null, muscle?: string | null): TargetMode {
  if (metric === 'floors') return 'floors'
  if (muscle === 'cardio') return 'cardio'
  return 'strength'
}

/**
 * @param sets 逐组目标（长度即组数，空数组＝不设目标）
 * @param mode 念哪一栏；缺省按力量动作（次数／重量）
 */
export function formatSetTargets(
  sets: SetTargetLike[] | null | undefined,
  mode: TargetMode = 'strength'
): string {
  const list = sets ?? []
  if (list.length === 0) return '未设目标'
  if (mode === 'cardio') return `目标 ${list.length} 组`

  if (mode === 'floors') {
    const floors = list.map((s) => s.floors ?? null)
    if (floors.every((v) => v === floors[0])) {
      return floors[0] != null ? `目标 ${list.length} 组 × ${floors[0]} 层` : `目标 ${list.length} 组`
    }
    return `目标 ${list.length} 组 · ${floors.map((v) => (v == null ? '–' : String(v))).join('/')} 层`
  }

  const reps = list.map((s) => s.reps)
  const weights = list.map((s) => s.weight)
  const sameReps = reps.every((v) => v === reps[0])
  const sameWeight = weights.every((v) => v === weights[0])

  const parts: string[] = [`${list.length} 组`]
  if (sameReps) {
    if (reps[0] != null) parts.push(`${reps[0]} 次`)
  } else if (reps.some((v) => v != null)) {
    parts.push(`${reps.map((v) => (v == null ? '–' : String(v))).join('/')} 次`)
  }
  if (sameWeight) {
    if (weights[0] != null) parts.push(`${weights[0]} kg`)
  } else if (weights.some((v) => v != null)) {
    parts.push(`${weights.map((v) => (v == null ? '–' : String(v))).join('/')} kg`)
  }
  return `目标 ${parts.join(' · ')}`
}
