<!--
  列表行（晴窗）：左侧可选图标位、标题 ＋ 副标题、右侧值或箭头。行与行之间是细线，
  放在玻璃卡（页面的 `.glass`）或 FtSheet 里用。highlight＝浅底胶囊（如「今天的计划」）。
-->
<script setup lang="ts">
import FtIcon from '@/components/FtIcon.vue'

withDefaults(
  defineProps<{ title: string; sub?: string; value?: string; arrow?: boolean; highlight?: boolean }>(),
  { arrow: false, highlight: false }
)
defineEmits<{ click: [] }>()
</script>

<template>
  <view class="ft-row" :class="{ 'is-highlight': highlight }" @click="$emit('click')">
    <slot name="lead" />
    <view class="ft-row__main">
      <view class="ft-row__title-line">
        <text class="ft-row__title">{{ title }}</text>
        <slot name="badge" />
      </view>
      <text v-if="sub" class="ft-row__sub">{{ sub }}</text>
    </view>
    <slot name="trail">
      <text v-if="value" class="ft-row__value ft-num">{{ value }}</text>
    </slot>
    <FtIcon v-if="arrow" name="chevron-right" :size="18" tone="subtle" />
  </view>
</template>

<style lang="scss" scoped>
.ft-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 0;
  border-top: 1rpx solid $app-line-soft;
  transition: opacity $dur-fast $ease-out;
}

.ft-row:first-child {
  border-top: none;
}

.ft-row:active {
  opacity: 0.6;
}

.is-highlight {
  margin: 0 -20rpx;
  padding-left: 20rpx;
  padding-right: 20rpx;
  border-radius: $radius-md;
  border-top-color: transparent;
  background: $app-surface-2;
}

.is-highlight + .ft-row {
  border-top-color: transparent;
}

.ft-row__main {
  flex: 1;
  min-width: 0;
}

.ft-row__title-line {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.ft-row__title {
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.ft-row__sub {
  display: block;
  padding-top: 4rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.ft-row__value {
  font-size: $text-md;
  color: $app-ink;
}
</style>
