<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { getWorkoutTotals } from '@/api/workout'
import { clearAll, resetCaches } from '@/api/cache'
import { getBodyOverview } from '@/api/body'
import { formatWeight, getWeightUnit } from '@/utils/weight'
import { listExercises } from '@/api/exercise'
import { getFoodCount, getRecognitionUsage, getStorage, type RecognitionUsage, type StorageInfo } from '@/api/nutrition'
import { useScheduleStore } from '@/stores/schedule'
import { formatThousands } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import FtTabPage from '@/components/FtTabPage.vue'
import FtListRow from '@/components/FtListRow.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtButton from '@/components/FtButton.vue'
import { useTabPage } from '@/utils/tab-page'
import { ask } from '@/utils/confirm'

// 切页入场与底栏接线（ADR 0038／0040）：onShow 里登录守卫之后调 tab.enter()
const tab = useTabPage('profile')

// ── 我的：累计 → 入口两组 → 存储占用与识别用量 → 退出登录 ──

const username = ref('训练者')
const totalCount = ref(0)
const totalVolumeKg = ref(0)
const exerciseCount = ref(0)
const foodCount = ref(-1)
const storage = ref<StorageInfo | null>(null)
const recUsage = ref<RecognitionUsage | null>(null)
/** 最新体重：身体档案入口右侧的那一格不再是死「—」（ADR 0030 顺带修）。 */
const latestWeight = ref<number | null>(null)
const weightUnit = ref(getWeightUnit())

const schedule = useScheduleStore()

onShow(() => {
  if (!ensureLogin()) return
  tab.enter()
  username.value = (uni.getStorageSync('fitapp_username') as string) || '训练者'
  schedule.load()
  loadStats()
  weightUnit.value = getWeightUnit()
  void loadBody()
  void loadStorage()
  void loadRecognitionUsage()
})

/** 下拉＝清缓存后重取本页全部数字（ADR 0035 的逃生门）。 */
onPullDownRefresh(async () => {
  clearAll()
  schedule.load()
  await Promise.all([loadStats(), loadBody(), loadStorage(), loadRecognitionUsage()])
  uni.stopPullDownRefresh()
})

async function loadStats() {
  try {
    // 累计口径走**全量聚合接口**（ADR 0023）：列表接口有 limit 封顶，拿它求和会把「累计」算成「最近 200 次」
    const [totals, exercises, foodStat] = await Promise.all([
      getWorkoutTotals(),
      listExercises(),
      // 食物库含预置 1800+ 条：走统计接口拿精确总数（不拉列表）
      getFoodCount()
    ])
    totalCount.value = totals.count
    totalVolumeKg.value = Math.round(Number(totals.totalVolume) || 0)
    exerciseCount.value = exercises.length
    foodCount.value = foodStat.total
  } catch {
    // unwrap 已提示
  }
}

async function loadBody() {
  try {
    latestWeight.value = (await getBodyOverview()).latest?.weight ?? null
  } catch {
    // unwrap 已提示；入口照样能点
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

const countText = computed(() => String(totalCount.value))
/**
 * 累计容量文案：**有容量才显示数字**（有氧不计入容量，ADR 0023）——
 * 一次力量都没记过时显示「—」，显示 `0 kg` 看着像故障。
 */
const totalVolumeText = computed(() =>
  totalVolumeKg.value > 0 ? formatThousands(totalVolumeKg.value) : '—'
)
const showVolumeUnit = computed(() => totalVolumeKg.value > 0)
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
  void ask({
    title: '退出登录',
    content: '将清除本机登录状态；训练数据保存在服务器，不受影响。',
    confirmText: '退出'
  }).then((confirm) => {
    if (!confirm) return
    uni.removeStorageSync('fitapp_token')
    uni.removeStorageSync('fitapp_username')
    // 换人就用：数据缓存与照片缓存一并清掉（ADR 0035），别把上一个人的东西留在内存里
    resetCaches()
    uni.reLaunch({ url: '/pages/login/index' })
  })
}
</script>

<template>
  <FtTabPage :page="tab" class="prof">
    <view class="prof__body">
      <!-- 页头：头像 ＋ 用户名 -->
      <view class="prof__head">
        <view class="prof__avatar">{{ avatarChar }}</view>
        <view class="prof__who">
          <text class="prof__eyebrow">我的</text>
          <text class="prof__name">{{ username }}</text>
        </view>
      </view>

      <!-- 累计 -->
      <view class="glass duo">
        <view class="duo__cell">
          <text class="duo__label">累计训练</text>
          <view class="duo__num-row">
            <text class="duo__num ft-num">{{ countText }}</text>
            <text class="duo__unit">次</text>
          </view>
        </view>
        <view class="duo__cell">
          <text class="duo__label">总容量</text>
          <view class="duo__num-row">
            <text class="duo__num ft-num">{{ totalVolumeText }}</text>
            <text v-if="showVolumeUnit" class="duo__unit">kg</text>
          </view>
        </view>
      </view>

      <!-- 训练相关入口 -->
      <view class="glass entries">
        <FtListRow title="训练计划" :value="planSummary" arrow @click="goPlans" />
        <FtListRow title="训练安排" :value="weekSummary" arrow @click="goWeek" />
        <FtListRow title="动作库" :value="`${exerciseCount} 项已记录`" arrow @click="goLibrary" />
      </view>

      <!-- 身体、饮食与设置 -->
      <view class="glass entries">
        <FtListRow
          title="身体档案"
          :value="latestWeight == null ? '—' : formatWeight(latestWeight, weightUnit)"
          arrow
          @click="goBody"
        />
        <FtListRow title="食物库" :value="foodSummary" arrow @click="goFoods" />
        <FtListRow title="照片" :value="photoCountText" arrow @click="goAlbum" />
        <FtListRow title="设置" value="饮食目标 · 图片压缩" arrow @click="goSettings" />
      </view>

      <!-- 存储占用（ADR 0013）：目前只统计餐照 -->
      <view class="glass storage">
        <view class="storage__head">
          <text class="card-title">存储占用</text>
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

      <!-- 识别用量（ADR 0018）：外卡两值，点进明细分页 -->
      <view class="glass storage storage--tap" @click="goRecognitionLogs">
        <view class="storage__head">
          <text class="card-title">识别用量</text>
          <view class="storage__go">
            <text class="storage__val ft-num">{{ recTokensText }} · {{ recRequestsText }}</text>
            <FtIcon name="chevron-right" :size="18" tone="subtle" />
          </view>
        </view>
        <text class="storage__hint">营养成分表识别的 token 累计（不含费用）；点开看每次调用明细</text>
      </view>

      <view class="prof__logout">
        <FtButton variant="ghost" size="sm" @click="logout">退出登录</FtButton>
      </view>
    </view>
  </FtTabPage>
</template>

<style lang="scss" scoped>
.prof__body {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
  padding: calc(40rpx + env(safe-area-inset-top)) 40rpx 0;
}

.glass {
  @include glass-card;
}

.card-title {
  @include card-title;
}

/* ── 页头 ── */
.prof__head {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 12rpx 8rpx 4rpx;
}

.prof__avatar {
  @include glass($glass-bg-strong);
  width: 112rpx;
  height: 112rpx;
  flex-shrink: 0;
  border-radius: 50%;
  border: 1rpx solid $glass-edge;
  box-shadow: $glass-shadow;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: $font-num;
  font-size: $text-2xl;
  font-weight: $weight-heavy;
  color: $app-ink;
}

.prof__who {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.prof__eyebrow {
  font-size: $text-base;
  color: $app-ink-muted;
}

.prof__name {
  font-size: $text-hero;
  font-weight: $weight-bold;
  line-height: 1.25;
  color: $app-ink;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ── 累计 ── */
.duo {
  display: flex;
}

.duo__cell {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.duo__cell + .duo__cell {
  padding-left: 32rpx;
  border-left: 1rpx solid $app-line-soft;
}

.duo__label {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.duo__num-row {
  display: flex;
  align-items: baseline;
  gap: 6rpx;
}

.duo__num {
  font-size: 52rpx;
  font-weight: $weight-heavy;
  letter-spacing: -0.02em;
  line-height: 1.2;
  color: $app-ink;
}

.duo__unit {
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* ── 入口 ── */
.entries {
  padding-top: 8rpx;
  padding-bottom: 8rpx;
}

/* 入口右侧的值是说明，不是主角：比标题降一档 */
.entries :deep(.ft-row__value) {
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* ── 存储占用 ／ 识别用量 ── */
.storage__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16rpx;
}

.storage__val {
  font-size: $text-sm;
  color: $app-ink;
}

.storage__go {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.storage--tap:active {
  opacity: 0.7;
}

.storage__bar {
  height: 12rpx;
  margin-top: 20rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  overflow: hidden;
}

.storage__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: $app-ink-2;
}

/* 接近上限（≥90%）转警示色 */
.storage__fill--warn {
  background: $app-danger;
}

.storage__hint {
  display: block;
  margin-top: 16rpx;
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

/* 居中的这一行正对底栏中间圆钮：圆钮比胶囊高出约 34px，底部多让 24px，免得滚到底时被它压住 */
.prof__logout {
  display: flex;
  justify-content: center;
  padding: 8rpx 0 64rpx;
}
</style>
