<!--
  玻璃卡片（晴窗）。⚠️ 目前没有页面在用：各页换皮时都写了局部 `.glass`，收拢与否见 docs/todo 的样式收拢待办。
  ⚠️ 卡片带 backdrop-filter，它会成为 fixed 子元素的包含块——卡片里别放 fixed 的弹层。
-->
<script setup lang="ts">
withDefaults(defineProps<{ pad?: 'none' | 'sm' | 'md' }>(), { pad: 'md' })
defineEmits<{ click: [] }>()
</script>

<template>
  <view class="ft-glass" :class="'ft-glass--' + pad" @click="$emit('click')">
    <slot />
  </view>
</template>

<style lang="scss" scoped>
.ft-glass {
  @include glass;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-lg;
  box-shadow: $glass-shadow;
}

.ft-glass--sm {
  padding: 24rpx 32rpx;
}

.ft-glass--md {
  padding: 32rpx 36rpx;
}
</style>
