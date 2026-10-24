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
import FtButton from '@/components/FtButton.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtNutrientBars from '@/components/FtNutrientBars.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import FtSheet from '@/components/FtSheet.vue'
import FtTrendChart from '@/components/FtTrendChart.vue'
import MealSheet from '@/components/MealSheet.vue'
import { useDietStore } from '@/stores/diet'
import { buildNutrientBars } from '@/utils/diet'
import { formatThousands } from '@/utils/format'
import { activityLabel, gapText, sexLabel } from '@/utils/energy'
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
const TABS: { key: 'log' | 'trend'; label: string }[] = [
  { key: 'log', label: '记录' },
  { key: 'trend', label: '趋势' }
]
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

/** 每日总消耗与缺口（ADR 0029）：口径在服务端，这里只负责摆与念。 */
const energy = computed(() => overview.value?.energy ?? null)
const gapLabel = computed(() => gapText(overview.value?.gap))

function openEnergySheet() {
  energySheet.value.show = true
}

/** 档案缺项时给出口：去身体档案页补全（ADR 0029 的降级口径）。 */
function goProfile() {
  uni.navigateTo({ url: '/pages/body/index' })
}

const energySheet = ref({ show: false })

const bars = computed(() => buildNutrientBars(overview.value))
/** 大卡进度条（没设目标就只画轨道）。 */
const kcalRatio = computed(() =>
  kcalTarget.value == null || kcalTarget.value <= 0 ? 0 : Math.min(1, kcalNow.value / kcalTarget.value)
)
/** 卡头右侧：离目标还差多少／超了多少；没设目标就不说（与首页同一口径）。 */
const kcalGapText = computed(() => {
  const t = kcalTarget.value
  if (t == null || t <= 0) return ''
  const gap = t - kcalNow.value
  return gap >= 0 ? `还差 ${formatThousands(gap)} 大卡` : `超出 ${formatThousands(-gap)} 大卡`
})

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

</script>

<template>
  <view class="dietpg">
    <view class="dietpg__body">
      <!-- 页头：返回 ＋ 标题，右侧「记录／趋势」分段（导航不用橙，选中＝深字 ＋ 浅胶囊底） -->
      <FtPageHeader title="饮食" back>
        <template #right>
          <view class="seg" role="tablist">
            <view
              v-for="t in TABS"
              :key="t.key"
              class="seg__item"
              :class="{ 'seg__item--on': tab === t.key }"
              role="tab"
              :aria-selected="tab === t.key"
              @click="tab = t.key"
            >
              {{ t.label }}
            </view>
          </view>
        </template>
      </FtPageHeader>

      <!-- ── 记录 ── -->
      <template v-if="tab === 'log'">
        <view class="datebar">
          <view class="datebar__pill">
            <view class="datebar__arrow" role="button" aria-label="前一天" @click="shiftDate(-1)">
              <FtIcon name="chevron-left" :size="18" :stroke="2" />
            </view>
            <text class="datebar__label ft-num">{{ dateLabel }}</text>
            <view
              class="datebar__arrow"
              :class="{ 'datebar__arrow--off': isToday }"
              role="button"
              aria-label="后一天"
              @click="shiftDate(1)"
            >
              <FtIcon name="chevron-right" :size="18" :stroke="2" />
            </view>
          </view>
          <text v-if="!isToday" class="datebar__today" @click="goToday">回今天</text>
        </view>

        <!-- 大卡 ＋ 消耗与缺口 ＋ 营养素条形 -->
        <view class="glass kcal">
          <view class="kcal__head">
            <text class="card-title">{{ isToday ? '今天吃了' : '这天吃了' }}</text>
            <text v-if="kcalGapText" class="kcal__gap ft-num">{{ kcalGapText }}</text>
          </view>
          <view class="kcal__row">
            <text class="kcal__num ft-num">{{ formatThousands(kcalNow) }}</text>
            <text class="kcal__unit ft-num">
              / {{ kcalTarget == null ? '--' : formatThousands(kcalTarget) }} 大卡
            </text>
          </view>
          <view class="kcal__bar">
            <view class="kcal__bar-fill" :style="{ width: Math.round(kcalRatio * 100) + '%' }" />
          </view>

          <!-- 每日总消耗与热量缺口（ADR 0029）：目标那一行不动，新信息另起一行 -->
          <view v-if="energy?.complete" class="burn">
            <text class="burn__t ft-num">今日消耗 {{ energy.totalKcal }}</text>
            <text class="burn__sep">·</text>
            <text class="burn__gap ft-num">{{ gapLabel }}</text>
            <text class="burn__how" role="button" @click="openEnergySheet">怎么算的</text>
          </view>
          <view v-else-if="energy" class="burn burn--miss" role="button" @click="goProfile">
            <text class="burn__t">档案没填完，暂时算不出今日消耗</text>
            <text class="burn__how">去补全</text>
          </view>

          <FtNutrientBars :bars="bars" class="kcal__bars" />
        </view>

        <!-- 四餐 -->
        <view class="glass meals">
          <view v-for="r in mealRows" :key="r.key" class="meal" role="button" @click="sheetMeal = r.key">
            <view class="meal__ico" :class="'meal__ico--' + r.key">
              <FtIcon :name="r.key" :size="20" />
            </view>
            <text class="meal__label">{{ r.label }}</text>
            <text v-if="r.hasPhoto" class="meal__photo">照片</text>
            <text class="meal__meta ft-num" :class="{ 'meal__meta--none': !r.count }">
              {{ r.count ? `${r.count} 项 · ${r.kcal} 大卡` : '未记录' }}
            </text>
            <FtIcon name="chevron-right" :size="18" tone="subtle" />
          </view>
        </view>

        <!-- 饮水：点一下加／减一个步进；点步进数字就地改 -->
        <view class="glass water">
          <view class="water__ico">
            <FtIcon name="water" :size="20" />
          </view>
          <view class="water__main">
            <text class="water__label">饮水</text>
            <view class="water__num-row">
              <text class="water__num ft-num">{{ waterMl }}</text>
              <text class="water__target ft-num">/ {{ waterTarget == null ? '--' : waterTarget }} ml</text>
            </view>
            <view class="water__step">
              <template v-if="stepEditing">
                <text class="water__step-label">每次</text>
                <input
                  v-model="stepInput"
                  type="number"
                  class="water__step-input ft-num"
                  @blur="saveStep"
                  @confirm="saveStep"
                />
                <text class="water__step-label">ml</text>
              </template>
              <text v-else class="water__step-text ft-num" @click="startStepEdit">每次 {{ stepMl }} ml</text>
            </view>
          </view>
          <view class="water__btns">
            <view class="water__btn" role="button" aria-label="减一次" @click="bumpWater(-1)">
              <text class="water__minus">−</text>
            </view>
            <view class="water__btn water__btn--add" role="button" aria-label="加一次" @click="bumpWater(1)">
              <FtIcon name="plus" :size="22" :stroke="2.2" />
            </view>
          </view>
        </view>
      </template>

      <!-- ── 趋势 ── -->
      <template v-else>
        <view class="seg seg--wide" role="tablist">
          <view
            v-for="m in METRICS"
            :key="m.key"
            class="seg__item"
            :class="{ 'seg__item--on': metric === m.key }"
            role="tab"
            :aria-selected="metric === m.key"
            @click="metric = m.key"
          >
            {{ m.label }}
          </view>
        </view>

        <view class="glass chart">
          <view class="chart__head">
            <text class="card-title">{{ metricLabel }}</text>
            <view class="chart__right">
              <text v-if="metricTargetText" class="chart__target ft-num">目标 {{ metricTargetText }}</text>
              <view class="chart__full" role="button" @click="fullTrend = true">全屏</view>
            </view>
          </view>
          <FtTrendChart :points="trendPoints" :target="metricTarget(metric)" :height="180" />
          <text class="chart__tip">单指按住看数值 · 双指捏合缩放 · 双指拖动平移</text>
        </view>
      </template>
    </view>

    <!-- 全屏看趋势（指标切换保留在里面） -->
    <view v-if="fullTrend" class="ftrend">
      <view class="ftrend__head">
        <text class="ftrend__title">{{ metricLabel }}</text>
        <view class="ftrend__close" role="button" aria-label="退出全屏" @click="fullTrend = false">
          <FtIcon name="close" :size="20" :stroke="2" />
        </view>
      </view>
      <view class="seg seg--wide" role="tablist">
        <view
          v-for="m in METRICS"
          :key="m.key"
          class="seg__item"
          :class="{ 'seg__item--on': metric === m.key }"
          role="tab"
          :aria-selected="metric === m.key"
          @click="metric = m.key"
        >
          {{ m.label }}
        </view>
      </view>
      <view class="ftrend__chart">
        <FtTrendChart :points="trendPoints" :target="metricTarget(metric)" :height="fullHeight" />
      </view>
      <text v-if="metricTargetText" class="ftrend__note ft-num">目标 {{ metricTargetText }}</text>
      <text class="ftrend__note">单指按住看数值 · 双指捏合缩放 · 双指拖动平移</text>
    </view>

    <!-- 「怎么算的」弹层：算式与代入值全部来自服务端（ADR 0029） -->
    <FtSheet v-if="energySheet.show" title="今日消耗怎么算的" @close="energySheet.show = false">
      <scroll-view scroll-y class="esheet">
        <template v-if="energy?.complete">
          <view class="eq">
            <view class="eq__row">
              <text class="eq__k">基础代谢</text>
              <text class="eq__v ft-num">{{ energy.bmr }} 大卡</text>
            </view>
            <text class="eq__note">
              按 Mifflin-St Jeor：体重 {{ energy.weightKg }} kg · 身高 {{ energy.heightCm }} cm · 年龄
              {{ energy.age }} · {{ sexLabel(energy.sex) }}
            </text>
            <view class="eq__row">
              <text class="eq__k">× 生活活动系数</text>
              <text class="eq__v ft-num"
                >{{ energy.activityFactor }}（{{ activityLabel(energy.activityLevel) }}）</text
              >
            </view>
            <view class="eq__row">
              <text class="eq__k">= 基础消耗</text>
              <text class="eq__v ft-num">{{ energy.baseKcal }} 大卡</text>
            </view>
            <view class="eq__row">
              <text class="eq__k">+ 今日训练消耗</text>
              <text class="eq__v ft-num">{{ energy.trainingKcal }} 大卡</text>
            </view>
            <view class="eq__row eq__row--sum">
              <text class="eq__k">= 今日总消耗</text>
              <text class="eq__v ft-num">{{ energy.totalKcal }} 大卡</text>
            </view>
            <view class="eq__row">
              <text class="eq__k">− 今日摄入</text>
              <text class="eq__v ft-num">{{ kcalNow }} 大卡</text>
            </view>
            <view class="eq__row eq__row--sum">
              <text class="eq__k">= {{ gapLabel }}</text>
              <text class="eq__v ft-num">{{ overview?.gap ?? '–' }} 大卡</text>
            </view>
          </view>
          <text class="eq__hint">
            生活活动系数只算日常活动（工作、走动），专门训练不在里面——训练消耗是单独加上去的，不会重复计算。
          </text>
          <text class="eq__hint">
            目标热量仍是自己设的那个数（设置页），跟这里的消耗是两回事：目标是想达成的量，缺口是今天实际的状态。
          </text>
        </template>
      </scroll-view>
      <FtButton block class="esheet__ok" @click="energySheet.show = false">知道了</FtButton>
    </FtSheet>

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
.dietpg__body {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
  padding: 28rpx 40rpx calc(64rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-lg;
  box-shadow: $glass-shadow;
  padding: 32rpx 36rpx;
}

.card-title {
  font-size: $text-title;
  font-weight: $weight-bold;
  color: $app-ink;
}

/* ── 分段（页头的记录／趋势、趋势的五指标共用） ── */
.seg {
  @include glass;
  display: flex;
  padding: 6rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
}

.seg__item {
  padding: 12rpx 28rpx;
  border-radius: $radius-pill;
  text-align: center;
  font-size: $text-base;
  color: $app-ink-muted;
  transition: background $dur-fast $ease-out, color $dur-fast $ease-out;
}

.seg__item--on {
  background: $app-surface-2;
  color: $app-ink;
  font-weight: $weight-bold;
}

.seg--wide .seg__item {
  flex: 1;
  padding-left: 0;
  padding-right: 0;
}

/* ── 日期 ── */
.datebar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.datebar__pill {
  @include glass;
  display: flex;
  align-items: center;
  padding: 4rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
}

.datebar__arrow {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $app-ink-2;
}

.datebar__arrow:active {
  background: $app-surface-2;
}

.datebar__arrow--off {
  opacity: 0.25;
}

.datebar__label {
  padding: 0 12rpx;
  font-size: $text-base;
  color: $app-ink;
}

.datebar__today {
  position: absolute;
  right: 8rpx;
  font-size: $text-base;
  color: $app-ink-2;
}

/* ── 大卡 ── */
.kcal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kcal__gap {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.kcal__row {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  margin-top: 12rpx;
}

.kcal__num {
  font-size: $text-display;
  font-weight: $weight-heavy;
  letter-spacing: -0.03em;
  line-height: 1.05;
  color: $app-ink;
}

.kcal__unit {
  font-size: $text-md;
  color: $app-ink-muted;
}

.kcal__bar {
  height: 20rpx;
  margin-top: 20rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  overflow: hidden;
}

.kcal__bar-fill {
  height: 100%;
  border-radius: $radius-pill;
  background: $ring-kcal;
}

.kcal__bars {
  margin-top: 24rpx;
}

/* 今日消耗 / 缺口（ADR 0029） */
.burn {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  margin-top: 20rpx;
}

.burn__t {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.burn__sep {
  font-size: $text-sm;
  color: $app-ink-subtle;
}

.burn__gap {
  font-size: $text-sm;
  color: $app-ink;
}

.burn__how {
  margin-left: auto;
  font-size: $text-sm;
  color: $app-ink-2;
  text-decoration: underline;
  text-underline-offset: 4rpx;
}

.burn--miss {
  padding: 16rpx 20rpx;
  border-radius: $radius-sm;
  border: 2rpx dashed $app-line-dash;
}

/* ── 四餐 ── */
.meals {
  padding: 8rpx 28rpx;
}

.meal {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 0;
}

.meal + .meal {
  border-top: 1rpx solid $app-line-soft;
}

.meal:active {
  opacity: 0.6;
}

.meal__ico,
.water__ico {
  width: 72rpx;
  height: 72rpx;
  flex-shrink: 0;
  border-radius: $radius-sm;
  display: flex;
  align-items: center;
  justify-content: center;
}

.meal__ico--breakfast {
  background: $meal-breakfast-bg;
  color: $meal-breakfast-fg;
}

.meal__ico--lunch {
  background: $meal-lunch-bg;
  color: $meal-lunch-fg;
}

.meal__ico--dinner {
  background: $meal-dinner-bg;
  color: $meal-dinner-fg;
}

.meal__ico--snack {
  background: $meal-snack-bg;
  color: $meal-snack-fg;
}

.meal__label {
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.meal__photo {
  padding: 2rpx 12rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.meal__meta {
  flex: 1;
  text-align: right;
  font-size: $text-sm;
  color: $app-ink;
}

.meal__meta--none {
  color: $app-ink-muted;
}

/* ── 饮水 ── */
.water {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.water__ico {
  align-self: flex-start;
  background: $meal-water-bg;
  color: $meal-water-fg;
}

.water__main {
  flex: 1;
  min-width: 0;
}

.water__label {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.water__num-row {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.water__num {
  font-size: $text-2xl;
  font-weight: $weight-heavy;
  line-height: 1.2;
  color: $app-ink;
}

.water__target {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.water__step {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 6rpx;
}

.water__step-text {
  font-size: $text-sm;
  color: $app-ink-2;
  text-decoration: underline dashed;
  text-underline-offset: 6rpx;
}

.water__step-label {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.water__step-input {
  width: 112rpx;
  height: 56rpx;
  padding: 0 12rpx;
  box-sizing: border-box;
  border-radius: $radius-xs;
  background: $app-surface;
  box-shadow: inset 0 0 0 2rpx $app-line;
  text-align: right;
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.water__btns {
  flex-shrink: 0;
  display: flex;
  gap: 16rpx;
}

/* 饮水按钮用饮水入口的色系：加一杯是记录，不是「开始训练」那类主动作，不占橙色 */
.water__btn {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: $app-surface-2;
  color: $app-ink-2;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform $dur-fast $ease-out;
}

.water__btn:active {
  transform: scale(0.94);
}

.water__btn--add {
  background: $meal-water-bg;
  color: $meal-water-fg;
}

.water__minus {
  font-size: $text-2xl;
  line-height: 1;
}

/* ── 趋势 ── */
.chart {
  padding: 28rpx 28rpx 20rpx;
}

.chart__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8rpx 16rpx;
}

.chart__right {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.chart__target {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.chart__full {
  padding: 8rpx 24rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-sm;
  color: $app-ink-2;
}

.chart__tip {
  display: block;
  padding: 12rpx 8rpx 0;
  font-size: $text-xs;
  color: $app-ink-muted;
}

/* ── 全屏趋势 ── */
.ftrend {
  position: fixed;
  inset: 0;
  z-index: 150;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: calc(32rpx + env(safe-area-inset-top)) 40rpx calc(32rpx + env(safe-area-inset-bottom));
  background: $app-canvas-bg;
  background-color: $app-canvas;
}

.ftrend__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ftrend__title {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $app-ink;
}

.ftrend__close {
  @include glass($glass-bg-strong);
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  border: 1rpx solid $glass-edge;
  box-shadow: $shadow-sm;
  color: $app-ink-2;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ftrend__chart {
  flex: 1;
  min-height: 0;
}

.ftrend__note {
  display: block;
  text-align: center;
  font-size: $text-xs;
  color: $app-ink-muted;
}

/* ── 「怎么算的」弹层 ── */
.esheet {
  max-height: 56vh;
  margin-top: 20rpx;
}

.eq {
  padding: 8rpx 28rpx;
  background: $app-surface;
  border-radius: $radius-lg;
}

.eq__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 16rpx 0;
}

.eq__row + .eq__row,
.eq__note + .eq__row {
  border-top: 1rpx solid $app-line-soft;
}

/* 「＝」那一行是小计：上面压一条深线 */
.eq__row.eq__row--sum {
  border-top: 2rpx solid $app-ink-2;
}

.eq__k {
  font-size: $text-base;
  color: $app-ink-muted;
}

.eq__v {
  font-size: $text-md;
  color: $app-ink;
}

.eq__note {
  display: block;
  padding-bottom: 12rpx;
  font-size: $text-xs;
  line-height: 1.6;
  color: $app-ink-muted;
}

.eq__hint {
  display: block;
  padding-top: 16rpx;
  font-size: $text-sm;
  line-height: 1.7;
  color: $app-ink-muted;
}

.dietpg .esheet__ok {
  flex-shrink: 0;
  margin-top: 28rpx;
}

@media (prefers-reduced-motion: reduce) {
  .seg__item,
  .water__btn {
    transition: none;
  }
}
</style>
