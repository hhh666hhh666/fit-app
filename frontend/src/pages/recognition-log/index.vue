<script setup lang="ts">
import { ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import { listRecognitionLogs, type RecognitionLogItem } from '@/api/nutrition'
import { formatDateTimeLabel, formatThousands } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'

// ── 识别明细（ADR 0018）：逐条流水——输入/输出/缓存命中 token、模型、耗时、成败；不显示费用 ──

const PAGE_SIZE = 50

const list = ref<RecognitionLogItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)

onShow(() => {
  if (!ensureLogin()) return
  void reload()
})

async function reload() {
  loading.value = true
  finished.value = false
  try {
    const items = await listRecognitionLogs(0, PAGE_SIZE)
    list.value = items
    finished.value = items.length < PAGE_SIZE
  } catch {
    // unwrap 已提示
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loading.value || loadingMore.value || finished.value) return
  loadingMore.value = true
  try {
    const items = await listRecognitionLogs(list.value.length, PAGE_SIZE)
    list.value = list.value.concat(items)
    finished.value = items.length < PAGE_SIZE
  } catch {
    // unwrap 已提示
  } finally {
    loadingMore.value = false
  }
}

onReachBottom(() => void loadMore())

function goBack() {
  uni.navigateBack()
}

/** qwen3-vl-8b-instruct → vl-8b-instruct（显示用，省宽度）。 */
function shortModel(m: string): string {
  return m.replace(/^qwen[\d.]*-/, '')
}

/** 入 2,400 · 出 312 · 缓存 1,800（缓存只在 >0 时显示）。 */
function tokensText(r: RecognitionLogItem): string {
  const parts = [`入 ${formatThousands(r.tokensIn)}`, `出 ${formatThousands(r.tokensOut)}`]
  if (r.cachedTokens > 0) parts.push(`缓存 ${formatThousands(r.cachedTokens)}`)
  return parts.join(' · ')
}

function durationText(ms: number): string {
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`
}
</script>

<template>
  <view class="rlog">
    <view class="rlog__head">
      <text class="rlog__back" @click="goBack">‹</text>
      <text class="rlog__title">识别明细</text>
      <view class="rlog__head-space" />
    </view>

    <view class="rlog__body">
      <view class="rlist">
        <view v-for="r in list" :key="r.id" class="rrow">
          <view class="rrow__line">
            <text class="rrow__time ft-num">{{ formatDateTimeLabel(r.createdAt) }}</text>
            <text class="rrow__model ft-num">{{ shortModel(r.model) }}</text>
            <text v-if="!r.success" class="rrow__fail">失败</text>
          </view>
          <view class="rrow__line rrow__line--sub">
            <text class="rrow__tokens ft-num">{{ tokensText(r) }}</text>
            <text class="rrow__dur ft-num">{{ durationText(r.durationMs) }}</text>
          </view>
        </view>
        <view v-if="!loading && list.length === 0" class="rlist__empty">
          还没有识别记录——去食物库点「拍照识别」
        </view>
        <view v-else-if="loadingMore" class="rlist__more">加载中…</view>
        <view v-else-if="finished && list.length > 0" class="rlist__more">
          共 {{ list.length }} 条
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.rlog__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 12rpx;
}

.rlog__back {
  font-size: 56rpx;
  line-height: 1;
  color: $app-ink;
  width: 64rpx;
}

.rlog__title {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.rlog__head-space {
  width: 64rpx;
}

.rlog__body {
  padding: 12rpx 32rpx 40rpx;
}

.rlist {
  background: $app-surface;
  border: 1rpx solid $app-line-soft;
  border-radius: $radius-lg;
  overflow: hidden;
}

.rrow {
  padding: 24rpx 28rpx;
}

.rrow + .rrow {
  border-top: 1rpx solid $app-line-soft;
}

.rrow__line {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.rrow__line--sub {
  padding-top: 8rpx;
}

.rrow__time {
  font-size: $text-sm;
  color: $app-ink;
}

.rrow__model {
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.rrow__fail {
  margin-left: auto;
  font-size: 20rpx;
  color: #c4453c;
  border: 1rpx solid #c4453c;
  border-radius: 6rpx;
  padding: 3rpx 10rpx;
}

.rrow__tokens {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.rrow__dur {
  margin-left: auto;
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.rlist__empty {
  padding: 60rpx 24rpx;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

.rlist__more {
  padding: 20rpx 24rpx;
  text-align: center;
  font-size: $text-xs;
  color: $app-ink-subtle;
  border-top: 1rpx solid $app-line-soft;
}
</style>
