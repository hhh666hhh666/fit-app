<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useScheduleStore } from '@/stores/schedule'
import { getPlan, type PlanDetail, type PlanListItem } from '@/api/plan'
import { formatClock, formatDateLabel, formatThousands } from '@/utils/format'
import { formatSetTargets, targetModeOf } from '@/utils/plan-target'
import { clearSession, loadSession, netElapsedSec } from '@/utils/session'
import { ensureLogin } from '@/utils/guard'
import FtButton from '@/components/FtButton.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import FtSheet from '@/components/FtSheet.vue'

// ── 训练计划列表（晴窗，ADR 0041）──
const schedule = useScheduleStore()

onShow(() => {
  if (!ensureLogin()) return
  schedule.load()
})

/** 周期天数 = 周表里已安排训练的天数（页头下那行「每周练 N 天」） */
const cycleDays = computed(() => schedule.week.filter((s) => typeof s === 'number').length)

function summaryOf(names: string[], count: number): string {
  const head = names.join(' · ')
  return count > names.length ? `${head} +${count - names.length}` : head
}

/** 上次容量文案：上次只有有氧就显示「—」（有氧不计入容量，ADR 0023）。 */
function lastVolumeText(p: PlanListItem): string {
  if (p.lastHasStrength !== true) return '容量 —'
  return `容量 ${formatThousands(Math.round(p.lastVolume ?? 0))} kg`
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
    <view class="plans__body">
      <FtPageHeader title="训练计划" back>
        <template #right>
          <view class="pill" role="button" @click="toNew">
            <FtIcon name="plus" :size="16" :stroke="2" />
            <text>新建</text>
          </view>
        </template>
      </FtPageHeader>

      <text v-if="schedule.plans.length > 0" class="plans__meta">
        {{ schedule.plans.length }} 个计划 · {{ cycleDays ? `每周练 ${cycleDays} 天` : '还没排进周表' }}
      </text>

      <!-- 计划卡：整卡点开＝预览编排（ADR 0032），「编辑」单独一个按钮 -->
      <view
        v-for="p in schedule.plans"
        :key="p.id"
        class="glass pcard"
        role="button"
        @click="openPreview(p.id)"
      >
        <view class="pcard__head">
          <text class="pcard__name">{{ p.name }}</text>
          <view class="pcard__edit" role="button" @click.stop="toEdit(p.id)">编辑</view>
        </view>
        <text class="pcard__summary">{{ summaryOf(p.exerciseNames, p.exerciseCount) }}</text>
        <view class="pcard__foot">
          <text class="pcard__last ft-num">
            {{ p.lastTrainedAt ? `上次 ${formatDateLabel(p.lastTrainedAt)}` : '还没练过' }}
          </text>
          <text class="pcard__stat ft-num">
            {{ p.exerciseCount }} 个动作<text v-if="p.lastVolume != null"> · {{ lastVolumeText(p) }}</text>
          </text>
        </view>
      </view>

      <!-- 空态 -->
      <view v-if="!schedule.loading && schedule.plans.length === 0" class="glass">
        <FtEmpty title="还没有训练计划" sub="建一个计划、排进周表，开始训练时照着走。">
          <FtButton variant="primary" class="plans__empty-btn" @click="toNew">新建计划</FtButton>
        </FtEmpty>
      </view>

      <text v-if="schedule.plans.length > 0" class="note">点开计划先看编排，再决定开始</text>
    </view>

    <!-- 计划预览弹层：先看清编排骨再开练（ADR 0032） -->
    <FtSheet
      v-if="preview.open"
      :title="preview.plan?.name ?? '训练计划'"
      :sub="preview.loading ? '读取编排…' : `${preview.plan?.exercises.length ?? 0} 个动作 · 开始前先看清练什么`"
      @close="closePreview"
    >
      <scroll-view v-if="preview.plan" scroll-y class="pv__scroll">
        <view class="pv">
          <view v-for="(e, i) in preview.plan.exercises" :key="e.exerciseId" class="pvrow">
            <text class="pvrow__idx ft-num">{{ i + 1 }}</text>
            <view class="pvrow__mid">
              <view class="pvrow__line">
                <text class="pvrow__name">{{ e.name }}</text>
                <text v-if="e.circuitNo != null" class="badge">{{ previewCircuitOf(e.circuitNo) }}</text>
              </view>
              <text class="pvrow__goal ft-num">{{ previewGoalOf(e) }}</text>
            </view>
          </view>
        </view>
      </scroll-view>
      <view class="pv__actions">
        <FtButton v-if="preview.plan" variant="primary" block @click="startPreviewPlan">开始训练</FtButton>
        <FtButton variant="ghost" block @click="closePreview">先不练</FtButton>
      </view>
    </FtSheet>
  </view>
</template>

<style lang="scss" scoped>
.plans__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(64rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-lg;
  box-shadow: $glass-shadow;
  padding: 32rpx 36rpx;
}

/* 页头右侧的胶囊按钮 */
.pill {
  @include glass($glass-bg-strong);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 16rpx 28rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $app-ink-2;
}

.pill:active {
  opacity: 0.7;
}

.plans__meta {
  padding: 0 8rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* ── 计划卡 ── */
.pcard {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  transition: opacity $dur-fast $ease-out;
}

.pcard:active {
  opacity: 0.75;
}

.pcard__head {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.pcard__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $app-ink;
}

.pcard__edit {
  flex-shrink: 0;
  padding: 10rpx 24rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-sm;
  color: $app-ink-2;
}

.pcard__edit:active {
  opacity: 0.6;
}

.pcard__summary {
  font-size: $text-base;
  line-height: 1.6;
  color: $app-ink-muted;
}

.pcard__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 12rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid $app-line-soft;
}

.pcard__last {
  flex-shrink: 0;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.pcard__stat {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-sm;
  color: $app-ink-2;
}

.plans .plans__empty-btn {
  align-self: flex-start;
  margin-top: 16rpx;
}

/* 正文末尾居中：底下没有底栏，按普通页留白即可 */
.note {
  padding: 8rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

/* ── 计划预览弹层（壳是 FtSheet，分组是白卡） ── */
.pv__scroll {
  flex: 1;
  min-height: 0;
  max-height: 56vh;
}

.pv {
  padding: 0 28rpx;
  background: $app-surface;
  border-radius: $radius-lg;
}

.pvrow {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 0;
}

.pvrow + .pvrow {
  border-top: 1rpx solid $app-line-soft;
}

.pvrow__idx {
  width: 40rpx;
  flex-shrink: 0;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

.pvrow__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.pvrow__line {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.pvrow__name {
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.pvrow__goal {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.badge {
  flex-shrink: 0;
  padding: 2rpx 12rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.pv__actions {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding-top: 28rpx;
}

@media (prefers-reduced-motion: reduce) {
  .pcard {
    transition: none;
  }
}
</style>
