// 四个主 tab 的切换方向信号（ADR 0037 / 0038）。
//
// 为什么需要它：四页之间走 `uni.switchTab`（页面保活，不再重建），但「从哪一页来」
// 只有发起方知道，只能在跳转前留下来。
//
// 为什么不落 Pinia／storage：这是一次性的过场信号，不是业务状态。开关是 SPA 内跳转、
// 不重载 JS，模块级变量足够存活；消费即清，也不需要「什么时候忘掉」的规则。
// 刷新页面时信号为空 → 退化为无方向（不播动画），天然安全。

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
 * 页面 `onShow` 时调用一次，取本次入场方向并清掉信号。
 * 'right' ＝ 目标在右边，内容从右侧进来；null ＝ 没有方向。
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

/** 时长与缓动对齐 `uni.scss` 的 `$dur-base` / `$ease-out`（JS 读不到 SCSS 变量，改 token 记得同步） */
const ENTER_MS = 220
const ENTER_EASE = 'cubic-bezier(0.22, 0.61, 0.36, 1)'

function prefersReducedMotion() {
  return typeof window !== 'undefined' && !!window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * 等选择器真的能在文档里找到元素再回调。上限 250ms——超时就放弃，
 * 宁可这一帧没动画，也不能把调用方卡住。
 *
 * ⚠️ 为什么不用 `ref`：uni-app H5 里 `<view ref="x">` 拿到的是**组件实例**
 * （`[object Object]`，没有 `tagName` / `animate`），拿它调 `el.animate` 会静默失败。
 * 为什么不在 `onShow` 里直接 `querySelector`：那一刻页面组件已建好，但 KeepAlive
 * 把它挂回 DOM 是之后的事，查不到就是 null——2026-10-21 真机「动效没了」两个坑都踩过。
 */
function whenInDom(selector: string, cb: (el: HTMLElement) => void) {
  const started = performance.now()
  const tick = () => {
    const el = document.querySelector(selector) as HTMLElement | null
    if (el && el.isConnected) {
      cb(el)
      return
    }
    if (performance.now() - started > 250) return
    requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
}

/**
 * 播放一次切页入场。只动页面正文那一层（`__body`），**绝不动页面根元素**——
 * 底栏是根元素的 fixed 子元素，根元素一有 transform 就成了它的包含块，会跟着位移。
 */
export function playTabEnter(selector: string, dir: 'left' | 'right' | null) {
  if (!dir || prefersReducedMotion()) return
  whenInDom(selector, (el) => {
    if (typeof el.animate !== 'function') return
    el.getAnimations().forEach((a) => a.cancel())
    el.animate(
      [
        { transform: `translateX(${dir === 'right' ? 18 : -18}vw)`, opacity: 0.6 },
        { transform: 'translateX(0)', opacity: 1 }
      ],
      {
        duration: ENTER_MS,
        easing: ENTER_EASE,
        // backwards：结束后不残留 transform（残留 transform 会让正文常驻一个合成层）
        fill: 'backwards'
      }
    )
  })
}

/**
 * 等元素进文档后再播一段动画，给 FtTabBar 的指示条用。
 * `target` 允许传组件实例（取 `$el`）——uni 的 `ref` 给的就是实例。
 */
export function animateWhenConnected(
  target: unknown,
  keyframes: Keyframe[],
  options: KeyframeAnimationOptions
) {
  const raw = (target as { $el?: HTMLElement } | null)?.$el ?? (target as HTMLElement | null)
  if (!raw || typeof raw.animate !== 'function') return
  const el = raw
  const started = performance.now()
  const tick = () => {
    if (el.isConnected) {
      el.getAnimations().forEach((a) => a.cancel())
      el.animate(keyframes, options)
      return
    }
    if (performance.now() - started > 250) return
    requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
}
