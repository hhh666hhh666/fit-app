<script setup lang="ts">
import { PALETTE } from '@/utils/palette'
// 训练详情（从日志页点进来；接 GET /api/workouts/{id}）。
// 右上角「编辑」进入就地编辑：标题、各组、消耗、备注可改；底部悬浮「保存修改」提交（PUT /api/workouts/{id}），
// 右上角变「取消」＝放弃这次改动。
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getWorkout, updateWorkout, updateWorkoutNote } from '@/api/workout'
import type { WorkoutDetail } from '@/api/workout'
import { formatClock, formatDateTimeLabel, formatThousands } from '@/utils/format'
import { metricKindOf, metricLabel, metricUnit, metricValue } from '@/utils/metric'
import { ensureLogin } from '@/utils/guard'
import FtButton from '@/components/FtButton.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'

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

/** 页头右侧：只读 →「编辑」进编辑态；编辑态 →「取消」问一句再退出（保存走底部按钮）。 */
function onHeadAction() {
  if (!detail.value || saving.value) return
  if (editing.value) confirmDiscard(() => (editing.value = false))
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

/** 编辑中要离开（返回或取消）先问一句；确认后才执行 then。 */
function confirmDiscard(then: () => void) {
  uni.showModal({
    title: '放弃修改？',
    content: '当前改动尚未保存，放弃后不会保留。',
    confirmText: '放弃',
    confirmColor: PALETTE.danger,
    success: ({ confirm }) => {
      if (confirm) then()
    }
  })
}

function back() {
  if (editing.value) {
    confirmDiscard(() => {
      editing.value = false
      uni.navigateBack()
    })
    return
  }
  uni.navigateBack()
}
</script>

<template>
  <view class="detail">
    <view class="detail__body" :class="{ 'is-editing': editing }">
      <FtPageHeader
        :title="detail ? detail.title : '训练详情'"
        :eyebrow="detail ? formatDateTimeLabel(detail.createdAt) : undefined"
        back
        @back="back"
      >
        <template v-if="detail" #right>
          <view class="pill" role="button" @click="onHeadAction">{{ editing ? '取消' : '编辑' }}</view>
        </template>
      </FtPageHeader>

      <!-- 加载与异常态 -->
      <view v-if="loading" class="hint">加载中…</view>
      <view v-else-if="notFound || !detail" class="glass">
        <FtEmpty title="这条训练记录不存在或已删除" sub="可能在别的设备上删掉了。">
          <FtButton variant="ghost" size="sm" class="detail__empty-back" @click="back">返回日志</FtButton>
        </FtEmpty>
      </view>

      <template v-else>
        <!-- 编辑态：标题 -->
        <view v-if="editing" class="glass field-card">
          <text class="field-card__label">标题</text>
          <input
            v-model="draft.title"
            class="field-card__input"
            maxlength="64"
            placeholder="训练标题"
            placeholder-class="ph"
          />
        </view>

        <!-- 数据卡：时长 / 组数 / 总容量 ＋ 消耗（ADR 0029：编辑态可改，留空＝没填） -->
        <view class="glass metrics">
          <view class="metrics__row">
            <view class="metrics__cell">
              <text class="metrics__label">时长</text>
              <text class="metrics__value ft-num">{{ formatClock(detail.durationSec) }}</text>
            </view>
            <view class="metrics__cell">
              <text class="metrics__label">组数</text>
              <text class="metrics__value ft-num">{{ shownSets }}<text class="metrics__unit">组</text></text>
            </view>
            <view class="metrics__cell">
              <text class="metrics__label">{{ volumeLabel }}</text>
              <text class="metrics__value ft-num">{{ volumeValue }}<text v-if="volumeUnit" class="metrics__unit">{{ volumeUnit }}</text></text>
            </view>
          </view>
          <view class="metrics__burn">
            <text class="metrics__label">消耗</text>
            <view class="metrics__burn-right">
              <input
                v-if="editing"
                v-model="draft.calories"
                type="number"
                class="inp metrics__burn-input ft-num"
                placeholder="未填"
                placeholder-class="ph"
              />
              <text v-else class="metrics__burn-value ft-num">{{ detail.calories != null ? detail.calories : '—' }}</text>
              <text v-if="editing || detail.calories != null" class="metrics__unit">大卡</text>
            </view>
          </view>
        </view>

        <!-- 只读：按动作分组的组表 -->
        <view v-if="!editing" class="glass blocks">
          <view v-for="(b, i) in detail.blocks" :key="i" class="block">
            <view class="block__head">
              <text class="block__name">{{ b.name }}</text>
              <text class="block__meta ft-num">{{ b.sets.length }} 组 · {{ blockMetricText(b) }}</text>
            </view>
            <view v-for="(s, j) in b.sets" :key="j" class="set">
              <text class="set__label">第 {{ j + 1 }} 组</text>
              <text v-if="s.pr" class="pr">PR</text>
              <text class="set__value ft-num" :class="{ 'set__value--pr': s.pr }">
                {{ setReadText(s, b.metric === 'floors') }}
              </text>
              <!-- 用时列：这个动作有一组带用时就整列留位，没带的组留空，数值才对得齐 -->
              <text v-if="b.sets.some((x) => x.durationSec != null)" class="set__dur ft-num">
                {{ s.durationSec != null ? formatClock(s.durationSec) : '' }}
              </text>
            </view>
          </view>
        </view>

        <!-- 编辑态：逐组改重量/次数/用时，可增删组（动作名不可改） -->
        <template v-else>
          <view v-for="(b, i) in draft.blocks" :key="i" class="glass eblock">
            <view class="block__head">
              <text class="block__name">{{ b.name }}</text>
              <text class="block__meta ft-num">{{ b.sets.length }} 组 · {{ blockMetricText(b) }}</text>
            </view>
            <view v-for="(s, j) in b.sets" :key="j" class="eset">
              <view class="eset__line">
                <text class="set__label">第 {{ j + 1 }} 组</text>
                <view class="eset__vals">
                  <view class="inp">
                    <input v-if="b.metric === 'floors'" v-model="s.floors" type="number" class="inp__field ft-num" />
                    <input v-else v-model="s.weight" type="digit" class="inp__field ft-num" />
                    <text class="inp__unit">{{ b.metric === 'floors' ? '层' : 'kg' }}</text>
                  </view>
                  <template v-if="b.metric !== 'floors'">
                    <text class="eset__x">×</text>
                    <view class="inp">
                      <input v-model="s.reps" type="number" class="inp__field ft-num" />
                      <text class="inp__unit">次</text>
                    </view>
                  </template>
                </view>
                <view class="eset__del" role="button" aria-label="删掉这一组" @click="removeSet(i, j)">
                  <FtIcon name="close" :size="14" :stroke="2" />
                </view>
              </view>
              <!-- 组用时：留空＝这条没有用时（编辑是整体覆盖，见 ADR 0022） -->
              <view class="eset__line eset__line--dur">
                <text class="set__label">用时</text>
                <view class="inp">
                  <input v-model="s.dur" type="text" placeholder="01:42" placeholder-class="ph" class="inp__field inp__field--dur ft-num" />
                  <text class="inp__unit">分:秒</text>
                </view>
              </view>
            </view>
            <view class="addbtn" role="button" @click="addSet(i)">
              <FtIcon name="plus" :size="16" :stroke="2" />
              <text>加一组</text>
            </view>
          </view>
        </template>

        <!-- 备注：只读态失焦即存；编辑态随「保存修改」一起保存 -->
        <view class="glass note">
          <text class="card-title">备注</text>
          <textarea
            v-if="editing"
            v-model="draft.note"
            class="note__input"
            placeholder="添加备注…"
            placeholder-class="ph"
            :maxlength="200"
            auto-height
          />
          <textarea
            v-else
            v-model="detail.note"
            class="note__input"
            placeholder="添加备注…"
            placeholder-class="ph"
            :maxlength="200"
            auto-height
            @blur="saveNote"
          />
        </view>
      </template>
    </view>

    <!-- 编辑态：贴底悬浮的保存 -->
    <view v-if="editing" class="detail__bottom">
      <FtButton variant="primary" block :disabled="saving" class="detail__save" @click="save">保存修改</FtButton>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.detail__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(64rpx + env(safe-area-inset-bottom));
}

/* 编辑态底下多了悬浮的保存，正文末尾让出它的高度 */
.detail__body.is-editing {
  padding-bottom: calc(200rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

.card-title {
  @include card-title;
}

.ph {
  color: $app-ink-subtle;
  font-weight: $weight-regular;
}

.hint {
  padding: 80rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* 页头右侧的胶囊按钮 */
.pill {
  @include glass-pill;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.pill:active {
  opacity: 0.7;
}

.detail .detail__empty-back {
  align-self: flex-start;
  margin-top: 8rpx;
}

/* ── 编辑态标题 ── */
.field-card {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding-top: 28rpx;
  padding-bottom: 28rpx;
}

.field-card__label {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.field-card__input {
  height: 92rpx;
  padding: 0 24rpx;
  border-radius: $radius-md;
  background: $app-surface;
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $app-ink;
}

/* ── 数据卡（与汇总页同一个） ── */
.metrics {
  padding: 32rpx 12rpx 24rpx;
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
  align-items: center;
  justify-content: space-between;
  margin: 28rpx 24rpx 0;
  padding-top: 20rpx;
  border-top: 1rpx solid $app-line-soft;
}

.metrics__burn-right {
  display: flex;
  align-items: center;
}

.metrics__burn-value {
  font-size: $text-lg;
  color: $app-ink;
}

.metrics__burn-input {
  width: 180rpx;
}

/* ── 只读组表 ── */
.blocks {
  padding: 12rpx 36rpx;
}

.block {
  padding: 24rpx 0;
}

.block + .block {
  border-top: 1rpx solid $app-line-soft;
}

.block__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16rpx;
  padding-bottom: 8rpx;
}

.block__name {
  min-width: 0;
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.block__meta {
  flex-shrink: 0;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.set {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 12rpx 0;
}

.set__label {
  flex-shrink: 0;
  font-size: $text-base;
  color: $app-ink-muted;
}

/* PR：浅橙底 ＋ 深橙字（PR 是「动作的结果」，归橙色；与汇总页同一个） */
.pr {
  padding: 2rpx 12rpx;
  border-radius: $radius-pill;
  background: $accent-soft;
  font-size: $text-xs;
  font-weight: $weight-bold;
  color: $accent-strong;
}

.set__value {
  margin-left: auto;
  font-size: $text-md;
  color: $app-ink;
}

.set__value--pr {
  color: $accent-strong;
}

.set__dur {
  width: 88rpx;
  flex-shrink: 0;
  text-align: right;
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* ── 编辑态 ── */
.eblock {
  padding: 28rpx 32rpx;
}

.eblock .block__head {
  padding-bottom: 12rpx;
}

/* 每组两行：上行＝组号 ＋ 重量×次数 ＋ 删；下行＝用时 */
.eset {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 20rpx 0;
  border-top: 1rpx solid $app-line-soft;
}

.eset__line {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.eset__line--dur {
  padding-right: 60rpx;
}

.eset__vals {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8rpx;
}

.eset__line--dur .inp {
  margin-left: auto;
}

.eset__x {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.eset__del {
  width: 48rpx;
  height: 48rpx;
  flex-shrink: 0;
  border-radius: 50%;
  background: $app-surface-2;
  color: $app-ink-muted;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 白底小输入框 ＋ 单位 */
.inp {
  display: flex;
  align-items: center;
  gap: 6rpx;
  height: 64rpx;
  padding: 0 16rpx;
  border-radius: $radius-sm;
  background: $app-surface;
}

.inp__field {
  width: 88rpx;
  text-align: right;
  font-size: $text-md;
  color: $app-ink;
}

.inp__field--dur {
  width: 104rpx;
}

.inp__unit {
  flex-shrink: 0;
  font-size: $text-xs;
  color: $app-ink-muted;
}

/* 加一组：虚线框，与计划编辑「添加动作」同一个 */
.addbtn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  height: 80rpx;
  margin-top: 8rpx;
  border: 2rpx dashed $app-line-dash;
  border-radius: $radius-md;
  font-size: $text-base;
  color: $app-ink-muted;
}

.addbtn:active {
  opacity: 0.6;
}

/* ── 备注 ── */
.note {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 28rpx 36rpx;
}

.note__input {
  width: 100%;
  min-height: 80rpx;
  font-size: $text-md;
  line-height: 1.7;
  color: $app-ink;
}

/* ── 底部保存 ── */
.detail__bottom {
  position: fixed;
  left: $tabbar-side;
  right: $tabbar-side;
  bottom: calc(#{$tabbar-gap} + env(safe-area-inset-bottom));
}

.detail__bottom .detail__save {
  height: 104rpx;
  font-size: 30rpx;
}
</style>
