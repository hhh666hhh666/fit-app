<script setup lang="ts">
import { computed, ref } from 'vue'
import { listRefCandidates, type DiaryRef } from '@/api/diary'
import { refMarkdown, refSub, refTitle } from '@/utils/diary-ref'
import { todayKey } from '@/utils/format'
import FtEmpty from '@/components/FtEmpty.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtSheet from '@/components/FtSheet.vue'

// ── 选一条记录引用进日记（ADR 0044）：默认列这篇日记当天的记录，可翻到别的日期 ──

const props = defineProps<{ date: string }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'pick', markdown: string): void }>()

const date = ref(props.date)
const rows = ref<DiaryRef[]>([])
const loading = ref(false)
const failed = ref(false)

const label = computed(() => {
  const [, m, d] = date.value.split('-').map(Number)
  const week = '日一二三四五六'[new Date(`${date.value}T00:00:00`).getDay()]
  return `${m}月${d}日 · 周${week}`
})
const canNext = computed(() => date.value < todayKey())

async function load() {
  loading.value = true
  failed.value = false
  try {
    rows.value = await listRefCandidates(date.value)
  } catch {
    rows.value = []
    failed.value = true
  } finally {
    loading.value = false
  }
}

function shift(delta: number) {
  const d = new Date(`${date.value}T00:00:00`)
  d.setDate(d.getDate() + delta)
  const next = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  if (next > todayKey()) return
  date.value = next
  void load()
}

void load()
</script>

<template>
  <FtSheet title="引用记录" @close="emit('close')">
    <view class="rs">
      <view class="rs__bar">
        <view class="rs__arrow" role="button" aria-label="前一天" @click="shift(-1)">
          <FtIcon name="chevron-left" :size="18" :stroke="2" />
        </view>
        <text class="rs__date ft-num">{{ label }}</text>
        <view
          class="rs__arrow"
          :class="{ 'rs__arrow--off': !canNext }"
          role="button"
          aria-label="后一天"
          @click="shift(1)"
        >
          <FtIcon name="chevron-right" :size="18" :stroke="2" />
        </view>
      </view>

      <view v-if="loading" class="rs__hint">加载中…</view>
      <view v-else-if="failed" class="rs__hint">没读到记录，稍后再试</view>
      <FtEmpty v-else-if="rows.length === 0" title="这天没有训练和饮食记录" sub="翻到别的日期看看。" />
      <view v-else class="rs__list">
        <view
          v-for="r in rows"
          :key="r.key"
          class="rs__row"
          role="button"
          @click="emit('pick', refMarkdown(r))"
        >
          <text class="rs__title">{{ refTitle(r) }}</text>
          <text class="rs__sub ft-num">{{ refSub(r) }}</text>
        </view>
      </view>
    </view>
  </FtSheet>
</template>

<style lang="scss" scoped>
.rs {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.rs__bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
}

.rs__arrow {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $app-ink-2;
  @include press(fill);
}

.rs__arrow--off {
  opacity: 0.25;
}

.rs__date {
  min-width: 240rpx;
  text-align: center;
  font-size: $text-md;
  color: $app-ink;
}

.rs__hint {
  padding: 48rpx 0;
  text-align: center;
  font-size: $text-base;
  color: $app-ink-muted;
}

.rs__list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  max-height: 55vh;
  overflow-y: auto;
}

.rs__row {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  padding: 24rpx 28rpx;
  border-radius: $radius-md;
  background: $app-surface-2;
  @include press(dim);
}

.rs__title {
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.rs__sub {
  font-size: $text-sm;
  color: $app-ink-muted;
}
</style>
