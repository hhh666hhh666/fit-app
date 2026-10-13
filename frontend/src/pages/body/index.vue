<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { createBodyRecord, getBodyOverview, getBodyRecords } from '@/api/body'
import type { BodyOverview, BodyRecordRow } from '@/api/body'
import FtTrendChart from '@/components/FtTrendChart.vue'
import { formatDateLabel, formatThousands } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'

// ── 身体档案（设计稿：.workbuddy/stitch/screen/body.png）──

function goBack() {
  uni.navigateBack()
}

const overview = ref<BodyOverview | null>(null)
const records = ref<BodyRecordRow[]>([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const [ov, rows] = await Promise.all([getBodyOverview(), getBodyRecords(20)])
    overview.value = ov
    records.value = rows
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

/** 从饮食卡「记体重」带参进入（?record=1）：加载后自动打开录入弹层。 */
const pendingRecord = ref(false)

onLoad((query) => {
  const q = (query || {}) as Record<string, string | undefined>
  pendingRecord.value = q.record === '1'
})

onShow(() => {
  if (!ensureLogin()) return
  load()
  if (pendingRecord.value) {
    pendingRecord.value = false
    openSheet()
  }
})

const series = computed(() => overview.value?.weightSeries ?? [])

// ── 折线：交给 FtTrendChart（可缩放时间轴，ADR 0015）──
const firstWeight = computed(() => series.value[0]?.weight ?? null)
const lastWeight = computed(() => series.value[series.value.length - 1]?.weight ?? null)

/** 喂给图表组件的逐点序列（体重值域窄，组件那边用 zeroBased=false 定 y 轴）。 */
const weightPoints = computed(() =>
  series.value.map((p) => ({ date: p.date, value: Number(p.weight) }))
)

// ── 录入弹层 ──
const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const sheet = ref({
  show: false,
  measuredAt: today(),
  weight: '',
  chest: '',
  waist: '',
  hip: '',
  arm: '',
  note: '',
  saving: false
})

function openSheet() {
  sheet.value = {
    show: true,
    measuredAt: today(),
    weight: '',
    chest: '',
    waist: '',
    hip: '',
    arm: '',
    note: '',
    saving: false
  }
}

function onDateChange(e: { detail: { value: string } }) {
  sheet.value.measuredAt = e.detail.value
}

async function save() {
  const f = sheet.value
  const payload = {
    measuredAt: f.measuredAt,
    weight: f.weight ? Number(f.weight) : null,
    chest: f.chest ? Number(f.chest) : null,
    waist: f.waist ? Number(f.waist) : null,
    hip: f.hip ? Number(f.hip) : null,
    arm: f.arm ? Number(f.arm) : null,
    note: f.note.trim()
  }
  if (payload.weight == null && payload.chest == null && payload.waist == null
    && payload.hip == null && payload.arm == null) {
    uni.showToast({ title: '体重和围度至少填一项', icon: 'none' })
    return
  }
  f.saving = true
  try {
    await createBodyRecord(payload)
    sheet.value.show = false
    uni.showToast({ title: '已记录', icon: 'success' })
    await load()
  } catch {
    // unwrap 已提示
  } finally {
    f.saving = false
  }
}

function signed(v: number | null | undefined): string {
  if (v == null) return ''
  const n = Number(v)
  return `${n > 0 ? '+' : ''}${n.toFixed(1)}`
}

function girthValue(v: number | null | undefined): string {
  return v == null ? '–' : String(v)
}
</script>

<template>
  <view class="bodypg">
    <view class="bodypg__head">
      <text class="bodypg__back" @click="goBack">‹</text>
      <text class="bodypg__title">身体档案</text>
      <view class="bodypg__head-space" />
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
              <text class="wcard__num ft-num">{{ overview.latest.weight }}</text>
              <text class="wcard__unit">kg</text>
            </template>
            <text v-else class="wcard__none">还没有体重记录</text>
          </view>
          <text v-if="overview?.isLowest" class="wcard__badge ft-marker ft-num">阶段最低 · PR</text>
        </view>
        <view v-if="overview?.latest" class="wcard__foot">
          <text class="wcard__date ft-num">{{ formatDateLabel(overview.latest.date) }} · 最近一次记录</text>
          <text v-if="overview.weekDiff != null" class="wcard__diff ft-num">
            较上周 {{ signed(overview.weekDiff) }} kg
          </text>
        </view>
      </view>

      <!-- 基础围度卡 -->
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

      <!-- 近 3 个月体重 -->
      <view class="ft-card chartcard">
        <view class="chartcard__head">
          <text class="chartcard__title">近 3 个月体重</text>
          <text v-if="firstWeight != null && lastWeight != null" class="chartcard__range ft-num">
            {{ formatThousands(firstWeight) }} → {{ formatThousands(lastWeight) }} kg
          </text>
        </view>
        <FtTrendChart
          v-if="series.length >= 2"
          :points="weightPoints"
          :height="180"
          :zero-based="false"
        />
        <text v-else class="chartcard__empty">再记一次体重，就能看到趋势线</text>
        <text v-if="series.length >= 2" class="chartcard__tip">
          单指按住看数值 · 双指捏合缩放 · 双指拖动平移
        </text>
      </view>

      <!-- 明细 -->
      <view class="ft-card mcard">
        <view class="mcard__head">
          <text class="mcard__title">最近记录明细</text>
          <text class="mcard__meta ft-num">DATE / KG / DIFF</text>
        </view>
        <view v-if="records.length === 0" class="mcard__empty">还没有记录</view>
        <view v-for="(r, i) in records" :key="r.id" class="mrow">
          <view class="mrow__dot" :class="{ 'mrow__dot--hollow': i !== 0 }" />
          <text class="mrow__date ft-num">{{ formatDateLabel(r.date) }}</text>
          <text class="mrow__note">{{ r.note }}</text>
          <text class="mrow__kg ft-num">{{ formatThousands(Number(r.weight)) }} kg</text>
          <text class="mrow__diff ft-num">{{ r.diff == null ? '—' : signed(r.diff) }}</text>
        </view>
      </view>

      <view class="bodypg__pad" />
    </view>

    <!-- 底部固定：记录今天 -->
    <view class="bodypg__bottom">
      <view class="ft-btn ft-btn--primary ft-btn--block" @click="openSheet">+ 记录今天的数据</view>
    </view>

    <!-- 录入弹层 -->
    <view v-if="sheet.show" class="sheet-mask" @click="sheet.show = false" />    <view v-if="sheet.show" class="sheet">
      <view class="sheet__handle" />
      <text class="sheet__title">记录身体数据</text>

      <view class="fld">
        <text class="fld__label">日期</text>
        <picker mode="date" :value="sheet.measuredAt" @change="onDateChange">
          <view class="fld__picker ft-num">{{ sheet.measuredAt }} ▾</view>
        </picker>
      </view>

      <view class="fld">
        <text class="fld__label">体重（kg）</text>
        <input
          v-model="sheet.weight"
          class="fld__input ft-num"
          type="digit"
          placeholder="如 72.5"
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
  align-items: center;
  padding: 26rpx 4rpx;
  border-top: 1rpx solid $app-line-soft;
}

.mrow__dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: $app-ink;
  margin-right: 16rpx;
  flex-shrink: 0;
}

.mrow__dot--hollow {
  background: transparent;
  border: 2rpx solid $app-line;
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

.mrow__kg {
  margin-left: auto;
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

.mcard__empty {
  padding: 40rpx 0;
  text-align: center;
  font-size: $text-sm;
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

/* ── 录入弹层 ── */
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
  padding-bottom: 20rpx;
}

.fld {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 0;
  border-bottom: 1rpx solid $app-line-soft;
  flex: 1;
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
