<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'

// 启动时不做跳转：登录态由各页 onShow 的 ensureLogin() 兜底，
// 因为 onLaunch 里做 reLaunch 有时序问题。
onLaunch(() => {})
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
</style>
