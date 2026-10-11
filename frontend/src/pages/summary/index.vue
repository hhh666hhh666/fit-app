<script setup lang="ts">
import { computed } from 'vue'
import { useTrainingStore } from '@/stores/training'
import { updateWorkoutNote } from '@/api/workout'
import { formatClock, formatDateLabel, formatThousands } from '@/utils/format'

const training = useTrainingStore()
const summary = computed(() => training.current)

const totalSets = computed(() =>
  summary.value ? summary.value.blocks.reduce((sum, b) => sum + b.sets.length, 0) : 0
)

const volumeText = computed(() =>
  summary.value ? formatThousands(summary.value.totalVolume) : '0'
)

function blockVolume(block: { sets: { weight: number; reps: number }[] }): string {
  return formatThousands(
    block.sets.reduce((sum, s) => sum + Number(s.weight || 0) * Number(s.reps || 0), 0)
  )
}

async function close() {
  const s = summary.value
  if (s && s.note && s.id) {
    try {
      await updateWorkoutNote(s.id, s.note)
    } catch {
      // 备注更新失败不拦退出
    }
  }
  uni.reLaunch({ url: '/pages/home/index' })
}
</script>

<template>
  <view class="summary">
    <view class="summary__body">
      <!-- 顶栏 -->
      <view class="summary__head">
        <text class="summary__brand">fit-app</text>
        <text class="summary__close" @click="close">×</text>
      </view>

      <template v-if="summary">
        <!-- 标题 -->
        <view class="summary__title-wrap">
          <text class="summary__title">训练完成</text>
          <text class="summary__sub">{{ formatDateLabel(summary.createdAt) }} · {{ summary.title }}</text>
        </view>

        <!-- 数据条：时长 / 组数 / 总容量 -->
        <view class="metrics">
          <view class="metrics__cell">
            <text class="metrics__label">时长</text>
            <text class="metrics__value ft-num">{{ formatClock(summary.durationSec) }}</text>
          </view>
          <view class="metrics__cell">
            <text class="metrics__label">组数</text>
            <text class="metrics__value ft-num">{{ totalSets }}<text class="metrics__unit"> 组</text></text>
          </view>
          <view class="metrics__cell">
            <text class="metrics__label">总容量</text>
            <text class="metrics__value ft-num">{{ volumeText }}<text class="metrics__unit"> kg</text></text>
          </view>
        </view>

        <!-- 本次动作明细 -->
        <view class="detail">
          <view class="detail__head">
            <text class="detail__head-title">本次动作</text>
            <text class="detail__head-count ft-num">{{ summary.blocks.length }} 项</text>
          </view>
          <view v-for="(b, i) in summary.blocks" :key="i" class="detail__block">
            <view class="detail__block-head">
              <text class="detail__name">{{ b.name }}</text>
              <text class="detail__block-meta ft-num">{{ b.sets.length }} 组 · {{ blockVolume(b) }} kg</text>
            </view>
            <view class="detail__sets">
              <template v-for="(s, j) in b.sets" :key="j">
                <text v-if="j > 0" class="detail__sep">·</text>
                <text class="detail__chip ft-num">{{ s.weight }} × {{ s.reps }}</text>
              </template>
            </view>
          </view>
        </view>

        <!-- 备注 -->
        <view class="note">
          <textarea
            v-model="summary.note"
            class="note__input"
            placeholder="添加备注…"
            placeholder-class="note__placeholder"
            :maxlength="200"
            auto-height
          />
        </view>
      </template>

      <view v-else class="ft-empty">
        还没有刚结束的训练
      </view>
    </view>

    <!-- 底部 -->
    <view class="summary__bottom">
      <view class="ft-btn ft-btn--primary ft-btn--block summary__done" @click="close">
        {{ summary ? '完成' : '回主页' }}
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.summary__body {
  padding: 36rpx 32rpx 0;
}

.summary__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 40rpx;
}

.summary__brand {
  font-size: $text-sm;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: $app-ink-muted;
}

.summary__close {
  font-size: 44rpx;
  line-height: 1;
  color: $app-ink-muted;
  padding: 0 8rpx;
}

.summary__title-wrap {
  margin-bottom: 36rpx;
}

.summary__title {
  display: block;
  font-size: 48rpx;
  font-weight: 700;
  color: $app-ink;
}

.summary__sub {
  display: block;
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink-muted;
  padding-top: 6rpx;
}

/* ── 数据条 ── */
.metrics {
  display: flex;
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-lg;
  padding: 32rpx 0;
  margin-bottom: 28rpx;
}

.metrics__cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.metrics__cell + .metrics__cell {
  border-left: 1rpx solid $app-line;
}

.metrics__label {
  font-size: 22rpx;
  font-weight: 500;
  color: $app-ink-muted;
}

.metrics__value {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.metrics__unit {
  font-size: $text-sm;
  font-weight: 400;
  color: $app-ink-muted;
}

/* ── 明细卡 ── */
.detail {
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-lg;
  overflow: hidden;
  margin-bottom: 28rpx;
}

.detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 32rpx;
  border-bottom: 1rpx solid $app-line;
}

.detail__head-title {
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: $app-ink-muted;
}

.detail__head-count {
  font-size: 22rpx;
  color: $app-ink-muted;
}

.detail__block {
  padding: 28rpx 32rpx;
}

.detail__block + .detail__block {
  border-top: 1rpx solid $app-line;
}

.detail__block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14rpx;
}

.detail__name {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.detail__block-meta {
  font-size: 22rpx;
  color: $app-ink-muted;
}

.detail__sets {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10rpx;
}

.detail__sep {
  color: $app-line;
  font-weight: 700;
  font-size: $text-sm;
}

.detail__chip {
  font-size: $text-sm;
  color: $app-ink;
  background: $app-canvas;
  border: 1rpx solid $app-line-soft;
  border-radius: 8rpx;
  padding: 8rpx 16rpx;
}

/* ── 备注 ── */
.note {
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-lg;
  padding: 24rpx;
  margin-bottom: 28rpx;
}

.note__input {
  width: 100%;
  font-size: $text-sm;
  line-height: 1.7;
  color: $app-ink;
}

.note__placeholder {
  color: $app-ink-muted;
}

/* ── 底部 ── */
.summary__bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: $app-canvas;
  padding: 16rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
}

.summary__done {
  height: 96rpx;
  border-radius: 24rpx;
}
</style>
