/**
 * 弹层进场只播一次（2026-11-23）。
 *
 * 弹层的进场是挂载即播的 keyframes（App.vue「弹层进出」）。H5 上 `navigateTo` 到下一页时，
 * 上一页整页被 `display: none` 藏起来；`navigateBack` 回来再显示，浏览器会把 keyframes 当成
 * 新动画**从头再播一遍**——表现是「从记一餐点进食物详情、返回时弹层又从底下升上来一次」。
 *
 * 这里在整个文档上听 `animationend`：遮罩、面板（以及减少动态效果时的整层淡入）播完进场，
 * 就给它挂 `data-entered`，CSS 里据此把 animation 关掉，之后再怎么藏、怎么显示都不会重播。
 * 用属性不用 class：面板有 `:class` 绑定（如拖动中的 `is-dragging`），Vue 重新渲染会整串
 * 改写 className，把手加的 class 冲掉；它不碰自己不认识的属性。
 */
const ENTER_ANIMATIONS = new Set(['ft-panel-up', 'ft-fade-in'])

function onAnimationEnd(e: AnimationEvent) {
  if (!ENTER_ANIMATIONS.has(e.animationName)) return
  const el = e.target as HTMLElement | null
  if (!el || typeof el.setAttribute !== 'function') return
  if (!el.classList.contains('ft-overlay__panel') && !el.classList.contains('ft-overlay__hit') && !el.classList.contains('ft-overlay')) return
  el.setAttribute('data-entered', '')
}

let installed = false

export function installOverlayEnterOnce() {
  if (installed || typeof document === 'undefined') return
  installed = true
  document.addEventListener('animationend', onAnimationEnd, true)
}
