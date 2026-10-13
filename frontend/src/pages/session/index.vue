<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useTrainingStore } from '@/stores/training'
import { createWorkout, getLastRecord } from '@/api/workout'
import { getPlan } from '@/api/plan'
import { formatThousands, muscleLabel } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'

// ── 训练会话（计划多动作：按目标预填 → 点勾完成 → 全组完成自动切下一动作）──
const training = useTrainingStore()

const planTitle = ref('自由训练')
/** 来源计划 id（自由训练为 null）；结束训练时随记录入库。 */
const planId = ref<number | null>(null)

type SetRow = { w: string; r: string; done: boolean }

type Block = {
  exerciseId: number | null
  name: string
  muscle: string
  /** 「上次数据」文案。 */
  last: string
  /** 计划目标提示（如「目标 4 组 × 8 次 · 60 kg」；自由训练为空）。 */
  goal: string
  targetSets: number | null
  targetReps: number | null
  targetWeight: number | null
  sets: SetRow[]
}

const blocks = ref<Block[]>([])
const current = ref(0)
const curBlock = computed<Block | undefined>(() => blocks.value[current.value])

/** 按目标铺初始组行：设了目标组数就铺 N 行并预填重量/次数；未设 = 1 行空值。 */
function buildSets(t: Pick<Block, 'targetSets' | 'targetReps' | 'targetWeight'>): SetRow[] {
  const n = t.targetSets && t.targetSets > 0 ? t.targetSets : 1
  const w = t.targetWeight != null ? String(t.targetWeight) : ''
  const r = t.targetReps != null ? String(t.targetReps) : ''
  return Array.from({ length: n }, () => ({ w, r, done: false }))
}

function goalText(e: {
  targetSets?: number | null
  targetReps?: number | null
  targetWeight?: number | null
}): string {
  const parts: string[] = []
  if (e.targetSets != null || e.targetReps != null) {
    parts.push(`目标 ${e.targetSets ?? '–'} 组 × ${e.targetReps ?? '–'} 次`)
  }
  if (e.targetWeight != null) parts.push(`${e.targetWeight} kg`)
  return parts.join(' · ')
}

/** 「上次数据」：有 exerciseId 时从真接口取（自动/手动切动作时都要重拉）。 */
async function loadLastForBlock(i: number) {
  const b = blocks.value[i]
  if (!b || !b.exerciseId) return
  try {
    const rec = await getLastRecord(b.exerciseId)
    b.last = rec ? `${rec.weight} kg × ${rec.reps}` : '暂无记录'
  } catch {
    // 拉取失败保留本地兜底文案
  }
}

/** 自由训练：单块占位（动作选择落地前沿用设计稿演示数据），1 行空值起步。 */
function initFreeBlock() {
  blocks.value = [
    {
      exerciseId: 1,
      name: '杠铃卧推',
      muscle: '胸',
      last: '暂无记录',
      goal: '',
      targetSets: null,
      targetReps: null,
      targetWeight: null,
      sets: buildSets({ targetSets: null, targetReps: null, targetWeight: null })
    }
  ]
  current.value = 0
  loadLastForBlock(0)
}

// 从「开始训练」带计划进来：拉计划详情，全部动作进场（各自按目标预填组行）
async function loadPlan(pid: number) {
  try {
    const plan = await getPlan(pid)
    if (plan.exercises.length === 0) return
    planId.value = plan.id
    planTitle.value = plan.name
    blocks.value = plan.exercises.map((e) => {
      const targetSets = e.targetSets ?? null
      const targetReps = e.targetReps ?? null
      const targetWeight = e.targetWeight ?? null
      return {
        exerciseId: e.exerciseId,
        name: e.name,
        muscle: muscleLabel(e.muscle),
        last: '暂无记录',
        goal: goalText({ targetSets, targetReps, targetWeight }),
        targetSets,
        targetReps,
        targetWeight,
        sets: buildSets({ targetSets, targetReps, targetWeight })
      }
    })
    current.value = 0
    loadLastForBlock(0)
  } catch {
    // request 层已统一提示；退化为自由训练块，不白屏
    initFreeBlock()
  }
}

onLoad((query) => {
  if (!ensureLogin()) return
  const q = (query || {}) as Record<string, string | undefined>
  const pid = Number(q.plan)
  if (pid) {
    loadPlan(pid)
  } else {
    initFreeBlock()
  }
})

/** 活动行 = 第一个未完成的组；全部完成时无活动行。 */
function isActive(i: number) {
  return (curBlock.value?.sets.findIndex((s) => !s.done) ?? -1) === i
}

/** 本动作是否全部完成。 */
function isBlockDone(i: number): boolean {
  const b = blocks.value[i]
  return !!b && b.sets.length > 0 && b.sets.every((s) => s.done)
}

/** 本动作已完成组数（切换条上的 x/y）。 */
function doneInBlock(i: number): number {
  return blocks.value[i]?.sets.filter((s) => s.done).length ?? 0
}

/** 自由切换：点切换条任意动作即跳过去（不丢已勾的组）。 */
function switchTo(i: number) {
  if (i === current.value || !blocks.value[i]) return
  current.value = i
  loadLastForBlock(i)
}

/** 从 from 起环形找下一个「还有未完成组」的动作；都完成返回 -1。 */
function nextUnfinished(from: number): number {
  const n = blocks.value.length
  for (let k = 1; k < n; k++) {
    const idx = (from + k) % n
    if (!isBlockDone(idx)) return idx
  }
  return -1
}

/** 点勾 = 仅标记完成（不再自动补行）；本动作全部完成 → 自动切到下一个未完成动作。 */
function completeSet(i: number) {
  const b = curBlock.value
  if (!b) return
  const row = b.sets[i]
  if (!row || row.done) return
  if (!row.w || !row.r) {
    uni.showToast({ title: '请填写重量和次数', icon: 'none' })
    return
  }
  row.done = true
  if (isBlockDone(current.value)) {
    const next = nextUnfinished(current.value)
    if (next >= 0) {
      current.value = next
      loadLastForBlock(next)
      uni.showToast({ title: `进入下一动作：${blocks.value[next]?.name ?? ''}`, icon: 'none' })
    } else {
      uni.showToast({ title: '全部动作已完成，可点「结束训练」', icon: 'none' })
    }
  }
}

/** 加一组（独立按钮）：沿用上一行的值预填。 */
function addSet() {
  const b = curBlock.value
  if (!b) return
  const last = b.sets[b.sets.length - 1]
  b.sets.push({ w: last?.w ?? '', r: last?.r ?? '', done: false })
}

const doneCount = computed(() =>
  blocks.value.reduce((sum, b) => sum + b.sets.filter((s) => s.done).length, 0)
)
const totalVolume = computed(() =>
  blocks.value.reduce(
    (sum, b) =>
      sum + b.sets.filter((s) => s.done).reduce((v, s) => v + Number(s.w || 0) * Number(s.r || 0), 0),
    0
  )
)
const volumeText = computed(() => formatThousands(totalVolume.value))

// 训练计时（进页面开始走秒；真实接数据后从训练开始时间起算）
const seconds = ref(0)
let timer: ReturnType<typeof setInterval> | undefined
const clock = computed(() => {
  const m = String(Math.floor(seconds.value / 60)).padStart(2, '0')
  const s = String(seconds.value % 60).padStart(2, '0')
  return `${m}:${s}`
})
onMounted(() => {
  timer = setInterval(() => {
    seconds.value++
  }, 1000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function finish() {
  const doneBlocks = blocks.value
    .map((b) => ({ b, sets: b.sets.filter((s) => s.done) }))
    .filter((x) => x.sets.length > 0)
  if (doneBlocks.length === 0) {
    uni.showToast({ title: '还没有完成的组', icon: 'none' })
    return
  }
  // 有任意动作的任意组未完成 → 先确认是否提前结束
  const pending = blocks.value.reduce((sum, b) => sum + b.sets.filter((s) => !s.done).length, 0)
  if (pending > 0) {
    uni.showModal({
      title: '提前结束训练？',
      content: `还有 ${pending} 组未完成，确定现在结束吗？未完成的组不会入库。`,
      confirmText: '结束',
      cancelText: '继续',
      success: ({ confirm }) => {
        if (confirm) void submit(doneBlocks)
      }
    })
    return
  }
  void submit(doneBlocks)
}

async function submit(doneBlocks: { b: Block; sets: SetRow[] }[]) {
  try {
    const detail = await createWorkout({
      planId: planId.value,
      title: planTitle.value,
      durationSec: seconds.value,
      blocks: doneBlocks.map((x) => ({
        exerciseId: x.b.exerciseId,
        name: x.b.name,
        sets: x.sets.map((s) => ({ weight: Number(s.w), reps: Number(s.r) }))
      }))
    })
    training.setCurrent(detail)
    uni.navigateTo({ url: '/pages/summary/index' })
  } catch {
    // request 层已统一提示；留在本页可重试
  }
}
function comingSoon(tip: string) {
  uni.showToast({ title: tip, icon: 'none' })
}
</script>

<template>
  <view class="session">
    <view class="session__body">
      <!-- 1. 顶部状态行 -->
      <view class="session__head">
        <view class="session__status">
          <view class="session__dot" />
          <text class="session__status-text">训练中</text>
          <text class="session__status-mid">·</text>
          <text class="session__clock ft-num">{{ clock }}</text>
        </view>
        <text class="session__end" @click="finish">结束训练</text>
      </view>

      <template v-if="curBlock">
        <!-- 动作切换条：自由切换（可做完 A 一组去做 B，再回来做 A 下一组） -->
        <scroll-view scroll-x class="switcher">
          <view class="switcher__inner">
            <view
              v-for="(b, i) in blocks"
              :key="i"
              class="switcher__chip"
              :class="{
                'switcher__chip--active': i === current,
                'switcher__chip--done': i !== current && isBlockDone(i)
              }"
              @click="switchTo(i)"
            >
              <text class="switcher__name">{{ b.name }}</text>
              <view class="switcher__meta">
                <text v-if="i !== current && isBlockDone(i)" class="switcher__tick">✓</text>
                <text class="switcher__prog ft-num">{{ doneInBlock(i) }}/{{ b.sets.length }}</text>
              </view>
            </view>
          </view>
        </scroll-view>

        <!-- 2. 录入卡 -->
        <view class="card">
          <view class="card__head">
            <view class="card__title-row">
              <text class="card__title">{{ curBlock.name }}</text>
              <text class="card__tag">{{ curBlock.muscle }}</text>
            </view>
            <view class="card__last">
              <text v-if="curBlock.last === '暂无记录'">暂无记录</text>
              <text v-else>上次 <text class="ft-num">{{ curBlock.last }}</text></text>
              <text v-if="curBlock.goal" class="card__goal ft-num">{{ curBlock.goal }}</text>
            </view>
          </view>

          <view class="card__legend">
            <text class="legend__c1">组次</text>
            <view class="legend__right">
              <text class="ft-num">负荷 / 次数</text>
              <text class="legend__state">状态</text>
            </view>
          </view>

          <view
            v-for="(s, i) in curBlock.sets"
            :key="i"
            class="setrow"
            :class="{ 'setrow--active': isActive(i) }"
          >
            <view v-if="isActive(i)" class="setrow__bar" />

            <text class="setrow__idx" :class="{ 'setrow__idx--strong': isActive(i) }">
              第 {{ i + 1 }} 组
            </text>

            <view class="setrow__right">
              <!-- 完成态：只读值 + 实心勾 -->
              <template v-if="s.done">
                <text class="setrow__value ft-num">{{ s.w }} kg × {{ s.r }}</text>
                <view class="setrow__checkbox">
                  <wd-icon name="check" size="14px" />
                </view>
              </template>

              <!-- 未完成：值可改；点勾完成（活动行黑框高亮，其余浅框） -->
              <template v-else>
                <view class="setrow__inputs">
                  <view class="setrow__input" :class="{ 'setrow__input--soft': !isActive(i) }">
                    <input v-model="s.w" type="digit" class="setrow__input-field ft-num" />
                    <text class="setrow__unit">kg</text>
                  </view>
                  <text class="setrow__x">×</text>
                  <view class="setrow__input" :class="{ 'setrow__input--soft': !isActive(i) }">
                    <input v-model="s.r" type="number" class="setrow__input-field ft-num" />
                    <text class="setrow__unit">次</text>
                  </view>
                </view>
                <view
                  class="setrow__check"
                  :class="{ 'setrow__check--pending': !isActive(i) }"
                  @click="completeSet(i)"
                >
                  <wd-icon name="check" size="14px" :color="isActive(i) ? '#ffffff' : ''" />
                </view>
              </template>
            </view>
          </view>
        </view>

        <!-- 3. 加一组 -->
        <view class="add-set" @click="addSet">
          <text class="add-set__plus">+</text>
          <text>加一组</text>
        </view>

        <!-- 4. 添加动作（临场增删动作见待办，暂为占位） -->
        <view class="add-act" @click="comingSoon('动作选择建设中')">
          <text class="add-set__plus">+</text>
          <text>添加动作</text>
        </view>
      </template>

      <view class="session__pad" />
    </view>

    <!-- 5. 底部固定区 -->
    <view class="session__bottom">
      <view class="session__summary">
        <text>本次 </text>
        <text class="ft-num session__summary-num">{{ doneCount }}</text>
        <text> 组 · 总重 </text>
        <text class="ft-num session__summary-num">{{ volumeText }} kg</text>
      </view>
      <view class="ft-btn ft-btn--primary ft-btn--block session__finish" @click="finish">
        结束训练
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.session__body {
  padding: 48rpx 32rpx 0;
}

/* ── 顶部状态行 ── */
.session__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
  margin-bottom: 32rpx;
}

.session__status {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.session__dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: $app-ink;
}

.session__status-text {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.session__status-mid {
  color: $app-ink-muted;
}

.session__clock {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.session__end {
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink-muted;
}

/* ── 动作切换条 ── */
.switcher {
  white-space: nowrap;
  margin-bottom: 24rpx;
}

.switcher__inner {
  display: inline-flex;
  gap: 16rpx;
  padding-bottom: 4rpx;
}

.switcher__chip {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  min-width: 168rpx;
  padding: 16rpx 24rpx;
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-md;
}

.switcher__chip--active {
  background: $app-ink;
  border-color: $app-ink;
}

.switcher__chip--done {
  background: $app-canvas;
}

.switcher__name {
  font-size: $text-sm;
  font-weight: 600;
  color: $app-ink;
  max-width: 240rpx;
  overflow: hidden;
  text-overflow: ellipsis;
}

.switcher__chip--active .switcher__name {
  color: #ffffff;
}

.switcher__meta {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.switcher__tick {
  font-size: 20rpx;
  line-height: 1;
  color: $app-ink;
}

.switcher__prog {
  font-size: 20rpx;
  color: $app-ink-muted;
}

.switcher__chip--active .switcher__prog {
  color: rgba(255, 255, 255, 0.7);
}

/* ── 录入卡 ── */
.card {
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-lg;
  overflow: hidden;
  margin-bottom: 24rpx;
}

.card__head {
  padding: 32rpx 32rpx 24rpx;
  border-bottom: 1rpx solid $app-line;
}

.card__title-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 6rpx;
}

.card__title {
  font-size: 40rpx;
  font-weight: 700;
  color: $app-ink;
}

.card__tag {
  font-size: 22rpx;
  font-weight: 500;
  color: $app-ink-muted;
  background: $app-canvas;
  border: 1rpx solid $app-line;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.card__last {
  font-size: $text-sm;
  color: $app-ink-muted;
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.card__goal {
  color: $app-ink;
  font-weight: 500;
}

/* 表头 */
.card__legend {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 32rpx;
  background: #fbfbfa;
  border-bottom: 1rpx solid $app-line;
  font-size: 22rpx;
  font-weight: 500;
  color: $app-ink-muted;
}

.legend__c1 {
  width: 128rpx;
}

.legend__right {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.legend__state {
  width: 56rpx;
  text-align: right;
}

/* 组行 */
.setrow {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx;
  background: $app-surface;
}

.setrow + .setrow {
  border-top: 1rpx solid $app-line;
}

.setrow--active {
  padding: 20rpx 32rpx;
}

/* 活动行的左缘荧光条（本屏唯一的高亮用法） */
.setrow__bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 8rpx;
  background: $app-highlight;
}

.setrow__idx {
  width: 128rpx;
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink-muted;
  flex-shrink: 0;
}

.setrow__idx--strong {
  color: $app-ink;
  font-weight: 600;
}

.setrow__right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 32rpx;
}

.setrow__value {
  font-size: $text-md;
  font-weight: 500;
  color: $app-ink;
}

.setrow__checkbox {
  width: 40rpx;
  height: 40rpx;
  border-radius: 8rpx;
  background: $app-canvas;
  border: 1rpx solid $app-line;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $app-ink;
  flex-shrink: 0;
}

/* 输入态 */
.setrow__inputs {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.setrow__input {
  display: flex;
  align-items: center;
  border: 2rpx solid $app-ink;
  border-radius: 8rpx;
  background: $app-surface;
  padding: 8rpx 16rpx;
}

/* 非活动行（预填待做）：浅框，值可改 */
.setrow__input--soft {
  border-color: $app-line;
}

.setrow__input-field {
  width: 64rpx;
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
  text-align: left;
}

.setrow__unit {
  font-size: 22rpx;
  color: $app-ink-muted;
  margin-left: 8rpx;
}

.setrow__x {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.setrow__check {
  width: 48rpx;
  height: 48rpx;
  border-radius: 8rpx;
  background: $app-ink;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* 非活动行（预填待做）的勾：线框空心，点击即完成 */
.setrow__check--pending {
  background: $app-surface;
  border: 2rpx solid $app-ink;
  color: $app-ink;
}

/* ── + 加一组 ── */
.add-set {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 8rpx;
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink-muted;
}

.add-set__plus {
  font-size: $text-md;
}

/* ── 添加动作（占位） ── */
.add-act {
  display: flex;
  align-items: center;
  gap: 8rpx;
  border-top: 1rpx solid $app-line;
  margin-top: 24rpx;
  padding: 28rpx 8rpx 0;
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink-muted;
}

/* ── 底部固定区 ── */
.session__pad {
  height: 320rpx;
}

.session__bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: $app-canvas;
  border-top: 1rpx solid $app-line;
  padding: 24rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
}

.session__summary {
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
  margin-bottom: 24rpx;
}

.session__summary-num {
  color: $app-ink;
  font-weight: 500;
}

.session__finish {
  height: 104rpx;
  font-size: $text-md;
}
</style>
