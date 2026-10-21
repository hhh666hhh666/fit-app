// 四个主 tab 的切换方向信号（ADR 0037）。
//
// 为什么需要它：四页之间走 uni.reLaunch——旧页在新页挂载之前就已卸载，
// 「A 滑出、B 滑入」这种真推拉从来没有一帧同时存在两页。能做的只有让**新页**
// 按方向入场，而方向只有发起方知道，只能在跳转前留下来。
//
// 为什么不落 Pinia／storage：这是一次性的过场信号，不是业务状态。reLaunch 是
// SPA 内跳转、不重载 JS，模块级变量足够存活；消费即清，也不需要「什么时候忘掉」
// 的规则。刷新页面时信号为空 → 退化成无方向的淡入，天然安全。

export type TabKey = 'home' | 'log' | 'stats' | 'profile'

/** 下标即 tab 栏的左右顺序，方向只由下标差决定 */
const ORDER: readonly TabKey[] = ['home', 'log', 'stats', 'profile']

/** 信号有效期：超时即视为上一次的残留（连点、慢加载、从下级页回来） */
const FRESH_MS = 800

let pending: { from: TabKey; at: number } | null = null

/** 跳转前调用，记下「从哪来」 */
export function markTabNav(from: TabKey) {
  pending = { from, at: Date.now() }
}

/**
 * 页面 setup 时调用一次，取本次入场方向并清掉信号。
 * 'right' ＝ 目标在右边，内容从右侧进来；null ＝ 没有方向，走纯淡入。
 */
export function takeTabEnter(to: TabKey): 'left' | 'right' | null {
  const signal = pending
  pending = null
  if (!signal || Date.now() - signal.at > FRESH_MS) return null
  const fromIdx = ORDER.indexOf(signal.from)
  const toIdx = ORDER.indexOf(to)
  if (fromIdx < 0 || toIdx < 0 || fromIdx === toIdx) return null
  return toIdx > fromIdx ? 'right' : 'left'
}
