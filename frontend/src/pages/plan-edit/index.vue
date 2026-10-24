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
import FtButton from '@/components/FtButton.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import FtSheet from '@/components/FtSheet.vue'

// ── 计划编辑/新建（晴窗，ADR 0041）──

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
</script>

<template>
  <view class="pe">
    <view class="pe__body">
      <FtPageHeader :title="isEdit ? '编辑计划' : '新建计划'" back>
        <template #right>
          <view v-if="isEdit" class="pill pill--danger" role="button" @click="removePlan">删除</view>
        </template>
      </FtPageHeader>

      <!-- 名称 -->
      <view class="glass block">
        <text class="block__label">计划名称</text>
        <input
          v-model="name"
          class="name-input"
          placeholder="如：胸 & 三头"
          placeholder-class="ph"
          maxlength="32"
        />
        <text class="block__stat ft-num">{{ rows.length }} 个动作 · 共 {{ totalSets }} 组</text>
      </view>

      <!-- 编排：标题行右端是高级设置（循环组）入口 -->
      <view class="glass elist">
        <view class="elist__head">
          <view class="elist__title">
            <text class="card-title">动作编排</text>
            <text class="elist__hint">按顺序执行 · 点一行调整</text>
          </view>
          <view class="adv" role="button" @click="openCircuit">
            <FtIcon name="sliders" :size="15" tone="muted" />
            <text>循环组</text>
            <text v-if="circuitCount > 0" class="adv__n ft-num">{{ circuitCount }}</text>
          </view>
        </view>

        <view v-for="(r, i) in rows" :key="r.exerciseId" class="erow" role="button" @click="onRowTap(i)">
          <text class="erow__idx ft-num">{{ i + 1 }}</text>
          <view class="erow__mid">
            <view class="erow__line">
              <text class="erow__name">{{ r.name }}</text>
              <text v-if="r.circuitNo != null" class="badge">循环{{ circuitLabel(r.circuitNo) }}</text>
            </view>
            <text class="erow__sub">{{ muscleLabel(r.muscle) }}</text>
          </view>
          <text class="erow__target ft-num">{{ targetText(r) }}</text>
        </view>

        <FtEmpty v-if="rows.length === 0" title="还没有动作" sub="点下面「添加动作」，从动作库里挑。" />

        <view class="addbtn" role="button" @click="addExercise">
          <FtIcon name="plus" :size="16" :stroke="2" />
          <text>添加动作</text>
        </view>
      </view>
    </view>

    <!-- 保存：贴底悬浮的主按钮，与编辑食物页同一位置 -->
    <view class="pe__bottom">
      <FtButton variant="primary" block :disabled="saving" class="pe__save" @click="save">
        {{ isEdit ? '保存修改' : '创建计划' }}
      </FtButton>
    </view>

    <!-- 目标组次弹层（逐组，ADR 0027／0031） -->
    <FtSheet
      v-if="targetSheet.show"
      title="目标组次"
      :sub="rows[targetSheet.index]?.name"
      @close="targetSheet.show = false"
    >
      <scroll-view scroll-y class="ts__scroll">
        <view class="field">
          <text class="field__label">组数</text>
          <input
            :value="targetSheet.count"
            type="number"
            class="field__input ft-num"
            placeholder="不设"
            placeholder-class="ph"
            @input="onCountInput"
          />
        </view>

        <!-- 逐组次数／重量：行数由上面的组数决定 -->
        <view v-if="targetSheet.rows.length > 0" class="sets">
          <view class="sets__head">
            <text class="sets__title">每组目标</text>
            <view class="sets__fill" role="button" @click="fillAllSets">按第 1 组填满</view>
          </view>
          <view v-for="(s, i) in targetSheet.rows" :key="i" class="setrow">
            <text class="setrow__idx ft-num">第 {{ i + 1 }} 组</text>
            <!-- 爬楼：一层一栏（ADR 0031）；力量：次数／重量两栏 -->
            <view v-if="editingIsFloors" class="setrow__field">
              <input v-model="s.floors" type="number" class="setrow__input ft-num" placeholder="–" placeholder-class="ph" />
              <text class="setrow__unit">层</text>
            </view>
            <template v-else>
              <view class="setrow__field">
                <input v-model="s.reps" type="number" class="setrow__input ft-num" placeholder="–" placeholder-class="ph" />
                <text class="setrow__unit">次</text>
              </view>
              <view class="setrow__field">
                <input v-model="s.weight" type="digit" class="setrow__input ft-num" placeholder="–" placeholder-class="ph" />
                <text class="setrow__unit">kg</text>
              </view>
            </template>
          </view>
        </view>

        <text v-if="editingIsFloors" class="ts__hint">
          每组层数可留空（全空＝只记组数）；开始训练时按各组目标预填
        </text>
        <text v-else-if="editingIsCardio" class="ts__hint">有氧动作只记组数，不设每组次数与重量</text>
        <text v-else class="ts__hint">组数留空＝不设目标；开始训练时按各组目标预填</text>
      </scroll-view>
      <FtButton variant="primary" block class="ts__btn" @click="confirmTarget">确定</FtButton>
    </FtSheet>

    <!-- 高级设置：循环组（ADR 0028） -->
    <FtSheet
      v-if="circuitSheet.show"
      title="循环组"
      sub="组号相同的动作轮流做：做完 A 第 1 组马上做 B 第 1 组，一轮走完再进下一轮。轮数按组内最多的组数算。"
      @close="circuitSheet.show = false"
    >
      <scroll-view scroll-y class="ts__scroll">
        <view class="cs">
          <view v-for="(r, i) in rows" :key="r.exerciseId" class="cs__row">
            <view class="cs__main">
              <text class="cs__name">{{ r.name }}</text>
              <text v-if="isCardio(r)" class="cs__sub">有氧动作不参与循环</text>
              <view v-else-if="r.circuitNo != null" class="cs__nos" role="radiogroup" aria-label="循环组号">
                <view
                  v-for="no in MAX_CIRCUIT"
                  :key="no"
                  class="cs__no ft-num"
                  :class="{ 'cs__no--on': r.circuitNo === no }"
                  role="radio"
                  :aria-checked="r.circuitNo === no"
                  @click.stop="setCircuitNo(i, no)"
                >
                  {{ CIRCUIT_LABELS[no - 1] }}
                </view>
              </view>
            </view>
            <view
              class="cs__toggle"
              :class="{ 'cs__toggle--on': r.circuitNo != null, 'is-off': isCardio(r) }"
              role="switch"
              :aria-checked="r.circuitNo != null"
              @click.stop="toggleCircuit(i)"
            >
              {{ r.circuitNo != null ? '退出循环' : '加入循环' }}
            </view>
          </view>
        </view>
      </scroll-view>
      <FtButton variant="primary" block class="ts__btn" @click="circuitSheet.show = false">完成</FtButton>
    </FtSheet>
  </view>
</template>

<style lang="scss" scoped>
.pe__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(200rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-lg;
  box-shadow: $glass-shadow;
  padding: 32rpx 36rpx;
}

.card-title {
  font-size: $text-title;
  font-weight: $weight-bold;
  color: $app-ink;
}

.ph {
  color: $app-ink-subtle;
  font-weight: $weight-regular;
}

/* 页头右侧的胶囊按钮；删除用危险色字，不上色块（一屏的色块留给保存） */
.pill {
  @include glass($glass-bg-strong);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 16rpx 28rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $app-ink-2;
}

.pill--danger {
  color: $app-danger;
}

.pill:active {
  opacity: 0.7;
}

/* ── 名称 ── */
.block__label {
  display: block;
  padding-bottom: 12rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.name-input {
  width: 100%;
  height: 92rpx;
  box-sizing: border-box;
  padding: 0 24rpx;
  border-radius: $radius-md;
  background: $app-surface;
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $app-ink;
}

.block__stat {
  display: block;
  padding-top: 16rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* ── 编排 ── */
.elist {
  padding: 28rpx 28rpx 28rpx;
}

.elist__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
  padding: 0 8rpx 12rpx;
}

.elist__title {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.elist__hint {
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* 循环组入口：浅底小胶囊；已有动作进了循环就挂个数 */
.adv {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 10rpx 20rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-sm;
  color: $app-ink-2;
}

.adv:active {
  opacity: 0.6;
}

.adv__n {
  min-width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  padding: 0 8rpx;
  box-sizing: border-box;
  border-radius: $radius-pill;
  background: $app-ink;
  color: $app-surface;
  font-size: $text-xs;
  text-align: center;
}

.erow {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 8rpx;
  border-top: 1rpx solid $app-line-soft;
}

.erow:active {
  opacity: 0.6;
}

.erow__idx {
  width: 40rpx;
  flex-shrink: 0;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

.erow__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.erow__line {
  display: flex;
  align-items: center;
  gap: 12rpx;
  min-width: 0;
}

.erow__name {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.badge {
  flex: none;
  padding: 2rpx 12rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.erow__sub {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.erow__target {
  flex-shrink: 0;
  max-width: 280rpx;
  text-align: right;
  font-size: $text-sm;
  color: $app-ink-2;
}

.elist :deep(.ft-empty-state) {
  padding: 24rpx 8rpx 8rpx;
}

/* 添加动作：虚线框，与记录弹层／组合弹层的「添加食物」同一个 */
.addbtn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  height: 88rpx;
  margin-top: 16rpx;
  border: 2rpx dashed $app-line-dash;
  border-radius: $radius-md;
  font-size: $text-base;
  color: $app-ink-muted;
}

.addbtn:active {
  opacity: 0.6;
}

/* ── 底部保存 ── */
.pe__bottom {
  position: fixed;
  left: $tabbar-side;
  right: $tabbar-side;
  bottom: calc(#{$tabbar-gap} + env(safe-area-inset-bottom));
}

.pe__bottom .pe__save {
  height: 104rpx;
  font-size: 30rpx;
}

/* ── 弹层（壳是 FtSheet，分组是白卡） ── */
.ts__scroll {
  flex: 1;
  min-height: 0;
  max-height: 58vh;
}

.pe .ts__btn {
  flex-shrink: 0;
  margin-top: 28rpx;
}

.ts__hint {
  display: block;
  padding: 20rpx 8rpx 0;
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

.field {
  display: flex;
  align-items: center;
  gap: 20rpx;
  height: 92rpx;
  padding: 0 28rpx;
  background: $app-surface;
  border-radius: $radius-lg;
}

.field__label {
  flex-shrink: 0;
  font-size: $text-base;
  color: $app-ink-muted;
}

.field__input {
  flex: 1;
  text-align: right;
  font-size: $text-lg;
  color: $app-ink;
}

/* 逐组目标 */
.sets {
  margin-top: 20rpx;
  padding: 8rpx 28rpx 12rpx;
  background: $app-surface;
  border-radius: $radius-lg;
}

.sets__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0 12rpx;
}

.sets__title {
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $app-ink;
}

.sets__fill {
  padding: 8rpx 20rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-sm;
  color: $app-ink-2;
}

.sets__fill:active {
  opacity: 0.6;
}

.setrow {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 14rpx 0;
  border-top: 1rpx solid $app-line-soft;
}

.setrow__idx {
  width: 104rpx;
  flex-shrink: 0;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.setrow__field {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8rpx;
  height: 68rpx;
  padding: 0 20rpx;
  border-radius: $radius-sm;
  background: $app-surface-2;
}

.setrow__input {
  flex: 1;
  min-width: 0;
  text-align: right;
  font-size: $text-md;
  color: $app-ink;
}

.setrow__unit {
  flex-shrink: 0;
  font-size: $text-xs;
  color: $app-ink-muted;
}

/* 循环组 */
.cs {
  padding: 0 28rpx;
  background: $app-surface;
  border-radius: $radius-lg;
}

.cs__row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 0;
}

.cs__row + .cs__row {
  border-top: 1rpx solid $app-line-soft;
}

.cs__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.cs__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.cs__sub {
  font-size: $text-sm;
  color: $app-ink-subtle;
}

/* 组号 A–D：一组小分段（选中＝深字 ＋ 浅胶囊底） */
.cs__nos {
  display: flex;
  align-self: flex-start;
  padding: 4rpx;
  border: 1rpx solid $app-line;
  border-radius: $radius-pill;
}

.cs__no {
  width: 64rpx;
  height: 48rpx;
  line-height: 48rpx;
  text-align: center;
  border-radius: $radius-pill;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.cs__no--on {
  background: $app-surface-2;
  color: $app-ink;
}

.cs__toggle {
  flex-shrink: 0;
  padding: 12rpx 24rpx;
  border: 1rpx solid $app-line;
  border-radius: $radius-pill;
  font-size: $text-sm;
  color: $app-ink-2;
}

.cs__toggle--on {
  color: $app-ink-muted;
}

.cs__toggle.is-off {
  opacity: 0.4;
}

.cs__toggle:active {
  opacity: 0.6;
}
</style>
