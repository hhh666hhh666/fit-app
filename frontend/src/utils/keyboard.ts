/**
 * 键盘弹起时给 <html> 挂 `is-keyboard-open`（ADR 0066 同批修复）：安卓壳的软键盘是 `adjustResize`，
 * 键盘一弹网页就变矮，`position: fixed` 贴底的主按钮（保存、完成、底栏…）会被顶到表单中间、盖住输入框。
 * 贴底悬浮件 `@include hide-on-keyboard`（uni.scss），键盘弹起时藏起来，收起再出现。
 *
 * 判据：有输入框获得焦点，且可视高度比见过的最高值矮了 150px 以上（只有焦点、没变矮的是电脑浏览器，不藏）。
 */
const CLASS = 'is-keyboard-open'
const MIN_DROP = 150

let tallest = 0

function editing(): boolean {
  const el = document.activeElement as HTMLElement | null
  if (!el) return false
  const tag = el.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || el.isContentEditable
}

function viewportHeight(): number {
  return window.visualViewport?.height ?? window.innerHeight
}

function check() {
  const h = viewportHeight()
  if (h > tallest) tallest = h
  const open = editing() && tallest - h > MIN_DROP
  document.documentElement.classList.toggle(CLASS, open)
}

export function installKeyboardWatch() {
  if (typeof window === 'undefined' || typeof document === 'undefined') return
  tallest = viewportHeight()
  window.addEventListener('resize', check)
  window.visualViewport?.addEventListener('resize', check)
  // 焦点换来换去时高度可能不变：失焦后等键盘收完再看一次
  document.addEventListener('focusin', () => setTimeout(check, 120))
  document.addEventListener('focusout', () => setTimeout(check, 120))
  // 转屏：最高值要重新量
  window.addEventListener('orientationchange', () => {
    tallest = 0
    setTimeout(check, 300)
  })
}
