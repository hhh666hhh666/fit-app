<!-- 空状态（晴窗）：一句标题 ＋ 一句怎么办；按钮放默认插槽。 -->
<script setup lang="ts">
defineProps<{ title: string; sub?: string }>()
</script>

<template>
  <view class="ft-empty-state">
    <text class="ft-empty-state__title">{{ title }}</text>
    <text v-if="sub" class="ft-empty-state__sub">{{ sub }}</text>
    <slot />
  </view>
</template>

<style lang="scss" scoped>
.ft-empty-state {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 24rpx 4rpx 40rpx;
}

.ft-empty-state__title {
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.ft-empty-state__sub {
  font-size: $text-sm;
  color: $app-ink-muted;
  line-height: 1.6;
}
</style>
