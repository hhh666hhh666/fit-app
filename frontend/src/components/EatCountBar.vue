<!--
  边吃边数的常驻条（ADR 0067）：有进行中的计数时，首页与饮食页顶部挂一条
  「边吃边数 · 晚餐 · 饺子 12 个 · 猪肉丸 3 个」，点它回到计数页。一种都还没选时不显示。
-->
<script setup lang="ts">
import { computed } from 'vue'
import FtIcon from '@/components/FtIcon.vue'
import { countSummary, eatCount, openEatCount } from '@/utils/eatCount'

const MEAL_NAMES = { breakfast: '早餐', lunch: '午餐', dinner: '晚餐', snack: '加餐' } as const

const shown = computed(() => !!eatCount.value && eatCount.value.items.length > 0)
const summary = computed(() => (eatCount.value ? countSummary(eatCount.value) : ''))
</script>

<template>
  <view v-if="shown && eatCount" class="ecb" role="button" :aria-label="`回到边吃边数：${summary}`" @click="openEatCount(null)">
    <view class="ecb__ico">
      <FtIcon name="tally" :size="18" :stroke="2" />
    </view>
    <view class="ecb__mid">
      <text class="ecb__t">边吃边数 · {{ MEAL_NAMES[eatCount.meal] }}</text>
      <text class="ecb__s ft-num">{{ summary }}</text>
    </view>
    <text class="ecb__go">继续数</text>
  </view>
</template>

<style lang="scss" scoped>
.ecb {
  @include glass-card;
  @include press(dim);
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 28rpx 20rpx 20rpx;
}

.ecb__ico {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: $radius-md;
  background: $meal-count-bg;
  color: $meal-count-fg;
}

.ecb__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2rpx;
}

.ecb__t {
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.ecb__s {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.ecb__go {
  flex: none;
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $app-ink-2;
}
</style>
