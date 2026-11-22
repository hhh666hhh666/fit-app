<!--
  识别前的提示词面板（ADR 0060）：记一餐的 AI 识别、食物库的标签识别共用。
  提示词选填、每次都是空的；下面列出最近用过的几条，点了才填进去（追加到已写的后面）。
  点「拍照识别」把提示词交给父组件，由父组件去选图、识别。
-->
<script setup lang="ts">
import { ref } from 'vue'
import FtButton from '@/components/FtButton.vue'
import { recentHints, rememberHint } from '@/utils/recognize-hints'

const props = defineProps<{ placeholder?: string; busy?: boolean }>()
const emit = defineEmits<{ (e: 'start', hint: string | null): void; (e: 'cancel'): void }>()

const MAX_LEN = 500
const hint = ref('')
const recent = ref(recentHints())

function useRecent(h: string) {
  const cur = hint.value.trim()
  if (cur.includes(h)) return
  hint.value = (cur ? `${cur}；${h}` : h).slice(0, MAX_LEN)
}

function start() {
  if (props.busy) return
  const h = hint.value.trim()
  if (h) rememberHint(h)
  emit('start', h || null)
}
</script>

<template>
  <view class="rh">
    <text class="rh__title">给 AI 一点提示（选填）</text>
    <textarea
      v-model="hint"
      class="rh__area"
      :maxlength="MAX_LEN"
      :placeholder="placeholder || '如：这是半份；用的橄榄油；米饭只吃了一半'"
      placeholder-class="rh__ph"
      auto-height
    />
    <view v-if="recent.length" class="rh__chips">
      <text v-for="h in recent" :key="h" class="rh__chip" role="button" @click="useRecent(h)">{{ h }}</text>
    </view>
    <view class="rh__btns">
      <FtButton class="rh__btn" @click="emit('cancel')">取消</FtButton>
      <FtButton variant="primary" class="rh__btn" :disabled="busy" @click="start">拍照识别</FtButton>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.rh {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.rh__title {
  font-size: $text-sm;
  font-weight: $weight-bold;
  color: $app-ink;
}

.rh__area {
  box-sizing: border-box;
  width: 100%;
  min-height: 120rpx;
  padding: 20rpx 24rpx;
  border-radius: $radius-sm;
  background: $app-surface;
  box-shadow: inset 0 0 0 1rpx $app-line;
  font-size: $text-md;
  line-height: 1.5;
  color: $app-ink;
}

.rh__ph {
  color: $app-ink-subtle;
}

.rh__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.rh__chip {
  max-width: 100%;
  padding: 8rpx 20rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-sm;
  color: $app-ink-2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rh__btns {
  display: flex;
  gap: 16rpx;
}

.rh__btn {
  flex: 1;
}
</style>
