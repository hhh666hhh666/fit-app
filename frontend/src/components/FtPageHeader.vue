<!--
  页头（晴窗）：小号上标 ＋ 大标题，右侧插槽放胶囊按钮；二级页面带返回。
  页面都是 `navigationStyle: custom`，顶部安全区由这里让出。
-->
<script setup lang="ts">
import FtIcon from '@/components/FtIcon.vue'

withDefaults(defineProps<{ title: string; eyebrow?: string; back?: boolean }>(), { back: false })

function goBack() {
  uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/home/index' }) })
}
</script>

<template>
  <view class="ft-head">
    <view v-if="back" class="ft-head__back" role="button" aria-label="返回" @click="goBack">
      <FtIcon name="chevron-left" :size="22" :stroke="2" />
    </view>
    <view class="ft-head__main">
      <text v-if="eyebrow" class="ft-head__eyebrow">{{ eyebrow }}</text>
      <text class="ft-head__title">{{ title }}</text>
    </view>
    <slot name="right" />
  </view>
</template>

<style lang="scss" scoped>
.ft-head {
  display: flex;
  align-items: flex-end;
  gap: 16rpx;
  padding: calc(12rpx + env(safe-area-inset-top)) 8rpx 4rpx;
}

.ft-head__back {
  width: 72rpx;
  height: 72rpx;
  margin-left: -12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-pill;
  color: $app-ink;
}

.ft-head__back:active {
  background: $app-surface-2;
}

.ft-head__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.ft-head__eyebrow {
  font-size: $text-base;
  color: $app-ink-muted;
}

.ft-head__title {
  font-family: $font-num;
  font-size: $text-hero;
  font-weight: $weight-heavy;
  letter-spacing: -0.02em;
  line-height: 1.2;
  color: $app-ink;
}
</style>
