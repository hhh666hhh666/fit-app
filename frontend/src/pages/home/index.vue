<script setup lang="ts">
import {computed, ref} from 'vue'
import {onPullDownRefresh, onShow} from '@dcloudio/uni-app'
import FtTabBar from '@/components/FtTabBar.vue'
import FtDietRings from '@/components/FtDietRings.vue'
import MealSheet from '@/components/MealSheet.vue'
import {useScheduleStore} from '@/stores/schedule'
import {useDietStore} from '@/stores/diet'
import {addWater, type DayOverview, getDay, type MealKey} from '@/api/nutrition'
import {getMonthSummary, getWorkout, listWorkouts} from '@/api/workout'
import {clearAll} from '@/api/cache'
import {buildDietRings} from '@/utils/diet'
import {formatDateLabel, formatThousands} from '@/utils/format'
import {ensureLogin} from '@/utils/guard'
import {playTabEnter, takeTabEnter} from '@/utils/nav'

// 切页入场（ADR 0038）：页面现在被保活、不再重建，所以只能每次 onShow 按方向重放一遍。
const tabDir = ref<'left' | 'right' | null>(null)
const tabSeq = ref(0)

const today = (() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getMonth() + 1}月${d.getDate()}日 · 周${week}`
})()

// 饮食记录（真数据：今日概览 → 大卡/四环/快捷；数字区点开「饮食」页）
const dietStore = useDietStore()
const dietDay = ref<DayOverview | null>(null)
const dietShortcuts = [
  {key: 'breakfast', label: '早餐'},
  {key: 'lunch', label: '午餐'},
  {key: 'dinner', label: '晚餐'},
  {key: 'snack', label: '加餐'},
  {key: 'water', label: '饮水'},
  {key: 'weight', label: '记体重'}
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
const dietTargetText = computed(() => {
  const t = dietDay.value?.targets.kcal
  return t == null ? '目标 --' : `目标 ${Math.round(Number(t))}`
})
const dietRings = computed(() => buildDietRings(dietDay.value))

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
  const dir = takeTabEnter('home')
  tabDir.value = dir
  tabSeq.value++
  playTabEnter('.home__body', dir)
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
  <view class="home">
    <view class="home__body">
      <!-- 顶部行 -->
      <view class="home__head">
        <view class="home__head-main">
          <text class="home__date ft-num">{{ today }}</text>
          <text class="home__hint">{{ todayHint }}</text>
        </view>
        <text class="home__head-link" @click="goLibrary">动作库</text>
      </view>

      <!-- 饮食记录卡（真数据；数字区/四环点开「饮食」页，快捷直接记账） -->
      <view class="ft-card diet">
        <view class="diet__head">
          <text class="diet__title">饮食记录</text>
        </view>

        <view class="diet__tap" @click="goDiet">
          <view class="diet__kcal">
            <text class="diet__kcal-num ft-num">{{ dietKcal }}</text>
            <text class="diet__kcal-unit"> / {{ dietTargetText }} 大卡</text>
          </view>
          <FtDietRings :rings="dietRings"/>
        </view>

        <view class="diet__shortcuts">
          <view
              v-for="s in dietShortcuts"
              :key="s.key"
              class="diet__shortcut"
              @click="onDietShortcut(s.key)"
          >
            <view class="diet__ico" :class="'diet__ico--' + s.key"/>
            <text class="diet__shortcut-label">{{ s.label }}</text>
          </view>
        </view>
      </view>

      <!-- 上次训练（真数据） -->
      <view v-if="lastWorkout" class="ft-card last">
        <view class="last__head">
          <view class="last__head-left">
            <view class="last__bar"/>
            <text class="last__title">{{ lastWorkout.title }}</text>
          </view>
          <text class="last__date ft-num">{{ lastWorkout.date }}</text>
        </view>
        <view v-for="(row, i) in lastWorkout.rows" :key="i" class="last__row">
          <view class="last__dot"/>
          <text class="last__name">{{ row.name }}</text>
          <text class="last__value ft-num" :class="{ 'ft-marker': row.pr }">{{ row.value }}</text>
        </view>
        <view class="last__foot ft-num">{{ lastWorkout.footer }}</view>
      </view>

      <view v-else-if="lastLoaded" class="ft-card last last--empty">
        <view class="last__head">
          <view class="last__head-left">
            <view class="last__bar"/>
            <text class="last__title">上次训练</text>
          </view>
        </view>
        <text class="last__none">还没有训练记录，从今天开始。</text>
      </view>

      <!-- 统计条（白框卡片） -->
      <view class="strip-card">
        <view class="strip">
          <view class="strip__item">
            <text class="strip__label">本周</text>
            <text class="strip__num ft-num">{{ weekCount }}</text>
            <text class="strip__label">练</text>
          </view>
          <view class="strip__divider"/>
          <view class="strip__item">
            <text class="strip__label">本月</text>
            <text class="strip__num ft-num">{{ monthCount }}</text>
            <text class="strip__label">练</text>
          </view>
        </view>
      </view>

      <view class="home__pad"/>
    </view>

    <!-- 底栏：开练入口（中间圆钮）、选计划弹层、进行中的回程都在里面（ADR 0041） -->
    <view class="home__bottom">
      <FtTabBar active="home" :enter-dir="tabDir" :enter-seq="tabSeq"/>
    </view>

    <!-- 饮食记录弹层 -->
    <MealSheet
        v-if="sheetMeal"
        :date="todayKey"
        :meal="sheetMeal"
        @close="sheetMeal = null"
        @changed="refreshDiet"
    />
  </view>
</template>

<style lang="scss" scoped>
.home__body {
  padding: 28rpx 32rpx 0;
}

/* ── 顶部行 ── */
.home__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 8rpx 0 24rpx;
}

.home__date {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.home__head-main {
  display: flex;
  flex-direction: column;
}

.home__hint {
  font-size: $text-base;
  color: $app-ink-muted;
}

.home__head-link {
  font-size: $text-base;
  color: $app-ink-muted;
}

/* ── 饮食记录卡 ── */
.diet__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.diet__title {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.diet__kcal {
  display: flex;
  align-items: baseline;
  margin: 18rpx 0 26rpx;
}

.diet__kcal-num {
  font-size: $text-3xl;
  font-weight: 600;
  color: $app-ink;
  line-height: 1;
}

.diet__kcal-unit {
  font-size: $text-md;
  color: $app-ink-muted;
}

.diet__tap {
  margin-bottom: 26rpx;
}

.diet__shortcuts {
  display: flex;
  justify-content: space-between;
}

.diet__shortcut {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  flex: 1;
}

.diet__shortcut-label {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.diet__ico {
  width: 40rpx;
  height: 40rpx;
  background-repeat: no-repeat;
  background-size: contain;
  background-position: center;
}

/* 六个快捷图标：自绘线条（stroke 用 muted 色），风格与设计稿一致 */
.diet__ico--breakfast {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23666b73' stroke-width='1.7' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M4 9h12v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5V9z'/%3E%3Cpath d='M16 10h1.5a2.3 2.3 0 0 1 0 4.6H16'/%3E%3Cpath d='M7.5 3v2.4M11.5 3v2.4'/%3E%3C/svg%3E");
}

.diet__ico--lunch {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23666b73' stroke-width='1.7' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M4 11h16a8 8 0 0 1-16 0z'/%3E%3Cpath d='M9 4.5v3M15 4.5v3'/%3E%3C/svg%3E");
}

.diet__ico--dinner {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23666b73' stroke-width='1.7' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M20 14.5A8.5 8.5 0 1 1 9.5 4a7 7 0 0 0 10.5 10.5z'/%3E%3C/svg%3E");
}

.diet__ico--snack {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23666b73' stroke-width='1.7' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M6.5 12.5h11l-1.2 7.5H7.7z'/%3E%3Cpath d='M9.5 12.5c0-2.2 1.1-3.4 2.5-3.4s2.5 1.2 2.5 3.4'/%3E%3Cpath d='M12 9V6.6'/%3E%3Ccircle cx='12' cy='5.4' r='1.2'/%3E%3C/svg%3E");
}

.diet__ico--water {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23666b73' stroke-width='1.7' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M12 4c3 4 6 7 6 10a6 6 0 0 1-12 0c0-3 3-6 6-10z'/%3E%3C/svg%3E");
}

.diet__ico--weight {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23666b73' stroke-width='1.7' stroke-linecap='round' stroke-linejoin='round'%3E%3Crect x='4' y='4' width='16' height='16' rx='4.5'/%3E%3Cpath d='M12 12.2l2.6-3.1'/%3E%3C/svg%3E");
}

/* ── 上次训练卡 ── */
.last__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 18rpx;
}

.last__head-left {
  display: flex;
  align-items: center;
}

/* 标题左侧的粗竖条标记（对齐设计稿） */
.last__bar {
  width: 10rpx;
  height: 34rpx;
  border-radius: 4rpx;
  background: $app-ink;
  margin-right: 14rpx;
  flex-shrink: 0;
}

.last__title {
  font-size: $text-base;
  font-weight: 600;
  color: $app-ink;
}

.last__date {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.last__row {
  display: flex;
  align-items: center;
  padding: 22rpx 0;
  border-top: 1rpx solid $app-line-soft;
}

.last__dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: $app-ink;
  margin-right: 16rpx;
  flex-shrink: 0;
}

.last__name {
  font-size: $text-md;
  color: $app-ink;
}

.last__value {
  margin-left: auto;
  font-size: $text-md;
  font-weight: 500;
  color: $app-ink;
}

.last--empty {
  padding-bottom: 32rpx;
}

.last__none {
  display: block;
  padding-top: 8rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.last__foot {
  padding-top: 18rpx;
  border-top: 1rpx solid $app-line-soft;
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* ── 统计条（白框卡片）── */
.strip-card {
  background: $app-surface;
  border: 1rpx solid $app-line-soft;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  padding: 24rpx 28rpx;
  margin-bottom: 24rpx;
}

.strip {
  display: flex;
  align-items: center;
}

.strip__item {
  flex: 1;
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 8rpx;
  font-size: $text-base;
  color: $app-ink-muted;
}

.strip__num {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.strip__divider {
  width: 1rpx;
  height: 32rpx;
  background: $app-line;
}

/* ── 底部固定区 ── */
.home__pad {
  height: $tabbar-space;
}

.home__bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  /* 高于切页克隆层（.tab-slide-layer 是 10）：转场期间底栏钉住、不被遮 */
  z-index: 20;
  /* 底栏是悬浮胶囊，四周留白处要让点击穿透到下面的内容（胶囊和圆钮自己接点击） */
  pointer-events: none;
}
</style>
