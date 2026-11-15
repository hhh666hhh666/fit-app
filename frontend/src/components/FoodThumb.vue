<!--
  食物图片的小缩略图（ADR 0057）：列表、记一餐的选食物弹层、组合编辑里每行左边那一格。
  只在食物有图时由调用方渲染（v-if="f.imageKey"）；图还没取到时显示同尺寸的底色块，取不到就一直是底色块。
-->
<script setup lang="ts">
import { ref, watch } from 'vue'
import { fetchFoodImage } from '@/utils/photo'

const props = withDefaults(defineProps<{ id: number; imageKey: string; size?: number }>(), { size: 44 })

const url = ref<string | null>(null)

watch(
  () => [props.id, props.imageKey] as const,
  async ([id, key]) => {
    url.value = null
    const got = await fetchFoodImage(id, key, 'thumb')
    // 列表滚动复用时 props 可能已经换成别的食物
    if (props.id === id && props.imageKey === key) url.value = got
  },
  { immediate: true }
)
</script>

<template>
  <view class="fthumb" :style="{ width: `${size}px`, height: `${size}px` }">
    <image v-if="url" class="fthumb__img" :src="url" mode="aspectFill" />
  </view>
</template>

<style lang="scss" scoped>
.fthumb {
  flex: none;
  overflow: hidden;
  border-radius: $radius-sm;
  background: $app-surface-2;
}

.fthumb__img {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
