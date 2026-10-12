<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import FtTabBar from '@/components/FtTabBar.vue'
import { listWorkouts } from '@/api/workout'
import type { WorkoutListItem } from '@/api/workout'
import { formatDateLabel, formatThousands } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'

const items = ref<WorkoutListItem[]>([])
const loading = ref(true)

const WEEK_CHARS = ['日', '一', '二', '三', '四', '五', '六']

const monthLabel = (() => {
  const d = new Date()
  return `${d.getFullYear()}年${d.getMonth() + 1}月`
})()

const totalVolume = computed(() =>
  items.value.reduce((sum, s) => sum + Number(s.totalVolume || 0), 0)
)

async function load() {
  loading.value = true
  try {
    items.value = await listWorkouts()
  } catch {
    // 401 时 request 层已跳登录页；其它错误已 toast
  } finally {
    loading.value = false
  }
}

onShow(() => {
  if (!ensureLogin()) return
  load()
})

function weekdayOf(iso: string): string {
  return `周${WEEK_CHARS[new Date(iso).getDay()]}`
}

function minutes(item: WorkoutListItem): number {
  return Math.round(item.durationSec / 60)
}

function lineOf(item: WorkoutListItem): string {
  return item.firstExercise
    ? `${item.firstExercise} · ${formatThousands(Number(item.totalVolume))} kg`
    : `${formatThousands(Number(item.totalVolume))} kg`
}

function goHome() {
  uni.reLaunch({ url: '/pages/home/index' })
}

/** 点一行 → 训练详情（带 id；detail 页自己拉 GET /api/workouts/{id}） */
function openDetail(id: number) {
  uni.navigateTo({ url: `/pages/detail/index?id=${id}` })
}

function monthPending() {
  uni.showToast({ title: '月份筛选建设中', icon: 'none' })
}
</script>

<template>
  <view class="log">
    <view class="log__body">
      <!-- 顶栏 -->
      <view class="log__head">
        <text class="log__title">训练日志</text>
        <text class="log__month ft-num" @click="monthPending">{{ monthLabel }} ▾</text>
      </view>

      <!-- 月概览条 -->
      <view class="overview">
        <text class="overview__caps">MONTH OVERVIEW</text>
        <text class="overview__nums ft-num">
          本月 {{ items.length }} 练 · 总容量 {{ formatThousands(totalVolume) }} kg
        </text>
      </view>

      <!-- 时间线列表 -->
      <view v-if="loading" class="ft-loading">加载中…</view>
      <view v-else-if="items.length === 0" class="ft-empty">还没有训练记录——去练一次吧</view>
      <view v-else class="ledger">
        <view class="ledger__thead">
          <text class="col-date">日期 / DATE</text>
          <text class="col-mid">项目 &amp; 主项容量 / DETAILS</text>
          <text class="col-time">用时 / TIME</text>
        </view>
        <view v-for="item in items" :key="item.id" class="lrow" @click="openDetail(item.id)">
          <view class="lrow__date">
            <text class="lrow__date-main ft-num">{{ formatDateLabel(item.createdAt) }}</text>
            <text class="lrow__date-week">{{ weekdayOf(item.createdAt) }}</text>
          </view>
          <view class="lrow__mid">
            <view class="lrow__title-line">
              <text class="lrow__title">{{ item.title }}</text>
            </view>
            <text class="lrow__detail ft-num">{{ lineOf(item) }}</text>
          </view>
          <view class="lrow__time">
            <text class="ft-num">{{ minutes(item) }} 分钟</text>
          </view>
        </view>
      </view>

      <!-- 新建入口 -->
      <view class="log__new" @click="goHome">
        <text class="log__new-plus">+</text>
        <text>新建训练记录</text>
      </view>

      <view class="log__pad" />
    </view>

    <view class="log__bottom">
      <FtTabBar active="log" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.log__body {
  padding: 36rpx 32rpx 0;
}

.log__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 24rpx;
}

.log__title {
  font-size: 48rpx;
  font-weight: 700;
  color: $app-ink;
}

.log__month {
  font-size: $text-sm;
  color: $app-ink-muted;
  border: 1rpx solid $app-line;
  border-radius: 8rpx;
  padding: 8rpx 16rpx;
}

/* ── 月概览条 ── */
.overview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: $app-surface;
  border: 1rpx solid $app-line;
  padding: 20rpx 24rpx;
  margin-bottom: 20rpx;
}

.overview__caps {
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: $app-ink-muted;
}

.overview__nums {
  font-size: $text-sm;
  color: $app-ink;
}

/* ── 时间线列表 ── */
.ledger {
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-lg;
  overflow: hidden;
  margin-bottom: 24rpx;
}

.ledger__thead {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  background: $app-canvas;
  border-bottom: 1rpx solid $app-line;
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: $app-ink-muted;
}

.col-date {
  width: 170rpx;
  flex-shrink: 0;
}

.col-mid {
  flex: 1;
}

.col-time {
  width: 130rpx;
  text-align: right;
  flex-shrink: 0;
}

.lrow {
  display: flex;
  align-items: center;
  padding: 24rpx;
}

.lrow + .lrow {
  border-top: 1rpx solid $app-line-soft;
}

.lrow__date {
  width: 170rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.lrow__date-main {
  font-size: $text-md;
  font-weight: 500;
  color: $app-ink;
}

.lrow__date-week {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.lrow__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  padding-right: 12rpx;
}

.lrow__title-line {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.lrow__title {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.lrow__detail {
  font-size: $text-sm;
  color: $app-ink-muted;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lrow__time {
  width: 130rpx;
  text-align: right;
  font-size: $text-sm;
  color: $app-ink-muted;
  flex-shrink: 0;
}

/* ── 新建按钮 ── */
.log__new {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  height: 88rpx;
  background: $app-ink;
  color: #ffffff;
  font-size: $text-md;
  font-weight: 500;
  border-radius: 16rpx;
}

.log__new-plus {
  font-size: $text-lg;
}

.log__pad {
  height: 240rpx;
}

/* ── 底部 tab ── */
.log__bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
}
</style>
