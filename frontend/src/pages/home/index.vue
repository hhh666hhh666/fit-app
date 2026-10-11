<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import FtTabBar from '@/components/FtTabBar.vue'
import { useScheduleStore } from '@/stores/schedule'
import { getWorkout, listWorkouts } from '@/api/workout'
import { formatDateLabel, formatThousands } from '@/utils/format'

const today = (() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getMonth() + 1}月${d.getDate()}日 · 周${week}`
})()

// 饮食记录（第二阶段开放；当前为空态预览，与设计稿一致）
const diet = {
  kcal: 0,
  target: '目标 --',
  rings: [
    { key: 'protein', label: '蛋白(g)', value: 0, total: 0, percent: 0 },
    { key: 'carb', label: '碳水(g)', value: 0, total: 0, percent: 0 },
    { key: 'fat', label: '脂肪(g)', value: 0, total: 0, percent: 0 },
    { key: 'water', label: '饮水(ml)', value: 0, total: 0, percent: 0 }
  ],
  shortcuts: [
    { key: 'breakfast', label: '早餐' },
    { key: 'lunch', label: '午餐' },
    { key: 'dinner', label: '晚餐' },
    { key: 'snack', label: '加餐' },
    { key: 'water', label: '饮水' },
    { key: 'weight', label: '记体重' }
  ]
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

// ── 周安排驱动的「开始训练」──
const schedule = useScheduleStore()
const sheet = ref<'rest' | 'empty' | null>(null)

onShow(() => {
  schedule.load()
  refreshLastWorkout()
  refreshCounts()
})

const bottomHint = computed(() => {
  if (schedule.todayPlan) return `今天 · ${schedule.todayPlan.name}`
  if (schedule.isRest) return '今天休息'
  return '今天没有安排'
})

function goLibrary() {
  uni.navigateTo({ url: '/pages/library/index' })
}

function onStart() {
  if (schedule.todayPlan) {
    uni.navigateTo({ url: `/pages/session/index?plan=${schedule.todayPlan.id}` })
  } else if (schedule.isRest) {
    sheet.value = 'rest'
  } else {
    sheet.value = 'empty'
  }
}

function freeTrain() {
  sheet.value = null
  uni.navigateTo({ url: '/pages/session/index' })
}

function goWeek() {
  sheet.value = null
  uni.navigateTo({ url: '/pages/week/index' })
}

function dietPending() {
  uni.showToast({ title: '饮食记录 · 第二阶段开放', icon: 'none' })
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

      <!-- 饮食记录卡（第二阶段开放，此处按定稿稿空态预留） -->
      <view class="ft-card diet">
        <view class="diet__head">
          <text class="diet__title">饮食记录</text>
          <text class="diet__link" @click="dietPending">记一笔</text>
        </view>

        <view class="diet__kcal">
          <text class="diet__kcal-num ft-num">{{ diet.kcal }}</text>
          <text class="diet__kcal-unit"> / {{ diet.target }} 大卡</text>
        </view>

        <view class="diet__rings">
          <view v-for="r in diet.rings" :key="r.key" class="diet__ring-col">
            <view class="diet__ring" :class="'diet__ring--' + r.key">
              <text class="diet__ring-num ft-num">{{ r.value }}</text>
              <text class="diet__ring-sub ft-num">{{ r.value }} / {{ r.total }}</text>
            </view>
            <text class="diet__ring-label">{{ r.label }}</text>
            <text class="diet__ring-pct ft-num">{{ r.percent }}%</text>
          </view>
        </view>

        <view class="diet__shortcuts">
          <view v-for="s in diet.shortcuts" :key="s.key" class="diet__shortcut" @click="dietPending">
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

      <view class="home__pad" />
    </view>

    <!-- 底部固定区：开始训练 + tab 栏 -->
    <view class="home__bottom">
      <text class="home__bottom-link">{{ bottomHint }}</text>
      <view class="ft-btn ft-btn--primary ft-btn--block" @click="onStart">开始训练</view>
      <FtTabBar active="home" />
    </view>

    <!-- 休息日 / 未安排弹层 -->
    <view v-if="sheet" class="sheet-mask" @click="sheet = null" />
    <view v-if="sheet" class="sheet">
      <view class="sheet__handle" />
      <text class="sheet__title">{{ sheet === 'rest' ? '今天是休息日' : '今天没有安排' }}</text>
      <text class="sheet__sub">{{
        sheet === 'rest' ? '好好恢复，明天见。' : '设置每周安排后，开始训练会自动按计划进行。'
      }}</text>
      <view class="ft-btn ft-btn--primary ft-btn--block" @click="freeTrain">自由训练</view>
      <view class="ft-btn ft-btn--ghost ft-btn--block sheet__second" @click="goWeek">
        调整训练安排
      </view>
    </view>
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

.diet__link {
  font-size: $text-base;
  color: $app-ink-muted;
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

.diet__rings {
  display: flex;
  justify-content: space-between;
  margin-bottom: 26rpx;
}

.diet__ring-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 25%;
}

.diet__ring {
  width: 116rpx;
  height: 116rpx;
  border-radius: 50%;
  border: 6rpx solid $app-line;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.diet__ring--protein {
  border-color: $ring-protein;
}
.diet__ring--carb {
  border-color: $ring-carb;
}
.diet__ring--fat {
  border-color: $ring-fat;
}
.diet__ring--water {
  border-color: $ring-water;
}

.diet__ring-num {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
  line-height: 1.1;
}

.diet__ring-sub {
  font-size: 18rpx;
  color: $app-ink-subtle;
  line-height: 1.2;
}

.diet__ring-label {
  margin-top: 10rpx;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.diet__ring-pct {
  font-size: 18rpx;
  color: $app-ink-subtle;
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

/* ── 休息日 / 未安排弹层 ── */
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
</style>
