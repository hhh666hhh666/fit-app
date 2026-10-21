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

/** 时长与缓动对齐 `uni.scss` 的 `$ease-out`（JS 读不到 SCSS 变量，改 token 记得同步） */
const SLIDE_MS = 260
const EASE = 'cubic-bezier(0.22, 0.61, 0.36, 1)'

function prefersReducedMotion() {
  return typeof window !== 'undefined' && !!window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** 页面滚动发生在外层容器上，克隆体要从顶部整体上移同样的距离才对得上当前视口 */
function scrollTopOf(el: HTMLElement) {
  let node: HTMLElement | null = el.parentElement
  while (node) {
    if (node.scrollTop > 0) return node.scrollTop
    node = node.parentElement
  }
  return document.scrollingElement?.scrollTop ?? window.scrollY ?? 0
}

/**
 * 切页转场：**把当前页正文克隆一层盖上去滑出**，紧接着新页从另一侧滑入。
 *
 * 为什么不用 View Transitions：2026-10-21 上线后发现主人设备的 WebView 不支持
 * （该特性要 Chrome 111+），`startViewTransition` 不存在 → 一直走兜底硬切，于是
 * 「中间一帧整页空白、连底栏都没有」。依赖运行时新特性的路子赌不起。
 *
 * 克隆层还有个白捡的好处：新页挂载要几十到几百毫秒，这段时间**屏幕上是旧页在挡着**，
 * 白屏被遮住了——这正是之前所有版本都没解决的。
 *
 * `dir` 是旧页滑出的方向；`fromSelector` 是当前页正文的选择器（`.log__body` 等）。
 * 底栏不在正文里，且四个页面的 `__bottom` 都有 `z-index: 20`，克隆层（z-index 10）
 * 压不到它——底栏钉住不动。
 */
export function startSlide(
  dir: 'left' | 'right',
  navigate: () => void,
  fromSelector: string
) {
  const src = document.querySelector(fromSelector) as HTMLElement | null
  if (!src || typeof src.cloneNode !== 'function' || prefersReducedMotion()) {
    navigate()
    return
  }

  const layer = document.createElement('div')
  layer.className = 'tab-slide-layer'
  const clone = src.cloneNode(true) as HTMLElement
  clone.style.transform = `translateY(${-scrollTopOf(src)}px)`
  layer.appendChild(clone)
  document.body.appendChild(layer)

  // 先把层铺上去，再切换：新页挂载期间由旧页克隆层顶着，不是白屏
  navigate()

  const dx = dir === 'left' ? -100 : 100
  const anim = layer.animate(
    [{ transform: 'translateX(0)' }, { transform: `translateX(${dx}%)` }],
    { duration: SLIDE_MS, easing: EASE, fill: 'forwards' }
  )
  let removed = false
  const drop = () => {
    if (removed) return
    removed = true
    layer.remove()
  }
  void Promise.resolve(anim.finished).catch(() => {}).then(drop)
  // 兜底：动画被跳过（某些省电模式）也要把层摘掉，否则它一直盖在页面上
  setTimeout(drop, SLIDE_MS + 250)
}

/**
 * 新页入场：从另一侧整屏滑进来。
 *
 * 传选择器而不是元素：`onShow` 触发时新页的 DOM 未必已经在文档里（首次挂载尤其慢），
 * 而 uni-app H5 的 `<view ref>` 拿到的是**组件实例**（没有 `tagName` / `animate`）。
 * 所以这里轮询到元素真的连进文档再播——**不设短上限**，晚到也要滑进来，
 * 因为那时候旧页克隆层多半已经滑走，正是它该出现的时刻。
 */
export function playTabEnter(selector: string, dir: 'left' | 'right' | null) {
  if (!dir || prefersReducedMotion()) return
  const started = performance.now()
  const tick = () => {
    const el = document.querySelector(selector) as HTMLElement | null
    if (el && el.isConnected) {
      if (typeof el.animate !== 'function') return
      el.getAnimations().forEach((a) => a.cancel())
      el.animate(
        [
          { transform: `translateX(${dir === 'right' ? 100 : -100}%)` },
          { transform: 'translateX(0)' }
        ],
        { duration: SLIDE_MS, easing: EASE, fill: 'backwards' }
      )
      return
    }
    // 两秒还没出来就别等了：页面根本没挂上，动画也没有意义
    if (performance.now() - started > 2000) return
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
