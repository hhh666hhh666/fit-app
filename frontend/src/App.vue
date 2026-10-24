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
/* ── 数字字体：Nunito 700 / 800，自托管、只含拉丁子集（ADR 0041）──
   0–9 在这套字里本来就等宽（同一个前进宽度），计时器每秒跳也不会左右抖，
   所以不依赖 `tnum` 特性（它不带）。swap：字体没到之前先用系统字，不白屏。 */
@font-face {
  font-family: 'Nunito';
  font-style: normal;
  font-weight: 700;
  font-display: swap;
  src: url('@/assets/fonts/nunito-latin-700-normal.woff2') format('woff2');
}

@font-face {
  font-family: 'Nunito';
  font-style: normal;
  font-weight: 800;
  font-display: swap;
  src: url('@/assets/fonts/nunito-latin-800-normal.woff2') format('woff2');
}

/* 纯色兜底放在 body 上（它的底色会铺满整个画布），页面根**必须透明**：
   下面那层渐变是 z-index:-1 的 fixed 伪元素，页面根一旦有底色就把它盖住了 */
body {
  background-color: $app-canvas;
}

page {
  background-color: transparent;
  color: $app-ink;
  font-family: $font-text;
  font-size: $text-md;
  line-height: 1.6;
}

/* 晴窗的底是一层渐变 ＋ 光晕，**钉在视口上**不随内容滚：长页滚到底光晕还在原位，
   玻璃面板底下始终有东西可透。不用 background-attachment: fixed——安卓 WebView
   上它要么不生效、要么滚动时整屏重绘（每块玻璃都在模糊，那就是掉帧）。 */
page::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background: $app-canvas-bg;
}

/* ── 通用件（按「晴窗」换皮；页面别再各写一份。组件化的版本见 components/Ft*.vue）── */

.ft-page {
  padding: 24rpx 24rpx 48rpx;
}

/* 玻璃卡片 */
.ft-card {
  @include glass;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-lg;
  box-shadow: $glass-shadow;
  padding: 32rpx;
  margin-bottom: 24rpx;
}

/* 数字：重量 / 次数 / 日期 / 容量一律走 Nunito（0–9 等宽，竖排也对得齐） */
.ft-num {
  font-family: $font-num;
  font-weight: $weight-bold;
  font-feature-settings: 'tnum';
  font-variant-numeric: tabular-nums;
}

.ft-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 92rpx;
  border-radius: $radius-pill;
  font-size: $text-md;
  font-weight: $weight-bold;
  background: $glass-bg-strong;
  color: $app-ink-2;
  border: 1rpx solid $glass-edge;
  box-shadow: $shadow-sm;
  box-sizing: border-box;
  transition: transform $dur-fast $ease-out, opacity $dur-fast $ease-out;
}

/* 按压反馈：轻微下沉。全站按钮一个手感 */
.ft-btn:active {
  transform: scale(0.97);
  opacity: 0.9;
}

/* 主按钮：橙色渐变（橙色只给动作——每页最多一个） */
.ft-btn--primary {
  background: $accent-bg;
  color: $app-surface;
  border-color: transparent;
  box-shadow: $accent-shadow;
}

.ft-btn--ghost {
  background: transparent;
  border-color: $app-line;
  box-shadow: none;
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

/* PR 高亮：浅橙底 ＋ 深橙字（晴窗里荧光笔退役；PR 属于「动作的结果」，归橙色） */
.ft-marker {
  background: $app-highlight;
  border-radius: 16rpx;
  padding: 4rpx 12rpx;
  color: $accent-strong;
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

/* 四个主 tab 的页面根（横向裁切、底部留白、钉住的底栏）收在 components/FtTabPage.vue。 */

/* ── 切页转场：旧页克隆层滑出（ADR 0040）──
   utils/nav.ts 在切换前把当前页正文 cloneNode 一份塞进这个层的 __content，
   新页就绪的那一帧放行：层撤掉底色、只滑 __content，新页同时从另一侧滑入。
   底色必须铺满：层铺上后要等新页就绪才起跑，这段时间新页已在底下原位，
   透明的话两页会叠成重影。层本身不动——底色是和 page::before 同一层钉在视口的渐变，
   一起滑的话光晕会跟着横移。
   层级：克隆层 10 ＜ 底栏副本 15 ＜ 真底栏（FtTabPage 的 __bottom）20。底栏每页一份，旧页一藏
   它就没了，新页的要等挂上才出现；副本就补这几帧，新底栏一画出来就把它盖住。
   ⚠️ 别把 View Transitions 那套（::view-transition-*）加回来：主人设备的 WebView
   不支持（要 Chrome 111+），当时一直走兜底硬切，表现为「中间一帧整页空白」。 */
.tab-slide-layer {
  position: fixed;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 10;
  /* 和 page::before 同一层渐变、同样钉在视口：撤掉的那一刻底下看不出接缝 */
  background: $app-canvas-bg;
  /* 克隆体可能比视口高，滑出时不能让它撑出滚动条 */
  contain: paint;
}

.tab-slide-layer.is-released {
  background: transparent;
}

.tab-slide-layer__content {
  position: absolute;
  inset: 0;
  will-change: transform;
}

.tab-slide-pin {
  position: fixed;
  inset: 0;
  z-index: 15;
  pointer-events: none;
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
