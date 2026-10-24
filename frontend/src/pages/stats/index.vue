<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { getStats, type StatsPeriod, type StatsData } from '@/api/stats'
import { clearAll } from '@/api/cache'
import { formatDateLabel, formatThousands } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import FtTabPage from '@/components/FtTabPage.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import { useTabPage } from '@/utils/tab-page'

// 切页入场与底栏接线（ADR 0038／0040）：onShow 里登录守卫之后调 tab.enter()
const tab = useTabPage('stats')

// ── 统计：周期分段 → 次数／时长 → 近 4 周 → PR 归档 ──

const PERIODS: { key: StatsPeriod; label: string }[] = [
  { key: 'week', label: '本周' },
  { key: 'month', label: '本月' },
  { key: 'year', label: '全年' }
]

const period = ref<StatsPeriod>('week')
const data = ref<StatsData | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    data.value = await getStats(period.value)
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

onShow(() => {
  if (!ensureLogin()) return
  tab.enter()
  load()
})

/** 下拉＝清缓存后重取当前周期（ADR 0035 的逃生门）。 */
onPullDownRefresh(async () => {
  clearAll()
  await load()
  uni.stopPullDownRefresh()
})

function switchPeriod(p: StatsPeriod) {
  if (period.value === p) return
  period.value = p
  load()
}

const periodLabel = computed(
  () => PERIODS.find((p) => p.key === period.value)?.label ?? '本周'
)

const dur = computed(() => {
  const sec = data.value?.durationSec ?? 0
  return { h: Math.floor(sec / 3600), m: Math.round((sec % 3600) / 60) }
})

const maxBarCount = computed(() =>
  Math.max(1, ...(data.value?.weekly ?? []).map((w) => w.count))
)

/** 周均（一位小数） */
const weekAvg = computed(() => {
  const total = (data.value?.weekly ?? []).reduce((s, w) => s + w.count, 0)
  return (total / 4).toFixed(1)
})

/** 柱下标签：后端按时间顺序给 W1–W4（最后一根是本周），这里念成相对周。 */
const WEEK_LABELS = ['3 周前', '2 周前', '上周', '本周']

function weekLabel(i: number, n: number): string {
  return WEEK_LABELS[WEEK_LABELS.length - n + i] ?? ''
}

function barHeight(count: number): string {
  return `${Math.max(4, Math.round((count / maxBarCount.value) * 150))}rpx`
}

</script>

<template>
  <FtTabPage :page="tab" class="stats">
    <view class="stats__body">
      <!-- 页头：标题 ＋ 周期分段（选中＝深字 ＋ 浅胶囊底，和底栏选中同一套；导航不用橙） -->
      <view class="stats__head">
        <text class="stats__title">统计</text>
        <view class="seg" role="tablist">
          <view
            v-for="p in PERIODS"
            :key="p.key"
            class="seg__item"
            :class="{ 'seg__item--on': period === p.key }"
            role="tab"
            :aria-selected="period === p.key"
            @click="switchPeriod(p.key)"
          >
            {{ p.label }}
          </view>
        </view>
      </view>

      <!-- 次数 ／ 时长 -->
      <view class="glass duo">
        <view class="duo__cell">
          <text class="duo__label">{{ periodLabel }}训练</text>
          <view class="duo__num-row">
            <text class="duo__num ft-num">{{ data?.count ?? 0 }}</text>
            <text class="duo__unit">练</text>
          </view>
        </view>
        <view class="duo__cell">
          <text class="duo__label">总训练时长</text>
          <view class="duo__num-row">
            <template v-if="dur.h > 0">
              <text class="duo__num ft-num">{{ dur.h }}</text>
              <text class="duo__unit">小时</text>
              <text class="duo__num ft-num">{{ dur.m }}</text>
              <text class="duo__unit">分</text>
            </template>
            <template v-else>
              <text class="duo__num ft-num">{{ dur.m }}</text>
              <text class="duo__unit">分钟</text>
            </template>
          </view>
        </view>
      </view>

      <!-- 近 4 周柱状 -->
      <view class="glass weeks">
        <view class="card-head">
          <text class="card-title">近 4 周训练次数</text>
          <text class="card-meta ft-num">周均 {{ weekAvg }} 次</text>
        </view>
        <view class="bars">
          <view v-for="(w, i) in data?.weekly ?? []" :key="w.label" class="barcol">
            <text class="barcol__num ft-num">{{ w.count }}</text>
            <!-- 最后一根是本周：深色；前三周浅一档，一眼看出「这周比前几周」 -->
            <view
              class="barcol__bar"
              :class="{ 'barcol__bar--now': i === (data?.weekly?.length ?? 0) - 1 }"
              :style="{ height: barHeight(w.count) }"
            />
            <text class="barcol__label">{{ weekLabel(i, data?.weekly?.length ?? 0) }}</text>
          </view>
        </view>
      </view>

      <!-- PR 归档 -->
      <view class="glass prcard">
        <view class="card-head">
          <text class="card-title">个人最好成绩</text>
          <text class="card-meta ft-num">{{ data?.prs?.length ?? 0 }} 项</text>
        </view>
        <FtEmpty
          v-if="(data?.prs?.length ?? 0) === 0"
          title="还没有 PR"
          sub="每个动作最重的一组会出现在这里。"
        />
        <view v-for="p in data?.prs ?? []" :key="p.exerciseId" class="prow">
          <text class="prow__name">{{ p.name }}</text>
          <text class="prow__val ft-num">{{ formatThousands(Number(p.weight)) }} kg × {{ p.reps }}</text>
          <text class="prow__date ft-num">{{ formatDateLabel(p.performedAt) }}</text>
        </view>
      </view>
    </view>
  </FtTabPage>
</template>

<style lang="scss" scoped>
.stats__body {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
  padding: calc(40rpx + env(safe-area-inset-top)) 40rpx 0;
}

.glass {
  @include glass-card;
}

.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16rpx;
}

.card-title {
  @include card-title;
}

.card-meta {
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* ── 页头 ── */
.stats__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  min-height: 84rpx;
  padding: 12rpx 8rpx 4rpx;
}

.stats__title {
  font-size: $text-hero;
  font-weight: $weight-bold;
  line-height: 1.25;
  color: $app-ink;
}

/* ── 周期分段 ── */
.seg {
  @include seg-track;
  display: flex;
}

.seg__item {
  @include seg-item(12rpx 24rpx);
}

.seg__item--on {
  @include seg-item-on;
}

/* ── 次数 ／ 时长 ── */
.duo {
  display: flex;
}

.duo__cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.duo__cell + .duo__cell {
  padding-left: 32rpx;
  border-left: 1rpx solid $app-line-soft;
}

.duo__label {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.duo__num-row {
  display: flex;
  align-items: baseline;
  gap: 6rpx;
}

.duo__num {
  font-size: $text-3xl;
  font-weight: $weight-heavy;
  letter-spacing: -0.02em;
  line-height: 1.15;
  color: $app-ink;
}

.duo__unit {
  margin-right: 6rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* ── 近 4 周 ── */
.bars {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  height: 260rpx;
  margin-top: 16rpx;
}

.barcol {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 10rpx;
  width: 112rpx;
  height: 100%;
}

.barcol__num {
  font-size: $text-sm;
  color: $app-ink;
}

.barcol__bar {
  width: 56rpx;
  border-radius: $radius-xs;
  background: $app-line-dash;
}

.barcol__bar--now {
  background: $app-ink-2;
}

.barcol__label {
  font-size: $text-xs;
  color: $app-ink-muted;
}

/* ── PR 归档 ── */
.prcard {
  padding-bottom: 16rpx;
}

.prow {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 0;
}

.prow + .prow,
.card-head + .prow {
  border-top: 1rpx solid $app-line-soft;
}

.card-head + .prow {
  margin-top: 16rpx;
}

.prow__name {
  flex: 1;
  min-width: 0;
  font-size: $text-md;
  color: $app-ink;
}

.prow__val {
  font-size: $text-md;
  color: $app-ink;
}

.prow__date {
  width: 120rpx;
  flex-shrink: 0;
  text-align: right;
  font-size: $text-sm;
  color: $app-ink-muted;
}

@media (prefers-reduced-motion: reduce) {
  .seg__item {
    transition: none;
  }
}
</style>
