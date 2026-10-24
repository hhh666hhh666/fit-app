// ── 返回键 / Esc 先关最上面的弹层 ──
// 安卓壳的 onBackPressed 先调 `window.__ftBack()`：返回 true 表示 H5 自己关掉了一层弹层，
// 壳就不再后退；返回 false（或旧壳根本不调）才走 WebView 的后退。电脑上按 Esc 走同一条路。
//
// 为什么不用 history.pushState 给弹层占一条历史：uni-app 的路由（vue-router 的 hash 模式）
// 也在听 popstate，弹层关掉后紧接着跳页（比如选计划 → 进会话页）时，回收那条历史的
// history.back() 会和跳页抢时序，能把刚进的页面又退回去。让壳先问一句最省事。
//
// 弹层自己在挂载时 push 一个「关我」的回调、卸载时撤掉（FtSheet、MealSheet 等都是 v-if 挂载），
// 组件里写一行 useBackClose(关闭函数) 即可。
import { onBeforeUnmount, onMounted } from 'vue'

type Closer = () => void

const stack: Closer[] = []

/** 登记一层弹层，返回撤销函数（在 onBeforeUnmount 里调） */
export function pushBack(close: Closer): () => void {
  stack.push(close)
  return () => {
    const i = stack.lastIndexOf(close)
    if (i >= 0) stack.splice(i, 1)
  }
}

/** 组件里一行接上：挂载时登记、卸载时撤掉 */
export function useBackClose(close: Closer): void {
  let off: (() => void) | null = null
  onMounted(() => {
    off = pushBack(close)
  })
  onBeforeUnmount(() => off?.())
}

function visible(el: Element | null): el is HTMLElement {
  return !!el && getComputedStyle(el).display !== 'none'
}

function openUniModal(): HTMLElement | null {
  const modal = document.querySelector('uni-modal')
  return visible(modal) && !modal.classList.contains('uni-fade-leave-active') ? modal : null
}

function openUniActionSheet(): HTMLElement | null {
  return document.querySelector<HTMLElement>('.uni-actionsheet.uni-actionsheet_toggle')
}

/** uni 自带的确认框 / 操作菜单开着时，替用户点它的「取消」（取消位只放无害的选项，见 utils/confirm.ts） */
function cancelUniOverlay(): boolean {
  const modal = openUniModal()
  if (modal) {
    // 没有取消钮的纯提示框：也吞掉这次返回，别让页面在它底下退走
    modal.querySelector<HTMLElement>('.uni-modal__btn_default')?.click()
    return true
  }
  const sheet = openUniActionSheet()
  if (sheet) {
    sheet.querySelector<HTMLElement>('.uni-actionsheet__action .uni-actionsheet__cell')?.click()
    return true
  }
  return false
}

/** 关掉最上面的一层；没有可关的返回 false */
export function handleBack(): boolean {
  if (cancelUniOverlay()) return true
  const top = stack[stack.length - 1]
  if (!top) return false
  top()
  return true
}

declare global {
  interface Window {
    __ftBack?: () => boolean
  }
}

let installed = false

/** App.vue 的 onLaunch 里调一次 */
export function installBackHandler(): void {
  if (installed) return
  installed = true
  window.__ftBack = handleBack
  // Esc 在松开时处理：uni 的确认框自己也在 keyup 时把 Esc 当「取消」。按下那一刻确认框
  // 已经开着，就交给它自己关；否则在 keydown 就开出来的确认框会被同一次 keyup 当场取消掉
  let uniOpenAtKeydown = false
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') uniOpenAtKeydown = !!openUniModal()
  })
  window.addEventListener('keyup', (e) => {
    if (e.key !== 'Escape' || uniOpenAtKeydown) return
    if (handleBack()) e.preventDefault()
  })
}
