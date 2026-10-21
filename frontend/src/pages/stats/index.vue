<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import FtTabBar from '@/components/FtTabBar.vue'
import { getStats, type StatsPeriod, type StatsData } from '@/api/stats'
import { clearAll } from '@/api/cache'
import { formatDateLabel, formatThousands } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import { playTabEnter, takeTabEnter } from '@/utils/nav'

// 切页入场（ADR 0038）：页面现在被保活、不再重建，所以只能每次 onShow 按方向重放一遍。
const tabDir = ref<'left' | 'right' | null>(null)
const tabSeq = ref(0)

// ── 统计（设计稿：.workbuddy/stitch/screen/stats.png）──

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
  const dir = takeTabEnter('stats')
  tabDir.value = dir
  tabSeq.value++
  playTabEnter('.stats__body', dir)
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

function barHeight(count: number): string {
  return `${Math.max(4, Math.round((count / maxBarCount.value) * 150))}rpx`
}

</script>

<template>
  <view class="stats">
    <view class="stats__body">
      <!-- 标题行 -->
      <view class="stats__head">
        <text class="stats__title">统计</text>
        <text class="stats__meta ft-num">FIT-APP LOG</text>
      </view>

      <!-- 周期 tab -->
      <view class="ptabs">
        <view
          v-for="p in PERIODS"
          :key="p.key"
          class="ptab"
          :class="{ 'ptab--active': period === p.key }"
          @click="switchPeriod(p.key)"
        >
          {{ p.label }}
        </view>
      </view>

      <!-- 双格卡 -->
      <view class="ft-card duo">
        <view class="duo__cell">
          <view class="duo__num-row">
            <text class="duo__num ft-num">{{ data?.count ?? 0 }}</text>
            <text class="duo__unit">练</text>
          </view>
          <text class="duo__label">{{ periodLabel }}训练次数</text>
        </view>
        <view class="duo__divider" />
        <view class="duo__cell">
          <view class="duo__num-row">
            <template v-if="dur.h > 0">
              <text class="duo__num ft-num">{{ dur.h }}<text class="duo__unit"> 小时 </text>{{ dur.m }}</text>
              <text class="duo__unit"> 分</text>
            </template>
            <template v-else>
              <text class="duo__num ft-num">{{ dur.m }}</text>
              <text class="duo__unit"> 分钟</text>
            </template>
          </view>
          <text class="duo__label">总训练时长</text>
        </view>
      </view>

      <!-- 近 4 周柱状 -->
      <view class="ft-card weeks">
        <view class="weeks__head">
          <text class="weeks__title">近 4 周训练次数</text>
          <text class="weeks__avg ft-num">周均 {{ weekAvg }} 次</text>
        </view>
        <view class="bars">
          <view v-for="w in data?.weekly ?? []" :key="w.label" class="barcol">
            <text class="barcol__num ft-num">{{ w.count }}</text>
            <view class="barcol__bar" :style="{ height: barHeight(w.count) }" />
            <text class="barcol__label ft-num">{{ w.label }}</text>
          </view>
        </view>
      </view>

      <!-- PR 归档 -->
      <view class="ft-card prcard">
        <view class="prcard__head">
          <text class="prcard__title">个人最好成绩</text>
          <text class="prcard__meta ft-num">PR ARCHIVE</text>
        </view>
        <view v-if="(data?.prs?.length ?? 0) === 0" class="prcard__empty">
          还没有训练记录——破纪录会出现在这里
        </view>
        <view
          v-for="(p, i) in data?.prs ?? []"
          :key="p.exerciseId"
          class="prow"
          :class="{ 'prow--lit': i === 0 }"
        >
          <view class="prow__dot" :class="{ 'prow__dot--hollow': i !== 0 }" />
          <text class="prow__name">{{ p.name }}</text>
          <text class="prow__val ft-num" :class="{ 'ft-marker': i === 0 }">
            {{ formatThousands(Number(p.weight)) }} kg × {{ p.reps }}
          </text>
          <text class="prow__date ft-num">{{ formatDateLabel(p.performedAt) }}</text>
        </view>
      </view>

      <text class="stats__foot">FIT-APP LEDGER · DISCIPLINE IN NUMBERS</text>
      <view class="stats__pad" />
    </view>

    <view class="stats__bottom">
      <FtTabBar active="stats" :enter-dir="tabDir" :enter-seq="tabSeq" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.stats__body {
  padding: 36rpx 32rpx 0;
}

/* ── 标题行 ── */
.stats__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 24rpx;
}

.stats__title {
  font-size: 48rpx;
  font-weight: 700;
  color: $app-ink;
}

.stats__meta {
  font-size: 22rpx;
  letter-spacing: 2rpx;
  color: $app-ink-subtle;
}

/* ── 周期 tab ── */
.ptabs {
  display: flex;
  gap: 44rpx;
  border-bottom: 1rpx solid $app-line;
  margin-bottom: 28rpx;
}

.ptab {
  font-size: $text-md;
  color: $app-ink-muted;
  padding-bottom: 14rpx;
  position: relative;
}

.ptab--active {
  color: $app-ink;
  font-weight: 700;
}

.ptab--active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1rpx;
  height: 5rpx;
  background: $app-ink;
  border-radius: 3rpx;
}

/* ── 双格卡 ── */
.duo {
  display: flex;
  align-items: stretch;
  padding: 32rpx 0;
}

.duo__cell {
  flex: 1;
  padding: 0 36rpx;
}

.duo__num-row {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.duo__num {
  font-size: $text-3xl;
  font-weight: 700;
  color: $app-ink;
  line-height: 1.1;
}

.duo__unit {
  font-size: $text-sm;
  font-weight: 400;
  color: $app-ink-muted;
}

.duo__label {
  display: block;
  font-size: $text-xs;
  color: $app-ink-muted;
  padding-top: 10rpx;
}

.duo__divider {
  width: 1rpx;
  background: $app-line;
}

/* ── 近 4 周 ── */
.weeks__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 24rpx;
}

.weeks__title {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.weeks__avg {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.bars {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  height: 230rpx;
  border-bottom: 1rpx solid $app-line;
}

.barcol {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 8rpx;
  width: 96rpx;
  height: 100%;
}

.barcol__num {
  font-size: $text-sm;
  font-weight: 600;
  color: $app-ink;
}

.barcol__bar {
  width: 56rpx;
  background: $app-ink;
  border-radius: 6rpx 6rpx 0 0;
}

.barcol__label {
  font-size: $text-xs;
  color: $app-ink-muted;
  /* 标签压在底线下方 */
  transform: translateY(40rpx);
}

/* ── PR 归档 ── */
.prcard__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 8rpx;
}

.prcard__title {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.prcard__meta {
  font-size: 22rpx;
  letter-spacing: 2rpx;
  color: $app-ink-subtle;
}

.prow {
  display: flex;
  align-items: center;
  padding: 28rpx 4rpx;
  border-top: 1rpx solid $app-line-soft;
}

.prow:first-of-type {
  border-top: none;
}

.prow__dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: $app-ink;
  margin-right: 18rpx;
  flex-shrink: 0;
}

.prow__dot--hollow {
  background: transparent;
  border: 2rpx solid $app-line;
}

.prow__name {
  font-size: $text-md;
  color: $app-ink;
}

.prow__val {
  margin-left: auto;
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.prow__date {
  font-size: $text-xs;
  color: $app-ink-muted;
  width: 110rpx;
  text-align: right;
  flex-shrink: 0;
}

.prcard__empty {
  padding: 48rpx 0 32rpx;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
  border-top: 1rpx solid $app-line-soft;
}

.stats__foot {
  display: block;
  text-align: center;
  font-size: 20rpx;
  letter-spacing: 2rpx;
  color: $app-ink-subtle;
  padding: 40rpx 0 8rpx;
}

.stats__pad {
  height: 200rpx;
}

.stats__bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  /* 高于切页克隆层（.tab-slide-layer 是 10）：转场期间底栏钉住、不被遮 */
  z-index: 20;
}
</style>
