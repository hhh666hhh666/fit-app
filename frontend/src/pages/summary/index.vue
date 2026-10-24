<script setup lang="ts">
import FtIcon from '@/components/FtIcon.vue'
import FtButton from '@/components/FtButton.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import { computed } from 'vue'
import { useTrainingStore } from '@/stores/training'
import { updateWorkoutNote } from '@/api/workout'
import { formatClock, formatDateTimeLabel, formatThousands } from '@/utils/format'
import { metricKindOf, metricLabel, metricUnit, metricValue } from '@/utils/metric'
import { onShow } from '@dcloudio/uni-app'
import { ensureLogin } from '@/utils/guard'

onShow(() => {
  if (!ensureLogin()) return
})

const training = useTrainingStore()
const summary = computed(() => training.current)

const totalSets = computed(() =>
  summary.value ? summary.value.blocks.reduce((sum, b) => sum + b.sets.length, 0) : 0
)

/** 有氧动作不参与容量（ADR 0023）：整场都没有力量动作时，容量位显示「—」。 */
const hasLift = computed(() => (summary.value?.blocks ?? []).some((b) => !b.cardio))

/** 层数口径（爬楼）的合计——有氧块不做容量，它的成绩是层数（ADR 0025）。 */
const totalFloors = computed(() =>
  (summary.value?.blocks ?? [])
    .filter((b) => b.metric === 'floors')
    .reduce((sum, b) => sum + b.sets.reduce((v, s) => v + (Number(s.floors) || 0), 0), 0)
)
const metricKind = computed(() => metricKindOf(hasLift.value, totalFloors.value))
const volumeLabel = computed(() => metricLabel(metricKind.value))
const volumeValue = computed(() =>
  summary.value
    ? metricValue(metricKind.value, Number(summary.value.totalVolume), totalFloors.value)
    : '0'
)
const volumeUnit = computed(() => metricUnit(metricKind.value))

/** 单块指标文案：层数口径给层数、其他有氧给「—」、力量给容量。 */
function blockMetricText(block: {
  cardio?: boolean
  metric?: string | null
  sets: { weight: number; reps: number; floors?: number | null }[]
}): string {
  if (block.metric === 'floors') {
    const floors = block.sets.reduce((v, s) => v + (Number(s.floors) || 0), 0)
    return `${formatThousands(floors)} 层`
  }
  if (block.cardio) return '—'
  const sum = block.sets.reduce((v, s) => v + Number(s.weight || 0) * Number(s.reps || 0), 0)
  return `${formatThousands(sum)} kg`
}

/** 单组文案：层数口径（爬楼）念层数，其余念「重量 × 次数」。 */
function setText(block: { metric?: string | null }, s: { weight: number; reps: number; floors?: number | null }): string {
  return block.metric === 'floors' ? `${s.floors ?? 0} 层` : `${s.weight} × ${s.reps}`
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
  // 首页是 tabBar 页（ADR 0038）：切回 tab 用 switchTab，顺带清掉下级页整条栈
  uni.switchTab({ url: '/pages/home/index' })
}
</script>

<template>
  <view class="summary">
    <view class="summary__body">
      <!-- 页头：右上角关闭（＝完成），标题区跟页头一套字阶 -->
      <view class="summary__head">
        <view class="summary__title-wrap">
          <text v-if="summary" class="summary__eyebrow">
            {{ formatDateTimeLabel(summary.createdAt) }} · {{ summary.title }}
          </text>
          <text class="summary__title">训练完成</text>
        </view>
        <view class="summary__close" role="button" aria-label="关闭" @click="close">
          <FtIcon name="close" :size="20" :stroke="2" />
        </view>
      </view>

      <template v-if="summary">
        <!-- 数据卡：时长 / 组数 / 总容量，手填了消耗再多一行（ADR 0029：没填就不出现，别拿 0 占位） -->
        <view class="glass metrics">
          <view class="metrics__row">
            <view class="metrics__cell">
              <text class="metrics__label">时长</text>
              <text class="metrics__value ft-num">{{ formatClock(summary.durationSec) }}</text>
            </view>
            <view class="metrics__cell">
              <text class="metrics__label">组数</text>
              <text class="metrics__value ft-num">{{ totalSets }}<text class="metrics__unit">组</text></text>
            </view>
            <view class="metrics__cell">
              <text class="metrics__label">{{ volumeLabel }}</text>
              <text class="metrics__value ft-num">{{ volumeValue }}<text v-if="volumeUnit" class="metrics__unit">{{ volumeUnit }}</text></text>
            </view>
          </view>
          <view v-if="summary.calories != null" class="metrics__burn">
            <text class="metrics__label">本次消耗</text>
            <text class="metrics__burn-value ft-num">{{ summary.calories }}<text class="metrics__unit">大卡</text></text>
          </view>
        </view>

        <!-- 本次动作明细 -->
        <view class="glass detail">
          <view class="detail__head">
            <text class="detail__head-title">本次动作</text>
            <text class="detail__head-count ft-num">{{ summary.blocks.length }} 项</text>
          </view>
          <view v-for="(b, i) in summary.blocks" :key="i" class="detail__block">
            <view class="detail__block-head">
              <text class="detail__name">{{ b.name }}</text>
              <text class="detail__block-meta ft-num">{{ b.sets.length }} 组 · {{ blockMetricText(b) }}</text>
            </view>
            <view class="detail__sets">
              <view v-for="(s, j) in b.sets" :key="j" class="detail__chip" :class="{ 'detail__chip--pr': s.pr }">
                <text class="ft-num">{{ setText(b, s) }}</text>
                <text v-if="s.pr" class="detail__pr">PR</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 备注 -->
        <view class="glass note">
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

      <view v-else class="glass">
        <FtEmpty title="还没有刚结束的训练" sub="结束一次训练后，这里会显示这一场的汇总。" />
      </view>
    </view>

    <!-- 底部：悬浮的主按钮，与会话页的汇总条同一位置 -->
    <view class="summary__bottom">
      <FtButton variant="primary" block class="summary__done" @click="close">
        {{ summary ? '完成' : '回主页' }}
      </FtButton>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.summary__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: calc(36rpx + env(safe-area-inset-top)) 40rpx calc(200rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-lg;
  box-shadow: $glass-shadow;
  padding: 32rpx 36rpx;
}

/* ── 页头 ── */
.summary__head {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 12rpx 8rpx 8rpx;
}

.summary__title-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.summary__eyebrow {
  font-size: $text-base;
  color: $app-ink-muted;
}

.summary__title {
  font-size: $text-hero;
  font-weight: $weight-bold;
  line-height: 1.25;
  color: $app-ink;
}

.summary__close {
  @include glass;
  width: 72rpx;
  height: 72rpx;
  flex-shrink: 0;
  border-radius: 50%;
  border: 1rpx solid $glass-edge;
  box-shadow: $shadow-sm;
  color: $app-ink-2;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ── 数据卡 ── */
.metrics {
  padding: 32rpx 12rpx;
}

.metrics__row {
  display: flex;
}

.metrics__cell {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.metrics__cell + .metrics__cell {
  border-left: 1rpx solid $app-line-soft;
}

.metrics__label {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.metrics__value {
  font-size: 44rpx;
  font-weight: $weight-heavy;
  letter-spacing: -0.02em;
  color: $app-ink;
}

.metrics__unit {
  /* ⚠️ 不靠文本里的空格留间距：模板里的前导空白会被折叠，数字与单位会贴在一起 */
  margin-left: 6rpx;
  font-size: $text-sm;
  font-weight: $weight-bold;
  letter-spacing: 0;
  color: $app-ink-muted;
}

.metrics__burn {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 16rpx;
  margin: 28rpx 24rpx 0;
  padding-top: 24rpx;
  border-top: 1rpx solid $app-line-soft;
}

.metrics__burn-value {
  font-size: $text-lg;
  color: $app-ink;
}

/* ── 明细卡 ── */
.detail {
  padding: 28rpx 36rpx 12rpx;
}

.detail__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 8rpx;
}

.detail__head-title {
  font-size: $text-title;
  font-weight: $weight-bold;
  color: $app-ink;
}

.detail__head-count {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.detail__block {
  padding: 24rpx 0;
}

.detail__block + .detail__block {
  border-top: 1rpx solid $app-line-soft;
}

.detail__block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.detail__name {
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.detail__block-meta {
  flex-shrink: 0;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.detail__sets {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.detail__chip {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 20rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-sm;
  color: $app-ink;
}

/* PR 组：浅橙底 ＋ 深橙字（PR 是「动作的结果」，归橙色） */
.detail__chip--pr {
  background: $accent-soft;
  color: $accent-strong;
}

.detail__pr {
  font-size: $text-xs;
  font-weight: $weight-bold;
}

/* ── 备注 ── */
.note {
  padding: 24rpx 32rpx;
}

.note__input {
  width: 100%;
  min-height: 80rpx;
  font-size: $text-md;
  line-height: 1.7;
  color: $app-ink;
}

.note__placeholder {
  color: $app-ink-muted;
}

/* ── 底部 ── */
.summary__bottom {
  position: fixed;
  left: $tabbar-side;
  right: $tabbar-side;
  bottom: calc(#{$tabbar-gap} + env(safe-area-inset-bottom));
}

.summary__bottom .summary__done {
  height: 104rpx;
  font-size: 30rpx;
}
</style>
