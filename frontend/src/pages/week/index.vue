<script setup lang="ts">
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useScheduleStore } from '@/stores/schedule'
import { ensureLogin } from '@/utils/guard'
import FtIcon from '@/components/FtIcon.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'

const schedule = useScheduleStore()
const WEEK_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

onShow(() => {
  if (!ensureLogin()) return
  // 走缓存即可：改某天后 setScheduleDay 已按「计划」域作废（ADR 0035），这里再强刷等于白请求
  schedule.load()
})

const rows = computed(() =>
  WEEK_NAMES.map((label, idx) => {
    const slot = schedule.week[idx]
    const plan = typeof slot === 'number' ? schedule.plans.find((p) => p.id === slot) : null
    return {
      idx,
      label,
      name: slot === 'rest' ? '休息' : plan ? plan.name : '未安排',
      isRest: slot === 'rest',
      isEmpty: slot === null,
      isToday: idx === schedule.todayIndex
    }
  })
)

function editDay(idx: number) {
  const itemList = [...schedule.plans.map((p) => p.name), '休息', '清除安排']
  uni.showActionSheet({
    itemList,
    success: async ({ tapIndex }) => {
      const planCount = schedule.plans.length
      if (tapIndex < planCount) {
        await schedule.setDay(idx, schedule.plans[tapIndex].id)
      } else if (tapIndex === planCount) {
        await schedule.setDay(idx, 'rest')
      } else {
        await schedule.setDay(idx, null)
      }
    }
  })
}
</script>

<template>
  <view class="week">
    <view class="week__body">
      <FtPageHeader title="训练安排" back />

      <text class="note">设置每周安排，开始训练时自动按当天执行</text>

      <view class="glass days">
        <view
          v-for="row in rows"
          :key="row.idx"
          class="dayrow"
          :class="{ 'is-today': row.isToday }"
          role="button"
          @click="editDay(row.idx)"
        >
          <view class="dayrow__dot" :class="{ 'dayrow__dot--hollow': row.isRest || row.isEmpty }" />
          <text class="dayrow__label">{{ row.label }}</text>
          <text v-if="row.isToday" class="badge">今天</text>
          <text class="dayrow__name" :class="{ 'dayrow__name--muted': row.isRest || row.isEmpty }">
            {{ row.name }}
          </text>
          <FtIcon name="chevron-right" :size="18" tone="subtle" />
        </view>
      </view>

      <text class="note">计划内容在「训练计划」里编辑</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.week__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(64rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

.note {
  padding: 0 8rpx;
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

.days {
  padding: 8rpx 28rpx;
}

.dayrow {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 28rpx 0;
  @include press(dim);
}

.dayrow + .dayrow {
  border-top: 1rpx solid $app-line-soft;
}

/* 今天：浅底胶囊（与首页「今天的计划」同一个高亮），上下细线让开 */
.dayrow.is-today {
  margin: 0 -16rpx;
  padding-left: 16rpx;
  padding-right: 16rpx;
  border-radius: $radius-md;
  border-top-color: transparent;
  background: $app-surface-2;
}

.dayrow.is-today + .dayrow {
  border-top-color: transparent;
}

.dayrow__dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: $app-ink;
  flex-shrink: 0;
}

.dayrow__dot--hollow {
  background: transparent;
  border: 2rpx solid $app-line-dash;
  box-sizing: border-box;
}

.dayrow__label {
  flex-shrink: 0;
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.badge {
  @include badge($app-surface);
}

.dayrow__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: right;
  font-size: $text-md;
  color: $app-ink;
}

.dayrow__name--muted {
  color: $app-ink-muted;
}
</style>
