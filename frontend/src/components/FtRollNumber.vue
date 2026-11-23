<!--
  滚动数字（边吃边数，ADR 0067）：每一位是一列 0–9，数字变了就把那一列滚到新位置；
  进位时十位、百位跟着各自滚，看起来像机械计数器。按个位往左对齐位置，位数变化时新的一位从 0 滚进来。
  字体、字号、颜色都跟随父元素；系统「减少动效」时直接跳到新值。
-->
<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ value: number }>()

/** 从个位起的每一位（个位在最后，模板里按从高到低排）。 */
const digits = computed(() => {
  const n = Math.max(0, Math.round(Number(props.value) || 0))
  return String(n).split('').map(Number)
})
</script>

<template>
  <view class="roll" :aria-label="String(Math.round(value || 0))">
    <view v-for="(d, i) in digits" :key="digits.length - i" class="roll__col" aria-hidden="true">
      <view class="roll__strip" :style="{ transform: `translateY(${-d * 10}%)` }">
        <text v-for="k in 10" :key="k" class="roll__d">{{ k - 1 }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.roll {
  display: inline-flex;
  height: 1.1em;
  line-height: 1.1;
  overflow: hidden;
}

.roll__col {
  height: 1.1em;
  overflow: hidden;
}

.roll__strip {
  display: flex;
  flex-direction: column;
  transition: transform 420ms $ease-emphasis;
  will-change: transform;

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
}

.roll__d {
  display: block;
  height: 1.1em;
  text-align: center;
}
</style>
