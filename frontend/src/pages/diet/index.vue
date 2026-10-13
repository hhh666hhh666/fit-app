<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  addWater,
  getDay,
  getTrend,
  type DayOverview,
  type MealKey,
  type TrendPoint
} from '@/api/nutrition'
import FtDietRings from '@/components/FtDietRings.vue'
import FtTrendChart from '@/components/FtTrendChart.vue'
import MealSheet from '@/components/MealSheet.vue'
import { useDietStore } from '@/stores/diet'
import { buildDietRings } from '@/utils/diet'
import { ensureLogin } from '@/utils/guard'

// ── 全屏「饮食」页：记录（今日 / 按天翻看）＋ 趋势（五指标切换）──

const diet = useDietStore()

type Metric = 'kcal' | 'protein' | 'carb' | 'fat' | 'water'

const METRICS: { key: Metric; label: string; unit: string }[] = [
  { key: 'kcal', label: '热量', unit: '大卡' },
  { key: 'protein', label: '蛋白', unit: 'g' },
  { key: 'carb', label: '碳水', unit: 'g' },
  { key: 'fat', label: '脂肪', unit: 'g' },
  { key: 'water', label: '饮水', unit: 'ml' }
]

const MEAL_ROWS: { key: MealKey; label: string }[] = [
  { key: 'breakfast', label: '早餐' },
  { key: 'lunch', label: '午餐' },
  { key: 'dinner', label: '晚餐' },
  { key: 'snack', label: '加餐' }
]

function ymd(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const todayStr = ymd(new Date())

const tab = ref<'log' | 'trend'>('log')
const dateStr = ref(todayStr)
const overview = ref<DayOverview | null>(null)
const sheetMeal = ref<MealKey | null>(null)

const trend = ref<TrendPoint[]>([])
const metric = ref<Metric>('kcal')

onShow(() => {
  if (!ensureLogin()) return
  void load()
})

async function load() {
  try {
    const [day, tr] = await Promise.all([getDay(dateStr.value), getTrend(), diet.load(false)])
    overview.value = day
    trend.value = tr.days
  } catch {
    // request 层已统一提示
  }
}

async function loadDay() {
  try {
    overview.value = await getDay(dateStr.value)
  } catch {
    // request 层已统一提示
  }
}

/** 弹层里改了条目/照片：当日与趋势都刷新。 */
function onChanged() {
  void loadDay()
  void refreshTrend()
}

async function refreshTrend() {
  try {
    trend.value = (await getTrend()).days
  } catch {
    // request 层已统一提示
  }
}

// ── 日期 ──

const dateLabel = computed(() => {
  const d = new Date(`${dateStr.value}T00:00:00`)
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getMonth() + 1}月${d.getDate()}日 · 周${week}`
})
const isToday = computed(() => dateStr.value === todayStr)

function shiftDate(delta: number) {
  const d = new Date(`${dateStr.value}T00:00:00`)
  d.setDate(d.getDate() + delta)
  const s = ymd(d)
  if (s > todayStr) return
  dateStr.value = s
  void loadDay()
}

function goToday() {
  if (isToday.value) return
  dateStr.value = todayStr
  void loadDay()
}

// ── 记录：汇总 / 环 / 餐次 ──

const kcalNow = computed(() => Math.round(Number(overview.value?.totals.kcal ?? 0)))
const kcalTarget = computed(() => {
  const t = overview.value?.targets.kcal
  return t == null ? null : Math.round(Number(t))
})

const rings = computed(() => buildDietRings(overview.value))

const mealRows = computed(() =>
  MEAL_ROWS.map((m) => {
    const items = overview.value?.meals.find((g) => g.meal === m.key)?.items ?? []
    const kcal = Math.round(items.reduce((s, i) => s + Number(i.kcal || 0), 0))
    return {
      ...m,
      count: items.length,
      kcal,
      hasPhoto: (overview.value?.photoMeals ?? []).includes(m.key)
    }
  })
)

// ── 饮水（点一下加/减一步进；点步进数字就地改）──

const waterMl = computed(() => overview.value?.water.ml ?? 0)
const waterTarget = computed(() => overview.value?.water.target ?? null)
const stepMl = computed(() => diet.settings?.waterStepMl ?? 250)

const stepEditing = ref(false)
const stepInput = ref('')
const waterBusy = ref(false)

function startStepEdit() {
  stepInput.value = String(stepMl.value)
  stepEditing.value = true
}

async function saveStep() {
  stepEditing.value = false
  const n = Math.round(Number(stepInput.value))
  if (!Number.isFinite(n) || n < 10 || n > 2000) {
    if (stepInput.value.trim()) uni.showToast({ title: '步进 10–2000ml', icon: 'none' })
    return
  }
  if (n === stepMl.value) return
  if (!diet.settings) await diet.load()
  const base = diet.settings
  if (!base) return
  try {
    await diet.save({ ...base, waterStepMl: n })
  } catch {
    // request 层已统一提示
  }
}

async function bumpWater(direction: 1 | -1) {
  if (waterBusy.value) return
  waterBusy.value = true
  try {
    const ml = await addWater(dateStr.value, direction * stepMl.value)
    if (overview.value) overview.value.water = { ...overview.value.water, ml }
  } catch {
    // request 层已统一提示
  } finally {
    waterBusy.value = false
  }
}

// ── 趋势（canvas 自绘：折线 + 网格 + 目标虚线 + 末值标注）──

const metricLabel = computed(() => METRICS.find((m) => m.key === metric.value)?.label ?? '')

function metricTarget(k: Metric): number | null {
  const t = overview.value?.targets
  const v = k === 'water' ? t?.water : t?.[k]
  return v == null ? null : Number(v)
}

const metricTargetText = computed(() => {
  const v = metricTarget(metric.value)
  if (v == null || v <= 0) return ''
  const unit = METRICS.find((m) => m.key === metric.value)?.unit ?? ''
  return `${Math.round(v)} ${unit}`
})

/** 当前指标下的逐日序列（喂给图表组件；缩放、准星、标签都在组件里处理）。 */
const trendPoints = computed(() =>
  trend.value.map((p) => ({ date: p.date, value: Number(p[metric.value] || 0) }))
)

/** 全屏看趋势。 */
const fullTrend = ref(false)

/** 全屏时的画布高度（约屏高 55%，给横轴与准星留出空间）。 */
const fullHeight = computed(() => {
  const info = uni.getSystemInfoSync()
  return Math.max(220, Math.round(info.windowHeight * 0.55))
})

// 旧的自绘 canvas 已退役——绘制层统一收在 FtTrendChart.vue（ADR 0015）

function goBack() {
  uni.navigateBack()
}
</script>

<template>
  <view class="dietpg">
    <view class="dietpg__head">
      <text class="dietpg__back" @click="goBack">‹</text>
      <text class="dietpg__title">饮食记录</text>
      <view class="dietpg__head-space" />
    </view>

    <view class="tabs">
      <view class="tab" :class="{ 'tab--active': tab === 'log' }" @click="tab = 'log'">记录</view>
      <view class="tab" :class="{ 'tab--active': tab === 'trend' }" @click="tab = 'trend'">趋势</view>
    </view>

    <!-- ── 记录 ── -->
    <view v-if="tab === 'log'" class="dietpg__body">
      <view class="datebar">
        <text class="datebar__arrow" @click="shiftDate(-1)">‹</text>
        <text class="datebar__label ft-num">{{ dateLabel }}</text>
        <text
          class="datebar__arrow"
          :class="{ 'datebar__arrow--disabled': isToday }"
          @click="shiftDate(1)"
        >
          ›
        </text>
        <text v-if="!isToday" class="datebar__today" @click="goToday">回今天</text>
      </view>

      <view class="ft-card kcalcard">
        <view class="kcalrow">
          <text class="kcalrow__num ft-num">{{ kcalNow }}</text>
          <text class="kcalrow__unit">
            / {{ kcalTarget == null ? '目标 --' : `目标 ${kcalTarget}` }} 大卡
          </text>
        </view>
        <FtDietRings :rings="rings" />
      </view>

      <view class="ft-card mealcard">
        <view v-for="r in mealRows" :key="r.key" class="mealrow" @click="sheetMeal = r.key">
          <text class="mealrow__label">{{ r.label }}</text>
          <text v-if="r.hasPhoto" class="mealrow__photo">照片</text>
          <text class="mealrow__meta ft-num">
            {{ r.count ? `${r.count} 项 · ${r.kcal} 大卡` : '未记录' }}
          </text>
          <text class="mealrow__go">›</text>
        </view>
      </view>

      <view class="ft-card watercard">
        <view class="waterrow">
          <view class="waterrow__left">
            <text class="waterrow__label">饮水</text>
            <view class="waterrow__num-row">
              <text class="waterrow__num ft-num">{{ waterMl }}</text>
              <text class="waterrow__target ft-num">
                / {{ waterTarget == null ? '--' : waterTarget }} ml
              </text>
            </view>
          </view>
          <view class="waterrow__right">
            <view class="waterrow__step">
              <template v-if="stepEditing">
                <input
                  v-model="stepInput"
                  type="number"
                  class="waterrow__step-input ft-num"
                  @blur="saveStep"
                  @confirm="saveStep"
                />
                <text class="waterrow__step-unit">ml</text>
              </template>
              <text v-else class="waterrow__step-text ft-num" @click="startStepEdit">
                步进 {{ stepMl }}ml
              </text>
            </view>
            <view class="waterrow__btns">
              <view class="waterrow__btn" @click="bumpWater(-1)">−</view>
              <view class="waterrow__btn waterrow__btn--solid" @click="bumpWater(1)">+</view>
            </view>
          </view>
        </view>
      </view>

      <view class="dietpg__pad" />
    </view>

    <!-- ── 趋势 ── -->
    <view v-else class="dietpg__body">
      <view class="metabs">
        <view
          v-for="m in METRICS"
          :key="m.key"
          class="metab"
          :class="{ 'metab--active': metric === m.key }"
          @click="metric = m.key"
        >
          {{ m.label }}
        </view>
      </view>

      <view class="ft-card chartcard">
        <view class="chartcard__head">
          <text class="chartcard__title">{{ metricLabel }}</text>
          <view class="chartcard__right">
            <text v-if="metricTargetText" class="chartcard__target ft-num">
              目标 {{ metricTargetText }}
            </text>
            <text class="chartcard__full" @click="fullTrend = true">全屏</text>
          </view>
        </view>
        <FtTrendChart :points="trendPoints" :target="metricTarget(metric)" :height="180" />
        <text class="chartcard__tip">单指按住看数值 · 双指捏合缩放 · 双指拖动平移</text>
      </view>

      <view class="dietpg__pad" />
    </view>

    <!-- 全屏看趋势（指标切换保留在里面） -->
    <view v-if="fullTrend" class="ftrend">
      <view class="ftrend__head">
        <text class="ftrend__title">{{ metricLabel }}</text>
        <text class="ftrend__close" @click="fullTrend = false">×</text>
      </view>
      <view class="ftrend__tabs">
        <view
          v-for="m in METRICS"
          :key="m.key"
          class="metab"
          :class="{ 'metab--active': metric === m.key }"
          @click="metric = m.key"
        >
          {{ m.label }}
        </view>
      </view>
      <view class="ftrend__chart">
        <FtTrendChart :points="trendPoints" :target="metricTarget(metric)" :height="fullHeight" />
      </view>
      <text v-if="metricTargetText" class="ftrend__target ft-num">目标 {{ metricTargetText }}</text>
      <text class="ftrend__tip">单指按住看数值 · 双指捏合缩放 · 双指拖动平移</text>
    </view>

    <MealSheet
      v-if="sheetMeal"
      :date="dateStr"
      :meal="sheetMeal"
      @close="sheetMeal = null"
      @changed="onChanged"
    />
  </view>
</template>

<style lang="scss" scoped>
.dietpg {
  min-height: 100vh;
}

.dietpg__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 12rpx;
}

.dietpg__back {
  font-size: 56rpx;
  line-height: 1;
  color: $app-ink;
  width: 64rpx;
}

.dietpg__title {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.dietpg__head-space {
  width: 64rpx;
}

.dietpg__body {
  padding: 0 32rpx;
}

.dietpg__pad {
  height: 80rpx;
}

/* ── tab ── */
.tabs {
  display: flex;
  gap: 40rpx;
  padding: 8rpx 36rpx 20rpx;
}

.tab {
  font-size: $text-md;
  color: $app-ink-subtle;
  padding-bottom: 10rpx;
}

.tab--active {
  color: $app-ink;
  font-weight: 600;
  border-bottom: 4rpx solid $app-ink;
}

/* ── 日期行 ── */
.datebar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
  padding: 8rpx 0 20rpx;
  position: relative;
}

.datebar__arrow {
  font-size: 44rpx;
  line-height: 1;
  color: $app-ink;
  padding: 0 16rpx;
}

.datebar__arrow--disabled {
  color: $app-line;
}

.datebar__label {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.datebar__today {
  position: absolute;
  right: 0;
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* ── 大卡 + 环 ── */
.kcalcard {
  padding: 30rpx 32rpx 26rpx;
  margin-bottom: 24rpx;
}

.kcalrow {
  display: flex;
  align-items: baseline;
  margin-bottom: 26rpx;
}

.kcalrow__num {
  font-size: $text-3xl;
  font-weight: 600;
  color: $app-ink;
  line-height: 1;
}

.kcalrow__unit {
  font-size: $text-md;
  color: $app-ink-muted;
  margin-left: 6rpx;
}

/* ── 餐次 ── */
.mealcard {
  padding: 8rpx 28rpx;
  margin-bottom: 24rpx;
}

.mealrow {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
}

.mealrow + .mealrow {
  border-top: 1rpx solid $app-line-soft;
}

.mealrow__label {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
  width: 120rpx;
  flex-shrink: 0;
}

.mealrow__photo {
  font-size: 20rpx;
  color: $app-ink-muted;
  border: 1rpx solid $app-line;
  border-radius: 8rpx;
  padding: 2rpx 10rpx;
  margin-right: 12rpx;
}

.mealrow__meta {
  flex: 1;
  text-align: right;
  font-size: $text-sm;
  color: $app-ink-muted;
  margin-right: 16rpx;
}

.mealrow__go {
  font-size: 40rpx;
  color: $app-ink-subtle;
  line-height: 1;
}

/* ── 饮水 ── */
.watercard {
  padding: 26rpx 28rpx;
}

.waterrow {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.waterrow__label {
  font-size: 22rpx;
  color: $app-ink-subtle;
}

.waterrow__num-row {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  padding-top: 6rpx;
}

.waterrow__num {
  font-size: $text-2xl;
  font-weight: 700;
  color: $app-ink;
  line-height: 1.1;
}

.waterrow__target {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.waterrow__right {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.waterrow__step {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

.waterrow__step-text {
  font-size: $text-sm;
  color: $app-ink-muted;
  border-bottom: 1rpx dashed $app-line;
  padding-bottom: 2rpx;
}

.waterrow__step-input {
  width: 96rpx;
  text-align: right;
  border-bottom: 2rpx solid $app-ink;
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.waterrow__step-unit {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.waterrow__btns {
  display: flex;
  gap: 16rpx;
}

.waterrow__btn {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  border: 2rpx solid $app-ink;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $text-xl;
  color: $app-ink;
  line-height: 1;
}

.waterrow__btn--solid {
  background: $app-ink;
  color: #ffffff;
}

/* ── 趋势 ── */
.metabs {
  display: flex;
  gap: 16rpx;
  padding: 4rpx 0 22rpx;
}

.metab {
  flex: 1;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
  border: 1rpx solid $app-line;
  border-radius: 40rpx;
  padding: 12rpx 0;
  background: $app-surface;
}

.metab--active {
  color: #ffffff;
  background: $app-ink;
  border-color: $app-ink;
  font-weight: 600;
}

.chartcard {
  padding: 28rpx 24rpx 20rpx;
}

.chartcard__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 8rpx 16rpx;
}

.chartcard__title {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.chartcard__right {
  display: flex;
  align-items: baseline;
  gap: 20rpx;
}

.chartcard__target {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.chartcard__full {
  font-size: $text-sm;
  color: $app-ink;
  border-bottom: 1rpx solid $app-ink;
  padding-bottom: 2rpx;
}

.chartcard__tip {
  display: block;
  padding: 10rpx 8rpx 0;
  font-size: 20rpx;
  color: $app-ink-subtle;
}

/* ── 全屏趋势 ── */
.ftrend {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: $app-canvas;
  z-index: 150;
  padding: calc(32rpx + env(safe-area-inset-top)) 32rpx calc(32rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.ftrend__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 20rpx;
}

.ftrend__title {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.ftrend__close {
  font-size: 52rpx;
  line-height: 1;
  color: $app-ink-muted;
  padding: 0 8rpx;
}

.ftrend__tabs {
  display: flex;
  gap: 16rpx;
  padding-bottom: 24rpx;
}

.ftrend__chart {
  flex: 1;
  min-height: 0;
}

.ftrend__target,
.ftrend__tip {
  display: block;
  text-align: center;
  padding-top: 16rpx;
  font-size: $text-xs;
  color: $app-ink-subtle;
}
</style>
