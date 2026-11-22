// ── 识别前的提示词：最近用过的几条（ADR 0060）──
//    只是本机的快捷项：每次识别提示词框都是空的，点了快捷项才带上，绝不自动填（带到下一张照片会误导 AI）。
//    按用户分开存（current-user.ts 的用户 id）；存不了就当没有，不影响识别。

import { currentUserId } from './current-user'

const MAX = 5
const keyOf = () => `fitapp_recog_hints_${currentUserId() || 'anon'}`

/** 最近用过的提示词，最近的在前。 */
export function recentHints(): string[] {
  try {
    const raw = uni.getStorageSync(keyOf()) as unknown
    const list = typeof raw === 'string' && raw ? (JSON.parse(raw) as unknown) : raw
    return Array.isArray(list) ? list.filter((s): s is string => typeof s === 'string' && !!s.trim()).slice(0, MAX) : []
  } catch {
    return []
  }
}

/** 用过一条就挪到最前（去重，最多留 5 条）。 */
export function rememberHint(hint: string): void {
  const h = hint.trim()
  if (!h) return
  try {
    const next = [h, ...recentHints().filter((x) => x !== h)].slice(0, MAX)
    uni.setStorageSync(keyOf(), JSON.stringify(next))
  } catch {
    /* 存不了就算了 */
  }
}
