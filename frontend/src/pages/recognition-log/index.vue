<script setup lang="ts">
import { ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import { getRecognitionStats, listRecognitionLogs, type RecognitionLogItem, type RecognitionStats } from '@/api/nutrition'
import { formatDateTimeLabel, formatThousands, formatYuan } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import FtEmpty from '@/components/FtEmpty.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'

// ── 识别明细（ADR 0018）：逐条流水——输入/输出/缓存命中 token、模型、耗时、成败；列表不显示费用，
//    顶部摘要卡（全部范围的估算金额）点进「用量统计」页（ADR 0054 后续注记） ──

const PAGE_SIZE = 50

const list = ref<RecognitionLogItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)

/** 顶部摘要卡：固定按「全部」统计，点进「用量统计」页（ADR 0054 后续注记）。 */
const summary = ref<RecognitionStats | null>(null)

onShow(() => {
  if (!ensureLogin()) return
  void reload()
  void loadSummary()
})

async function loadSummary() {
  try {
    summary.value = await getRecognitionStats('all')
  } catch {
    // unwrap 已提示；摘要卡缺了不影响明细
  }
}

function openStats() {
  uni.navigateTo({ url: '/pages/recognition-log/stats' })
}

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

function openDetail(id: number) {
  uni.navigateTo({ url: `/pages/recognition-log/detail?id=${id}` })
}

function durationText(ms: number): string {
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`
}
</script>

<template>
  <view class="rlog">
    <view class="rlog__body">
      <FtPageHeader title="识别明细" back />

      <view v-if="summary && summary.requests > 0" class="glass sum" role="button" @click="openStats">
        <view class="sum__main">
          <text class="sum__k">用量统计 · 全部</text>
          <text class="sum__v ft-num">
            估算 {{ summary.pricedRequests > 0 ? formatYuan(summary.cost) : '未计价' }} · {{ formatThousands(summary.totalTokens) }} token · {{ formatThousands(summary.requests) }} 次
          </text>
        </view>
        <FtIcon name="chevron-right" :size="18" />
      </view>

      <text class="note">每次拍照识别一条：用了多少 token、哪个模型、花了多久；点一条看全部信息</text>

      <view class="glass rlist">
        <view v-for="r in list" :key="r.id" class="rrow" role="button" @click="openDetail(r.id)">
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
  @include glass-card;
}

.sum {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 28rpx;
  color: $app-ink-subtle;
  @include press(dim);
}

.sum__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  min-width: 0;
}

.sum__k {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.sum__v {
  font-size: $text-md;
  color: $app-ink;
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

.rrow {
  @include press(dim);
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
  @include badge(null, null);
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
