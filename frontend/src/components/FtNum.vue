<!--
  数字展示（晴窗）：Nunito 数字 ＋ 可选单位。单位也走数字字体（kg、× 和数字同一套字形），
  颜色降一档。size：md 跟正文，lg＝卡片里的统计数，display＝首页大卡这种主数字。
-->
<script setup lang="ts">
withDefaults(defineProps<{ value: string | number; unit?: string; size?: 'md' | 'lg' | 'display' }>(), {
  size: 'md'
})
</script>

<template>
  <text class="ft-n" :class="'ft-n--' + size">
    <text>{{ value }}</text>
    <text v-if="unit" class="ft-n__u">{{ unit }}</text>
  </text>
</template>

<style lang="scss" scoped>
.ft-n {
  font-family: $font-num;
  font-weight: $weight-bold;
  color: $app-ink;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.ft-n__u {
  margin-left: 6rpx;
  color: $app-ink-muted;
  font-size: $text-md;
  font-weight: $weight-bold;
  letter-spacing: 0;
}

.ft-n--md {
  font-size: $text-md;
}

.ft-n--lg {
  font-size: $text-2xl;
  font-weight: $weight-heavy;
}

.ft-n--display {
  font-size: $text-display;
  font-weight: $weight-heavy;
  letter-spacing: -0.03em;
  line-height: 1.05;
}
</style>
