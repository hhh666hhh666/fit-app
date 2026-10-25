/**
 * 二级页推入：`navigateTo` / `redirectTo` 打开的页面，整页从右侧 32px 带淡入滑到位（260ms，与切 tab 同长同曲线）。
 *
 * - **只播入场**：`navigateBack` 回到的上一页一直保活在原地，什么都不播；主 tab 之间走 `switchTab`，
 *   有自己的整屏横移（ADR 0040），这里不拦它。
 * - **只动 transform 与 opacity**：这两样由合成线程播，主线程忙着挂新页、拉数据也不掉帧，120Hz 屏上就是 120 帧。
 *   0.28.x 动的是 `left`，每帧都要重排 ＋ 重绘整页，帧率上不去。
 * - **播的时候把 uni-page-body 临时撑成视口大小的 fixed 框**：二级页多半有 `position: fixed` 的底栏
 *   （会话页、编辑页、选择条…），祖先一带 transform，fixed 就改以它为定位基准（ADR 0037 踩过）。
 *   基准框本身正好等于视口，底栏就仍落在视口里原来的位置，只是跟着整页一起滑——和原生页面推入一样。
 *   新页本来就从顶部开始，撑成 fixed 框时内容不跳；文档高度跟着塌成一屏，上一页留下的窗口滚动也顺手归零。
 * - **用 MutationObserver 接新页**：它在节点插进文档后的微任务里回调、早于这一帧绘制，
 *   新页的第一帧就已经在起点上，不会先在原位闪一下再跳出去。只在跳转后的 1.5 秒内观察，平时不挂。
 * - 减少动态效果：不位移，只淡入 150ms。
 */
import { prefersReducedMotion, SLIDE_EASE, SLIDE_MS } from './nav'

const PUSH_PX = 32
/** 跳转后最多等新页多久；过了就不播，页面根本没挂上时动画也没意义 */
const WAIT_MS = 1500

let observer: MutationObserver | null = null
let giveUp: ReturnType<typeof setTimeout> | undefined

function stop() {
  observer?.disconnect()
  observer = null
  if (giveUp) clearTimeout(giveUp)
}

/** 播的时候临时加在 uni-page-body 上的行内样式，播完逐条清掉 */
const FRAME: Record<string, string> = {
  position: 'fixed',
  top: '0',
  right: '0',
  bottom: '0',
  left: '0',
  overflow: 'hidden'
}

function fade(body: HTMLElement) {
  body.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 150, easing: 'linear' })
}

function play(page: Element) {
  const body = page.querySelector('uni-page-body') as HTMLElement | null
  if (!body || typeof body.animate !== 'function') return
  if (prefersReducedMotion()) {
    fade(body)
    return
  }
  for (const [k, v] of Object.entries(FRAME)) body.style.setProperty(k, v)
  const anim = body.animate(
    [
      { transform: `translateX(${PUSH_PX}px)`, opacity: 0 },
      { transform: 'none', opacity: 1 }
    ],
    { duration: SLIDE_MS, easing: SLIDE_EASE }
  )
  let restored = false
  const done = () => {
    if (restored) return
    restored = true
    for (const k of Object.keys(FRAME)) body.style.removeProperty(k)
  }
  void Promise.resolve(anim.finished).catch(() => {}).then(done)
  // 兜底：动画被跳过（页面在后台、省电模式）finished 可能迟迟不来，定位也得还原
  setTimeout(done, SLIDE_MS + 250)
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
