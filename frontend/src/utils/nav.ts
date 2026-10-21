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

/**
 * 播放一次切页入场。只动页面正文那一层（`__body`），**绝不动页面根元素**——
 * 底栏是根元素的 fixed 子元素，根元素一有 transform 就成了它的包含块，会跟着位移。
 *
 * 为什么用 Web Animations API 而不是 CSS 动画类：页面现在被保活（`switchTab`），
 * 不再重建，「挂载时带 class」只能播一次；WAAPI 可以在每次 `onShow` 直接重放，
 * 也不必为了重播去读 `offsetWidth` 强制重排。
 *
 * 时长与缓动对齐 `uni.scss` 的 `$dur-base` / `$ease-out`（JS 读不到 SCSS 变量，
 * 这两个数是唯一的手抄点，改 token 时记得同步这里）。
 */
export function playTabEnter(selector: string, dir: 'left' | 'right' | null) {
  if (!dir || typeof document === 'undefined') return
  if (typeof window !== 'undefined' && window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    return
  }
  const el = document.querySelector(selector) as HTMLElement | null
  if (!el || typeof el.animate !== 'function') return
  // 连点时把上一次没播完的取消，免得两段位移叠在一起
  el.getAnimations().forEach((a) => a.cancel())
  el.animate(
    [
      { transform: `translateX(${dir === 'right' ? 18 : -18}vw)`, opacity: 0.6 },
      { transform: 'translateX(0)', opacity: 1 }
    ],
    {
      duration: 220,
      easing: 'cubic-bezier(0.22, 0.61, 0.36, 1)',
      // backwards：结束后不残留 transform（0.17.3 的教训：残留 transform 会让正文常驻一个合成层）
      fill: 'backwards'
    }
  )
}

