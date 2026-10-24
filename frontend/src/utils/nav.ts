// 四个主 tab 的切换方向信号（ADR 0037 / 0038 / 0040）。
//
// 为什么需要它：四页之间走 `uni.switchTab`（页面保活，不再重建），但「从哪一页来」
// 只有发起方知道，只能在跳转前留下来。
//
// 为什么不落 Pinia／storage：这是一次性的过场信号，不是业务状态。开关是 SPA 内跳转、
// 不重载 JS，模块级变量足够存活；消费即清，也不需要「什么时候忘掉」的规则。

import { tabIndex, type TabKey } from '@/utils/tabs'

export type { TabKey }

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
  // 方向只由 tab 表里的下标差决定（下标即底栏的左右顺序）
  const fromIdx = tabIndex(signal.from)
  const toIdx = tabIndex(to)
  if (fromIdx < 0 || toIdx < 0 || fromIdx === toIdx) return null
  lastFromIdx = fromIdx
  return toIdx > fromIdx ? 'right' : 'left'
}

/** 本次入场的来源格下标；没有有效信号时为 null */
export function lastTabFromIndex(): number | null {
  return lastFromIdx
}

/**
 * 时长与缓动是 `uni.scss` 的 `$dur-slide` / `$ease-out` 的镜像（JS 读不到 SCSS 变量；
 * `scripts/check-tab-config.sh` 校验两边一致）。
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

/**
 * `cloneNode` 不带 canvas 的像素，克隆出来是空白（趋势图）。
 * 按顺序一一对应，把当前这一帧画过去——滑出那 260ms 里它们就是一张静止的截图。
 */
function copyCanvases(from: HTMLElement, to: HTMLElement) {
  const srcs = from.querySelectorAll('canvas')
  if (!srcs.length) return
  const dsts = to.querySelectorAll('canvas')
  srcs.forEach((s, i) => {
    const d = dsts[i]
    if (!d || !s.width || !s.height) return
    try {
      d.width = s.width
      d.height = s.height
      d.getContext('2d')?.drawImage(s, 0, 0)
    } catch {
      // 跨域污染等画不过去就算了，空白一闪而过，不影响切页
    }
  })
}

/**
 * 已铺好、还没起跑的旧页克隆层；由新页入场同帧放行。
 * 参数 `withEnter`：新页正在同一帧滑进来（`playTabEnter` 放行）＝ true；
 * 新页没来、超时或没有方向（兜底放行）＝ false，见 startSlide 里两种滑法的区别。
 */
let heldOut: ((withEnter: boolean) => void) | null = null

function releaseOut(withEnter = false) {
  const go = heldOut
  heldOut = null
  go?.(withEnter)
}

function prefersReducedMotion() {
  return typeof window !== 'undefined' && !!window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** 滑动期间挂在 <html> 上：玻璃去掉背景模糊（`uni.scss` 的 glass mixin） */
const SLIDING_CLASS = 'is-tab-sliding'
/** 连点时上一层还没摘、下一层又铺上，用计数而不是开关，最后一层摘掉才撤类 */
let slidingCount = 0

/**
 * 副本里的 SVG id 必须改名：同一个 id 在文档里出现两次时，`url(#id)` 解析到**文档顺序里
 * 第一个**——那是旧页里的原件，旧页一切走就被藏起来，副本的投影跟着消失。
 */
function renameSvgIds(root: HTMLElement) {
  root.querySelectorAll('[id]').forEach((el) => {
    const old = el.id
    const fresh = `${old}-pin`
    el.id = fresh
    root.querySelectorAll(`[filter="url(#${old})"]`).forEach((ref) => {
      ref.setAttribute('filter', `url(#${fresh})`)
    })
  })
}

/** 只对「从上往下依次排」的容器剪枝：删掉一段子块后，剩下的块位置算得出来 */
function stacksVertically(cs: CSSStyleDeclaration): boolean {
  if (cs.display === 'block' || cs.display === 'flow-root') return true
  const flex = cs.display === 'flex' || cs.display === 'inline-flex'
  return flex && cs.flexDirection === 'column'
    && ['normal', 'flex-start', 'start'].includes(cs.justifyContent)
}

const isPositioned = (el: Element) => /^(absolute|fixed)$/.test(getComputedStyle(el).position)
const isInlineLevel = (el: Element) => /^(inline|contents)/.test(getComputedStyle(el).display)
const hasLooseText = (el: Element) =>
  Array.from(el.childNodes).some((n) => n.nodeType === Node.TEXT_NODE && !!n.textContent?.trim())

/**
 * 克隆体只留看得见的那一屏（2026-10-24）。
 *
 * 为什么：`cloneNode(true)` 复制整页，日志页翻过几页之后几百行全在里面，点下去那一刻
 * 要插进文档、算样式、排版——直接变成点击延迟。而滑出去的只有视口里那一屏。
 *
 * 怎么剪（对照原件的位置，逐层往下，最多 6 层）：
 * - 视口**下方**的子块直接删——它们排在后面，删了不影响前面的块；
 * - 视口**上方**的一段子块换成一个等高的占位块——直接删会让下面的内容整体上移。
 *   占位高度＝第一个可见块的顶 − 第一个块的顶 − 行间距 − 可见块的上外边距，
 *   块级（外边距折叠）和纵向 flex（gap 不折叠）两种排法都对得上。
 * 只剪「从上往下依次排」的容器（块级、纵向且从头排的 flex）；居中、反向、网格、横排、
 * 内部滚动的容器原样保留，剪了位置就不对了。
 */
function pruneOffscreen(src: HTMLElement, clone: HTMLElement, depth = 0) {
  if (depth > 6) return
  const kids = Array.from(src.children) as HTMLElement[]
  const twins = Array.from(clone.children) as HTMLElement[]
  if (!kids.length || kids.length !== twins.length) return
  const cs = getComputedStyle(src)
  if (/(auto|scroll)/.test(cs.overflowX + cs.overflowY)) return

  const vh = window.innerHeight
  const rects = kids.map((k) => k.getBoundingClientRect())

  // 先往下钻：横跨视口的块里面可能还是一长串
  rects.forEach((r, i) => {
    if (r.bottom > 0 && r.top < vh && kids[i].children.length) pruneOffscreen(kids[i], twins[i], depth + 1)
  })

  if (!stacksVertically(cs)) return

  for (let i = kids.length - 1; i >= 0; i--) {
    if (rects[i].top > vh) twins[i].remove()
  }

  const first = rects.findIndex((r) => r.bottom > 0)
  if (first <= 0) return
  // 占位公式只对块级盒子成立：行内元素的框比它所在的行框矮（实测差 2px，后面整串跟着上移），
  // 夹着的裸文本节点也不在 children 里、删不掉；定位元素不占文档流。这三种都不剪上方。
  if (hasLooseText(src)) return
  if (kids.slice(0, first + 1).some((k) => isPositioned(k) || isInlineLevel(k))) return
  const gap = cs.display.includes('flex') ? parseFloat(cs.rowGap) || 0 : 0
  const mt0 = parseFloat(getComputedStyle(kids[0]).marginTop) || 0
  const mtFirst = parseFloat(getComputedStyle(kids[first]).marginTop) || 0
  const h = rects[first].top - rects[0].top - gap - mtFirst
  if (h < 0) return
  const spacer = document.createElement('div')
  Object.assign(spacer.style, { height: `${h}px`, marginTop: `${mt0}px`, flex: 'none' })
  clone.insertBefore(spacer, twins[0])
  for (let i = 0; i < first; i++) twins[i].remove()
}

/** 把一块原件按它此刻在视口里的位置克隆进 `into`（绝对定位，滚动不用另算） */
function cloneAt(src: HTMLElement, into: HTMLElement, prune = false): HTMLElement {
  const r = src.getBoundingClientRect()
  const clone = src.cloneNode(true) as HTMLElement
  // 先拷画布再剪枝：copyCanvases 按顺序一一对应，剪过之后序号就对不上了
  copyCanvases(src, clone)
  if (prune) pruneOffscreen(src, clone)
  copyInherited(src.parentElement, clone)
  Object.assign(clone.style, {
    position: 'absolute',
    left: `${r.left}px`,
    top: `${r.top}px`,
    width: `${r.width}px`,
    margin: '0',
    boxSizing: 'border-box'
  })
  into.appendChild(clone)
  return clone
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
 * **层不动、只滑里面的内容**（2026-10-24）：层的底色是和页面同一层钉在视口的渐变，
 * 以前整层一起滑，光晕就跟着横移，看得出「背景也在走」。现在放行那一帧新页正好在屏幕外，
 * 层把底色撤掉、只滑里面的克隆内容，背景纹丝不动。兜底放行（新页没来）时新页还停在原位，
 * 那时仍得整层带底色滑走，否则两页叠影。
 *
 * **底栏副本**（2026-10-24）：底栏是每页各一份，旧页一切走它就跟着藏了，新页的底栏要等新页
 * 挂上才出现——中间那几帧屏幕上没有底栏（真机连拍两帧实锤）。所以把当前底栏（`pinned`）
 * 也克隆一份钉在「克隆层之上、真底栏之下」（z-index 15，介于 10 与 20 之间）：新底栏一画出来
 * 就把副本原样盖住，指示条从那一刻起照常滑；副本随克隆层一起摘掉。
 *
 * `dir` 是旧页滑出的方向；`fromSelectors` 是当前页要跟着滑的块（正文 `.log__body` 等，
 * 每页一块）；`pinned` 是钉住不动、只需要补位的块（底栏）。
 */
export function startSlide(
  dir: 'left' | 'right',
  navigate: () => void,
  fromSelectors: readonly string[],
  pinned: readonly HTMLElement[] = []
) {
  const srcs = fromSelectors
    .map((s) => document.querySelector(s) as HTMLElement | null)
    .filter((el): el is HTMLElement => !!el && typeof el.cloneNode === 'function')
  if (!srcs.length || prefersReducedMotion()) {
    navigate()
    return
  }

  // 先挂类再克隆：克隆体一进文档就是「不模糊」的样子，不会先模糊一帧再撤
  const html = document.documentElement
  slidingCount++
  html.classList.add(SLIDING_CLASS)

  const layer = document.createElement('div')
  layer.className = 'tab-slide-layer'
  const content = document.createElement('div')
  content.className = 'tab-slide-layer__content'
  layer.appendChild(content)
  for (const src of srcs) cloneAt(src, content, true)
  document.body.appendChild(layer)

  let pin: HTMLElement | null = null
  const bars = pinned.filter((el) => el.isConnected && el.getBoundingClientRect().width > 0)
  if (bars.length) {
    pin = document.createElement('div')
    pin.className = 'tab-slide-pin'
    for (const bar of bars) renameSvgIds(cloneAt(bar, pin))
    document.body.appendChild(pin)
  }

  let removed = false
  const drop = () => {
    if (removed) return
    removed = true
    layer.remove()
    pin?.remove()
    slidingCount = Math.max(0, slidingCount - 1)
    if (slidingCount === 0) html.classList.remove(SLIDING_CLASS)
  }

  // 上一次还没放行的层（连点）立刻放它滑走，不排队
  releaseOut()
  const dx = dir === 'left' ? -100 : 100
  heldOut = (withEnter) => {
    // 新页同帧滑进来：层撤底色、只滑内容；兜底：整层带底色滑走（新页还停在原位，要挡住它）
    let target: HTMLElement = layer
    if (withEnter) {
      layer.classList.add('is-released')
      target = content
    }
    const anim = target.animate(
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
 * 两边在同一帧起跑。`selectors` 里其余的块（如果有）跟着一起滑。
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
      // 新页这一帧开始从屏幕外滑进来：旧页克隆层可以撤底色、只滑内容
      releaseOut(true)
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
