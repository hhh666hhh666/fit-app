<script setup lang="ts">
import { PALETTE } from '@/utils/palette'
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { createPlan, deletePlan, getPlan, updatePlan } from '@/api/plan'
import type { PlanSavePayload } from '@/api/plan'
import { usePickStore } from '@/stores/pick'
import { muscleLabel } from '@/utils/format'
import { formatSetTargets, targetModeOf } from '@/utils/plan-target'
import { ensureLogin } from '@/utils/guard'
import FtIcon from '@/components/FtIcon.vue'

// ── 计划编辑/新建（设计稿：.workbuddy/stitch/screen/plan-edit.png）──

/** 一组的目标：三项都可空（只记次数、只记重量、只记层数都合法）。 */
interface SetTarget {
  reps: number | null
  weight: number | null
  /** 该组目标层数：仅爬楼（`metric = floors`）使用（ADR 0031）。 */
  floors: number | null
}

interface Row {
  exerciseId: number
  name: string
  muscle: string
  /** 记录口径（`floors`＝爬楼）；新建动作时未知，判有氧用肌群兜底。 */
  metric: string | null
  /** 逐组目标；**长度即组数**，空数组＝不设目标（ADR 0027）。 */
  sets: SetTarget[]
  /** 循环组号；null＝不参与循环（ADR 0028）。 */
  circuitNo: number | null
}

/** 循环组号 → 显示字母（组号从 1 起）。 */
const CIRCUIT_LABELS = ['A', 'B', 'C', 'D']
const MAX_SETS = 20
const MAX_CIRCUIT = 4
/** 单组层数上限（2026-10-19 拍定，与后端 `PlanSaveDTO.SetDTO.floors` 同一口径）。 */
const MAX_FLOORS = 51102

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
      metric: e.metric ?? null,
      sets: toSets(e.sets, e.targetSets, e.targetReps, e.targetWeight),
      circuitNo: e.circuitNo ?? null
    }))
  } catch {
    // unwrap 已提示
  }
}

/** 后端一律返回 sets；老数据（只有组数）在这里兜一次，免得打开计划看不到目标。 */
function toSets(
  sets: { reps?: number | null; weight?: number | null; floors?: number | null }[] | null | undefined,
  targetSets?: number | null,
  targetReps?: number | null,
  targetWeight?: number | null
): SetTarget[] {
  if (sets && sets.length > 0) {
    return sets.map((s) => ({
      reps: s.reps ?? null,
      weight: s.weight ?? null,
      floors: s.floors ?? null
    }))
  }
  if (targetSets != null && targetSets > 0) {
    return Array.from({ length: targetSets }, () => ({
      reps: targetReps ?? null,
      weight: targetWeight ?? null,
      floors: null
    }))
  }
  return []
}

// 从动作库（点选模式）返回：取走选中动作并入编排；重复则提示
onShow(() => {
  if (!ensureLogin()) return
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
    metric: null,
    sets: [],
    circuitNo: null
  })
})

const totalSets = computed(() => rows.value.reduce((sum, r) => sum + r.sets.length, 0))

const pad2 = (n: number) => String(n).padStart(2, '0')

/** 有氧动作（按层数录或纯有氧肌群）：不参与循环组（ADR 0028）。 */
function isCardio(r: Row): boolean {
  return r.metric === 'floors' || r.muscle === 'cardio'
}

function circuitLabel(no: number | null): string {
  if (no == null) return ''
  return CIRCUIT_LABELS[no - 1] ?? String(no)
}

/**
 * 计划行摘要（ADR 0027／0031）：折叠规则抽在 `utils/plan-target` 里，与会话页、预览弹层共用同一份念法。
 */
function targetText(r: Row): string {
  return formatSetTargets(r.sets, targetModeOf(r.metric, r.muscle))
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

// ── 目标组次弹层（逐组编辑，ADR 0027／0031）──
interface SheetRow {
  reps: string
  weight: string
  /** 层数：仅爬楼行使用，其余口径一直是空串。 */
  floors: string
}

const targetSheet = ref<{ show: boolean; index: number; count: string; rows: SheetRow[] }>({
  show: false,
  index: -1,
  count: '',
  rows: []
})

function openTargetEditor(i: number) {
  const r = rows.value[i]
  const mode = targetModeOf(r.metric, r.muscle)
  targetSheet.value = {
    show: true,
    index: i,
    count: r.sets.length > 0 ? String(r.sets.length) : '',
    // 纯有氧（非 floors）只记组数，不铺逐组行；爬楼铺「层数」行；力量铺「次数／重量」行
    rows:
      mode === 'cardio'
        ? []
        : r.sets.map((s) => ({
            reps: s.reps != null ? String(s.reps) : '',
            weight: s.weight != null ? String(s.weight) : '',
            floors: s.floors != null ? String(s.floors) : ''
          }))
  }
}

/** 当前正在编辑的动作按哪种口径（决定提示语与逐组行的字段）。 */
const editingMode = computed(() => {
  const i = targetSheet.value.index
  return i >= 0 && rows.value[i] ? targetModeOf(rows.value[i].metric, rows.value[i].muscle) : 'strength'
})

/** 纯有氧（只记组数、没有逐组行）。 */
const editingIsCardio = computed(() => editingMode.value === 'cardio')
/** 爬楼：逐组行只有一个「层数」栏。 */
const editingIsFloors = computed(() => editingMode.value === 'floors')

/**
 * 组数输入：改数字即增删行；新行默认沿用上一行的值。
 * 事件形态在 H5 与 App 端不同（uni-app 给 `detail.value`，原生 input 给 `target.value`），两处都取。
 */
function onCountInput(e: any) {
  const raw: string = e?.detail?.value ?? e?.target?.value ?? ''
  targetSheet.value.count = raw
  // 纯有氧只记组数：不铺逐组行（没有可填的栏）
  if (editingIsCardio.value) return
  const n = raw.trim() === '' ? 0 : Math.min(MAX_SETS, Math.max(0, Math.floor(Number(raw) || 0)))
  const list = targetSheet.value.rows
  if (n > list.length) {
    const last = list[list.length - 1] ?? { reps: '', weight: '', floors: '' }
    while (list.length < n) list.push({ ...last })
  } else if (n < list.length) {
    list.splice(n)
  }
}

/** 整组填充：把第 1 组的值刷到所有组（「4 组里 3 组一样」的常用情况）。 */
function fillAllSets() {
  const list = targetSheet.value.rows
  if (list.length === 0) return
  const first = list[0]
  list.forEach((r) => {
    r.reps = first.reps
    r.weight = first.weight
    r.floors = first.floors
  })
}

function confirmTarget() {
  const t = targetSheet.value
  if (t.index < 0) return
  const row = rows.value[t.index]
  const mode = targetModeOf(row.metric, row.muscle)

  if (t.count.trim() === '') {
    row.sets = []
    targetSheet.value.show = false
    return
  }
  const n = Number(t.count)
  if (!Number.isInteger(n) || n < 1 || n > MAX_SETS) {
    uni.showToast({ title: `组数 1–${MAX_SETS}`, icon: 'none' })
    return
  }

  // 纯有氧（口径不是 floors）：只记组数，组内数值一律留空
  if (mode === 'cardio') {
    row.sets = Array.from({ length: n }, () => ({ reps: null, weight: null, floors: null }))
    targetSheet.value.show = false
    return
  }

  // 爬楼：只校验层数；每组可留空（全空＝只记组数），不做「至少填一项」那套要求
  if (mode === 'floors') {
    const parsed: SetTarget[] = []
    for (const s of t.rows) {
      const floors = s.floors.trim() === '' ? null : Number(s.floors)
      if (floors != null && (!Number.isInteger(floors) || floors < 1 || floors > MAX_FLOORS)) {
        uni.showToast({ title: `每组层数 1–${MAX_FLOORS}`, icon: 'none' })
        return
      }
      parsed.push({ reps: null, weight: null, floors })
    }
    row.sets = parsed
    targetSheet.value.show = false
    return
  }

  const parsed: SetTarget[] = []
  for (const s of t.rows) {
    const reps = s.reps.trim() === '' ? null : Number(s.reps)
    const weight = s.weight.trim() === '' ? null : Number(s.weight)
    if (reps != null && (!Number.isInteger(reps) || reps < 1 || reps > 100)) {
      uni.showToast({ title: '每组次数 1–100', icon: 'none' })
      return
    }
    if (weight != null && (!Number.isFinite(weight) || weight < 0 || weight > 999.99)) {
      uni.showToast({ title: '每组重量 0–999.9 kg', icon: 'none' })
      return
    }
    parsed.push({ reps, weight, floors: null })
  }
  // 填了组数却一个值都没有 = 把「不设目标」表达成了 N 个空行，没有意义
  if (parsed.every((s) => s.reps == null && s.weight == null)) {
    uni.showToast({ title: '每组的次数或重量至少填一项', icon: 'none' })
    return
  }
  row.sets = parsed
  targetSheet.value.show = false
}

// ── 高级设置：循环组（ADR 0028）──
const circuitSheet = ref({ show: false })

function openCircuit() {
  if (rows.value.length === 0) {
    uni.showToast({ title: '先添加动作', icon: 'none' })
    return
  }
  circuitSheet.value.show = true
}

const circuitCount = computed(() => rows.value.filter((r) => r.circuitNo != null).length)

function toggleCircuit(i: number) {
  const r = rows.value[i]
  if (isCardio(r)) {
    uni.showToast({ title: '有氧动作不参与循环', icon: 'none' })
    return
  }
  r.circuitNo = r.circuitNo == null ? 1 : null
}

function setCircuitNo(i: number, no: number) {
  rows.value[i].circuitNo = no
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
  const payload: PlanSavePayload = {
    name: planName,
    exercises: rows.value.map((r) => ({
      exerciseId: r.exerciseId,
      sets: r.sets.map((s) => ({ reps: s.reps, weight: s.weight, floors: s.floors })),
      circuitNo: r.circuitNo
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
    confirmColor: PALETTE.danger,
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

      <!-- 编排标题：右端是高级设置入口（齿轮） -->
      <view class="pe__sect">
        <text class="pe__sect-l"
          >动作编排 <text class="pe__sect-sub">[按顺序执行]</text></text
        >
        <view class="pe__gear" @click="openCircuit">
          <FtIcon name="sliders" :size="15" tone="subtle" />
          <text class="pe__gear-text">高级</text>
          <text v-if="circuitCount > 0" class="pe__gear-dot ft-num">{{ circuitCount }}</text>
        </view>
      </view>

      <!-- 动作行 -->
      <view v-if="rows.length > 0" class="elist">
        <view v-for="(r, i) in rows" :key="r.exerciseId" class="erow" @click="onRowTap(i)">
          <text class="erow__idx ft-num">{{ i + 1 }}</text>
          <view class="erow__mid">
            <view class="erow__name-row">
              <text class="erow__name">{{ r.name }}</text>
              <text v-if="r.circuitNo != null" class="erow__circuit ft-num">
                循环{{ circuitLabel(r.circuitNo) }}
              </text>
            </view>
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

    <!-- 目标组次弹层（逐组） -->
    <view v-if="targetSheet.show" class="ts-mask" @click="targetSheet.show = false" />
    <view v-if="targetSheet.show" class="ts">
      <view class="ts__handle" />
      <text class="ts__title">目标组次</text>
      <view class="ts__row">
        <text class="ts__label">组数</text>
        <input
          :value="targetSheet.count"
          type="number"
          class="ts__input ft-num"
          placeholder="–"
          @input="onCountInput"
        />
      </view>

      <!-- 逐组次数／重量：行数由上面的组数决定 -->
      <view v-if="targetSheet.rows.length > 0" class="ts__sets">
        <view class="ts__sets-head">
          <text class="ts__sets-title">每组目标</text>
          <text class="ts__fill" @click="fillAllSets">整组填充</text>
        </view>
        <view v-for="(s, i) in targetSheet.rows" :key="i" class="ts__setrow">
          <text class="ts__setidx ft-num">第 {{ i + 1 }} 组</text>
          <!-- 爬楼：一层一栏（ADR 0031）；力量：次数／重量两栏 -->
          <view v-if="editingIsFloors" class="ts__setfield">
            <input v-model="s.floors" type="number" class="ts__setinput ft-num" placeholder="–" />
            <text class="ts__setunit">层</text>
          </view>
          <template v-else>
            <view class="ts__setfield">
              <input v-model="s.reps" type="number" class="ts__setinput ft-num" placeholder="–" />
              <text class="ts__setunit">次</text>
            </view>
            <view class="ts__setfield">
              <input v-model="s.weight" type="digit" class="ts__setinput ft-num" placeholder="–" />
              <text class="ts__setunit">kg</text>
            </view>
          </template>
        </view>
      </view>

      <text v-if="editingIsFloors" class="ts__hint">
        每组层数可留空（全空＝只记组数）；开始训练时按各组目标预填
      </text>
      <text v-else-if="editingIsCardio" class="ts__hint">
        有氧动作只记组数，不设每组次数与重量
      </text>
      <text v-else class="ts__hint">组数留空 = 不设目标；开始训练时按各组目标预填</text>
      <view class="ft-btn ft-btn--primary ft-btn--block" @click="confirmTarget">确定</view>
    </view>

    <!-- 高级设置：循环组 -->
    <view v-if="circuitSheet.show" class="ts-mask" @click="circuitSheet.show = false" />
    <view v-if="circuitSheet.show" class="ts ts--tall">
      <view class="ts__handle" />
      <text class="ts__title">高级设置 · 循环组</text>
      <text class="ts__desc">
        组号相同的动作会轮流做：做完 A 第 1 组马上做 B 第 1 组，一轮走完再进下一轮。轮数按组内最多的组数算。
      </text>
      <scroll-view scroll-y class="cs__list">
        <view v-for="(r, i) in rows" :key="r.exerciseId" class="cs__row">
          <view class="cs__main">
            <text class="cs__name">{{ r.name }}</text>
            <text v-if="isCardio(r)" class="cs__sub">有氧动作不参与循环</text>
            <view v-else-if="r.circuitNo != null" class="cs__nos">
              <text
                v-for="no in MAX_CIRCUIT"
                :key="no"
                class="cs__no ft-num"
                :class="{ 'cs__no--on': r.circuitNo === no }"
                @click.stop="setCircuitNo(i, no)"
              >
                {{ CIRCUIT_LABELS[no - 1] }}
              </text>
            </view>
          </view>
          <view
            class="cs__toggle"
            :class="{
              'cs__toggle--on': r.circuitNo != null,
              'cs__toggle--off': isCardio(r)
            }"
            @click.stop="toggleCircuit(i)"
          >
            {{ r.circuitNo != null ? `循环${circuitLabel(r.circuitNo)}` : '加入循环' }}
          </view>
        </view>
      </scroll-view>
      <view class="ft-btn ft-btn--primary ft-btn--block" @click="circuitSheet.show = false">
        完成
      </view>
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
  align-items: center;
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

/* 齿轮入口：高级设置 */
.pe__gear {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 6rpx 10rpx;
  border-radius: $radius-sm;
}

.pe__gear-text {
  font-size: 22rpx;
  letter-spacing: 2rpx;
  color: $app-ink-subtle;
}

.pe__gear-dot {
  min-width: 28rpx;
  height: 28rpx;
  line-height: 28rpx;
  padding: 0 6rpx;
  border-radius: 14rpx;
  background: $app-ink;
  color: $app-surface;
  font-size: 18rpx;
  text-align: center;
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

.erow__name-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.erow__name {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.erow__circuit {
  font-size: 18rpx;
  padding: 2rpx 8rpx;
  border-radius: 6rpx;
  background: $app-canvas;
  border: 1rpx solid $app-line;
  color: $app-ink-muted;
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
  max-width: 300rpx;
  text-align: right;
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

/* ── 弹层（目标组次 / 高级设置共用） ── */
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
  max-height: 82vh;
  overflow-y: auto;
}

.ts--tall {
  max-height: 86vh;
  display: flex;
  flex-direction: column;
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

.ts__desc {
  display: block;
  font-size: $text-xs;
  line-height: 1.6;
  color: $app-ink-muted;
  padding-bottom: 20rpx;
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

/* ── 逐组目标列表 ── */
.ts__sets {
  padding-top: 8rpx;
}

.ts__sets-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0 8rpx;
}

.ts__sets-title {
  font-size: $text-sm;
  font-weight: 600;
  color: $app-ink;
}

.ts__fill {
  font-size: $text-xs;
  color: $app-ink-muted;
  padding: 6rpx 14rpx;
  border: 1rpx solid $app-line;
  border-radius: 999rpx;
}

.ts__setrow {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 12rpx 0;
  border-bottom: 1rpx solid $app-line-soft;
}

.ts__setidx {
  width: 110rpx;
  flex-shrink: 0;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.ts__setfield {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6rpx;
  background: $app-canvas;
  border: 1rpx solid $app-line-soft;
  border-radius: $radius-sm;
  padding: 10rpx 12rpx;
}

.ts__setinput {
  flex: 1;
  min-width: 0;
  font-size: $text-sm;
  font-weight: 600;
  color: $app-ink;
  text-align: right;
}

.ts__setunit {
  font-size: 18rpx;
  color: $app-ink-subtle;
}

/* ── 高级设置：循环组 ── */
.cs__list {
  max-height: 52vh;
}

.cs__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid $app-line-soft;
}

.cs__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.cs__name {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.cs__sub {
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.cs__nos {
  display: flex;
  gap: 10rpx;
}

.cs__no {
  width: 48rpx;
  height: 40rpx;
  line-height: 40rpx;
  text-align: center;
  border-radius: $radius-sm;
  border: 1rpx solid $app-line;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.cs__no--on {
  background: $app-ink;
  border-color: $app-ink;
  color: $app-surface;
}

.cs__toggle {
  flex-shrink: 0;
  padding: 12rpx 20rpx;
  border-radius: 999rpx;
  border: 1rpx solid $app-line;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.cs__toggle--on {
  background: $app-ink;
  border-color: $app-ink;
  color: $app-surface;
}

.cs__toggle--off {
  opacity: 0.4;
}
</style>
