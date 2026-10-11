<script setup lang="ts">
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useScheduleStore } from '@/stores/schedule'
import { formatDateLabel, formatThousands } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'

// ── 训练计划列表（设计稿：.workbuddy/stitch/screen/plans.png）──
const schedule = useScheduleStore()

onShow(() => {
  if (!ensureLogin()) return
  schedule.load()
})

/** 周期天数 = 周表里已安排训练的天数（设计稿「CYCLE: 3-DAY SPLIT」） */
const cycleDays = computed(() => schedule.week.filter((s) => typeof s === 'number').length)

function summaryOf(names: string[], count: number): string {
  const head = names.join(' · ')
  return count > names.length ? `${head} +${count - names.length}` : head
}

function goBack() {
  uni.navigateBack()
}

function toNew() {
  uni.navigateTo({ url: '/pages/plan-edit/index' })
}

function toEdit(id: number) {
  uni.navigateTo({ url: `/pages/plan-edit/index?id=${id}` })
}

/** 打开一个计划即可照着练：直接进训练会话 */
function toTrain(id: number) {
  uni.navigateTo({ url: `/pages/session/index?plan=${id}` })
}
</script>

<template>
  <view class="plans">
    <view class="plans__head">
      <view class="plans__nav">
        <text class="plans__back" @click="goBack">‹</text>
        <text class="plans__meta ft-num">FIT-APP // ROUTINES</text>
        <text class="plans__meta ft-num">SYS.LOG // V3.2</text>
      </view>
      <view class="plans__title-row">
        <text class="plans__title">训练计划</text>
        <text class="plans__new" @click="toNew">+ 新建</text>
      </view>
    </view>

    <view class="plans__sect">
      <text class="plans__sect-t ft-num">ACTIVE ROUTINES [{{ schedule.plans.length }}]</text>
      <text class="plans__sect-t ft-num">
        CYCLE: {{ cycleDays ? `${cycleDays}-DAY` : '--' }} SPLIT
      </text>
    </view>

    <!-- 计划卡 -->
    <view
      v-for="p in schedule.plans"
      :key="p.id"
      class="ft-card pcard"
      @click="toTrain(p.id)"
    >
      <view class="pcard__head">
        <view class="pcard__name-wrap">
          <view class="pcard__bar" />
          <text class="pcard__name">{{ p.name }}</text>
        </view>
        <view class="pcard__tools">
          <text class="pcard__count ft-num">{{ p.exerciseCount }} 个动作</text>
          <view class="pcard__sep" />
          <text class="pcard__edit" @click.stop="toEdit(p.id)">编辑</text>
        </view>
      </view>

      <text class="pcard__summary">{{ summaryOf(p.exerciseNames, p.exerciseCount) }}</text>

      <view class="pcard__foot">
        <text class="pcard__last ft-num">
          {{ p.lastTrainedAt ? `上次：${formatDateLabel(p.lastTrainedAt)}` : '还没练过' }}
        </text>
        <text v-if="p.lastVolume != null" class="pcard__vol ft-num">
          总容量：{{ formatThousands(Math.round(p.lastVolume)) }} kg
        </text>
      </view>
    </view>

    <!-- 空态 -->
    <view v-if="!schedule.loading && schedule.plans.length === 0" class="plans__empty">
      <text class="plans__empty-title">还没有训练计划</text>
      <text class="plans__empty-sub">建一个计划、排进周表，开始训练时照着走。</text>
      <view class="ft-btn ft-btn--primary plans__empty-btn" @click="toNew">+ 新建计划</view>
    </view>

    <view v-if="schedule.plans.length > 0" class="plans__hint">
      <text class="plans__hint-dot">●</text>
      <text>打开一个计划即可照着练</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.plans {
  padding: 28rpx 32rpx calc(48rpx + env(safe-area-inset-bottom));
}

/* ── 头部 ── */
.plans__nav {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 4rpx 0 24rpx;
}

.plans__back {
  font-size: 56rpx;
  line-height: 1;
  color: $app-ink;
  padding-right: 8rpx;
}

.plans__meta {
  font-size: 22rpx;
  letter-spacing: 2rpx;
  color: $app-ink-subtle;
}

.plans__meta:last-child {
  margin-left: auto;
}

.plans__title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.plans__title {
  font-size: 56rpx;
  font-weight: 700;
  color: $app-ink;
  letter-spacing: 1rpx;
}

.plans__new {
  font-size: $text-md;
  font-weight: 500;
  color: $app-ink;
}

/* ── 分区行 ── */
.plans__sect {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 36rpx 4rpx 20rpx;
}

.plans__sect-t {
  font-size: 22rpx;
  letter-spacing: 2rpx;
  color: $app-ink-subtle;
}

/* ── 计划卡 ── */
.pcard__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pcard__name-wrap {
  display: flex;
  align-items: center;
}

/* 名字左侧粗竖线标记（对齐设计稿） */
.pcard__bar {
  width: 10rpx;
  height: 36rpx;
  border-radius: 4rpx;
  background: $app-ink;
  margin-right: 16rpx;
  flex-shrink: 0;
}

.pcard__name {
  font-size: $text-lg;
  font-weight: 700;
  color: $app-ink;
}

.pcard__tools {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.pcard__count {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.pcard__sep {
  width: 1rpx;
  height: 24rpx;
  background: $app-line;
}

.pcard__edit {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.pcard__summary {
  display: block;
  padding: 14rpx 0 22rpx 26rpx;
  font-size: $text-base;
  color: $app-ink-muted;
}

.pcard__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1rpx solid $app-line-soft;
  padding-top: 20rpx;
}

.pcard__last {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.pcard__vol {
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink;
}

/* ── 空态 ── */
.plans__empty {
  padding: 120rpx 16rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.plans__empty-title {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.plans__empty-sub {
  font-size: $text-sm;
  color: $app-ink-muted;
  padding: 12rpx 0 40rpx;
}

.plans__empty-btn {
  width: 340rpx;
}

/* ── 底部提示 ── */
.plans__hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  padding: 40rpx 0 8rpx;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

.plans__hint-dot {
  font-size: 18rpx;
}
</style>
