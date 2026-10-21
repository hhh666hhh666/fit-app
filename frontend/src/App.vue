<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { hydrateCache } from '@/api/cache'

// 启动时不做跳转：登录态由各页 onShow（或 onLoad）首行的 ensureLogin() 兜底，
// 见 utils/guard.ts；因为 onLaunch 里做 reLaunch 有时序问题。
// ⚠️ 守卫是**逐页**的：新建页面记得加 `if (!ensureLogin()) return`，uni-app 没有全局页面钩子。
onLaunch(() => {
  // 冷启动同步灌回上个进程的数据快照（ADR 0036）：页面 onShow 里的取数直接命中，零请求。
  // 同步是对的——异步就要在 read() 里加「等就绪」的闸门，忘了 await 就是「偶尔多拉一轮」。
  hydrateCache()

  // 主 tab 的页面 chunk 按页懒加载（各页产物 `pages-*-index.js`）。页面虽然保活了
  // （`switchTab`，ADR 0038），但**第一次**切过去仍要现拉 chunk，那一拍里页面还挂不上，
  // 真机上就是「闪一下」（0.17.3 之前更糟：`reLaunch` 连旧页一起拆，整页空白）。
  // 这里只把模块取回来，不注册路由、不挂载组件。等首屏那口气松下来就预热：
  // 太晚会赶不上「冷启动后第一次切页」，那一次正是白屏最长的。
  setTimeout(() => {
    void import('@/pages/log/index.vue')
    void import('@/pages/stats/index.vue')
    void import('@/pages/profile/index.vue')
  }, 200)
})
</script>

<style lang="scss">
page {
  background-color: $app-canvas;
  color: $app-ink;
  font-size: $text-md;
  line-height: 1.6;
}

/* ── 通用件（2026-10-11 按「训练日志」设计语言抽出，页面别再各写一份）── */

.ft-page {
  padding: 24rpx 24rpx 48rpx;
}

.ft-card {
  background: $app-surface;
  border: 1rpx solid $app-line-soft;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  padding: 28rpx;
  margin-bottom: 24rpx;
}

/* 数字：所有重量/次数/日期/容量用表格数字（等宽对齐，像日志的栏目） */
.ft-num {
  font-feature-settings: 'tnum';
  font-variant-numeric: tabular-nums;
}

.ft-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: $radius-lg;
  font-size: $text-lg;
  background: $app-surface;
  color: $app-ink;
  border: 1rpx solid $app-line;
  box-sizing: border-box;
}

/* 主按钮：墨黑底白字（全站仅此一种主按钮样式） */
.ft-btn--primary {
  background: $app-ink;
  color: #ffffff;
  border-color: $app-ink;
  font-weight: 500;
}

.ft-btn--ghost {
  background: transparent;
  border-color: $app-line;
  color: $app-ink-muted;
}

.ft-btn--block {
  width: 100%;
}

.ft-btn--disabled {
  opacity: 0.4;
}

.ft-tag {
  display: inline-flex;
  align-items: center;
  height: 40rpx;
  padding: 0 16rpx;
  border-radius: $radius-xs;
  background: $app-surface-2;
  color: $app-ink-muted;
  font-size: $text-sm;
  line-height: 1;
}

.ft-muted {
  color: $app-ink-muted;
}

.ft-subtle {
  color: $app-ink-subtle;
}

/* 荧光笔高亮：只给「被划过的数字」用（如 PR）——整块圆角荧光底（对齐设计稿） */
.ft-marker {
  background: $app-highlight;
  border-radius: 16rpx;
  padding: 4rpx 12rpx;
}

.ft-empty {
  padding: 120rpx 0;
  text-align: center;
  color: $app-ink-muted;
  font-size: $text-md;
}

.ft-loading {
  padding: 120rpx 0;
  text-align: center;
  color: $app-ink-muted;
}

/* 四个主 tab 的页面根：横向裁掉切页位移期间的溢出。
   位移（translateX ±18vw）会让内容短暂超出页面宽度；不裁的话，移动端 WebView 会把
   它当成可横向滚动的容器，在视口底部画一条横向滚动条——全宽、约 3dp 的灰色长条，
   压在 tabbar 下缘（2026-10-21 真机反馈的「底部出现长条」就是它）。
   用 clip 而不是 hidden：hidden 会把页面根变成滚动容器，clip 只裁不滚。两者都不影响
   内部 fixed 底栏（fixed 的包含块是视口，不受祖先 overflow 裁剪）。
   ⚠️ 新增主 tab 页面时，类名要一起加进这里。 */
.home,
.log,
.stats,
.prof {
  overflow-x: hidden; /* 老 WebView 兜底 */
  overflow-x: clip;
}

/* ── 切页：整页快照的连续推拉（ADR 0039）──
   `switchTab` 之后框架只让一页在文档里，两页从不同时可见，所以「旧页往左走、新页从右来」
   只能靠 View Transitions：切换前拍旧页、切换后拍新页，两张整页快照并排滑动。
   旧/新快照的选择器依据根上的 `data-tab-slide`（= 旧页滑出的方向，由 utils/nav.ts 设）。
   ⚠️ 位移是 ±100% 屏宽、240ms——比「单页 8vw 微移」慢得多也连续得多；
   想调手感就改这里的 duration，`data-tab-slide` 的语义别改反。 */
@keyframes tab-slide-out-left {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-100%);
  }
}

@keyframes tab-slide-in-right {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}

@keyframes tab-slide-out-right {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(100%);
  }
}

@keyframes tab-slide-in-left {
  from {
    transform: translateX(-100%);
  }
  to {
    transform: translateX(0);
  }
}

:root[data-tab-slide='left']::view-transition-old(root) {
  animation: tab-slide-out-left 240ms $ease-out both;
}

:root[data-tab-slide='left']::view-transition-new(root) {
  animation: tab-slide-in-right 240ms $ease-out both;
}

:root[data-tab-slide='right']::view-transition-old(root) {
  animation: tab-slide-out-right 240ms $ease-out both;
}

:root[data-tab-slide='right']::view-transition-new(root) {
  animation: tab-slide-in-left 240ms $ease-out both;
}

/* 底栏自己是一组，钉住不动：前后两张快照都停在原位、不加任何动画。
   root 组滑动时它不会跟着走——这就是「只有内容翻页」。 */
::view-transition-group(app-tabbar) {
  animation: none;
}

::view-transition-old(app-tabbar),
::view-transition-new(app-tabbar) {
  animation: none;
}

/* 底栏自己是一组，钉住不动：前后两张快照都停在原位、不加任何动画。
   root 组滑动时它不会跟着走——这就是「只有内容翻页」。 */
::view-transition-group(app-tabbar) {
  animation: none;
}

::view-transition-old(app-tabbar),
::view-transition-new(app-tabbar) {
  animation: none;
}

/* ── 切页：整页快照的连续推拉（ADR 0039）──
   `switchTab` 之后框架只让一页在文档里，两页从不同时可见，所以「旧页往左走、新页从右来」
   只能靠 View Transitions：切换前拍旧页、切换后拍新页，两张整页快照并排滑动。
   旧/新快照的选择器依据根上的 `data-tab-slide`（= 旧页滑出的方向，由 utils/nav.ts 设）。
   ⚠️ 位移是 ±100% 屏宽、240ms——比「单页 8vw 微移」慢得多也连续得多；
   想调手感就改这里的 duration，`data-tab-slide` 的语义别改反。 */
@keyframes tab-slide-out-left {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-100%);
  }
}

@keyframes tab-slide-in-right {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}

@keyframes tab-slide-out-right {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(100%);
  }
}

@keyframes tab-slide-in-left {
  from {
    transform: translateX(-100%);
  }
  to {
    transform: translateX(0);
  }
}

:root[data-tab-slide='left']::view-transition-old(root) {
  animation: tab-slide-out-left 240ms $ease-out both;
}

:root[data-tab-slide='left']::view-transition-new(root) {
  animation: tab-slide-in-right 240ms $ease-out both;
}

:root[data-tab-slide='right']::view-transition-old(root) {
  animation: tab-slide-out-right 240ms $ease-out both;
}

:root[data-tab-slide='right']::view-transition-new(root) {
  animation: tab-slide-in-left 240ms $ease-out both;
}

/* 底栏自己是一组，钉住不动：前后两张快照都停在原位、不加任何动画。
   root 组滑动时它不会跟着走——这就是「只有内容翻页」。 */
::view-transition-group(app-tabbar) {
  animation: none;
}

::view-transition-old(app-tabbar),
::view-transition-new(app-tabbar) {
  animation: none;
}

/* 底栏自己是一组，钉住不动：前后两张快照都停在原位、不加任何动画。
   root 组滑动时它不会跟着走——这就是「只有内容翻页」。 */
::view-transition-group(app-tabbar) {
  animation: none;
}

::view-transition-old(app-tabbar),
::view-transition-new(app-tabbar) {
  animation: none;
}

/* ── 内置 tabBar 必须自己压掉（ADR 0038）──
   四个主页面已在 pages.json 注册成 tabBar 页（为的是 switchTab 的保活），但 H5 运行期
   **不认 `custom: true`**：只要 `route.meta.isTabBar` 为真且 `tabBar.shown` 为真，它就会
   自己渲染一条 `uni-tabbar`，并给 `uni-page-wrapper` 扣掉 --tab-bar-height 的高度。
   我们用的是自绘的 FtTabBar，所以这两件事都得压掉——用 CSS 而不是 `uni.hideTabBar()`，
   是因为要保证**首帧**就生效，异步 API 会晚一拍、先闪一条内置栏。 */
uni-tabbar {
  display: none !important;
}

uni-app.uni-app--showtabbar uni-page-wrapper {
  height: 100% !important;
}

uni-app.uni-app--showtabbar uni-page-wrapper::after,
uni-app.uni-app--showtabbar uni-page-body::after {
  display: none !important;
}

</style>
