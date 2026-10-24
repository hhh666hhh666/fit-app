<script setup lang="ts">
import { ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import { listRecognitionLogs, type RecognitionLogItem } from '@/api/nutrition'
import { formatDateTimeLabel, formatThousands } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import FtEmpty from '@/components/FtEmpty.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'

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
    <view class="rlog__body">
      <FtPageHeader title="识别明细" back />

      <text class="note">每次拍照识别一条：用了多少 token、哪个模型、花了多久</text>

      <view class="glass rlist">
        <view v-for="r in list" :key="r.id" class="rrow">
          <view class="rrow__line">
            <text class="rrow__time ft-num">{{ formatDateTimeLabel(r.createdAt) }}</text>
            <text v-if="!r.success" class="badge badge--fail">失败</text>
            <text class="rrow__dur ft-num">{{ durationText(r.durationMs) }}</text>
          </view>
          <view class="rrow__line">
            <text class="rrow__tokens ft-num">{{ tokensText(r) }}</text>
            <text class="rrow__model">{{ shortModel(r.model) }}</text>
          </view>
        </view>
        <FtEmpty
          v-if="!loading && list.length === 0"
          title="还没有识别记录"
          sub="去食物库点「拍照识别」，识别一次这里就有一条。"
        />
        <view v-else-if="loadingMore" class="rlist__more">加载中…</view>
        <view v-else-if="finished && list.length > 0" class="rlist__more ft-num">共 {{ list.length }} 条</view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.rlog__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(64rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-lg;
  box-shadow: $glass-shadow;
  padding: 32rpx 36rpx;
}

.note {
  padding: 0 8rpx;
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

.rlist {
  padding: 8rpx 28rpx;
}

.rrow {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  padding: 24rpx 0;
}

.rrow + .rrow {
  border-top: 1rpx solid $app-line-soft;
}

.rrow__line {
  display: flex;
  align-items: center;
  gap: 12rpx;
  min-width: 0;
}

.rrow__time {
  font-size: $text-md;
  color: $app-ink;
}

.rrow__dur {
  margin-left: auto;
  font-size: $text-sm;
  color: $app-ink-2;
}

.rrow__tokens {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.rrow__model {
  margin-left: auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.badge {
  flex: none;
  padding: 2rpx 12rpx;
  border-radius: $radius-pill;
  font-size: $text-xs;
}

/* 失败：浅橙底 ＋ 危险色字（与编辑食物页「待核对」同一个角标） */
.badge--fail {
  background: $accent-soft;
  color: $app-danger;
}

.rlist__more {
  padding: 24rpx 0;
  border-top: 1rpx solid $app-line-soft;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}
</style>
