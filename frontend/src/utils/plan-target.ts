/**
 * 计划目标的文案（[ADR 0027]）：一个动作的逐组目标怎么念。
 *
 * 各组的次数／重量全相同时折叠成人话（「目标 3 组 × 12 次 · 60 kg」）；
 * 不同时按斜杠列出每组的值（「目标 3 组 · 12/10/8 次 · 60/65/65 kg」）。
 * 计划编辑页与会话页共用这一处，避免两个页面把同一份目标念成两种样子。
 */

export interface SetTargetLike {
  reps: number | null
  weight: number | null
}

/**
 * @param sets   逐组目标（长度即组数，空数组＝不设目标）
 * @param cardio 有氧动作（按层数录）：只念组数，不念次数与重量
 */
export function formatSetTargets(
  sets: SetTargetLike[] | null | undefined,
  cardio = false
): string {
  const list = sets ?? []
  if (list.length === 0) return '未设目标'
  if (cardio) return `目标 ${list.length} 组`

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
