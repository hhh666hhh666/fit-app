/**
 * 二级页推入：`navigateTo` / `redirectTo` 打开的页面，正文从右侧 24px 带淡入滑到位（220ms）。
 *
 * - **只播入场**：`navigateBack` 回到的上一页一直保活在原地，什么都不播；主 tab 之间走 `switchTab`，
 *   有自己的整屏横移（ADR 0040），这里不拦它。
 * - **动 `left` 不动 `transform`**：二级页多半有 `position: fixed` 的底栏（会话页、编辑页、选择条…），
 *   祖先一带 transform，fixed 就改以它为定位基准、跟着一起滑，动画结束又跳回视口底部（ADR 0037 踩过）。
 *   相对定位的 `left` 不改定位基准：正文滑、钉住的底栏原地不动，只跟着整页一起淡入。
 * - **用 MutationObserver 接新页**：它在节点插进文档后的微任务里回调、早于这一帧绘制，
 *   新页的第一帧就已经在起点上，不会先在原位闪一下再跳出去。只在跳转后的 1.5 秒内观察，平时不挂。
 * - 减少动态效果：不位移，只淡入 150ms。
 */
import { prefersReducedMotion, SLIDE_EASE } from './nav'

const PUSH_MS = 220
const PUSH_PX = 24
/** 跳转后最多等新页多久；过了就不播，页面根本没挂上时动画也没意义 */
const WAIT_MS = 1500

let observer: MutationObserver | null = null
let giveUp: ReturnType<typeof setTimeout> | undefined

function stop() {
  observer?.disconnect()
  observer = null
  if (giveUp) clearTimeout(giveUp)
}

function play(page: Element) {
  const body = page.querySelector('uni-page-body') as HTMLElement | null
  if (!body || typeof body.animate !== 'function') return
  if (prefersReducedMotion()) {
    body.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 150, easing: 'linear' })
    return
  }
  // left 只对定位元素生效；uni-page-body 默认是 static，播完还原
  const wasStatic = getComputedStyle(body).position === 'static'
  if (wasStatic) body.style.position = 'relative'
  const anim = body.animate(
    [
      { left: `${PUSH_PX}px`, opacity: 0 },
      { left: '0px', opacity: 1 }
    ],
    { duration: PUSH_MS, easing: SLIDE_EASE }
  )
  let restored = false
  const done = () => {
    if (restored) return
    restored = true
    if (wasStatic) body.style.position = ''
  }
  void Promise.resolve(anim.finished).catch(() => {}).then(done)
  // 兜底：动画被跳过（页面在后台、省电模式）finished 可能迟迟不来，定位也得还原
  setTimeout(done, PUSH_MS + 250)
}

/** 跳转发起时调用：盯住接下来插进文档的那个页面 */
function expect(url: string) {
  const route = url.replace(/^\//, '').split('?')[0]
  stop()
  if (typeof MutationObserver === 'undefined') return
  observer = new MutationObserver((records) => {
    for (const r of records) {
      for (const n of Array.from(r.addedNodes)) {
        if (!(n instanceof Element)) continue
        const page = n.matches('uni-page') ? n : n.querySelector('uni-page')
        if (page && page.getAttribute('data-page') === route) {
          stop()
          play(page)
          return
        }
      }
    }
  })
  observer.observe(document.body, { childList: true, subtree: true })
  giveUp = setTimeout(stop, WAIT_MS)
}

export function installPagePush() {
  if (typeof document === 'undefined') return
  for (const api of ['navigateTo', 'redirectTo'] as const) {
    uni.addInterceptor(api, {
      invoke(args: { url?: string }) {
        if (args?.url) expect(args.url)
      }
    })
  }
}
