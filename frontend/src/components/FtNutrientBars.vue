<!--
  营养素条形（晴窗，ADR 0041）：四列「名称 ＋ 细进度条 ＋ 现值／目标」，取代同心环。
  轨道浅、填充同色系（`$ring-*` token）；没设目标只画轨道。数据由 utils/diet 的 buildNutrientBars 给。
-->
<script setup lang="ts">
import type { NutrientBar } from '@/utils/diet'

defineProps<{ bars: NutrientBar[] }>()
</script>

<template>
  <view class="bars">
    <view v-for="b in bars" :key="b.key" class="bars__col">
      <text class="bars__name">{{ b.name }}</text>
      <view class="bars__track" :class="'bars__track--' + b.key">
        <view
          v-if="b.ratio != null"
          class="bars__fill"
          :class="'bars__fill--' + b.key"
          :style="{ width: Math.round(b.ratio * 100) + '%' }"
        />
      </view>
      <text class="bars__text ft-num">{{ b.text }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.bars {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16rpx;
}

.bars__col {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}

.bars__name {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.bars__track {
  height: 8rpx;
  border-radius: 8rpx;
  overflow: hidden;
}

.bars__fill {
  height: 100%;
  border-radius: 8rpx;
}

.bars__track--protein {
  background: $ring-protein-track;
}
.bars__track--carb {
  background: $ring-carb-track;
}
.bars__track--fat {
  background: $ring-fat-track;
}
.bars__track--water {
  background: $ring-water-track;
}

.bars__fill--protein {
  background: $ring-protein;
}
.bars__fill--carb {
  background: $ring-carb;
}
.bars__fill--fat {
  background: $ring-fat;
}
.bars__fill--water {
  background: $ring-water;
}

.bars__text {
  font-size: $text-sm;
  color: $app-ink;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
