<script setup lang="ts">
import { computed } from 'vue'
import { useScheduleStore } from '@/stores/schedule'

const schedule = useScheduleStore()
const WEEK_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const rows = computed(() =>
  WEEK_NAMES.map((label, idx) => {
    const slot = schedule.week[idx]
    const plan = typeof slot === 'number' ? schedule.plans.find((p) => p.id === slot) : null
    return {
      idx,
      label,
      name: slot === 'rest' ? '休息' : plan ? plan.name : '未安排',
      isRest: slot === 'rest',
      isEmpty: slot === null
    }
  })
)

function goBack() {
  uni.navigateBack()
}

function editDay(idx: number) {
  const itemList = [...schedule.plans.map((p) => p.name), '休息', '清除安排']
  uni.showActionSheet({
    itemList,
    success: ({ tapIndex }) => {
      const planCount = schedule.plans.length
      if (tapIndex < planCount) {
        schedule.setDay(idx, schedule.plans[tapIndex].id)
      } else if (tapIndex === planCount) {
        schedule.setDay(idx, 'rest')
      } else {
        schedule.setDay(idx, null)
      }
    }
  })
}
</script>

<template>
  <view class="week">
    <view class="week__head">
      <text class="week__back" @click="goBack">‹</text>
      <text class="week__title">训练安排</text>
      <view class="week__head-space" />
    </view>

    <view class="week__intro">设置每周安排，开始训练时自动按当天执行</view>

    <view class="ft-card week__card">
      <view v-for="row in rows" :key="row.idx" class="dayrow" @click="editDay(row.idx)">
        <view
          class="dayrow__dot"
          :class="{ 'dayrow__dot--hollow': row.isRest || row.isEmpty }"
        />
        <text class="dayrow__label">{{ row.label }}</text>
        <text class="dayrow__name" :class="{ 'dayrow__name--muted': row.isRest || row.isEmpty }">
          {{ row.name }}
        </text>
        <text class="dayrow__chevron">›</text>
      </view>
    </view>

    <view class="week__hint">计划内容在「训练计划」里编辑</view>
  </view>
</template>

<style lang="scss" scoped>
.week {
  padding: 28rpx 32rpx 0;
}

.week__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8rpx 0 24rpx;
}

.week__back {
  font-size: 56rpx;
  line-height: 1;
  color: $app-ink;
  padding: 0 24rpx 8rpx 0;
}

.week__title {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.week__head-space {
  width: 48rpx;
}

.week__intro {
  font-size: $text-sm;
  color: $app-ink-muted;
  padding: 0 4rpx 24rpx;
}

.week__card {
  padding: 8rpx 28rpx;
  margin-bottom: 24rpx;
}

.dayrow {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
}

.dayrow + .dayrow {
  border-top: 1rpx solid $app-line-soft;
}

.dayrow__dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: $app-ink;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.dayrow__dot--hollow {
  background: $app-canvas;
  border: 2rpx solid $app-line;
  box-sizing: border-box;
}

.dayrow__label {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
  width: 120rpx;
  flex-shrink: 0;
}

.dayrow__name {
  flex: 1;
  text-align: right;
  font-size: $text-md;
  color: $app-ink;
  margin-right: 16rpx;
}

.dayrow__name--muted {
  color: $app-ink-muted;
}

.dayrow__chevron {
  font-size: 40rpx;
  color: $app-ink-subtle;
  line-height: 1;
}

.week__hint {
  font-size: $text-sm;
  color: $app-ink-muted;
  padding: 0 4rpx;
}
</style>
