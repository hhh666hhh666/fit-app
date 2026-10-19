<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useScheduleStore } from '@/stores/schedule'
import { getPlan, type PlanDetail, type PlanListItem } from '@/api/plan'
import { formatClock, formatDateLabel, formatThousands } from '@/utils/format'
import { formatSetTargets, targetModeOf } from '@/utils/plan-target'
import { clearSession, loadSession, netElapsedSec } from '@/utils/session'
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

/** 上次容量文案：上次只有有氧就显示「—」（有氧不计入容量，ADR 0023）。 */
function lastVolumeText(p: PlanListItem): string {
  if (p.lastHasStrength !== true) return '总容量：—'
  return `总容量：${formatThousands(Math.round(p.lastVolume ?? 0))} kg`
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

// ── 点计划＝先看编排，再决定开练（ADR 0032）──
// 整卡点击不再直接进会话页：误触代价从「莫名开始一场训练」降到「多看一眼」。
const preview = ref<{ open: boolean; id: number | null; loading: boolean; plan: PlanDetail | null }>({
  open: false,
  id: null,
  loading: false,
  plan: null
})

async function openPreview(id: number) {
  preview.value = { open: true, id, loading: true, plan: null }
  try {
    const detail = await getPlan(id)
    // 快速换点另一张卡时，旧请求回来不许覆盖新的
    if (preview.value.id === id) preview.value.plan = detail
  } catch {
    preview.value.open = false
  } finally {
    if (preview.value.id === id) preview.value.loading = false
  }
}

function closePreview() {
  preview.value.open = false
}

const CIRCUIT_LABELS = ['A', 'B', 'C', 'D']

function previewGoalOf(e: PlanDetail['exercises'][number]): string {
  return formatSetTargets(e.sets, targetModeOf(e.metric, e.muscle))
}

function previewCircuitOf(no?: number | null): string {
  if (no == null) return ''
  return `循环${CIRCUIT_LABELS[no - 1] ?? no}`
}

/**
 * 「开始训练」：已有进行中的会话时先问一次（ADR 0020 同一套口径）——
 * 会话不设过期，冲突只在「要开新的」这一刻才需要决定。
 * ⚠️ 开新会话必须带 `fresh=1`：不带的话会话页会静默接续本机那条旧会话，点 A 计划却进了 B。
 */
function startPreviewPlan() {
  const id = preview.value.id
  if (id == null) return
  const s = loadSession()
  if (s && s.blocks.length > 0) {
    const done = s.blocks.reduce((n, b) => n + b.sets.filter((x) => x.done).length, 0)
    uni.showModal({
      title: '上一次训练还没结束',
      content: `已记 ${done} 组 · 已进行 ${formatClock(
        netElapsedSec(s, Date.now())
      )}。继续上一次，还是结束它并开新的？开新训练会让上一次未保存的组次丢失。`,
      confirmText: '继续上次',
      cancelText: '开新的',
      success: ({ confirm }) => {
        preview.value.open = false
        if (confirm) {
          uni.navigateTo({ url: '/pages/session/index' })
          return
        }
        clearSession()
        uni.navigateTo({ url: `/pages/session/index?plan=${id}&fresh=1` })
      }
    })
    return
  }
  preview.value.open = false
  uni.navigateTo({ url: `/pages/session/index?plan=${id}&fresh=1` })
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
      @click="openPreview(p.id)"
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
          {{ lastVolumeText(p) }}
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
      <text>点开计划先看编排，再决定开始</text>
    </view>

    <!-- 计划预览弹层：先看清编排骨再开练（ADR 0032） -->
    <view v-if="preview.open" class="sheet-mask" @click="closePreview" />
    <view v-if="preview.open" class="sheet">
      <view class="sheet__handle" />
      <text class="sheet__title">{{ preview.plan?.name ?? '训练计划' }}</text>
      <text class="sheet__sub ft-num">
        {{
          preview.loading
            ? '读取编排…'
            : `${preview.plan?.exercises.length ?? 0} 个动作 · 开始前先看清练什么`
        }}
      </text>

      <scroll-view v-if="preview.plan" scroll-y class="sheet__list">
        <view
          v-for="(e, i) in preview.plan.exercises"
          :key="e.exerciseId"
          class="pvrow"
        >
          <text class="pvrow__idx ft-num">{{ i + 1 }}</text>
          <view class="pvrow__mid">
            <view class="pvrow__name-row">
              <text class="pvrow__name">{{ e.name }}</text>
              <text v-if="e.circuitNo != null" class="pvrow__circuit ft-num">
                {{ previewCircuitOf(e.circuitNo) }}
              </text>
            </view>
            <text class="pvrow__goal ft-num">{{ previewGoalOf(e) }}</text>
          </view>
        </view>
      </scroll-view>

      <view v-if="preview.plan" class="ft-btn ft-btn--primary ft-btn--block" @click="startPreviewPlan">
        开始训练
      </view>
      <view class="ft-btn ft-btn--ghost ft-btn--block sheet__second" @click="closePreview">
        先不练
      </view>
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

/* ── 计划预览弹层（与首页「选择训练计划」同一套壳） ── */
.sheet-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(23, 25, 29, 0.4);
  z-index: 90;
}

.sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: $app-surface;
  border-radius: 40rpx 40rpx 0 0;
  padding: 20rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
  z-index: 91;
}

.sheet__handle {
  width: 72rpx;
  height: 8rpx;
  border-radius: 4rpx;
  background: $app-line;
  margin: 0 auto 28rpx;
}

.sheet__title {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  color: $app-ink;
}

.sheet__sub {
  display: block;
  font-size: $text-base;
  color: $app-ink-muted;
  padding: 10rpx 0 24rpx;
}

.sheet__list {
  max-height: 620rpx;
  margin-bottom: 28rpx;
}

.sheet__second {
  margin-top: 16rpx;
}

.pvrow {
  display: flex;
  align-items: center;
  padding: 22rpx 4rpx;
  border-top: 1rpx solid $app-line-soft;
}

.pvrow:first-child {
  border-top: none;
}

.pvrow__idx {
  width: 44rpx;
  flex-shrink: 0;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

.pvrow__mid {
  flex: 1;
  min-width: 0;
}

.pvrow__name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.pvrow__name {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.pvrow__circuit {
  font-size: $text-xs;
  color: $app-ink-muted;
  border: 1rpx solid $app-line;
  border-radius: $radius-sm;
  padding: 2rpx 10rpx;
  flex-shrink: 0;
}

.pvrow__goal {
  display: block;
  font-size: $text-sm;
  color: $app-ink-muted;
  padding-top: 6rpx;
}
</style>
