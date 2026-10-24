/**
 * 触感回执：完成一组时手机轻震一下，组间眼睛不在屏幕上也知道「记上了」。
 *
 * 两条路，按顺序试：
 * 1. 安卓壳的 `window.FitShell.vibrate`（0.1.6 起，`android-shell/.../ShellBridge.java`）——壳自己调马达，最可靠；
 * 2. H5 的 `navigator.vibrate`——旧壳、手机浏览器走这条；WebView 里能不能震看系统版本与厂商。
 * 电脑浏览器、iOS 两条都没有，静默跳过。**不能**因为震不了影响记录本身。
 */
interface FitShell {
  vibrate(pattern: string): void
}

function vibrate(pattern: number[]) {
  try {
    const shell = (window as unknown as { FitShell?: FitShell }).FitShell
    if (shell && typeof shell.vibrate === 'function') {
      shell.vibrate(pattern.join(','))
      return
    }
    if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') navigator.vibrate(pattern)
  } catch {
    // 没权限、被系统拦掉：不震就是了
  }
}

/** 完成一组：一下短震 */
export function tickSet() {
  vibrate([12])
}

/** 这一组破了个人纪录：两下，第二下更长，和平常的组分得出来 */
export function tickPr() {
  vibrate([12, 80, 28])
}
