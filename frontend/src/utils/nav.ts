// 四个主 tab 的切换方向信号（ADR 0037 / 0038 / 0040）。
//
// 为什么需要它：四页之间走 `uni.switchTab`（页面保活，不再重建），但「从哪一页来」
// 只有发起方知道，只能在跳转前留下来。
//
// 为什么不落 Pinia／storage：这是一次性的过场信号，不是业务状态。开关是 SPA 内跳转、
// 不重载 JS，模块级变量足够存活；消费即清，也不需要「什么时候忘掉」的规则。

export type TabKey = 'home' | 'log' | 'stats' | 'profile'

/** 下标即 tab 栏的左右顺序，方向只由下标差决定 */
const ORDER: readonly TabKey[] = ['home', 'log', 'stats', 'profile']

/** 信号有效期：超时即视为上一次的残留（连点、慢加载、从下级页回来） */
const FRESH_MS = 800

let pending: { from: TabKey; at: number } | null = null

/** 最近一次有效入场是从哪一格来的，给指示条当起点（跨格跳时不能只滑一格） */
let lastFromIdx: number | null = null

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
  lastFromIdx = null
  if (!signal || Date.now() - signal.at > FRESH_MS) return null
  const fromIdx = ORDER.indexOf(signal.from)
  const toIdx = ORDER.indexOf(to)
  if (fromIdx < 0 || toIdx < 0 || fromIdx === toIdx) return null
  lastFromIdx = fromIdx
  return toIdx > fromIdx ? 'right' : 'left'
}

/** 本次入场的来源格下标；没有有效信号时为 null */
export function lastTabFromIndex(): number | null {
  return lastFromIdx
}

/**
 * 时长与缓动对齐 `uni.scss` 的 `$ease-out`（JS 读不到 SCSS 变量，改 token 记得同步）。
 * 指示条也用这一对——正文和指示条是同一个动作，节奏必须一致。
 */
export const SLIDE_MS = 260
export const SLIDE_EASE = 'cubic-bezier(0.22, 0.61, 0.36, 1)'

/** 旧页克隆层最多等新页多久；超时就自己先滑，免得卡在旧页上 */
const HOLD_MAX_MS = 400

/**
 * 克隆体挂在 `body` 下，脱离了页面容器，**继承来的文字样式全丢**——全局的
 * `page { font-size; line-height: 1.6 }` 落在 `uni-page-body` 上，克隆体拿不到，
 * 行高退回 normal，每行变矮、整页看起来往上一蹦（2026-10-22 真机两帧对比）。
 * 所以克隆时把原位置父级上算好的这些值抄到克隆体上。
 */
const INHERITED = [
  'color',
  'font-family',
  'font-size',
  'font-weight',
  'letter-spacing',
  'text-align',
  'word-break',
  '-webkit-text-size-adjust'
] as const

function copyInherited(from: Element | null, to: HTMLElement) {
  if (!from) return
  const cs = getComputedStyle(from)
  for (const p of INHERITED) {
    const v = cs.getPropertyValue(p)
    if (v) to.style.setProperty(p, v)
  }
  // 行高单独处理：全局写的是无单位的 1.6（每个元素按自己的字号算），getComputedStyle
  // 却只给算好的 px。原样抄 px 会把所有子元素钉成同一个行高，大字号那几行照样变矮——
  // 所以折回成相对字号的倍数再抄。
  const lh = parseFloat(cs.lineHeight)
  const fs = parseFloat(cs.fontSize)
  if (lh > 0 && fs > 0) to.style.lineHeight = String(Math.round((lh / fs) * 1000) / 1000)
  // CSS 变量也是继承的（组件库主题变量等），一并带上
  for (let i = 0; i < cs.length; i++) {
    const p = cs[i]
    if (p.startsWith('--')) to.style.setProperty(p, cs.getPropertyValue(p))
  }
}

/** 已铺好、还没起跑的旧页克隆层；由新页入场同帧放行 */
let heldOut: (() => void) | null = null

function releaseOut() {
  const go = heldOut
  heldOut = null
  go?.()
}

function prefersReducedMotion() {
  return typeof window !== 'undefined' && !!window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * 切页转场：**把当前页正文克隆一层盖上去滑出**，新页从另一侧同帧滑入。
 *
 * 为什么不用 View Transitions：2026-10-21 上线后发现主人设备的 WebView 不支持
 * （该特性要 Chrome 111+），`startViewTransition` 不存在 → 一直走兜底硬切，于是
 * 「中间一帧整页空白、连底栏都没有」。依赖运行时新特性的路子赌不起。
 *
 * 克隆层还有个白捡的好处：新页挂载要几十到几百毫秒，这段时间**屏幕上是旧页在挡着**，
 * 白屏被遮住了——这正是之前所有版本都没解决的。
 *
 * **两层必须同帧起跑**（2026-10-22 修）：以前克隆层一点就滑，新页要等 `onShow` ＋ 一帧才动，
 * 中间那几帧新页已经停在原位、又被透明的克隆层叠着——看上去是「两页重影、再往回弹一下」。
 * 现在克隆层铺好后**先不动**（层有底色，把底下的新页挡住），由 `playTabEnter` 放行；
 * 新页迟迟不来就在 `HOLD_MAX_MS` 后自己滑。
 *
 * `dir` 是旧页滑出的方向；`fromSelectors` 是当前页要跟着滑的块（正文 `.log__body` 等，
 * 首页还有底部的 `.home__dock`）。每块按它此刻在视口里的位置钉进克隆层，所以页面滚动
 * 不用另算。tabbar 不在这些块里，且四页的 `__bottom` 都是 `z-index: 20`，
 * 克隆层（10）压不到它——只有 tabbar 钉住不动。
 */
export function startSlide(
  dir: 'left' | 'right',
  navigate: () => void,
  fromSelectors: readonly string[]
) {
  const srcs = fromSelectors
    .map((s) => document.querySelector(s) as HTMLElement | null)
    .filter((el): el is HTMLElement => !!el && typeof el.cloneNode === 'function')
  if (!srcs.length || prefersReducedMotion()) {
    navigate()
    return
  }

  const layer = document.createElement('div')
  layer.className = 'tab-slide-layer'
  for (const src of srcs) {
    const r = src.getBoundingClientRect()
    const clone = src.cloneNode(true) as HTMLElement
    copyInherited(src.parentElement, clone)
    Object.assign(clone.style, {
      position: 'absolute',
      left: `${r.left}px`,
      top: `${r.top}px`,
      width: `${r.width}px`,
      margin: '0',
      boxSizing: 'border-box'
    })
    layer.appendChild(clone)
  }
  document.body.appendChild(layer)

  let removed = false
  const drop = () => {
    if (removed) return
    removed = true
    layer.remove()
  }

  // 上一次还没放行的层（连点）立刻放它滑走，不排队
  releaseOut()
  const dx = dir === 'left' ? -100 : 100
  heldOut = () => {
    const anim = layer.animate(
      [{ transform: 'translateX(0)' }, { transform: `translateX(${dx}%)` }],
      { duration: SLIDE_MS, easing: SLIDE_EASE, fill: 'forwards' }
    )
    void Promise.resolve(anim.finished).catch(() => {}).then(drop)
    // 兜底：动画被跳过（某些省电模式）也要把层摘掉，否则它一直盖在页面上
    setTimeout(drop, SLIDE_MS + 250)
  }
  const mine = heldOut
  setTimeout(() => {
    if (heldOut === mine) releaseOut()
  }, HOLD_MAX_MS)

  // 先把层铺上去，再切换：新页挂载期间由旧页克隆层顶着，不是白屏
  navigate()
}

/**
 * 新页入场：从另一侧整屏滑进来。
 *
 * 传选择器而不是元素：`onShow` 触发时新页的 DOM 未必已经在文档里（首次挂载尤其慢），
 * 而 uni-app H5 的 `<view ref>` 拿到的是**组件实例**（没有 `tagName` / `animate`）。
 * 所以这里轮询到元素真的连进文档再播；第一块（正文）就绪的那一刻同时放行旧页克隆层，
 * 两边在同一帧起跑。`selectors` 里其余的块（首页 `.home__dock`）跟着一起滑。
 */
export function playTabEnter(selectors: string | readonly string[], dir: 'left' | 'right' | null) {
  if (!dir || prefersReducedMotion()) {
    releaseOut()
    return
  }
  const list = typeof selectors === 'string' ? [selectors] : selectors
  const started = performance.now()
  const tick = () => {
    const el = document.querySelector(list[0]) as HTMLElement | null
    if (el && el.isConnected) {
      if (typeof el.animate !== 'function') return releaseOut()
      releaseOut()
      for (const s of list) {
        const target = document.querySelector(s) as HTMLElement | null
        if (!target?.isConnected) continue
        target.getAnimations().forEach((a) => a.cancel())
        target.animate(
          [
            { transform: `translateX(${dir === 'right' ? 100 : -100}%)` },
            { transform: 'translateX(0)' }
          ],
          { duration: SLIDE_MS, easing: SLIDE_EASE, fill: 'backwards' }
        )
      }
      return
    }
    // 两秒还没出来就别等了：页面根本没挂上，动画也没有意义
    if (performance.now() - started > 2000) return releaseOut()
    requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
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
    if (performance.now() - started > 2000) return
    requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
}
