/**
 * 透明状态栏（2026-11-23，安卓壳 0.1.9 起）。
 *
 * 壳把页面画到状态栏底下（状态栏透明，页面渐变一直铺到屏幕顶）。WebView 量不到状态栏多高
 * （`env(safe-area-inset-top)` 在安卓上通常是 0），所以由壳经 `window.FitShell.statusBarHeight()`
 * 告诉这里（CSS px），写进根上的 `--ft-top`；各页顶部留白一律用 uni.scss 的 `$safe-top`，它读这个变量。
 * 系统栏高度变了（横竖屏、分屏）壳会再调一次 `window.__ftSetTopInset(px)`。
 *
 * 拿得到高度时还会在 body 上垫一条**顶部渐隐带**：和页面底同一层渐变，盖住状态栏那一段并往下淡出，
 * 内容滚上去时在那里慢慢消失，不会和时钟、信号图标叠在一起。它和底栏下面那条渐隐带是一对。
 *
 * 旧壳 / 浏览器：没有这个方法，`--ft-top` 不写，`$safe-top` 回落到 `env(safe-area-inset-top)`，
 * 也不垫渐隐带——和改之前一模一样。
 *
 * 全屏看图这类黑底层开着时，状态栏的深色图标看不见，用 `holdLightStatusIcons()` 临时换白色。
 */
interface FitShellStatusBar {
  statusBarHeight?: () => number
  setStatusBarIconsLight?: (light: boolean) => void
}

function shell(): FitShellStatusBar | null {
  if (typeof window === 'undefined') return null
  return (window as unknown as { FitShell?: FitShellStatusBar }).FitShell ?? null
}

const SCRIM_CLASS = 'ft-statusbar-scrim'

function applyTop(px: number) {
  if (typeof document === 'undefined' || !(px > 0)) return
  document.documentElement.style.setProperty('--ft-top', `${Math.round(px * 10) / 10}px`)
  if (!document.querySelector(`.${SCRIM_CLASS}`)) {
    const scrim = document.createElement('div')
    scrim.className = SCRIM_CLASS
    scrim.setAttribute('aria-hidden', 'true')
    document.body.appendChild(scrim)
  }
}

export function installStatusBar() {
  const s = shell()
  ;(window as unknown as { __ftSetTopInset?: (px: number) => void }).__ftSetTopInset = applyTop
  if (!s || typeof s.statusBarHeight !== 'function') return
  try {
    applyTop(Number(s.statusBarHeight()))
  } catch {
    // 壳那边出错：当它是旧壳，什么都不改
  }
}

let lightHolds = 0

/** 黑底层打开时调用，返回「放手」函数；多层叠着开时，最后一层关掉才换回深色图标。 */
export function holdLightStatusIcons(): () => void {
  const s = shell()
  if (!s || typeof s.setStatusBarIconsLight !== 'function') return () => {}
  lightHolds += 1
  if (lightHolds === 1) {
    try {
      s.setStatusBarIconsLight(true)
    } catch {
      // 换不了就算了
    }
  }
  let released = false
  return () => {
    if (released) return
    released = true
    lightHolds = Math.max(0, lightHolds - 1)
    if (lightHolds === 0) {
      try {
        s.setStatusBarIconsLight?.(false)
      } catch {
        // 同上
      }
    }
  }
}
