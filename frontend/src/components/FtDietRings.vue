<script setup lang="ts">
// ── 四环展示（蛋白/碳水/脂肪/饮水）：首页饮食卡与「饮食」页共用 ──

interface DietRing {
  key: 'protein' | 'carb' | 'fat' | 'water'
  label: string
  /** 环内大数字（取整后的现值）。 */
  value: string
  /** 环内小字（现值 / 目标）。 */
  sub: string
  /** 完成度（如「62%」；无目标时给「--」）。 */
  percent: string
}

defineProps<{ rings: DietRing[] }>()
</script>

<template>
  <view class="rings">
    <view v-for="r in rings" :key="r.key" class="rings__col">
      <view class="rings__ring" :class="'rings__ring--' + r.key">
        <text class="rings__num ft-num">{{ r.value }}</text>
        <text class="rings__sub ft-num">{{ r.sub }}</text>
      </view>
      <text class="rings__label">{{ r.label }}</text>
      <text class="rings__pct ft-num">{{ r.percent }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.rings {
  display: flex;
  justify-content: space-between;
}

.rings__col {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 25%;
}

.rings__ring {
  width: 116rpx;
  height: 116rpx;
  border-radius: 50%;
  border: 6rpx solid $app-line;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.rings__ring--protein {
  border-color: $ring-protein;
}
.rings__ring--carb {
  border-color: $ring-carb;
}
.rings__ring--fat {
  border-color: $ring-fat;
}
.rings__ring--water {
  border-color: $ring-water;
}

.rings__num {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
  line-height: 1.1;
}

.rings__sub {
  font-size: 18rpx;
  color: $app-ink-subtle;
  line-height: 1.2;
}

.rings__label {
  margin-top: 10rpx;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.rings__pct {
  font-size: 18rpx;
  color: $app-ink-subtle;
}
</style>
