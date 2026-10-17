<script setup lang="ts">
// 训练详情（从日志页点进来；接 GET /api/workouts/{id}）。
// 右上角「编辑」进入就地编辑：标题、各组重量/次数、备注可改；按钮变 ✓，点 ✓ 保存（PUT /api/workouts/{id}）。
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getWorkout, updateWorkout, updateWorkoutNote } from '@/api/workout'
import type { WorkoutDetail } from '@/api/workout'
import { formatClock, formatDateLabel, formatThousands } from '@/utils/format'
import { metricKindOf, metricLabel, metricUnit, metricValue } from '@/utils/metric'
import { ensureLogin } from '@/utils/guard'

const detail = ref<WorkoutDetail | null>(null)
const loading = ref(true)
const notFound = ref(false)

// ── 编辑态：草稿与只读详情分离，改坏了不脏原数据；点 ✓ 才提交 ──
const editing = ref(false)
const saving = ref(false)
/** dur 是「分:秒」文本（如 01:42）；留空＝这条没有用时（ADR 0022）。 */
type DraftSet = { weight: string; reps: string; floors: string; dur: string }
type DraftBlock = {
  exerciseId: number | null
  name: string
  cardio?: boolean
  /** 记录口径：`floors`＝按层数录（爬楼）。见 ADR 0025。 */
  metric?: string | null
  sets: DraftSet[]
}
const draft = ref<{ title: string; note: string; calories: string; blocks: DraftBlock[] }>({
  title: '',
  note: '',
  /** 本次消耗（大卡）文本：留空＝没填（与「填 0」是两件事）。 */
  calories: '',
  blocks: []
})

const totalSets = computed(() =>
  detail.value ? detail.value.blocks.reduce((sum, b) => sum + b.sets.length, 0) : 0
)

/** 编辑态随草稿实时变（改重量/次数看得到总容量跟着动）；有氧块不计入（ADR 0023）。 */
const shownSets = computed(() =>
  editing.value ? draft.value.blocks.reduce((s, b) => s + b.sets.length, 0) : totalSets.value
)
/** 整场有没有力量动作——纯有氧场次的三格第三格显示「—」。 */
const hasLift = computed(() => {
  const blocks = editing.value ? draft.value.blocks : (detail.value?.blocks ?? [])
  return blocks.some((b) => !b.cardio)
})
const shownVolume = computed(() => {
  if (!editing.value) return detail.value ? Number(detail.value.totalVolume) : 0
  return draft.value.blocks
    .filter((b) => !b.cardio)
    .reduce(
      (sum, b) => sum + b.sets.reduce((v, x) => v + Number(x.weight || 0) * Number(x.reps || 0), 0),
      0
    )
})

/** 层数口径（爬楼）的合计：编辑态随草稿变，只读态取后端值（ADR 0025）。 */
const shownFloors = computed(() =>
  editing.value
    ? draft.value.blocks
        .filter((b) => b.metric === 'floors')
        .reduce((sum, b) => sum + b.sets.reduce((v, x) => v + (Number(x.floors) || 0), 0), 0)
    : (detail.value?.blocks ?? [])
        .filter((b) => b.metric === 'floors')
        .reduce((sum, b) => sum + b.sets.reduce((v, s) => v + (Number(s.floors) || 0), 0), 0)
)
const metricKind = computed(() => metricKindOf(hasLift.value, shownFloors.value))
const volumeLabel = computed(() => metricLabel(metricKind.value))
const volumeValue = computed(() =>
  metricValue(metricKind.value, shownVolume.value, shownFloors.value)
)
const volumeUnit = computed(() => metricUnit(metricKind.value))

/** 单块指标文案：层数口径给层数、其他有氧给「—」（编辑态与只读态通用）。 */
function blockMetricText(block: {
  cardio?: boolean
  metric?: string | null
  sets: { weight: number | string; reps: number | string; floors?: number | string | null }[]
}): string {
  if (block.metric === 'floors') {
    const floors = block.sets.reduce((v, s) => v + (Number(s.floors) || 0), 0)
    return `${formatThousands(floors)} 层`
  }
  if (block.cardio) return '—'
  const sum = block.sets.reduce((v, s) => v + Number(s.weight || 0) * Number(s.reps || 0), 0)
  return `${formatThousands(sum)} kg`
}

/** 组次只读文案：层数口径显示层数，其余显示「重量 × 次数」。 */
function setReadText(
  x: { weight?: number | string; reps?: number | string; floors?: number | string | null },
  floorsMetric: boolean
): string {
  if (floorsMetric) return `${x.floors ?? '—'} 层`
  return `${x.weight ?? 0} kg × ${x.reps ?? 0}`
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

/** 顶栏动作：只读 →「编辑」进编辑态；编辑态 → ✓ 保存。 */
function onHeadAction() {
  if (!detail.value || saving.value) return
  if (editing.value) void save()
  else startEdit()
}

function startEdit() {
  const d = detail.value
  if (!d) return
  draft.value = {
    title: d.title,
    note: d.note ?? '',
    calories: d.calories != null ? String(d.calories) : '',
    blocks: d.blocks.map((b) => ({
      exerciseId: b.exerciseId ?? null,
      name: b.name,
      cardio: b.cardio === true,
      metric: b.metric ?? null,
      sets: b.sets.map((s) => ({
        weight: String(s.weight),
        reps: String(s.reps),
        floors: s.floors != null ? String(s.floors) : '',
        dur: s.durationSec != null ? formatClock(s.durationSec) : ''
      }))
    }))
  }
  editing.value = true
}

/**
 * 「分:秒」文本 → 秒。留空＝这条没有用时（合法）；写法不合法则拦下。
 * 与页面上显示的写法（`formatClock`）是同一套，不做换算。
 */
function parseDuration(text: string): { ok: true; value: number | null } | { ok: false } {
  const t = text.trim()
  if (!t) return { ok: true, value: null }
  const m = /^(\d{1,3}):([0-5]?\d)$/.exec(t)
  if (!m) return { ok: false }
  return { ok: true, value: Number(m[1]) * 60 + Number(m[2]) }
}

/** 加一组：沿用上一行的值预填（用时留空）。 */
function addSet(bi: number) {
  const b = draft.value.blocks[bi]
  const last = b.sets[b.sets.length - 1]
  b.sets.push({
    weight: last?.weight ?? '',
    reps: last?.reps ?? '',
    floors: last?.floors ?? '',
    dur: ''
  })
}

/** 删一组：每个动作至少留一组。 */
function removeSet(bi: number, si: number) {
  const b = draft.value.blocks[bi]
  if (b.sets.length <= 1) {
    uni.showToast({ title: '每个动作至少保留一组', icon: 'none' })
    return
  }
  b.sets.splice(si, 1)
}

async function save() {
  const d = detail.value
  if (!d) return
  const title = draft.value.title.trim()
  if (!title) {
    uni.showToast({ title: '标题不能为空', icon: 'none' })
    return
  }
  // 组次校验与「结束训练」提交同一口径：重量必填且 ≥0、次数必填且 ≥1；组用时留空或「分:秒」。
  // 层数口径（爬楼）只看层数——重量／次数是占位值（ADR 0025）
  for (const b of draft.value.blocks) {
    const floorsMetric = b.metric === 'floors'
    for (const s of b.sets) {
      if (floorsMetric) {
        const f = Number(s.floors)
        if (!String(s.floors).trim() || Number.isNaN(f) || f < 0) {
          uni.showToast({ title: `「${b.name}」有组次未填层数`, icon: 'none' })
          return
        }
        if (!parseDuration(s.dur).ok) {
          uni.showToast({ title: `「${b.name}」的组用时请按 01:42 的写法填`, icon: 'none' })
          return
        }
        continue
      }
      const w = Number(s.weight)
      if (!s.weight.trim() || Number.isNaN(w) || w < 0) {
        uni.showToast({ title: `「${b.name}」有组次未填重量`, icon: 'none' })
        return
      }
      const r = Number(s.reps)
      if (!s.reps.trim() || !Number.isInteger(r) || r < 1) {
        uni.showToast({ title: `「${b.name}」有组次未填次数`, icon: 'none' })
        return
      }
      if (!parseDuration(s.dur).ok) {
        uni.showToast({ title: `「${b.name}」的组用时请按 01:42 的写法填`, icon: 'none' })
        return
      }
    }
  }
  // 消耗：留空＝清空；填了就必须是 0–99999 的整数
  const kcalText = draft.value.calories.trim()
  const kcal = kcalText === '' ? null : Number(kcalText)
  if (kcal != null && (!Number.isInteger(kcal) || kcal < 0 || kcal > 99999)) {
    uni.showToast({ title: '消耗填 0–99999 的大卡整数', icon: 'none' })
    return
  }
  saving.value = true
  try {
    detail.value = await updateWorkout(d.id, {
      title,
      note: draft.value.note,
      calories: kcal,
      blocks: draft.value.blocks.map((b) => ({
        exerciseId: b.exerciseId,
        name: b.name,
        sets: b.sets.map((s) => {
          const d = parseDuration(s.dur)
          const floors = b.metric === 'floors'
          return {
            // floors 口径：重量／次数只是占位，服务端也只信 floors（ADR 0025）
            weight: floors ? 0 : Number(s.weight),
            reps: floors ? 1 : Number(s.reps),
            ...(floors ? { floors: Number(s.floors) || 0 } : {}),
            ...(d.ok && d.value != null ? { durationSec: d.value } : {})
          }
        })
      }))
    })
    editing.value = false
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch {
    // request 层已统一提示；留在编辑态可重试
  } finally {
    saving.value = false
  }
}

/** 只读态备注失焦即存（轻量保存，不动其它字段）。 */
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
  if (editing.value) {
    uni.showModal({
      title: '放弃修改？',
      content: '当前改动尚未保存，返回将丢弃。',
      confirmText: '放弃',
      confirmColor: '#C4453C',
      success: ({ confirm }) => {
        if (confirm) {
          editing.value = false
          uni.navigateBack()
        }
      }
    })
    return
  }
  uni.navigateBack()
}
</script>

<template>
  <view class="detail">
    <view class="detail__body">
      <!-- 顶栏：返回 ＋ 日期·标题 ＋ 编辑/✓ -->
      <view class="detail__head">
        <text class="detail__back" @click="back">←</text>
        <view class="detail__head-mid">
          <text v-if="!detail" class="detail__head-title">训练详情</text>
          <template v-else-if="editing">
            <text class="detail__head-date ft-num">{{ formatDateLabel(detail.createdAt) }}</text>
            <input
              v-model="draft.title"
              class="detail__title-input"
              maxlength="64"
              placeholder="训练标题"
              placeholder-class="detail__ph"
            />
          </template>
          <text v-else class="detail__head-title">
            {{ formatDateLabel(detail.createdAt) }} · {{ detail.title }}
          </text>
        </view>
        <view v-if="detail" class="detail__act" @click="onHeadAction">
          <!-- 编辑态：文字「编辑」变 ✓；点它即保存 -->
          <wd-icon v-if="editing" name="check" size="20px" />
          <text v-else class="detail__edit">编辑</text>
        </view>
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
            <text class="metrics__value ft-num">{{ shownSets }}<text class="metrics__unit"> 组</text></text>
          </view>
          <view class="metrics__cell">
            <text class="metrics__label">{{ volumeLabel }}</text>
            <text class="metrics__value ft-num">{{ volumeValue }}<text v-if="volumeUnit" class="metrics__unit"> {{ volumeUnit }}</text></text>
          </view>
        </view>

        <!-- 消耗（ADR 0029）：与时长同级的一行；编辑态可改，留空＝没填 -->
        <view class="burnline">
          <text class="burnline__label">消耗</text>
          <view class="burnline__right">
            <input
              v-if="editing"
              v-model="draft.calories"
              type="number"
              class="burnline__input ft-num"
              placeholder="未填"
              placeholder-class="burnline__ph"
            />
            <text v-else class="burnline__value ft-num">
              {{ detail.calories != null ? detail.calories : '—' }}
            </text>
            <text v-if="editing || detail.calories != null" class="burnline__unit">大卡</text>
          </view>
        </view>

        <!-- 只读：按动作分组的组表 -->
        <template v-if="!editing">
          <view v-for="(b, i) in detail.blocks" :key="i" class="ft-card block">
            <view class="block__head">
              <view class="block__name-line">
                <text class="block__dot" />
                <text class="block__name">{{ b.name }}</text>
              </view>
              <text class="block__meta ft-num">{{ b.sets.length }} 组 · {{ blockMetricText(b) }}</text>
            </view>
            <view class="block__sets">
              <view v-for="(s, j) in b.sets" :key="j" class="set">
                <text class="set__label" :class="{ 'set__label--pr': s.pr }">
                  第 {{ j + 1 }} 组
                  <text v-if="s.pr" class="set__pr ft-marker">PR</text>
                </text>
                <view class="set__vals">
                  <text class="set__value ft-num">{{ setReadText(s, b.metric === 'floors') }}</text>
                  <text v-if="s.durationSec != null" class="set__dur ft-num">
                    {{ formatClock(s.durationSec) }}
                  </text>
                </view>
              </view>
            </view>
          </view>
        </template>

        <!-- 编辑态：逐组改重量/次数/用时，可增删组（动作名不可改） -->
        <template v-else>
          <view v-for="(b, i) in draft.blocks" :key="i" class="ft-card block">
            <view class="block__head">
              <view class="block__name-line">
                <text class="block__dot" />
                <text class="block__name">{{ b.name }}</text>
              </view>
              <text class="block__meta ft-num">{{ b.sets.length }} 组 · {{ blockMetricText(b) }}</text>
            </view>
            <view class="block__sets">
              <view v-for="(s, j) in b.sets" :key="j" class="set set--edit">
                <view class="set__line">
                  <text class="set__label">第 {{ j + 1 }} 组</text>
                  <view class="set__edit">
                    <view class="set__inp">
                      <input
                        v-if="b.metric === 'floors'"
                        v-model="s.floors"
                        type="number"
                        class="set__field ft-num"
                      />
                      <input v-else v-model="s.weight" type="digit" class="set__field ft-num" />
                      <text class="set__unit">{{ b.metric === 'floors' ? '层' : 'kg' }}</text>
                    </view>
                    <template v-if="b.metric !== 'floors'">
                      <text class="set__x">×</text>
                      <view class="set__inp">
                        <input v-model="s.reps" type="number" class="set__field ft-num" />
                        <text class="set__unit">次</text>
                      </view>
                    </template>
                    <text class="set__del" @click="removeSet(i, j)">×</text>
                  </view>
                </view>
                <!-- 组用时：留空＝这条没有用时（编辑是整体覆盖，见 ADR 0022） -->
                <view class="set__line set__line--dur">
                  <text class="set__dur-label">用时</text>
                  <view class="set__inp set__inp--dur">
                    <input
                      v-model="s.dur"
                      type="text"
                      placeholder="01:42"
                      placeholder-class="set__dur-ph"
                      class="set__field set__field--dur ft-num"
                    />
                    <text class="set__unit">分:秒</text>
                  </view>
                </view>
              </view>
              <view class="set__add" @click="addSet(i)">+ 加一组</view>
            </view>
          </view>
        </template>

        <!-- 备注：只读态失焦即存；编辑态随 ✓ 一起保存 -->
        <view class="ft-card note">
          <view class="note__head">
            <text class="note__icon">≡</text>
            <text class="note__title">备注</text>
          </view>
          <textarea
            v-if="editing"
            v-model="draft.note"
            class="note__input"
            placeholder="添加备注…"
            placeholder-class="note__placeholder"
            :maxlength="200"
            auto-height
          />
          <textarea
            v-else
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

.detail__head-mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.detail__head-title {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail__head-date {
  font-size: 22rpx;
  color: $app-ink-muted;
}

/* 编辑态标题输入：下划线式，和只读标题同字号同字重，切换时不跳版 */
.detail__title-input {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
  border-bottom: 2rpx solid $app-ink;
  padding: 4rpx 0;
}

.detail__ph {
  color: $app-ink-subtle;
}

.detail__act {
  flex-shrink: 0;
  min-width: 64rpx;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  color: $app-ink;
  padding: 8rpx 0;
}

.detail__edit {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.detail__edit--off {
  opacity: 0.4;
}

.detail__empty-back {
  margin: 32rpx auto 0;
  width: 240rpx;
}

/* ── 三格指标 ── */
.burnline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-lg;
  padding: 20rpx 28rpx;
  margin-bottom: 24rpx;
}

.burnline__label {
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: $app-ink-muted;
}

.burnline__right {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.burnline__value {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.burnline__input {
  width: 180rpx;
  text-align: right;
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
  border-bottom: 2rpx solid $app-line;
}

.burnline__ph {
  color: $app-ink-subtle;
  font-weight: 400;
}

.burnline__unit {
  font-size: $text-xs;
  color: $app-ink-muted;
}

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
  /* ⚠️ 不靠文本里的空格留间距：模板里的前导空白会被折叠，数字与单位会贴在一起 */
  margin-left: 6rpx;
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

/* 值 ＋ 这一组的净用时 */
.set__vals {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.set__dur {
  font-size: 22rpx;
  color: $app-ink-muted;
}

/* ── 编辑态组行 ── */
/* 每组两行：上行＝组号 ＋ 重量×次数 ＋ 删；下行＝用时 */
.set--edit {
  flex-direction: column;
  align-items: stretch;
  gap: 12rpx;
}

.set__line {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.set__line--dur {
  justify-content: flex-start;
  gap: 12rpx;
}

.set__dur-label {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.set__inp--dur {
  padding: 4rpx 12rpx;
}

.set__field--dur {
  width: 120rpx;
}

.set__dur-ph {
  font-size: $text-md;
  color: $app-ink-subtle;
}

.set__edit {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.set__inp {
  display: flex;
  align-items: center;
  border: 1rpx solid $app-ink;
  border-radius: 8rpx;
  padding: 6rpx 12rpx;
}

.set__field {
  width: 72rpx;
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
  text-align: left;
}

.set__unit {
  font-size: 20rpx;
  color: $app-ink-muted;
  margin-left: 6rpx;
}

.set__x {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.set__del {
  width: 44rpx;
  text-align: center;
  font-size: 36rpx;
  line-height: 1;
  color: $app-ink-subtle;
}

.set__add {
  padding: 18rpx 0 4rpx;
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink-muted;
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
