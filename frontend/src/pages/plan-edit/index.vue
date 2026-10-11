<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { createPlan, deletePlan, getPlan, updatePlan } from '@/api/plan'
import { usePickStore } from '@/stores/pick'
import { muscleLabel } from '@/utils/format'

// ── 计划编辑/新建（设计稿：.workbuddy/stitch/screen/plan-edit.png）──

interface Row {
  exerciseId: number
  name: string
  muscle: string
  targetSets: number | null
  targetReps: number | null
}

const pick = usePickStore()

const editId = ref<number | null>(null)
const name = ref('')
const rows = ref<Row[]>([])

const isEdit = computed(() => editId.value != null)

onLoad((query) => {
  const q = (query || {}) as Record<string, string | undefined>
  const id = Number(q.id)
  if (id) {
    editId.value = id
    loadDetail(id)
  }
})

async function loadDetail(id: number) {
  try {
    const detail = await getPlan(id)
    name.value = detail.name
    rows.value = detail.exercises.map((e) => ({
      exerciseId: e.exerciseId,
      name: e.name,
      muscle: e.muscle,
      targetSets: e.targetSets ?? null,
      targetReps: e.targetReps ?? null
    }))
  } catch {
    // unwrap 已提示
  }
}

// 从动作库（点选模式）返回：取走选中动作并入编排；重复则提示
onShow(() => {
  const p = pick.take()
  if (!p) return
  if (rows.value.some((r) => r.exerciseId === p.id)) {
    uni.showToast({ title: '该动作已在计划中', icon: 'none' })
    return
  }
  rows.value.push({
    exerciseId: p.id,
    name: p.name,
    muscle: p.muscle,
    targetSets: null,
    targetReps: null
  })
})

const totalSets = computed(() => rows.value.reduce((sum, r) => sum + (r.targetSets ?? 0), 0))

const pad2 = (n: number) => String(n).padStart(2, '0')

function targetText(r: Row): string {
  if (r.targetSets == null && r.targetReps == null) return '未设目标'
  if (r.targetSets != null && r.targetReps != null) return `目标 ${r.targetSets} 组 × ${r.targetReps} 次`
  if (r.targetSets != null) return `目标 ${r.targetSets} 组`
  return `目标 ${r.targetReps} 次`
}

// ── 行操作：调整目标 / 上移 / 下移 / 移除（拖拽排序留后续迭代）──
function onRowTap(i: number) {
  const itemList = ['调整目标组次']
  if (i > 0) itemList.push('上移')
  if (i < rows.value.length - 1) itemList.push('下移')
  itemList.push('从计划中移除')
  uni.showActionSheet({
    itemList,
    success: ({ tapIndex }) => {
      const action = itemList[tapIndex]
      if (action === '调整目标组次') openTargetEditor(i)
      else if (action === '上移') move(i, i - 1)
      else if (action === '下移') move(i, i + 1)
      else if (action === '从计划中移除') rows.value.splice(i, 1)
    }
  })
}

function move(from: number, to: number) {
  const item = rows.value.splice(from, 1)[0]
  rows.value.splice(to, 0, item)
}

// ── 目标组次编辑弹层 ──
const targetSheet = ref({ show: false, index: -1, sets: '', reps: '' })

function openTargetEditor(i: number) {
  const r = rows.value[i]
  targetSheet.value = {
    show: true,
    index: i,
    sets: r.targetSets != null ? String(r.targetSets) : '',
    reps: r.targetReps != null ? String(r.targetReps) : ''
  }
}

function confirmTarget() {
  const t = targetSheet.value
  if (t.index < 0) return
  const sets = t.sets.trim() === '' ? null : Number(t.sets)
  const reps = t.reps.trim() === '' ? null : Number(t.reps)
  if ((sets != null && (sets < 1 || sets > 20)) || (reps != null && (reps < 1 || reps > 100))) {
    uni.showToast({ title: '组数 1–20、次数 1–100', icon: 'none' })
    return
  }
  rows.value[t.index].targetSets = sets
  rows.value[t.index].targetReps = reps
  targetSheet.value.show = false
}

function addExercise() {
  uni.navigateTo({ url: '/pages/library/index?picking=1' })
}

// ── 保存 / 删除 ──
const saving = ref(false)

async function save() {
  const planName = name.value.trim()
  if (!planName) {
    uni.showToast({ title: '先给计划起个名字', icon: 'none' })
    return
  }
  if (rows.value.length === 0) {
    uni.showToast({ title: '至少添加一个动作', icon: 'none' })
    return
  }
  const payload = {
    name: planName,
    exercises: rows.value.map((r) => ({
      exerciseId: r.exerciseId,
      targetSets: r.targetSets,
      targetReps: r.targetReps
    }))
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updatePlan(editId.value as number, payload)
    } else {
      await createPlan(payload)
    }
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch {
    // unwrap 已提示；留在本页可重试
  } finally {
    saving.value = false
  }
}

function removePlan() {
  if (!isEdit.value) return
  uni.showModal({
    title: '删除计划',
    content: '删除后，周表里引用它的安排也会一并移除。',
    confirmText: '删除',
    confirmColor: '#C4453C',
    success: async ({ confirm }) => {
      if (!confirm) return
      try {
        await deletePlan(editId.value as number)
        uni.showToast({ title: '已删除', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 400)
      } catch {
        // unwrap 已提示
      }
    }
  })
}

function goBack() {
  uni.navigateBack()
}
</script>

<template>
  <view class="pe">
    <view class="pe__body">
      <!-- 顶部栏 -->
      <view class="pe__head">
        <text class="pe__back" @click="goBack">‹</text>
        <text class="pe__head-title">{{ isEdit ? '编辑计划' : '新建计划' }}</text>
        <text v-if="isEdit" class="pe__del" @click="removePlan">删除</text>
        <view v-else class="pe__head-space" />
      </view>

      <!-- meta 行 -->
      <view class="pe__meta">
        <text class="ft-num">ROUTINE_ENTRY // {{ pad2(rows.length) }}</text>
        <text class="ft-num">LOG.REV 2026</text>
      </view>

      <!-- 名称卡 -->
      <view class="namecard">
        <text class="namecard__label">计划名称</text>
        <view class="namecard__input-row">
          <input
            v-model="name"
            class="namecard__input"
            placeholder="如：胸 & 三头"
            placeholder-class="namecard__ph"
            maxlength="32"
          />
          <text class="namecard__pen">✎</text>
        </view>
        <view class="namecard__stats">
          <text class="ft-num">EXERCISES: {{ pad2(rows.length) }}</text>
          <text class="ft-num">EST. VOLUME: ~{{ totalSets }} SETS</text>
        </view>
      </view>

      <!-- 编排标题 -->
      <view class="pe__sect">
        <text class="pe__sect-l"
          >动作编排 <text class="pe__sect-sub">[按顺序执行]</text></text
        >
        <text class="pe__sect-r">点行调整</text>
      </view>

      <!-- 动作行 -->
      <view v-if="rows.length > 0" class="elist">
        <view v-for="(r, i) in rows" :key="r.exerciseId" class="erow" @click="onRowTap(i)">
          <text class="erow__idx ft-num">{{ i + 1 }}</text>
          <view class="erow__mid">
            <text class="erow__name">{{ r.name }}</text>
            <text class="erow__sub">{{ muscleLabel(r.muscle) }}</text>
          </view>
          <text class="erow__target ft-num">{{ targetText(r) }}</text>
        </view>
      </view>

      <!-- 添加动作 -->
      <view class="pe__add" @click="addExercise">
        <text class="pe__add-plus">+</text>
        <text>添加动作</text>
      </view>

      <view class="pe__foot-note">FIT-APP LEDGER · RECORD DISCIPLINE // SINGLE LIFTER</view>
      <view class="pe__pad" />
    </view>

    <!-- 底部固定：保存 -->
    <view class="pe__bottom">
      <view
        class="ft-btn ft-btn--primary ft-btn--block"
        :class="{ 'ft-btn--disabled': saving }"
        @click="save"
      >
        保存修改
      </view>
    </view>

    <!-- 目标组次弹层 -->
    <view v-if="targetSheet.show" class="ts-mask" @click="targetSheet.show = false" />
    <view v-if="targetSheet.show" class="ts">
      <view class="ts__handle" />
      <text class="ts__title">目标组次</text>
      <view class="ts__row">
        <text class="ts__label">组数</text>
        <input v-model="targetSheet.sets" type="number" class="ts__input ft-num" placeholder="–" />
      </view>
      <view class="ts__row">
        <text class="ts__label">每组次数</text>
        <input v-model="targetSheet.reps" type="number" class="ts__input ft-num" placeholder="–" />
      </view>
      <text class="ts__hint">留空 = 不设目标</text>
      <view class="ft-btn ft-btn--primary ft-btn--block" @click="confirmTarget">确定</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.pe__body {
  padding: 28rpx 32rpx 0;
}

/* ── 顶部栏 ── */
.pe__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4rpx 0 20rpx;
}

.pe__back {
  font-size: 56rpx;
  line-height: 1;
  color: $app-ink;
  width: 64rpx;
}

.pe__head-title {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.pe__del {
  width: 64rpx;
  text-align: right;
  font-size: $text-sm;
  color: #c4453c;
}

.pe__head-space {
  width: 64rpx;
}

/* ── meta 行 ── */
.pe__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4rpx 22rpx;
  font-size: 22rpx;
  letter-spacing: 2rpx;
  color: $app-ink-subtle;
}

/* ── 名称卡 ── */
.namecard {
  background: $app-surface;
  border: 1rpx solid $app-line-soft;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  padding: 30rpx 32rpx 24rpx;
  margin-bottom: 36rpx;
}

.namecard__label {
  font-size: 22rpx;
  color: $app-ink-subtle;
}

.namecard__input-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  border-bottom: 2rpx solid $app-ink;
  padding: 14rpx 0 12rpx;
}

.namecard__input {
  flex: 1;
  font-size: 44rpx;
  font-weight: 700;
  color: $app-ink;
}

.namecard__ph {
  color: $app-ink-subtle;
  font-weight: 400;
}

.namecard__pen {
  font-size: $text-md;
  color: $app-ink-muted;
}

.namecard__stats {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 20rpx;
  font-size: 22rpx;
  color: $app-ink-muted;
}

/* ── 编排 ── */
.pe__sect {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 4rpx 20rpx;
}

.pe__sect-l {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.pe__sect-sub {
  font-size: $text-sm;
  font-weight: 400;
  color: $app-ink-muted;
}

.pe__sect-r {
  font-size: 22rpx;
  letter-spacing: 2rpx;
  color: $app-ink-subtle;
}

.elist {
  background: $app-surface;
  border: 1rpx solid $app-line-soft;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  overflow: hidden;
  margin-bottom: 24rpx;
}

.erow {
  display: flex;
  align-items: center;
  padding: 26rpx 28rpx;
}

.erow + .erow {
  border-top: 1rpx solid $app-line;
}

.erow__idx {
  width: 56rpx;
  font-size: $text-sm;
  font-weight: 600;
  color: $app-ink-muted;
  flex-shrink: 0;
}

.erow__mid {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  min-width: 0;
}

.erow__name {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.erow__sub {
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.erow__target {
  font-size: $text-sm;
  color: $app-ink-muted;
  flex-shrink: 0;
  margin-left: 16rpx;
}

/* ── 添加动作 ── */
.pe__add {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  height: 96rpx;
  border: 2rpx dashed $app-line;
  border-radius: $radius-lg;
  color: $app-ink-muted;
  font-size: $text-base;
  font-weight: 500;
  margin-bottom: 48rpx;
}

.pe__add-plus {
  font-size: $text-md;
}

.pe__foot-note {
  text-align: center;
  font-size: 20rpx;
  letter-spacing: 2rpx;
  color: $app-ink-subtle;
  padding: 8rpx 0;
}

.pe__pad {
  height: 260rpx;
}

/* ── 底部保存 ── */
.pe__bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: $app-canvas;
  padding: 20rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
}

/* ── 目标弹层 ── */
.ts-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(23, 25, 29, 0.4);
  z-index: 90;
}

.ts {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: $app-surface;
  border-radius: 40rpx 40rpx 0 0;
  padding: 20rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
  z-index: 91;
}

.ts__handle {
  width: 72rpx;
  height: 8rpx;
  border-radius: 4rpx;
  background: $app-line;
  margin: 0 auto 28rpx;
}

.ts__title {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  color: $app-ink;
  padding-bottom: 24rpx;
}

.ts__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0;
  border-bottom: 1rpx solid $app-line-soft;
}

.ts__label {
  font-size: $text-md;
  color: $app-ink;
}

.ts__input {
  width: 160rpx;
  text-align: right;
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.ts__hint {
  display: block;
  font-size: $text-xs;
  color: $app-ink-subtle;
  padding: 16rpx 0 32rpx;
}
</style>
