<script setup lang="ts">
// 训练详情（照设计稿 .workbuddy/stitch/screen/detail.png 还原）。
// 从日志页点进来；接 GET /api/workouts/{id}。
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getWorkout, updateWorkoutNote } from '@/api/workout'
import type { WorkoutDetail } from '@/api/workout'
import { formatClock, formatDateLabel, formatThousands } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'

const detail = ref<WorkoutDetail | null>(null)
const loading = ref(true)
const notFound = ref(false)

const totalSets = computed(() =>
  detail.value ? detail.value.blocks.reduce((sum, b) => sum + b.sets.length, 0) : 0
)

const volumeText = computed(() =>
  detail.value ? formatThousands(Number(detail.value.totalVolume)) : '0'
)

/** 单块容量 = Σ(重量×次数)，显示在动作名右侧 */
function blockVolume(block: { sets: { weight: number; reps: number }[] }): string {
  return formatThousands(
    block.sets.reduce((sum, s) => sum + Number(s.weight || 0) * Number(s.reps || 0), 0)
  )
}

async function load(id: number) {
  loading.value = true
  try {
    detail.value = await getWorkout(id)
  } catch {
    notFound.value = true
  } finally {
    loading.value = false
  }
}

onLoad((query) => {
  if (!ensureLogin()) return
  const id = Number(query?.id)
  if (!id) {
    loading.value = false
    notFound.value = true
    return
  }
  load(id)
})

/** 「编辑」＝滚到备注区（备注本身可直接改，失焦自动保存） */
function edit() {
  uni.pageScrollTo({ selector: '.note', duration: 300 })
}

async function saveNote() {
  const d = detail.value
  if (!d || !d.id) return
  try {
    await updateWorkoutNote(d.id, d.note ?? '')
  } catch {
    // 保存失败不打扰；request 层已 toast
  }
}

function back() {
  uni.navigateBack()
}
</script>

<template>
  <view class="detail">
    <view class="detail__body">
      <!-- 顶栏：返回 ＋ 日期·标题 ＋ 编辑 -->
      <view class="detail__head">
        <text class="detail__back" @click="back">←</text>
        <text v-if="detail" class="detail__head-title">
          {{ formatDateLabel(detail.createdAt) }} · {{ detail.title }}
        </text>
        <text v-else class="detail__head-title">训练详情</text>
        <text v-if="detail" class="detail__edit" @click="edit">编辑</text>
        <text v-else class="detail__edit detail__edit--off">编辑</text>
      </view>

      <!-- 加载与异常态 -->
      <view v-if="loading" class="ft-loading">加载中…</view>
      <view v-else-if="notFound || !detail" class="ft-empty">
        这条训练记录不存在或已删除
        <view class="detail__empty-back ft-btn ft-btn--ghost" @click="back">返回日志</view>
      </view>

      <template v-else>
        <!-- 三格指标 -->
        <view class="metrics">
          <view class="metrics__cell">
            <text class="metrics__label">时长</text>
            <text class="metrics__value ft-num">{{ formatClock(detail.durationSec) }}</text>
          </view>
          <view class="metrics__cell">
            <text class="metrics__label">总组数</text>
            <text class="metrics__value ft-num">{{ totalSets }}<text class="metrics__unit"> 组</text></text>
          </view>
          <view class="metrics__cell">
            <text class="metrics__label">总容量</text>
            <text class="metrics__value ft-num">{{ volumeText }}<text class="metrics__unit"> kg</text></text>
          </view>
        </view>

        <!-- 按动作分组的组表 -->
        <view v-for="(b, i) in detail.blocks" :key="i" class="ft-card block">
          <view class="block__head">
            <view class="block__name-line">
              <text class="block__dot" />
              <text class="block__name">{{ b.name }}</text>
            </view>
            <text class="block__meta ft-num">{{ b.sets.length }} 组 · {{ blockVolume(b) }} kg</text>
          </view>
          <view class="block__sets">
            <view v-for="(s, j) in b.sets" :key="j" class="set">
              <text class="set__label" :class="{ 'set__label--pr': s.pr }">
                第 {{ j + 1 }} 组
                <text v-if="s.pr" class="set__pr ft-marker">PR</text>
              </text>
              <text class="set__value ft-num">{{ s.weight }} kg × {{ s.reps }}</text>
            </view>
          </view>
        </view>

        <!-- 备注（可直接改，失焦保存） -->
        <view class="ft-card note">
          <view class="note__head">
            <text class="note__icon">≡</text>
            <text class="note__title">备注</text>
          </view>
          <textarea
            v-model="detail.note"
            class="note__input"
            placeholder="添加备注…"
            placeholder-class="note__placeholder"
            :maxlength="200"
            auto-height
            @blur="saveNote"
          />
        </view>

        <!-- 页脚 -->
        <view class="detail__foot ft-num">FIT-APP LEDGER · RECORD VERIFIED</view>
      </template>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.detail__body {
  padding: 36rpx 32rpx 48rpx;
}

/* ── 顶栏 ── */
.detail__head {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding-bottom: 28rpx;
}

.detail__back {
  font-size: 44rpx;
  line-height: 1;
  color: $app-ink;
  padding: 0 8rpx;
}

.detail__head-title {
  flex: 1;
  min-width: 0;
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail__edit {
  font-size: $text-sm;
  color: $app-ink-muted;
  flex-shrink: 0;
}

.detail__edit--off {
  opacity: 0.4;
}

.detail__empty-back {
  margin: 32rpx auto 0;
  width: 240rpx;
}

/* ── 三格指标 ── */
.metrics {
  display: flex;
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-lg;
  padding: 24rpx 0;
  margin-bottom: 24rpx;
}

.metrics__cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.metrics__cell + .metrics__cell {
  border-left: 1rpx solid $app-line-soft;
}

.metrics__label {
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: $app-ink-muted;
}

.metrics__value {
  font-size: 40rpx;
  font-weight: 600;
  color: $app-ink;
}

.metrics__unit {
  font-size: $text-sm;
  font-weight: 400;
  color: $app-ink-muted;
}

/* ── 动作卡 ── */
.block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid $app-line-soft;
}

.block__name-line {
  display: flex;
  align-items: center;
  gap: 12rpx;
  min-width: 0;
}

.block__dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: $app-ink;
  flex-shrink: 0;
}

.block__name {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.block__meta {
  font-size: $text-sm;
  color: $app-ink-muted;
  flex-shrink: 0;
}

.block__sets {
  padding-top: 8rpx;
}

.set {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
}

.set + .set {
  border-top: 1rpx solid $app-line-soft;
}

.set__label {
  font-size: $text-md;
  color: $app-ink-muted;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

/* ⚠️ 联合类名提优先级：scoped 的 .set__label（0,2,0）会压过全局 .ft-marker（0,1,0），
   荧光会不亮——summary 页踩过，这里直接写联合选择器。 */
.set__label.set__label--pr {
  color: $app-ink;
  font-weight: 500;
}

.set__pr {
  font-size: 20rpx;
  font-weight: 600;
  color: $app-ink;
  padding: 2rpx 10rpx;
  line-height: 1.4;
}

.set__value {
  font-size: $text-md;
  color: $app-ink;
}

/* ── 备注 ── */
.note__head {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding-bottom: 16rpx;
}

.note__icon {
  font-size: $text-md;
  color: $app-ink-muted;
}

.note__title {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.note__input {
  width: 100%;
  min-height: 120rpx;
  font-size: $text-md;
  color: $app-ink;
  line-height: 1.6;
}

.note__placeholder {
  color: $app-ink-subtle;
}

/* ── 页脚 ── */
.detail__foot {
  padding: 48rpx 0 16rpx;
  text-align: center;
  font-size: 20rpx;
  letter-spacing: 0.12em;
  color: $app-ink-subtle;
}
</style>
