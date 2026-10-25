<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { hydrateCache } from '@/api/cache'
import { installBackHandler } from '@/utils/backStack'
import { installPagePush } from '@/utils/pagePush'

// 启动时不做跳转：登录态由各页 onShow（或 onLoad）首行的 ensureLogin() 兜底，
// 见 utils/guard.ts；因为 onLaunch 里做 reLaunch 有时序问题。
// ⚠️ 守卫是**逐页**的：新建页面记得加 `if (!ensureLogin()) return`，uni-app 没有全局页面钩子。
onLaunch(() => {
  // 冷启动同步灌回上个进程的数据快照（ADR 0036）：页面 onShow 里的取数直接命中，零请求。
  // 同步是对的——异步就要在 read() 里加「等就绪」的闸门，忘了 await 就是「偶尔多拉一轮」。
  hydrateCache()

  // 返回键 / Esc 先关最上面的弹层（安卓壳的 onBackPressed 会先问 window.__ftBack）
  installBackHandler()

  // 二级页（navigateTo / redirectTo）从右侧推入；返回与主 tab 切换不经过这里
  installPagePush()

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

/* ── 全局只留两个类：数字字体与加载中占位。其余通用件都在 components/Ft*.vue（按钮、卡片、空态…），别再往这里加 ── */

/* 数字：重量 / 次数 / 日期 / 容量一律走 Nunito（0–9 等宽，竖排也对得齐） */
.ft-num {
  font-family: $font-num;
  font-weight: $weight-bold;
  font-feature-settings: 'tnum';
  font-variant-numeric: tabular-nums;
}

.ft-loading {
  padding: 120rpx 0;
  text-align: center;
  color: $app-ink-muted;
}

/* 四个主 tab 的页面根（横向裁切、底部留白、钉住的底栏）收在 components/FtTabPage.vue。 */

/* scroll-view 不画滚动条：手机 WebView 本来就是悬浮滚动条，电脑浏览器里那条灰槽会压住卡片右边
   （会话页的动作横滑条、记录弹层的列表都踩过） */
.uni-scroll-view {
  scrollbar-width: none;
}

.uni-scroll-view::-webkit-scrollbar {
  display: none;
}

/* ── uni 自带的确认框 / 操作菜单 / 轻提示：换成晴窗的皮 ──
   uni.showModal / showActionSheet / showToast 在 H5 是运行时自己渲染的 DOM（`uni-modal`、
   `uni-actionsheet`、`uni-toast`），默认是 3px 圆角、纯黑字、iOS 蓝确认、50% 纯黑遮罩。这里按类名覆盖：
   确认框＝实底圆角卡 ＋ 两枚胶囊钮（左取消灰底、右确认白底），按钮字色由 utils/confirm.ts 的
   confirmColor / cancelColor 内联给（深字或危险红）；操作菜单＝贴底实底面板；轻提示＝深色胶囊。
   ⚠️ 类名是 uni 运行时的内部结构，升级 uni-app 后打开一个确认框看一眼。 */
uni-modal .uni-mask,
uni-actionsheet .uni-mask {
  background: $app-mask;
}

uni-modal .uni-modal {
  width: calc(100% - 112rpx);
  max-width: 640rpx;
  border-radius: $radius-lg;
  background: $app-canvas-bottom;
  box-shadow: $shadow-md;
  font-family: $font-text;
}

uni-modal .uni-modal__hd {
  padding: 48rpx 40rpx 0;
}

uni-modal .uni-modal__title {
  font-size: $text-lg;
  font-weight: $weight-bold;
  line-height: 1.4;
  color: $app-ink;
}

uni-modal .uni-modal__bd {
  min-height: 0;
  padding: 16rpx 40rpx 40rpx;
  font-size: $text-md;
  line-height: 1.6;
  color: $app-ink-muted;
  word-break: normal;
  overflow-wrap: anywhere;
}

uni-modal .uni-modal__ft {
  gap: 16rpx;
  padding: 0 32rpx 32rpx;
  line-height: 88rpx;
  font-size: $text-md;
}

uni-modal .uni-modal__ft::after,
uni-modal .uni-modal__btn::after {
  display: none;
}

uni-modal .uni-modal__btn {
  border-radius: $radius-pill;
  font-weight: $weight-bold;
  transition: opacity $dur-fast $ease-out;
}

uni-modal .uni-modal__btn_default {
  background: $app-surface-2;
}

uni-modal .uni-modal__btn_primary {
  background: $app-surface;
  box-shadow: inset 0 0 0 1rpx $app-line;
}

uni-modal .uni-modal__btn:active {
  background-color: inherit;
  opacity: 0.7;
}

uni-actionsheet .uni-actionsheet {
  left: $tabbar-side;
  right: $tabbar-side;
  bottom: calc(#{$tabbar-gap} + env(safe-area-inset-bottom));
  font-family: $font-text;
}

uni-actionsheet .uni-actionsheet__menu,
uni-actionsheet .uni-actionsheet__action {
  border-radius: $radius-lg;
  background-color: $app-canvas-bottom;
  box-shadow: $shadow-md;
}

uni-actionsheet .uni-actionsheet__action {
  margin-top: 16rpx;
}

/* 菜单项的字色是运行时按 itemColor 内联的（默认纯黑），只能用 !important 压 */
uni-actionsheet .uni-actionsheet__cell {
  padding: 28rpx 24rpx;
  font-size: $text-lg;
  color: $app-ink !important;
}

uni-actionsheet .uni-actionsheet__action .uni-actionsheet__cell {
  font-weight: $weight-bold;
  color: $app-ink-muted !important;
}

uni-actionsheet .uni-actionsheet__cell::before {
  left: 32rpx;
  right: 32rpx;
  border-top-color: $app-line-soft;
}

uni-actionsheet .uni-actionsheet__cell:active {
  background-color: $app-surface-2;
}

uni-toast .uni-simple-toast__text,
uni-toast .uni-toast {
  background: $app-ink;
  color: $app-surface;
  border-radius: $radius-pill;
  font-family: $font-text;
  box-shadow: $shadow-md;
}

uni-toast .uni-simple-toast__text {
  /* 长提示会折成两行，全圆角两头太鼓，单独收一档 */
  border-radius: $radius-md;
  padding: 20rpx 36rpx;
  font-size: $text-md;
  line-height: 1.5;
}

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

/* ── 弹层进出：FtSheet、MealSheet、ComboEditSheet、PhotoViewer 共用 ──
   结构约定：组件的**根**是一个透明的全屏容器（类 `ft-overlay`），里面两层：
   `ft-overlay__hit`（遮罩的颜色 ＋ 点它关闭）和 `ft-overlay__panel`（面板，fixed 盖在它上面）。
   - 进场：挂载即播（keyframes）——遮罩淡进、面板从底部升起，父组件包不包 Transition 都有；
   - 离场：父组件用 `<Transition name="ft-sheet">` 包住 `v-if`。点遮罩、返回键、保存成功……
     不管从哪关，都是父组件把 v-if 置 false，Transition 先播完再卸载；
   - ⚠️ **只动 opacity 与 transform**（合成层，不重绘）。0.28.0 曾让根元素动 `background-color`，
     整屏遮罩每帧都在主线程重绘，安卓 WebView 上进出场明显掉帧（主人真机反馈「生硬、帧率很低」）；
   - Vue 只量**根元素**的时长：根上那条 opacity 过渡不改值，只是给它计时（量到时长、到点收尾），
     真正在动的是遮罩层与面板，三者同长；
   - 面板的 `will-change: transform` **只在离场时挂**（leave-active）：Vue 先挂 leave-active、隔一帧才换
     leave-to 起跑，图层就在面板还没动的那一帧先备好，离场第一帧不用临时把整块面板（记一餐里是长列表 ＋ 图片）
     重新栅格化。⚠️ 别改成常驻：will-change: transform 和 transform 一样会让面板成为 fixed 后代的定位基准，
     面板里套的全屏看图、组合编辑、uni 的 picker 弹窗就只铺满面板、不铺满屏幕（0.28.1 / 0.28.2 踩过）；
   - 内容重的弹层（记一餐）进场那 220ms 里不换内容，见 MealSheet「进场期间不改内容」。
   全屏看图没有面板，用 `ft-overlay--fade` 整层淡进淡出（底色在根上，动的是根的 opacity）。
   减少动态效果时不位移、只淡（确认「关上了」的那一下还在）。 */
.ft-overlay__hit {
  position: absolute;
  inset: 0;
  background: $app-mask;
  animation: ft-fade-in $dur-base $ease-out;
}

.ft-overlay__panel {
  animation: ft-panel-up $dur-base $ease-out;
}

/* 退场中不再接点击：正在离开的弹层上连点两下，不能把同一个动作再触发一次 */
.ft-overlay.ft-sheet-leave-active {
  pointer-events: none;
  transition: opacity $dur-leave linear;
}

.ft-overlay.ft-sheet-leave-active .ft-overlay__hit {
  animation: none;
  transition: opacity $dur-leave $ease-in;
}

.ft-overlay.ft-sheet-leave-active .ft-overlay__panel {
  animation: none;
  transition: transform $dur-leave $ease-in;
  will-change: transform;
}

.ft-overlay.ft-sheet-leave-to .ft-overlay__hit {
  opacity: 0;
}

/* 盖过下拉时的行内 translateY：从手指松开的位置接着往下走 */
.ft-overlay.ft-sheet-leave-to .ft-overlay__panel {
  transform: translateY(100%) !important;
}

.ft-overlay--fade {
  animation: ft-fade-in $dur-base $ease-out;
}

.ft-overlay--fade.ft-sheet-leave-active {
  transition: opacity $dur-leave $ease-in;
}

.ft-overlay--fade.ft-sheet-leave-to {
  opacity: 0;
}

@keyframes ft-panel-up {
  from {
    transform: translateY(100%);
  }
}

@keyframes ft-fade-in {
  from {
    opacity: 0;
  }
}

/* 减少动态效果：整层（遮罩连面板）只淡 opacity，不位移 */
@media (prefers-reduced-motion: reduce) {
  .ft-overlay,
  .ft-overlay--fade {
    animation: ft-fade-in $dur-fast linear;
  }

  .ft-overlay__hit,
  .ft-overlay__panel,
  .ft-overlay.ft-sheet-leave-active .ft-overlay__hit,
  .ft-overlay.ft-sheet-leave-active .ft-overlay__panel {
    animation: none;
    transition: none;
  }

  .ft-overlay.ft-sheet-leave-active,
  .ft-overlay--fade.ft-sheet-leave-active {
    transition: opacity $dur-fast linear;
  }

  .ft-overlay.ft-sheet-leave-to {
    opacity: 0;
  }

  .ft-overlay.ft-sheet-leave-to .ft-overlay__hit {
    opacity: 1;
  }

  .ft-overlay.ft-sheet-leave-to .ft-overlay__panel {
    transform: none !important;
  }
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
