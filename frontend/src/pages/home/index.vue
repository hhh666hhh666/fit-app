<script setup lang="ts">
import {computed, ref} from 'vue'
import {onPullDownRefresh, onShow} from '@dcloudio/uni-app'
import FtIcon, { type FtIconName } from '@/components/FtIcon.vue'
import FtNutrientBars from '@/components/FtNutrientBars.vue'
import MealSheet from '@/components/MealSheet.vue'
import {useScheduleStore} from '@/stores/schedule'
import {useDietStore} from '@/stores/diet'
import {addWater, type DayOverview, getDay, type MealKey} from '@/api/nutrition'
import {getMonthSummary, getWorkout, listWorkouts} from '@/api/workout'
import {clearAll} from '@/api/cache'
import {buildNutrientBars} from '@/utils/diet'
import {formatDateLabel, formatThousands} from '@/utils/format'
import {ensureLogin} from '@/utils/guard'
import FtTabPage from '@/components/FtTabPage.vue'
import { useTabPage } from '@/utils/tab-page'

// 切页入场与底栏接线（ADR 0038／0040）：onShow 里登录守卫之后调 tab.enter()
const tab = useTabPage('home')

// 页头：大标题是日期，上标是「周几 · 今天的安排」
const todayDate = (() => {
  const d = new Date()
  return `${d.getMonth() + 1}月${d.getDate()}日`
})()
const todayWeek = `周${['日', '一', '二', '三', '四', '五', '六'][new Date().getDay()]}`

// 饮食记录（真数据：今日概览 → 大卡/四环/快捷；数字区点开「饮食」页）
const dietStore = useDietStore()
const dietDay = ref<DayOverview | null>(null)
// 六个入口各占一个色相：浅色底 ＋ 同色系图标（$meal-* token，ADR 0041）
const dietShortcuts: { key: string; label: string; icon: FtIconName }[] = [
  {key: 'breakfast', label: '早餐', icon: 'breakfast'},
  {key: 'lunch', label: '午餐', icon: 'lunch'},
  {key: 'dinner', label: '晚餐', icon: 'dinner'},
  {key: 'snack', label: '加餐', icon: 'snack'},
  {key: 'water', label: '饮水', icon: 'water'},
  {key: 'weight', label: '记体重', icon: 'weight'}
]
const sheetMeal = ref<MealKey | null>(null)
const todayKey = (() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})()

async function refreshDiet() {
  try {
    dietDay.value = await getDay(todayKey)
    void dietStore.load(false)
  } catch {
    // request 层已统一提示
  }
}

const dietKcal = computed(() => Math.round(Number(dietDay.value?.totals.kcal ?? 0)))
const dietTarget = computed(() => {
  const t = dietDay.value?.targets.kcal
  return t == null || Number(t) <= 0 ? null : Math.round(Number(t))
})
/** 卡头右侧：离目标还差多少／超了多少；没设目标就不说 */
const dietGapText = computed(() => {
  const t = dietTarget.value
  if (t == null) return ''
  const gap = t - dietKcal.value
  return gap >= 0 ? `还差 ${formatThousands(gap)} 大卡` : `超出 ${formatThousands(-gap)} 大卡`
})
const dietRatio = computed(() =>
  dietTarget.value == null ? 0 : Math.min(1, dietKcal.value / dietTarget.value)
)
const dietBars = computed(() => buildNutrientBars(dietDay.value))

function goDiet() {
  uni.navigateTo({url: '/pages/diet/index'})
}

function onDietShortcut(key: string) {
  if (key === 'water') {
    void bumpWater()
  } else if (key === 'weight') {
    uni.navigateTo({url: '/pages/body/index?record=1'})
  } else {
    sheetMeal.value = key as MealKey
  }
}

async function bumpWater() {
  const step = dietStore.settings?.waterStepMl ?? 250
  try {
    const ml = await addWater(todayKey, step)
    if (dietDay.value) dietDay.value.water = {...dietDay.value.water, ml}
    uni.showToast({title: `饮水 +${step} ml`, icon: 'none', duration: 800})
  } catch {
    // request 层已统一提示
  }
}

// ── 上次训练（真数据：最近一次训练 + 各动作最后一组；该动作本次刷新过 PR 则荧光）──
type LastRow = { name: string; value: string; pr: boolean }
const lastWorkout = ref<{
  title: string
  date: string
  rows: LastRow[]
  footer: string
} | null>(null)
const lastLoaded = ref(false)

/** 爬楼块的行值＝该块各组层数合计；一层都没登记（历史数据）时返回 null，页面显示「—」。 */
function totalFloorsOf(sets: { floors?: number | null }[]): number | null {
  let seen = false
  let sum = 0
  for (const s of sets) {
    if (s.floors != null) {
      seen = true
      sum += s.floors
    }
  }
  return seen ? sum : null
}

async function refreshLastWorkout() {
  try {
    const list = await listWorkouts(1)
    if (list.length === 0) {
      lastWorkout.value = null
      lastLoaded.value = true
      return
    }
    const summary = list[0]
    const detail = await getWorkout(summary.id)
    lastWorkout.value = {
      title: `上次训练 · ${summary.title}`,
      date: formatDateLabel(summary.createdAt),
      rows: detail.blocks.map((b) => {
        const lastSet = b.sets[b.sets.length - 1]
        // 爬楼（ADR 0025／0031）：一行给整块总层数——层数没有「最后一组有意义」这回事；
        // 力量动作仍读最后一组，那是看渐进超负荷的依据。
        const floors = b.metric === 'floors' ? totalFloorsOf(b.sets) : null
        return {
          name: b.name,
          value:
              b.metric === 'floors'
                  ? floors != null
                      ? `${formatThousands(floors)} 层`
                      : '—'
                  : lastSet
                      ? `${lastSet.weight} kg × ${lastSet.reps}`
                      : '—',
          pr: b.sets.some((s) => s.pr)
        }
      }),
      // 底部口径（2026-10-19 拍定）：消耗（手填，未填写「—」）＋ 时长，不再给总容量
      footer: `消耗 ${summary.calories != null ? `${summary.calories} 大卡` : '—'} · ${Math.max(
          1,
          Math.round(summary.durationSec / 60)
      )} 分钟`
    }
    lastLoaded.value = true
  } catch {
    // request 层已统一提示
  }
}

// ── 统计条（真数据：本周 / 本月训练次数）──
const weekCount = ref(0)
const monthCount = ref(0)

/**
 * 统计条：本周／本月次数走服务端聚合（`/workouts/month-summary` 不传月份＝当月）。
 * ⚠️ 别改回「拉 200 条在前端过滤」——记录超过 200 条后会静默少算（ADR 0023：累计类数字必须走全量聚合）。
 */
async function refreshCounts() {
  try {
    const summary = await getMonthSummary()
    weekCount.value = summary.weekCount
    monthCount.value = summary.count
  } catch {
    // request 层已统一提示
  }
}

// ── 今天的安排（页头一行小字；开练入口与选计划弹层在底栏中间圆钮里，ADR 0041）──
const schedule = useScheduleStore()

onShow(() => {
  if (!ensureLogin()) return
  tab.enter()
  schedule.load()
  refreshLastWorkout()
  refreshCounts()
  void refreshDiet()
})

/**
 * 下拉刷新＝「就要最新」的逃生门（ADR 0035）：先清掉数据缓存再整套重取。
 * 日常切页不会走这里——命中缓存时干脆不发请求。
 */
onPullDownRefresh(async () => {
  clearAll()
  schedule.load()
  refreshLastWorkout()
  refreshCounts()
  await refreshDiet()
  uni.stopPullDownRefresh()
})

const todayHint = computed(() => {
  if (schedule.todayPlan) return `今天 · ${schedule.todayPlan.name}`
  if (schedule.isRest) return '今天休息'
  return '今天没有安排'
})

function goLibrary() {
  uni.navigateTo({url: '/pages/library/index'})
}
</script>

<template>
  <FtTabPage :page="tab" class="home">
    <view class="home__body">
      <!-- 页头：上标＝周几 · 今天的安排，大标题＝日期；右侧胶囊进动作库 -->
      <view class="home__head">
        <view class="home__head-main">
          <text class="home__hint">{{ todayWeek }} · {{ todayHint }}</text>
          <text class="home__date">{{ todayDate }}</text>
        </view>
        <view class="home__pill" role="button" @click="goLibrary">动作库</view>
      </view>

      <!-- 今天吃了：数字区与条形点开「饮食」页，六个入口直接记 -->
      <view class="glass diet">
        <view class="diet__tap" @click="goDiet">
          <view class="diet__head">
            <text class="card-title">今天吃了</text>
            <text v-if="dietGapText" class="diet__gap ft-num">{{ dietGapText }}</text>
          </view>
          <view class="diet__kcal">
            <text class="diet__kcal-num ft-num">{{ formatThousands(dietKcal) }}</text>
            <text class="diet__kcal-unit ft-num">
              / {{ dietTarget == null ? '--' : formatThousands(dietTarget) }} 大卡
            </text>
          </view>
          <view class="diet__bar">
            <view class="diet__bar-fill" :style="{ width: Math.round(dietRatio * 100) + '%' }" />
          </view>
          <FtNutrientBars :bars="dietBars" class="diet__bars" />
        </view>

        <view class="diet__shortcuts">
          <view
            v-for="s in dietShortcuts"
            :key="s.key"
            class="diet__shortcut"
            role="button"
            @click="onDietShortcut(s.key)"
          >
            <view class="diet__ico" :class="'diet__ico--' + s.key">
              <FtIcon :name="s.icon" :size="22" />
            </view>
            <text class="diet__shortcut-label">{{ s.label }}</text>
          </view>
        </view>
      </view>

      <!-- 上次训练 -->
      <view v-if="lastWorkout" class="glass last">
        <view class="last__head">
          <text class="card-title">{{ lastWorkout.title }}</text>
          <text class="last__date ft-num">{{ lastWorkout.date }}</text>
        </view>
        <view v-for="(row, i) in lastWorkout.rows" :key="i" class="last__row">
          <text class="last__name">{{ row.name }}</text>
          <text v-if="row.pr" class="last__pr">PR</text>
          <text class="last__value ft-num">{{ row.value }}</text>
        </view>
        <view class="last__foot ft-num">{{ lastWorkout.footer }}</view>
      </view>

      <view v-else-if="lastLoaded" class="glass last">
        <text class="card-title">上次训练</text>
        <text class="last__none">还没有训练记录。点底栏中间的按钮，从今天开始。</text>
      </view>

      <!-- 本周 / 本月次数 -->
      <view class="strip">
        <view class="glass strip__item">
          <text class="strip__label">本周</text>
          <text class="strip__num ft-num">{{ weekCount }}</text>
          <text class="strip__label">练</text>
        </view>
        <view class="glass strip__item">
          <text class="strip__label">本月</text>
          <text class="strip__num ft-num">{{ monthCount }}</text>
          <text class="strip__label">练</text>
        </view>
      </view>
    </view>

    <!-- 饮食记录弹层 -->
    <MealSheet
        v-if="sheetMeal"
        :date="todayKey"
        :meal="sheetMeal"
        @close="sheetMeal = null"
        @changed="refreshDiet"
    />
  </FtTabPage>
</template>

<style lang="scss" scoped>
.home__body {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
  padding: calc(40rpx + env(safe-area-inset-top)) 40rpx 0;
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

/* ── 页头 ── */
.home__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16rpx;
  padding: 12rpx 8rpx 4rpx;
}

.home__head-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.home__hint {
  font-size: $text-base;
  color: $app-ink-muted;
}

.home__date {
  font-family: $font-num;
  font-size: $text-hero;
  font-weight: $weight-heavy;
  letter-spacing: -0.02em;
  line-height: 1.2;
  color: $app-ink;
}

.home__pill {
  @include glass;
  flex-shrink: 0;
  padding: 16rpx 28rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $app-ink-2;
}

.home__pill:active {
  opacity: 0.7;
}

/* ── 今天吃了 ── */
.diet {
  padding: 36rpx 36rpx 32rpx;
}

.diet__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.diet__gap {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.diet__kcal {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  margin-top: 12rpx;
}

.diet__kcal-num {
  font-size: $text-display;
  font-weight: $weight-heavy;
  letter-spacing: -0.03em;
  line-height: 1.05;
  color: $app-ink;
}

.diet__kcal-unit {
  font-size: $text-md;
  color: $app-ink-muted;
}

.diet__bar {
  height: 20rpx;
  margin-top: 20rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  overflow: hidden;
}

.diet__bar-fill {
  height: 100%;
  border-radius: $radius-pill;
  background: $ring-kcal;
}

.diet__bars {
  margin-top: 24rpx;
}

.diet__shortcuts {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  margin-top: 28rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid $app-line-soft;
}

.diet__shortcut {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.diet__shortcut:active .diet__ico {
  transform: scale(0.94);
}

.diet__ico {
  width: 88rpx;
  height: 88rpx;
  border-radius: $radius-md;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform $dur-fast $ease-out;
}

.diet__ico--breakfast {
  background: $meal-breakfast-bg;
  color: $meal-breakfast-fg;
}

.diet__ico--lunch {
  background: $meal-lunch-bg;
  color: $meal-lunch-fg;
}

.diet__ico--dinner {
  background: $meal-dinner-bg;
  color: $meal-dinner-fg;
}

.diet__ico--snack {
  background: $meal-snack-bg;
  color: $meal-snack-fg;
}

.diet__ico--water {
  background: $meal-water-bg;
  color: $meal-water-fg;
}

.diet__ico--weight {
  background: $meal-weight-bg;
  color: $meal-weight-fg;
}

.diet__shortcut-label {
  font-size: $text-sm;
  color: $app-ink-2;
}

/* ── 上次训练 ── */
.last {
  padding: 32rpx 36rpx 28rpx;
}

.last__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16rpx;
  padding-bottom: 8rpx;
}

.last__date {
  flex-shrink: 0;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.last__row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 18rpx 0;
}

.last__row + .last__row {
  border-top: 1rpx solid $app-line-soft;
}

.last__name {
  flex: 1;
  min-width: 0;
  font-size: $text-md;
  color: $app-ink;
}

/* PR 是「动作的结果」，归橙色：浅橙底 ＋ 深橙字 */
.last__pr {
  padding: 2rpx 16rpx;
  border-radius: $radius-pill;
  background: $accent-soft;
  font-size: $text-xs;
  font-weight: $weight-bold;
  color: $accent-strong;
}

.last__value {
  font-size: $text-md;
  color: $app-ink;
}

.last__foot {
  padding-top: 8rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.last__none {
  display: block;
  padding-top: 12rpx;
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

/* ── 本周 / 本月 ── */
.strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20rpx;
}

.strip__item {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  padding: 24rpx 32rpx;
}

.strip__label {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.strip__num {
  font-size: 52rpx;
  font-weight: $weight-heavy;
  color: $app-ink;
}

@media (prefers-reduced-motion: reduce) {
  .diet__ico {
    transition: none;
  }
}
</style>
