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

/* ── 主 tab 切换：内容层按方向入场（ADR 0037）──
   只动各页的 __body 一层，绝不动页面根元素：四个页面的底部栏是根元素的 fixed
   子元素，根元素一旦有 transform 就会成为它的包含块，底栏会跟着位移、并在位移
   里露出画布底色。底栏钉住不动，正是「整屏在横向翻页」该有的样子。
   方向由 utils/nav.ts 在跳转前记下；无方向（刷新、从下级页回来）走淡入。 */

@keyframes tab-in-right {
  from {
    opacity: 0.6;
    transform: translateX(18vw);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes tab-in-left {
  from {
    opacity: 0.6;
    transform: translateX(-18vw);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes tab-in-fade {
  from {
    opacity: 0;
    transform: translateY(8rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.tab-in-right,
.tab-in-left,
.tab-in-fade {
  animation-duration: $dur-base;
  animation-timing-function: $ease-out;
  animation-fill-mode: both;
}

.tab-in-right {
  animation-name: tab-in-right;
}

.tab-in-left {
  animation-name: tab-in-left;
}

.tab-in-fade {
  animation-name: tab-in-fade;
}

/* 系统开了「减少动态效果」：一律直接到位（首页流光卡另有单独降级） */
@media (prefers-reduced-motion: reduce) {
  .tab-in-right,
  .tab-in-left,
  .tab-in-fade {
    animation: none;
  }
}
</style>
