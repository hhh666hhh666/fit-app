<!--
  多选态的「取消」：悬浮在右上角（position: fixed），页面往下滑也一直看得见。
  页头右侧插槽在多选态要空出来，位置和它对齐（页面左右留白 40rpx ＋ 页头内边距 8rpx）。
  返回键退出多选不归它管，页面里用 utils/backStack 的 useBackWhile 接。
-->
<script setup lang="ts">
defineEmits<{ (e: 'click'): void }>()
</script>

<template>
  <view class="ft-sel-cancel" role="button" @click="$emit('click')">取消</view>
</template>

<style lang="scss" scoped>
.ft-sel-cancel {
  @include glass-pill;
  position: fixed;
  /* 和页头右侧胶囊同一格：页面顶部留白 28rpx ＋ 页头上内边距 12rpx */
  top: calc(40rpx + env(safe-area-inset-top));
  right: 48rpx;
  /* 压住正文和切页克隆层（10），低于底栏（20）和弹层 */
  z-index: 18;
  box-shadow: $glass-shadow-float;
  @include press(shrink);
}
</style>
