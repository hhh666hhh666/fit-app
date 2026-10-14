<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import FtTabBar from '@/components/FtTabBar.vue'
import { listWorkouts } from '@/api/workout'
import { listExercises } from '@/api/exercise'
import { getFoodCount, getRecognitionUsage, getStorage, type RecognitionUsage, type StorageInfo } from '@/api/nutrition'
import { useScheduleStore } from '@/stores/schedule'
import { formatThousands } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'

// ── 我的（设计稿：.workbuddy/stitch/screen/profile.png）──

const username = ref('训练者')
const totalCount = ref(0)
const totalVolumeT = ref(0)
const exerciseCount = ref(0)
const foodCount = ref(-1)
const storage = ref<StorageInfo | null>(null)
const recUsage = ref<RecognitionUsage | null>(null)

const schedule = useScheduleStore()

onShow(() => {
  if (!ensureLogin()) return
  username.value = (uni.getStorageSync('fitapp_username') as string) || '训练者'
  schedule.load()
  loadStats()
  void loadStorage()
  void loadRecognitionUsage()
})

async function loadStats() {
  try {
    // 自用工具初期记录量小；封顶 200 条，超过按「200+」显示（不装精确数）
    const [workouts, exercises, foodStat] = await Promise.all([
      listWorkouts(200),
      listExercises(),
      // 食物库含预置 1800+ 条：走统计接口拿精确总数（不拉列表）
      getFoodCount()
    ])
    totalCount.value = workouts.length
    totalVolumeT.value = Math.round(
      workouts.reduce((sum, w) => sum + (Number(w.totalVolume) || 0), 0) / 1000
    )
    exerciseCount.value = exercises.length
    foodCount.value = foodStat.total
  } catch {
    // unwrap 已提示
  }
}

/** 存储占用（ADR 0013）：目前只统计图片。 */
async function loadStorage() {
  try {
    storage.value = await getStorage()
  } catch {
    // unwrap 已提示
  }
}

/** 识别用量（ADR 0018）：外卡只露累计 token ＋ 请求次数；不显示费用。 */
async function loadRecognitionUsage() {
  try {
    recUsage.value = await getRecognitionUsage()
  } catch {
    // unwrap 已提示
  }
}

/** 字节数转人话（B / KB / MB / GB）。 */
function fmtBytes(n: number | undefined): string {
  if (n == null) return '—'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}

const photoUsedText = computed(() => fmtBytes(storage.value?.photoBytes))
const photoQuotaText = computed(() => fmtBytes(storage.value?.photoQuotaBytes))
const photoCountText = computed(() =>
  storage.value ? `共 ${storage.value.photoCount} 张` : '—'
)
/** 已用占比（0~1）；接近上限时进度条转警示色。 */
const photoRatio = computed(() => {
  const s = storage.value
  if (!s || !s.photoQuotaBytes) return 0
  return Math.min(1, s.photoBytes / s.photoQuotaBytes)
})
const photoPct = computed(() => `${(photoRatio.value * 100).toFixed(2)}%`)

/** 识别用量外卡两值（ADR 0018）：累计 token ＋ 请求次数。 */
const recTokensText = computed(() =>
  recUsage.value ? `${formatThousands(recUsage.value.totalTokens)} token` : '—'
)
const recRequestsText = computed(() =>
  recUsage.value ? `共 ${formatThousands(recUsage.value.totalRequests)} 次` : '—'
)

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

const foodSummary = computed(() =>
  foodCount.value < 0 ? '—' : `${foodCount.value} 种食物`
)

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

function goFoods() {
  uni.navigateTo({ url: '/pages/foods/index' })
}

function goSettings() {
  uni.navigateTo({ url: '/pages/settings/index' })
}

function goAlbum() {
  uni.navigateTo({ url: '/pages/album/index' })
}

function goRecognitionLogs() {
  uni.navigateTo({ url: '/pages/recognition-log/index' })
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
        <view class="entry" @click="goFoods">
          <text class="entry__label">食物库</text>
          <text class="entry__value ft-num">{{ foodSummary }}</text>
          <text class="entry__go">›</text>
        </view>
        <view class="entry" @click="goAlbum">
          <text class="entry__label">照片</text>
          <text class="entry__value ft-num">{{ photoCountText }}</text>
          <text class="entry__go">›</text>
        </view>
        <view class="entry" @click="goSettings">
          <text class="entry__label">设置</text>
          <text class="entry__value ft-num">饮食目标 · 图片压缩</text>
          <text class="entry__go">›</text>
        </view>
      </view>

      <!-- 存储占用（ADR 0013）：目前只统计餐照 -->
      <view class="ft-card storage">
        <view class="storage__head">
          <text class="storage__label">存储占用</text>
          <text class="storage__val ft-num">{{ photoUsedText }} / {{ photoQuotaText }}</text>
        </view>
        <view class="storage__bar">
          <view
            class="storage__fill"
            :class="{ 'storage__fill--warn': photoRatio >= 0.9 }"
            :style="{ width: photoPct }"
          />
        </view>
        <text class="storage__hint">目前只统计餐照；达到上限后将无法再上传新照片</text>
      </view>

      <!-- 识别用量（ADR 0018）：存储占用下方；外卡两值，点进明细分页 -->
      <view class="ft-card storage rec-usage" @click="goRecognitionLogs">
        <view class="storage__head">
          <text class="storage__label">识别用量</text>
          <text class="storage__val ft-num">{{ recTokensText }} · {{ recRequestsText }}</text>
        </view>
        <text class="storage__hint">营养成分表识别的 token 累计（不含费用）；点开看每次调用明细</text>
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

/* ── 存储占用 ── */
.storage {
  padding: 28rpx 32rpx;
}

.storage__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 18rpx;
}

.storage__label {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.storage__val {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.storage__bar {
  height: 12rpx;
  background: $app-canvas;
  border: 1rpx solid $app-line;
  border-radius: 6rpx;
  overflow: hidden;
}

.storage__fill {
  height: 100%;
  background: $app-ink;
}

/* 接近上限：进度条转警示色 */
.storage__fill--warn {
  background: #c4453c;
}

.storage__hint {
  display: block;
  padding-top: 14rpx;
  font-size: $text-xs;
  color: $app-ink-subtle;
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
