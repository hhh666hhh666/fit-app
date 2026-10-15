<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onReachBottom, onShow } from '@dcloudio/uni-app'
import {
  batchDeleteBodyRecords,
  createBodyRecord,
  getBodyOverview,
  getBodyRecords,
  updateBodyRecord
} from '@/api/body'
import type { BodyOverview, BodyRecordRow } from '@/api/body'
import FtTrendChart from '@/components/FtTrendChart.vue'
import {
  formatDateLabel,
  formatDateTimeLabel,
  nowTime,
  toIsoMinute,
  todayKey
} from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import {
  formatWeight,
  getWeightUnit,
  kgToDisplay,
  parseWeightInput,
  setWeightUnit,
  weightPrecision,
  weightUnitLabel,
  weightUnitShort,
  type WeightUnit
} from '@/utils/weight'

// ── 身体档案：一条记录＝一次测定，体重与四围同一行（ADR 0017）──

const PAGE_SIZE = 20

function goBack() {
  uni.navigateBack()
}

const overview = ref<BodyOverview | null>(null)
const records = ref<BodyRecordRow[]>([])
const loading = ref(true)
const hasMore = ref(false)
const loadingMore = ref(false)
const weightUnit = ref<WeightUnit>('jin')

async function load() {
  loading.value = true
  try {
    const [ov, rows] = await Promise.all([getBodyOverview(), getBodyRecords(PAGE_SIZE, 0)])
    overview.value = ov
    records.value = rows
    hasMore.value = rows.length >= PAGE_SIZE
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const rows = await getBodyRecords(PAGE_SIZE, records.value.length)
    records.value = [...records.value, ...rows]
    hasMore.value = rows.length >= PAGE_SIZE
  } catch {
    // request 层已统一提示
  } finally {
    loadingMore.value = false
  }
}

onReachBottom(loadMore)

/** 从饮食卡「记体重」带参进入（?record=1）：加载后自动打开录入弹层。 */
const pendingRecord = ref(false)

onLoad((query) => {
  const q = (query || {}) as Record<string, string | undefined>
  pendingRecord.value = q.record === '1'
})

onShow(() => {
  if (!ensureLogin()) return
  weightUnit.value = getWeightUnit()
  load()
  if (pendingRecord.value) {
    pendingRecord.value = false
    openSheet()
  }
})

// ── 体重图（序列已由后端按日聚合、取当天最小）──
const weightPoints = computed(() =>
  (overview.value?.weightSeries ?? []).map((p) => ({
    date: p.date,
    value: kgToDisplay(Number(p.weight), weightUnit.value)
  }))
)
const firstWeight = computed(() => overview.value?.weightSeries[0]?.weight ?? null)
const lastWeight = computed(() => {
  const points = overview.value?.weightSeries ?? []
  return points.length ? points[points.length - 1].weight : null
})

// ── 围度图：一张图 + 部位分段条切换（四个部位数值跨度大，共用 y 轴会压扁）──
type GirthKey = 'chest' | 'waist' | 'hip' | 'arm'
const GIRTH_PARTS: { key: GirthKey; label: string }[] = [
  { key: 'chest', label: '胸围' },
  { key: 'waist', label: '腰围' },
  { key: 'hip', label: '臀围' },
  { key: 'arm', label: '臂围' }
]
const girthPart = ref<GirthKey>('chest')

const girthPoints = computed(() => {
  const key = girthPart.value
  return (overview.value?.girthSeries ?? [])
    .filter((p) => p[key] != null)
    .map((p) => ({ date: p.date, value: Number(p[key]) }))
})
const girthRange = computed(() => {
  const pts = girthPoints.value
  if (pts.length < 2) return ''
  return `${pts[0].value} → ${pts[pts.length - 1].value} cm`
})

// ── 多选删除：长按进入多选态；多选态下点行＝切换勾选（不再进编辑）──
const selectMode = ref(false)
const selected = ref<Set<number>>(new Set())
const selectedCount = computed(() => selected.value.size)
const allSelected = computed(
  () => records.value.length > 0 && selected.value.size === records.value.length
)

/**
 * H5 上长按触发后浏览器还会补发一次 click——不挡的话刚被长按选中的那条会被
 * 这次 click 又取消掉（选数 1→0，表现为「长按没反应」）。长按期间挂短窗口吞掉 click。
 */
let clickLockUntil = 0

function toggleSelect(id: number) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

function onRowClick(r: BodyRecordRow) {
  if (Date.now() < clickLockUntil) return
  if (selectMode.value) toggleSelect(r.id)
  else openEdit(r)
}

function onRowLongPress(r: BodyRecordRow) {
  if (selectMode.value) return
  clickLockUntil = Date.now() + 400
  selectMode.value = true
  selected.value = new Set([r.id])
}

function exitSelect() {
  selectMode.value = false
  selected.value = new Set()
}

function toggleAll() {
  selected.value = allSelected.value ? new Set() : new Set(records.value.map((r) => r.id))
}

function doDelete() {
  const ids = [...selected.value]
  if (ids.length === 0) return
  uni.showModal({
    title: '删除身体档案记录',
    content:
      ids.length === 1
        ? '删除这条记录？删除后不可恢复。'
        : `删除选中的 ${ids.length} 条记录？删除后不可恢复。`,
    confirmText: '删除',
    confirmColor: '#C4453C',
    success: async ({ confirm }) => {
      if (!confirm) return
      try {
        await batchDeleteBodyRecords(ids)
        exitSelect()
        uni.showToast({ title: `已删除 ${ids.length} 条`, icon: 'none' })
        await load() // 卡片与两张图都要按新数据重算
      } catch {
        // request 层已统一提示
      }
    }
  })
}

// ── 录入 / 编辑弹层（同一个，靠 editId 区分）──
const sheet = ref({
  show: false,
  editId: null as number | null,
  date: todayKey(),
  time: nowTime(),
  weight: '',
  chest: '',
  waist: '',
  hip: '',
  arm: '',
  note: '',
  saving: false,
  /** 新增时预填来源的测定时刻（用于「上次 ×月×日」提示）；编辑时为 null。 */
  prefilledFrom: null as string | null
})

function numOrBlank(v: number | null | undefined): string {
  return v == null ? '' : formatWeight(v, weightUnit.value)
}

function displayWeight(v: number | null | undefined): string {
  return formatWeight(v, weightUnit.value)
}

function unitChange(unit: WeightUnit) {
  if (unit === weightUnit.value) return
  const current = sheet.value.weight.trim()
  if (current) {
    const kg = parseWeightInput(current, weightUnit.value)
    sheet.value.weight = Number.isFinite(kg) ? formatWeight(kg, unit) : ''
  }
  weightUnit.value = unit
  setWeightUnit(unit)
}

function weightInputStep(): string {
  return weightPrecision(weightUnit.value) === 1 ? '0.1' : '0.01'
}

/** 新增：日期时间取「现在」，体重与四围预填上次数值（省敲键盘）。 */
function openSheet() {
  const last = records.value.find((r) => r.weight != null) ?? null
  sheet.value = {
    show: true,
    editId: null,
    date: todayKey(),
    time: nowTime(),
    weight: numOrBlank(last?.weight),
    chest: numOrBlank(last?.chest),
    waist: numOrBlank(last?.waist),
    hip: numOrBlank(last?.hip),
    arm: numOrBlank(last?.arm),
    note: '',
    saving: false,
    prefilledFrom: last?.date ?? null
  }
}

/** 编辑：点明细行进来，值全部预填（含时刻），弹层里不放删除——删除统一走长按。 */
function openEdit(r: BodyRecordRow) {
  const [date, time] = splitIso(r.date)
  sheet.value = {
    show: true,
    editId: r.id,
    date,
    time,
    weight: numOrBlank(r.weight),
    chest: numOrBlank(r.chest),
    waist: numOrBlank(r.waist),
    hip: numOrBlank(r.hip),
    arm: numOrBlank(r.arm),
    note: r.note ?? '',
    saving: false,
    prefilledFrom: null
  }
}

/** `2026-10-13T23:51:00` → ['2026-10-13', '23:51'] */
function splitIso(iso: string): [string, string] {
  const [d, t = '00:00:00'] = iso.split('T')
  return [d, t.slice(0, 5)]
}

/** 预填值的来源提示：不标的话，只想记体重时容易把上个月的围度当成今天的存进去。 */
const lastHint = computed(() => {
  const d = sheet.value.prefilledFrom
  return d ? `上次 ${formatDateLabel(d)}` : ''
})

function onDateChange(e: { detail: { value: string } }) {
  sheet.value.date = e.detail.value
}

function onTimeChange(e: { detail: { value: string } }) {
  sheet.value.time = e.detail.value
}

async function save() {
  const f = sheet.value
  const parsedWeight = parseWeightInput(f.weight, weightUnit.value)
  if (Number.isNaN(parsedWeight)) {
    uni.showToast({ title: `${weightUnitLabel(weightUnit.value)}最多保留${weightPrecision(weightUnit.value)}位小数`, icon: 'none' })
    return
  }
  const payload = {
    measuredAt: toIsoMinute(f.date, f.time),
    weight: parsedWeight,
    chest: f.chest ? Number(f.chest) : null,
    waist: f.waist ? Number(f.waist) : null,
    hip: f.hip ? Number(f.hip) : null,
    arm: f.arm ? Number(f.arm) : null,
    note: f.note.trim()
  }
  if (
    payload.weight == null &&
    payload.chest == null &&
    payload.waist == null &&
    payload.hip == null &&
    payload.arm == null
  ) {
    uni.showToast({ title: '体重和围度至少填一项', icon: 'none' })
    return
  }
  f.saving = true
  try {
    if (f.editId != null) await updateBodyRecord(f.editId, payload)
    else await createBodyRecord(payload)
    sheet.value.show = false
    uni.showToast({ title: '已保存', icon: 'success' })
    await load()
  } catch {
    // request 层已统一提示
  } finally {
    f.saving = false
  }
}

// ── 展示用小工具 ──
function signed(v: number | null | undefined, unit: WeightUnit = 'kg'): string {
  if (v == null) return ''
  const n = kgToDisplay(Number(v), unit)
  return `${n > 0 ? '+' : ''}${n.toFixed(weightPrecision(unit))}`
}

function girthValue(v: number | null | undefined): string {
  return v == null ? '–' : String(v)
}

/** 明细行第二行：把量过的围度挤成一行小字，没量的不出现。 */
function girthLine(r: BodyRecordRow): string {
  const parts: string[] = []
  if (r.chest != null) parts.push(`胸 ${r.chest}`)
  if (r.waist != null) parts.push(`腰 ${r.waist}`)
  if (r.hip != null) parts.push(`臀 ${r.hip}`)
  if (r.arm != null) parts.push(`臂 ${r.arm}`)
  return parts.join(' · ')
}
</script>

<template>
  <view class="bodypg">
    <!-- 顶栏：常规＝返回＋标题；多选态＝取消＋已选＋全选/删除（照日志页） -->
    <view class="bodypg__head">
      <template v-if="!selectMode">
        <text class="bodypg__back" @click="goBack">‹</text>
        <text class="bodypg__title">身体档案</text>
        <view class="bodypg__head-space" />
      </template>
      <template v-else>
        <text class="bodypg__cancel" @click="exitSelect">取消</text>
        <text class="bodypg__sel ft-num">已选 {{ selectedCount }} 项</text>
        <view class="bodypg__acts">
          <text class="bodypg__all" @click="toggleAll">{{ allSelected ? '取消全选' : '全选' }}</text>
          <text
            class="bodypg__del"
            :class="{ 'bodypg__del--off': selectedCount === 0 }"
            @click="doDelete"
          >
            删除
          </text>
        </view>
      </template>
    </view>

    <view v-if="loading" class="ft-loading">加载中…</view>

    <view v-else class="bodypg__body">
      <!-- 当前体重卡 -->
      <view class="ft-card wcard">
        <view class="wcard__head">
          <text class="wcard__label">当前体重</text>
          <text class="wcard__add" @click="openSheet">记一笔 +</text>
        </view>
        <view class="wcard__main">
          <view class="wcard__num-row">
            <template v-if="overview?.latest">
                <text class="wcard__num ft-num">{{ displayWeight(overview.latest.weight) }}</text>
              <text class="wcard__unit">{{ weightUnitShort(weightUnit) }}</text>
            </template>
            <text v-else class="wcard__none">还没有体重记录</text>
          </view>
          <text v-if="overview?.isLowest" class="wcard__badge ft-marker ft-num">阶段最低 · PR</text>
        </view>
        <view v-if="overview?.latest" class="wcard__foot">
          <text class="wcard__date ft-num">{{ formatDateLabel(overview.latest.date) }} · 最近一次记录</text>
          <text v-if="overview.weekDiff != null" class="wcard__diff ft-num">
            较上周 {{ signed(overview.weekDiff, weightUnit) }} {{ weightUnitShort(weightUnit) }}
          </text>
        </view>
      </view>

      <!-- 基础围度卡（最新一次） -->
      <view class="ft-card gcard">
        <view class="gcard__head">
          <text class="gcard__label">基础围度</text>
          <text v-if="overview?.girth" class="gcard__date ft-num">
            {{ formatDateLabel(overview.girth.date) }} 测定
          </text>
        </view>
        <view v-if="overview?.girth" class="gcard__grid">
          <view class="gcard__cell">
            <text class="gcard__glabel">胸围</text>
            <view class="gcard__gnum-row">
              <text class="gcard__gnum ft-num">{{ girthValue(overview.girth.chest) }}</text>
              <text class="gcard__gunit">cm</text>
            </view>
          </view>
          <view class="gcard__cell">
            <text class="gcard__glabel">腰围</text>
            <view class="gcard__gnum-row">
              <text class="gcard__gnum ft-num">{{ girthValue(overview.girth.waist) }}</text>
              <text class="gcard__gunit">cm</text>
            </view>
          </view>
          <view class="gcard__cell">
            <text class="gcard__glabel">臀围</text>
            <view class="gcard__gnum-row">
              <text class="gcard__gnum ft-num">{{ girthValue(overview.girth.hip) }}</text>
              <text class="gcard__gunit">cm</text>
            </view>
          </view>
          <view class="gcard__cell">
            <text class="gcard__glabel">臂围</text>
            <view class="gcard__gnum-row">
              <text class="gcard__gnum ft-num">{{ girthValue(overview.girth.arm) }}</text>
              <text class="gcard__gunit">cm</text>
            </view>
          </view>
        </view>
        <text v-else class="gcard__empty">还没有围度记录——「记一笔」里可以一起量</text>
      </view>

      <!-- 体重趋势 -->
      <view class="ft-card chartcard">
        <view class="chartcard__head">
          <text class="chartcard__title">体重趋势</text>
          <text v-if="firstWeight != null && lastWeight != null" class="chartcard__range ft-num">
            {{ displayWeight(firstWeight) }} → {{ displayWeight(lastWeight) }} {{ weightUnitShort(weightUnit) }}
          </text>
        </view>
        <FtTrendChart
          v-if="weightPoints.length >= 2"
          :points="weightPoints"
          :height="180"
          :zero-based="false"
        />
        <text v-else class="chartcard__empty">再记一次体重，就能看到趋势线</text>
        <text v-if="weightPoints.length >= 2" class="chartcard__tip">
          同一天记多次取最小值 · 单指按住看数值 · 双指捏合缩放
        </text>
      </view>

      <!-- 围度趋势（单独一张图，部位分段条切换） -->
      <view class="ft-card chartcard">
        <view class="chartcard__head">
          <text class="chartcard__title">围度趋势</text>
          <text v-if="girthRange" class="chartcard__range ft-num">{{ girthRange }}</text>
        </view>
        <view class="seg">
          <text
            v-for="p in GIRTH_PARTS"
            :key="p.key"
            class="seg__item"
            :class="{ 'seg__item--on': girthPart === p.key }"
            @click="girthPart = p.key"
          >
            {{ p.label }}
          </text>
        </view>
        <FtTrendChart
          v-if="girthPoints.length >= 2"
          :points="girthPoints"
          :height="180"
          :zero-based="false"
        />
        <text v-else class="chartcard__empty">这个部位再量一次，就能看到趋势线</text>
        <text v-if="girthPoints.length >= 2" class="chartcard__tip">
          同一天量多次取最小值 · 单指按住看数值 · 双指捏合缩放
        </text>
      </view>

      <!-- 全部记录（体重与围度同一条时间线） -->
      <view class="ft-card mcard">
        <view class="mcard__head">
          <text class="mcard__title">全部记录</text>
          <text class="mcard__meta ft-num">TIME / {{ weightUnitShort(weightUnit).toUpperCase() }} / DIFF</text>
        </view>
        <view v-if="records.length === 0" class="mcard__empty">还没有记录</view>
        <view
          v-for="(r, i) in records"
          :key="r.id"
          class="mrow"
          :class="{ 'mrow--sel': selectMode && selected.has(r.id) }"
          @click="onRowClick(r)"
          @longpress="onRowLongPress(r)"
        >
          <view
            v-if="selectMode"
            class="mrow__check"
            :class="{ 'mrow__check--on': selected.has(r.id) }"
          >
            <wd-icon v-if="selected.has(r.id)" name="check" size="13px" color="#ffffff" />
          </view>
          <view class="mrow__dot" :class="{ 'mrow__dot--hollow': i !== 0 }" />
          <view class="mrow__main">
            <view class="mrow__line1">
              <text class="mrow__date ft-num">{{ formatDateTimeLabel(r.date) }}</text>
              <text v-if="r.note" class="mrow__note">{{ r.note }}</text>
              <view class="mrow__right">
                <text v-if="r.weight != null" class="mrow__kg ft-num">
                  {{ displayWeight(r.weight) }} {{ weightUnitShort(weightUnit) }}
                </text>
                <text class="mrow__diff ft-num">{{ r.diff == null ? '—' : signed(r.diff, weightUnit) }}</text>
              </view>
            </view>
            <text v-if="girthLine(r)" class="mrow__girth ft-num">{{ girthLine(r) }}</text>
          </view>
        </view>
        <view v-if="loadingMore" class="mcard__more">加载中…</view>
        <view v-else-if="!hasMore && records.length > 0" class="mcard__more">
          共 {{ records.length }} 条
        </view>
      </view>

      <view class="bodypg__pad" />
    </view>

    <!-- 底部固定：记录今天（多选态下藏起来，避免误触） -->
    <view v-if="!selectMode" class="bodypg__bottom">
      <view class="ft-btn ft-btn--primary ft-btn--block" @click="openSheet">+ 记录今天的数据</view>
    </view>

    <!-- 录入 / 编辑弹层 -->
    <view v-if="sheet.show" class="sheet-mask" @click="sheet.show = false" />
    <view v-if="sheet.show" class="sheet">
      <view class="sheet__handle" />
      <text class="sheet__title">{{ sheet.editId == null ? '记录身体数据' : '编辑记录' }}</text>
      <text v-if="lastHint" class="sheet__hint ft-num">{{ lastHint }}</text>

      <view class="fld">
        <text class="fld__label">日期</text>
        <picker mode="date" :value="sheet.date" @change="onDateChange">
          <view class="fld__picker ft-num">{{ sheet.date }} ▾</view>
        </picker>
      </view>

      <view class="fld">
        <text class="fld__label">时刻</text>
        <picker mode="time" :value="sheet.time" @change="onTimeChange">
          <view class="fld__picker ft-num">{{ sheet.time }} ▾</view>
        </picker>
      </view>

      <view class="fld">
        <view class="fld__label-row">
          <text class="fld__label">体重（{{ weightUnitLabel(weightUnit) }}）</text>
          <view class="unit-switch">
            <text class="unit-switch__item" :class="{ 'unit-switch__item--on': weightUnit === 'jin' }" @click="unitChange('jin')">斤</text>
            <text class="unit-switch__item" :class="{ 'unit-switch__item--on': weightUnit === 'kg' }" @click="unitChange('kg')">千克</text>
          </view>
        </view>
        <input
          v-model="sheet.weight"
          class="fld__input ft-num"
          type="digit"
          :step="weightInputStep()"
          :placeholder="weightUnit === 'jin' ? '如 145.0' : '如 72.50'"
          placeholder-class="fld__ph"
        />
      </view>

      <view class="fld-grid">
        <view class="fld">
          <text class="fld__label">胸围（cm）</text>
          <input v-model="sheet.chest" class="fld__input ft-num" type="digit" placeholder="–" />
        </view>
        <view class="fld">
          <text class="fld__label">腰围（cm）</text>
          <input v-model="sheet.waist" class="fld__input ft-num" type="digit" placeholder="–" />
        </view>
        <view class="fld">
          <text class="fld__label">臀围（cm）</text>
          <input v-model="sheet.hip" class="fld__input ft-num" type="digit" placeholder="–" />
        </view>
        <view class="fld">
          <text class="fld__label">臂围（cm）</text>
          <input v-model="sheet.arm" class="fld__input ft-num" type="digit" placeholder="–" />
        </view>
      </view>

      <view class="fld">
        <text class="fld__label">备注</text>
        <input
          v-model="sheet.note"
          class="fld__input"
          placeholder="晨起空腹 / 练后称重…"
          placeholder-class="fld__ph"
          maxlength="64"
        />
      </view>

      <view
        class="ft-btn ft-btn--primary ft-btn--block sheet__btn"
        :class="{ 'ft-btn--disabled': sheet.saving }"
        @click="save"
      >
        保存
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.bodypg__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 20rpx;
}

.bodypg__back {
  font-size: 56rpx;
  line-height: 1;
  color: $app-ink;
  width: 64rpx;
}

.bodypg__title {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.bodypg__head-space {
  width: 64rpx;
}

/* ── 多选态顶栏 ── */
.bodypg__cancel {
  font-size: $text-base;
  color: $app-ink-muted;
  width: 64rpx;
}

.bodypg__sel {
  font-size: $text-base;
  color: $app-ink;
}

.bodypg__acts {
  display: flex;
  align-items: center;
  gap: 28rpx;
  width: 64rpx;
  justify-content: flex-end;
}

.bodypg__all {
  font-size: $text-base;
  color: $app-ink;
  white-space: nowrap;
}

.bodypg__del {
  font-size: $text-base;
  color: #c4453c;
  white-space: nowrap;
}

.bodypg__del--off {
  opacity: 0.35;
}

.bodypg__body {
  padding: 0 32rpx;
}

/* ── 当前体重卡 ── */
.wcard__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wcard__label {
  font-size: 22rpx;
  color: $app-ink-subtle;
}

.wcard__add {
  font-size: $text-base;
  color: $app-ink;
}

.wcard__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0 20rpx;
}

.wcard__num-row {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}

.wcard__num {
  font-size: 88rpx;
  font-weight: 700;
  color: $app-ink;
  line-height: 1;
}

.wcard__unit {
  font-size: $text-md;
  color: $app-ink-muted;
}

.wcard__none {
  font-size: $text-md;
  color: $app-ink-muted;
}

.wcard__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1rpx solid $app-line-soft;
  padding-top: 18rpx;
}

.wcard__date {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.wcard__diff {
  font-size: $text-sm;
  color: $app-ink;
}

/* ── 围度卡 ── */
.gcard__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 20rpx;
}

.gcard__label {
  font-size: 22rpx;
  color: $app-ink-subtle;
}

.gcard__date {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.gcard__grid {
  display: flex;
}

.gcard__cell {
  flex: 1;
  text-align: center;
}

.gcard__cell + .gcard__cell {
  border-left: 1rpx solid $app-line-soft;
}

.gcard__glabel {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.gcard__gnum-row {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 4rpx;
  padding-top: 10rpx;
}

.gcard__gnum {
  font-size: $text-2xl;
  font-weight: 700;
  color: $app-ink;
  line-height: 1.1;
}

.gcard__gunit {
  font-size: 18rpx;
  color: $app-ink-subtle;
}

.gcard__empty {
  display: block;
  padding: 24rpx 0 8rpx;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

/* ── 折线卡 ── */
.chartcard__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 16rpx;
}

.chartcard__title {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.chartcard__range {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.chartcard__tip {
  display: block;
  padding: 10rpx 8rpx 0;
  font-size: 20rpx;
  color: $app-ink-subtle;
}

.chartcard__empty {
  display: block;
  padding: 48rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

/* ── 部位分段条 ── */
.seg {
  display: flex;
  gap: 12rpx;
  padding-bottom: 20rpx;
}

.seg__item {
  flex: 1;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
  background: $app-surface-2;
  border-radius: $radius-sm;
  padding: 12rpx 0;
}

.seg__item--on {
  background: $app-ink;
  color: #ffffff;
  font-weight: 600;
}

/* ── 明细 ── */
.mcard__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 8rpx;
}

.mcard__title {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.mcard__meta {
  font-size: 20rpx;
  letter-spacing: 1rpx;
  color: $app-ink-subtle;
}

.mrow {
  display: flex;
  align-items: flex-start;
  padding: 22rpx 4rpx;
  border-top: 1rpx solid $app-line-soft;
}

.mrow--sel {
  background: $app-canvas;
  border-radius: $radius-md;
  padding-left: 16rpx;
  padding-right: 16rpx;
}

.mrow__check {
  width: 40rpx;
  height: 40rpx;
  border-radius: 8rpx;
  border: 1rpx solid $app-line;
  background: $app-surface;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: 16rpx;
  margin-top: 4rpx;
}

.mrow__check--on {
  background: $app-ink;
  border-color: $app-ink;
}

.mrow__dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: $app-ink;
  margin-right: 16rpx;
  flex-shrink: 0;
  margin-top: 14rpx;
}

.mrow__dot--hollow {
  background: transparent;
  border: 2rpx solid $app-line;
}

.mrow__main {
  flex: 1;
  min-width: 0;
}

.mrow__line1 {
  display: flex;
  align-items: baseline;
}

.mrow__date {
  font-size: $text-base;
  color: $app-ink;
  flex-shrink: 0;
}

.mrow__note {
  font-size: $text-xs;
  color: $app-ink-muted;
  margin-left: 14rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mrow__right {
  margin-left: auto;
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  flex-shrink: 0;
}

.mrow__kg {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.mrow__diff {
  width: 90rpx;
  text-align: right;
  font-size: $text-sm;
  color: $app-ink-muted;
  flex-shrink: 0;
}

.mrow__girth {
  display: block;
  padding-top: 8rpx;
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.mcard__empty {
  padding: 40rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

.mcard__more {
  padding: 24rpx 0 8rpx;
  text-align: center;
  font-size: $text-xs;
  color: $app-ink-subtle;
}

/* ── 底部 ── */
.bodypg__pad {
  height: 220rpx;
}

.bodypg__bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: $app-canvas;
  padding: 20rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
}

/* ── 录入 / 编辑弹层 ── */
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
  margin: 0 auto 24rpx;
}

.sheet__title {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  color: $app-ink;
  padding-bottom: 6rpx;
}

/* 预填提示：告诉主人这些数是上回的，不想记就清空（清空即不存） */
.sheet__hint {
  display: block;
  font-size: $text-xs;
  color: $app-ink-subtle;
  padding-bottom: 16rpx;
}

.fld {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 0;
  border-bottom: 1rpx solid $app-line-soft;
  flex: 1;
}

.fld__label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
}

.unit-switch {
  display: flex;
  gap: 8rpx;
  margin-left: 16rpx;
}

.unit-switch__item {
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  color: $app-ink-muted;
  background: $app-surface-2;
  font-size: $text-xs;
}

.unit-switch__item--on {
  color: #ffffff;
  background: $app-ink;
}

.fld__label {
  font-size: $text-sm;
  color: $app-ink-muted;
  flex-shrink: 0;
}

.fld__picker {
  font-size: $text-md;
  color: $app-ink;
}

.fld__input {
  text-align: right;
  font-size: $text-md;
  color: $app-ink;
  flex: 1;
  margin-left: 20rpx;
}

.fld__ph {
  color: $app-ink-subtle;
}

.fld-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 0 28rpx;
}

.fld-grid .fld {
  flex: 1 1 40%;
}

.sheet__btn {
  margin-top: 32rpx;
}
</style>
