<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { onHide, onShow } from '@dcloudio/uni-app'
import FtTabBar from '@/components/FtTabBar.vue'
import FtDietRings from '@/components/FtDietRings.vue'
import MealSheet from '@/components/MealSheet.vue'
import { useScheduleStore } from '@/stores/schedule'
import { useDietStore } from '@/stores/diet'
import { addWater, getDay, type DayOverview, type MealKey } from '@/api/nutrition'
import { getWorkout, listWorkouts } from '@/api/workout'
import { buildDietRings } from '@/utils/diet'
import { formatClock, formatDateLabel, formatThousands } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import { clearSession, loadSession, netElapsedSec, type Session } from '@/utils/session'

const today = (() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getMonth() + 1}月${d.getDate()}日 · 周${week}`
})()

// 饮食记录（真数据：今日概览 → 大卡/四环/快捷；数字区点开「饮食」页）
const dietStore = useDietStore()
const dietDay = ref<DayOverview | null>(null)
const dietShortcuts = [
  { key: 'breakfast', label: '早餐' },
  { key: 'lunch', label: '午餐' },
  { key: 'dinner', label: '晚餐' },
  { key: 'snack', label: '加餐' },
  { key: 'water', label: '饮水' },
  { key: 'weight', label: '记体重' }
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
  uni.navigateTo({ url: '/pages/diet/index' })
}

function onDietShortcut(key: string) {
  if (key === 'water') {
    void bumpWater()
  } else if (key === 'weight') {
    uni.navigateTo({ url: '/pages/body/index?record=1' })
  } else {
    sheetMeal.value = key as MealKey
  }
}

async function bumpWater() {
  const step = dietStore.settings?.waterStepMl ?? 250
  try {
    const ml = await addWater(todayKey, step)
    if (dietDay.value) dietDay.value.water = { ...dietDay.value.water, ml }
    uni.showToast({ title: `饮水 +${step} ml`, icon: 'none', duration: 800 })
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
        return {
          name: b.name,
          value: lastSet ? `${lastSet.weight} kg × ${lastSet.reps}` : '—',
          pr: b.sets.some((s) => s.pr)
        }
      }),
      footer: `总容量 ${formatThousands(Math.round(summary.totalVolume))} kg · ${Math.max(
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

async function refreshCounts() {
  try {
    const list = await listWorkouts(200)
    const now = new Date()
    const monday = new Date(now)
    monday.setDate(now.getDate() - ((now.getDay() + 6) % 7))
    monday.setHours(0, 0, 0, 0)
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1)
    weekCount.value = list.filter((w) => new Date(w.createdAt) >= monday).length
    monthCount.value = list.filter((w) => new Date(w.createdAt) >= monthStart).length
  } catch {
    // request 层已统一提示
  }
}

// ── 进行中的训练会话（ADR 0020：返回＝最小化，首页在「开始训练」上方给回程入口）──
const activeSession = ref<Session | null>(null)
const nowMs = ref(Date.now())
let sessionTimer: ReturnType<typeof setInterval> | undefined

const sessionClock = computed(() =>
  activeSession.value ? formatClock(netElapsedSec(activeSession.value, nowMs.value)) : ''
)
const sessionDoneCount = computed(() =>
  (activeSession.value?.blocks ?? []).reduce((sum, b) => sum + b.sets.filter((s) => s.done).length, 0)
)

function refreshActiveSession() {
  activeSession.value = loadSession()
}

function startSessionClock() {
  if (sessionTimer || !activeSession.value) return
  sessionTimer = setInterval(() => {
    nowMs.value = Date.now()
  }, 1000)
}

function stopSessionClock() {
  if (sessionTimer) {
    clearInterval(sessionTimer)
    sessionTimer = undefined
  }
}

// ── 「开始训练」→ 先选计划（ADR 0016；周安排只决定谁置顶，不自动执行）──
const schedule = useScheduleStore()
const picker = ref(false)

onShow(() => {
  if (!ensureLogin()) return
  schedule.load()
  refreshLastWorkout()
  refreshCounts()
  void refreshDiet()
  refreshActiveSession()
  startSessionClock()
})

onHide(() => {
  stopSessionClock()
  refreshActiveSession()
})

onUnmounted(() => {
  stopSessionClock()
})

const bottomHint = computed(() => {
  if (schedule.todayPlan) return `今天 · ${schedule.todayPlan.name}`
  if (schedule.isRest) return '今天休息'
  return '今天没有安排'
})

/** 选择器里的计划顺序：今天的置顶，其余保持原序。 */
const plansOrdered = computed(() => {
  const todayId = schedule.todayPlan?.id
  const list = [...schedule.plans]
  if (todayId == null) return list
  const i = list.findIndex((p) => p.id === todayId)
  return i <= 0 ? list : [list[i], ...list.slice(0, i), ...list.slice(i + 1)]
})

function goLibrary() {
  uni.navigateTo({ url: '/pages/library/index' })
}

/**
 * 点「开始训练」：已经有进行中的会话就先问一次（ADR 0020）——
 * 会话不设过期，冲突只在这一刻（要开新的）才需要决定。
 */
function onStart() {
  const s = activeSession.value
  if (s) {
    uni.showModal({
      title: '上一次训练还没结束',
      content: `已记 ${sessionDoneCount.value} 组 · 已进行 ${sessionClock.value}。继续上一次，还是结束它并开新的？开新训练会让上一次未保存的组次丢失。`,
      confirmText: '继续上次',
      cancelText: '开新的',
      success: ({ confirm }) => {
        if (confirm) {
          resumeTraining()
          return
        }
        clearSession()
        activeSession.value = null
        stopSessionClock()
        void schedule.load()
        picker.value = true
      }
    })
    return
  }
  void schedule.load()
  picker.value = true
}

/** 回到进行中的会话页（不带参数＝接续本机存的那条会话）。 */
function resumeTraining() {
  uni.navigateTo({ url: '/pages/session/index' })
}

/** 新会话带 `fresh=1`：会话页据此放弃接续、重新开一场。 */
function startPlan(id: number) {
  picker.value = false
  uni.navigateTo({ url: `/pages/session/index?plan=${id}&fresh=1` })
}

function freeTrain() {
  picker.value = false
  uni.navigateTo({ url: '/pages/session/index?fresh=1' })
}

/** 没有计划时的出口：去「我的 → 训练计划」新建（改计划只走那里）。 */
function goPlans() {
  picker.value = false
  uni.navigateTo({ url: '/pages/plans/index' })
}
</script>

<template>
  <view class="home">
    <view class="home__body">
      <!-- 顶部行 -->
      <view class="home__head">
        <text class="home__date ft-num">{{ today }}</text>
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
          <FtDietRings :rings="dietRings" />
        </view>

        <view class="diet__shortcuts">
          <view
            v-for="s in dietShortcuts"
            :key="s.key"
            class="diet__shortcut"
            @click="onDietShortcut(s.key)"
          >
            <view class="diet__ico" :class="'diet__ico--' + s.key" />
            <text class="diet__shortcut-label">{{ s.label }}</text>
          </view>
        </view>
      </view>

      <!-- 上次训练（真数据） -->
      <view v-if="lastWorkout" class="ft-card last">
        <view class="last__head">
          <view class="last__head-left">
            <view class="last__bar" />
            <text class="last__title">{{ lastWorkout.title }}</text>
          </view>
          <text class="last__date ft-num">{{ lastWorkout.date }}</text>
        </view>
        <view v-for="(row, i) in lastWorkout.rows" :key="i" class="last__row">
          <view class="last__dot" />
          <text class="last__name">{{ row.name }}</text>
          <text class="last__value ft-num" :class="{ 'ft-marker': row.pr }">{{ row.value }}</text>
        </view>
        <view class="last__foot ft-num">{{ lastWorkout.footer }}</view>
      </view>

      <view v-else-if="lastLoaded" class="ft-card last last--empty">
        <view class="last__head">
          <view class="last__head-left">
            <view class="last__bar" />
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
          <view class="strip__divider" />
          <view class="strip__item">
            <text class="strip__label">本月</text>
            <text class="strip__num ft-num">{{ monthCount }}</text>
            <text class="strip__label">练</text>
          </view>
        </view>
      </view>

      <view class="home__pad" :class="{ 'home__pad--taller': activeSession }" />
    </view>

    <!-- 底部固定区：进行中的训练横幅 + 开始训练 + tab 栏 -->
    <view class="home__bottom">
      <text class="home__bottom-link">{{ bottomHint }}</text>

      <!-- 训练进行中：返回＝最小化后的回程入口（ADR 0020） -->
      <view v-if="activeSession" class="resume" @click="resumeTraining">
        <view class="resume__dot" />
        <view class="resume__main">
          <text class="resume__title">训练进行中</text>
          <text class="resume__meta ft-num">{{ sessionDoneCount }} 组 · {{ sessionClock }}</text>
        </view>
        <text class="resume__go">继续 ›</text>
      </view>

      <view class="ft-btn ft-btn--primary ft-btn--block" @click="onStart">开始训练</view>
      <FtTabBar active="home" />
    </view>

    <!-- 训练计划选择器（点「开始训练」弹出） -->
    <view v-if="picker" class="sheet-mask" @click="picker = false" />
    <view v-if="picker" class="sheet">
      <view class="sheet__handle" />
      <text class="sheet__title">选择训练计划</text>
      <text class="sheet__sub">{{ bottomHint }}</text>

      <scroll-view v-if="plansOrdered.length" scroll-y class="sheet__list">
        <view
          v-for="p in plansOrdered"
          :key="p.id"
          class="prow"
          :class="{ 'prow--today': p.id === schedule.todayPlan?.id }"
          @click="startPlan(p.id)"
        >
          <view class="prow__bar" />
          <view class="prow__main">
            <view class="prow__name-row">
              <text class="prow__name">{{ p.name }}</text>
              <text v-if="p.id === schedule.todayPlan?.id" class="prow__badge">今天 · 建议</text>
            </view>
            <text class="prow__meta ft-num">
              {{ p.exerciseCount }} 个动作{{
                p.lastTrainedAt ? ` · 上次 ${formatDateLabel(p.lastTrainedAt)}` : ''
              }}
            </text>
          </view>
          <text class="prow__go">›</text>
        </view>
      </scroll-view>

      <view v-else class="sheet__empty">
        <text class="sheet__empty-t">还没有训练计划</text>
        <text class="sheet__empty-s">在「我的 → 训练计划」里建一个，之后就能在这里选。</text>
      </view>

      <view v-if="!plansOrdered.length" class="ft-btn ft-btn--primary ft-btn--block" @click="goPlans">
        去新建计划
      </view>
      <view class="ft-btn ft-btn--ghost ft-btn--block sheet__second" @click="freeTrain">
        自由训练
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
  </view>
</template>

<style lang="scss" scoped>
.home__body {
  padding: 28rpx 32rpx 0;
}

/* ── 顶部行 ── */
.home__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 8rpx 0 24rpx;
}

.home__date {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
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
  height: 320rpx;
}

/* 有进行中的训练时，底部固定区多一条横幅 */
.home__pad--taller {
  height: 440rpx;
}

.home__bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: $app-canvas;
  padding: 16rpx 32rpx 0;
}

.home__bottom-link {
  display: block;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
  padding: 10rpx 0 16rpx;
}

/* ── 训练进行中横幅（在「开始训练」按钮正上方） ── */
.resume {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 16rpx;
  padding: 20rpx 24rpx;
  background: $app-surface;
  border: 1rpx solid $app-ink;
  border-left: 8rpx solid $app-highlight;
  border-radius: $radius-md;
}

.resume__dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: $app-ink;
  flex-shrink: 0;
}

.resume__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  min-width: 0;
}

.resume__title {
  font-size: $text-sm;
  font-weight: 600;
  color: $app-ink;
}

.resume__meta {
  font-size: 22rpx;
  color: $app-ink-muted;
}

.resume__go {
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink;
  flex-shrink: 0;
}

/* ── 训练计划选择器弹层 ── */
.sheet-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(23, 25, 29, 0.4);
  z-index: 90;
}

.sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: $app-surface;
  border-radius: 40rpx 40rpx 0 0;
  padding: 20rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
  z-index: 91;
}

.sheet__handle {
  width: 72rpx;
  height: 8rpx;
  border-radius: 4rpx;
  background: $app-line;
  margin: 0 auto 28rpx;
}

.sheet__title {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  color: $app-ink;
}

.sheet__sub {
  display: block;
  font-size: $text-base;
  color: $app-ink-muted;
  padding: 10rpx 0 32rpx;
}

.sheet__second {
  margin-top: 16rpx;
}

/* 计划列表：够长时内部滚动，弹层本身不顶出屏幕 */
.sheet__list {
  max-height: 560rpx;
  margin-bottom: 32rpx;
}

.prow {
  display: flex;
  align-items: center;
  padding: 24rpx 4rpx;
  border-top: 1rpx solid $app-line-soft;
}

.prow:first-child {
  border-top: none;
}

/* 名字左侧粗竖线标记（与计划列表页一致） */
.prow__bar {
  width: 10rpx;
  height: 36rpx;
  border-radius: 4rpx;
  background: $app-ink;
  margin-right: 16rpx;
  flex-shrink: 0;
}

.prow__main {
  flex: 1;
  min-width: 0;
}

.prow__name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.prow__name {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.prow__badge {
  font-size: $text-xs;
  color: $app-ink-muted;
  border: 1rpx solid $app-line;
  border-radius: $radius-sm;
  padding: 2rpx 10rpx;
  flex-shrink: 0;
}

.prow__meta {
  display: block;
  font-size: $text-sm;
  color: $app-ink-muted;
  padding-top: 6rpx;
}

.prow__go {
  font-size: $text-lg;
  color: $app-ink-subtle;
  padding-left: 12rpx;
}

.prow--today {
  background: $app-canvas;
  border-radius: $radius-md;
  padding-left: 16rpx;
  padding-right: 16rpx;
}

.sheet__empty {
  padding: 24rpx 4rpx 40rpx;
}

.sheet__empty-t {
  display: block;
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.sheet__empty-s {
  display: block;
  font-size: $text-sm;
  color: $app-ink-muted;
  padding-top: 10rpx;
}
</style>
