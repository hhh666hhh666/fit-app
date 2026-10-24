<!--
  底部弹层（晴窗）：遮罩 ＋ 上圆角面板，从底部滑入。开关由父组件的 v-if 控制，
  点遮罩发 close。
  ⚠️ 别把它放进带 backdrop-filter / transform / filter 的祖先里：那样 fixed 会
  相对那个祖先定位，弹层就不贴屏幕底了。
-->
<script setup lang="ts">
defineProps<{ title?: string; sub?: string }>()
defineEmits<{ close: [] }>()
</script>

<template>
  <view class="ft-sheet-mask" @click="$emit('close')" />
  <view class="ft-sheet" role="dialog" :aria-label="title">
    <view class="ft-sheet__handle" />
    <text v-if="title" class="ft-sheet__title">{{ title }}</text>
    <text v-if="sub" class="ft-sheet__sub">{{ sub }}</text>
    <slot />
  </view>
</template>

<style lang="scss" scoped>
.ft-sheet-mask {
  position: fixed;
  inset: 0;
  background: $app-mask;
  z-index: 90;
  animation: ft-sheet-fade $dur-base $ease-out;
}

.ft-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 91;
  box-sizing: border-box;
  max-height: 86vh;
  display: flex;
  flex-direction: column;
  background: $app-canvas-bottom;
  border-radius: $radius-xl $radius-xl 0 0;
  box-shadow: $shadow-sheet;
  padding: 20rpx 40rpx calc(40rpx + env(safe-area-inset-bottom));
  animation: ft-sheet-up $dur-base $ease-out;
}

.ft-sheet__handle {
  flex-shrink: 0;
  width: 72rpx;
  height: 8rpx;
  border-radius: $radius-pill;
  background: $app-line;
  margin: 0 auto 28rpx;
}

.ft-sheet__title {
  display: block;
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $app-ink;
}

.ft-sheet__sub {
  display: block;
  font-size: $text-base;
  color: $app-ink-muted;
  padding: 8rpx 0 28rpx;
}

@keyframes ft-sheet-up {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

@keyframes ft-sheet-fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ft-sheet,
  .ft-sheet-mask {
    animation: none;
  }
}
</style>
