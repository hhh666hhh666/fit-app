<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import FtTabBar from '@/components/FtTabBar.vue'
import { listWorkouts } from '@/api/workout'
import { listExercises } from '@/api/exercise'
import { useScheduleStore } from '@/stores/schedule'
import { formatThousands } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'

// ── 我的（设计稿：.workbuddy/stitch/screen/profile.png）──

const username = ref('训练者')
const totalCount = ref(0)
const totalVolumeT = ref(0)
const exerciseCount = ref(0)

const schedule = useScheduleStore()

onShow(() => {
  if (!ensureLogin()) return
  username.value = (uni.getStorageSync('fitapp_username') as string) || '训练者'
  schedule.load()
  loadStats()
})

async function loadStats() {
  try {
    // 自用工具初期记录量小；封顶 200 条，超过按「200+」显示（不装精确数）
    const [workouts, exercises] = await Promise.all([listWorkouts(200), listExercises()])
    totalCount.value = workouts.length
    totalVolumeT.value = Math.round(
      workouts.reduce((sum, w) => sum + (Number(w.totalVolume) || 0), 0) / 1000
    )
    exerciseCount.value = exercises.length
  } catch {
    // unwrap 已提示
  }
}

const countText = computed(() => (totalCount.value >= 200 ? '200+' : String(totalCount.value)))
const avatarChar = computed(() => username.value.charAt(0).toUpperCase() || 'L')
const planSummary = computed(() =>
  schedule.plans.length ? `${schedule.plans.length} 个计划` : '还没有计划'
)
/** 周表已安排天数（计划或休息都算「已安排」）。 */
const weekSummary = computed(() => {
  const n = schedule.week.filter((s) => s !== null).length
  return n ? `${n} 天已安排` : '未安排'
})

function goBody() {
  uni.navigateTo({ url: '/pages/body/index' })
}

function goLibrary() {
  uni.navigateTo({ url: '/pages/library/index' })
}

function goPlans() {
  uni.navigateTo({ url: '/pages/plans/index' })
}

function goWeek() {
  uni.navigateTo({ url: '/pages/week/index' })
}

function logout() {
  uni.showModal({
    title: '退出登录',
    content: '将清除本机登录状态；训练数据保存在服务器，不受影响。',
    confirmText: '退出',
    success: ({ confirm }) => {
      if (!confirm) return
      uni.removeStorageSync('fitapp_token')
      uni.removeStorageSync('fitapp_username')
      uni.reLaunch({ url: '/pages/login/index' })
    }
  })
}
</script>

<template>
  <view class="prof">
    <view class="prof__body">
      <!-- 顶部 meta 行 -->
      <view class="prof__meta">
        <text class="ft-num">FIT-APP LEDGER</text>
        <text class="ft-num">ID: {{ username.toUpperCase() }}</text>
      </view>

      <!-- 用户卡 -->
      <view class="ft-card ucard">
        <view class="ucard__avatar">{{ avatarChar }}</view>
        <text class="ucard__name">{{ username }}</text>
        <text class="ucard__badge">LIFTER</text>
      </view>

      <!-- 统计双格 -->
      <view class="ft-card stats">
        <view class="stats__item">
          <text class="stats__label">累计训练</text>
          <view class="stats__num-row">
            <text class="stats__num ft-num">{{ countText }}</text>
            <text class="stats__unit">次</text>
          </view>
        </view>
        <view class="stats__divider" />
        <view class="stats__item">
          <text class="stats__label">总负荷容量</text>
          <view class="stats__num-row">
            <text class="stats__num ft-num">{{ formatThousands(totalVolumeT) }}</text>
            <text class="stats__unit">t</text>
          </view>
        </view>
      </view>

      <!-- 入口三行 -->
      <view class="ft-card entries">
        <view class="entry" @click="goBody">
          <text class="entry__label">身体档案</text>
          <text class="entry__value ft-num">—</text>
          <text class="entry__go">›</text>
        </view>
        <view class="entry" @click="goLibrary">
          <text class="entry__label">动作库</text>
          <text class="entry__value ft-num">{{ exerciseCount }} 项已记录</text>
          <text class="entry__go">›</text>
        </view>
        <view class="entry" @click="goPlans">
          <text class="entry__label">训练计划</text>
          <text class="entry__value ft-num">{{ planSummary }}</text>
          <text class="entry__go">›</text>
        </view>
        <view class="entry" @click="goWeek">
          <text class="entry__label">训练安排</text>
          <text class="entry__value ft-num">{{ weekSummary }}</text>
          <text class="entry__go">›</text>
        </view>
      </view>

      <text class="prof__logout" @click="logout">退出登录</text>

      <view class="prof__pad" />
    </view>

    <view class="prof__bottom">
      <FtTabBar active="profile" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.prof__body {
  padding: 36rpx 32rpx 0;
}

/* ── meta 行 ── */
.prof__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4rpx 28rpx;
  font-size: 22rpx;
  letter-spacing: 2rpx;
  color: $app-ink-subtle;
}

/* ── 用户卡 ── */
.ucard {
  display: flex;
  align-items: center;
  gap: 26rpx;
}

.ucard__avatar {
  width: 108rpx;
  height: 108rpx;
  border-radius: 50%;
  background: $app-canvas;
  border: 1rpx solid $app-line;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  font-weight: 700;
  color: $app-ink;
  flex-shrink: 0;
}

.ucard__name {
  font-size: 40rpx;
  font-weight: 700;
  color: $app-ink;
}

.ucard__badge {
  margin-left: auto;
  font-size: 20rpx;
  letter-spacing: 2rpx;
  color: $app-ink-muted;
  border: 1rpx solid $app-line;
  border-radius: 8rpx;
  padding: 8rpx 14rpx;
}

/* ── 统计双格 ── */
.stats {
  display: flex;
  align-items: stretch;
  padding: 32rpx 0;
}

.stats__item {
  flex: 1;
  padding: 0 36rpx;
}

.stats__label {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.stats__num-row {
  display: flex;
  align-items: baseline;
  gap: 10rpx;
  padding-top: 10rpx;
}

.stats__num {
  font-size: $text-3xl;
  font-weight: 700;
  color: $app-ink;
  line-height: 1;
}

.stats__unit {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.stats__divider {
  width: 1rpx;
  background: $app-line;
}

/* ── 入口 ── */
.entries {
  padding: 8rpx 28rpx;
}

.entry {
  display: flex;
  align-items: center;
  padding: 32rpx 4rpx;
}

.entry + .entry {
  border-top: 1rpx solid $app-line-soft;
}

.entry__label {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.entry__value {
  margin-left: auto;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.entry__go {
  font-size: 36rpx;
  line-height: 1;
  color: $app-ink-subtle;
  padding-left: 14rpx;
}

/* ── 退出 ── */
.prof__logout {
  display: block;
  text-align: center;
  font-size: $text-base;
  color: $app-ink-muted;
  padding: 48rpx 0;
}

.prof__pad {
  height: 200rpx;
}

/* ── 底部 tab ── */
.prof__bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
}
</style>
