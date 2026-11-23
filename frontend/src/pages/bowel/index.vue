<script setup lang="ts">
// ── 排便记录（ADR 0061）：入口在身体页最底部，不显眼 ──
//    顶部两个按钮一点就按现在记一笔，记完弹出编辑层可改时刻、补形态（大便）或颜色（小便）和备注；
//    中间是统计（每天几次、平均间隔、钟点分布、大便形态分布），下面是历史，点一条改，删了进回收站。
import { computed, reactive, ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import {
  addBowel,
  deleteBowel,
  getBowelStats,
  listBowel,
  updateBowel,
  type BowelKind,
  type BowelRecord,
  type BowelStats
} from '@/api/bowel'
import FtButton from '@/components/FtButton.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import FtSheet from '@/components/FtSheet.vue'
import { ask, TRASH_HINT } from '@/utils/confirm'
import { formatDateTimeLabel, toIsoMinute } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'

const PAGE = 30
const KIND_LABEL: Record<BowelKind, string> = { stool: '大便', urine: '小便' }
/** 布里斯托形态的大白话（1 最硬、7 最稀，3–4 型最理想）。 */
const BRISTOL: string[] = ['一颗颗的硬球', '结块的香肠状', '有裂纹的香肠状', '光滑的香肠状', '边缘清楚的软团', '糊状', '水样']
/** 小便颜色七档（ADR 0061 修订）：仿常见尿色卡；不选就是未知。色块颜色在 uni.scss 的 $urine-1…7。 */
const URINE_COLORS: string[] = ['无色透明', '浅黄', '黄', '深黄', '琥珀', '茶褐', '红／粉']

const RANGES = [
  { days: 7, label: '7 天' },
  { days: 30, label: '30 天' },
  { days: 90, label: '90 天' }
]

const records = ref<BowelRecord[]>([])
const stats = ref<BowelStats | null>(null)
const range = ref(30)
const loading = ref(true)
const hasMore = ref(true)
const loadingMore = ref(false)
const adding = ref(false)

onShow(() => {
  if (!ensureLogin()) return
  void reload()
})

onReachBottom(() => void loadMore())

async function reload() {
  try {
    const [list, s] = await Promise.all([listBowel(PAGE, 0), getBowelStats(range.value)])
    records.value = list
    hasMore.value = list.length >= PAGE
    stats.value = s
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value || loading.value) return
  loadingMore.value = true
  try {
    const more = await listBowel(PAGE, records.value.length)
    records.value = [...records.value, ...more]
    hasMore.value = more.length >= PAGE
  } catch {
    // request 层已统一提示
  } finally {
    loadingMore.value = false
  }
}

async function setRange(days: number) {
  if (range.value === days) return
  range.value = days
  try {
    stats.value = await getBowelStats(days)
  } catch {
    // request 层已统一提示
  }
}

/** 一点就按现在记一笔，接着弹出编辑层：可以改时刻、补形态和备注。 */
async function quickAdd(kind: BowelKind) {
  if (adding.value) return
  adding.value = true
  try {
    const r = await addBowel({ kind })
    uni.showToast({ title: `已记一次${KIND_LABEL[kind]}`, icon: 'none' })
    await reload()
    openSheet(r, true)
  } catch {
    // request 层已统一提示
  } finally {
    adding.value = false
  }
}

// ── 统计 ──

const dayBars = computed(() => {
  const days = stats.value?.days ?? []
  const max = Math.max(1, ...days.map((d) => d.stool + d.urine))
  return days.map((d) => ({ ...d, stoolPct: (d.stool / max) * 100, urinePct: (d.urine / max) * 100 }))
})

const hourBars = computed(() => {
  const s = stats.value
  if (!s) return []
  const totals = s.stool.hours.map((v, i) => v + s.urine.hours[i])
  const max = Math.max(1, ...totals)
  return totals.map((_, i) => ({
    hour: i,
    stoolPct: (s.stool.hours[i] / max) * 100,
    urinePct: (s.urine.hours[i] / max) * 100
  }))
})

const bristolRows = computed(() => {
  const b = stats.value?.bristol ?? []
  const max = Math.max(1, ...b)
  return BRISTOL.map((label, i) => ({ type: i + 1, label, count: b[i] ?? 0, pct: ((b[i] ?? 0) / max) * 100 }))
})
const bristolTotal = computed(() => (stats.value?.bristol ?? []).reduce((s, v) => s + v, 0))

const colorRows = computed(() => {
  const c = stats.value?.color ?? []
  const max = Math.max(1, ...c)
  return URINE_COLORS.map((label, i) => ({ n: i + 1, label, count: c[i] ?? 0, pct: ((c[i] ?? 0) / max) * 100 }))
})
const colorTotal = computed(() => (stats.value?.color ?? []).reduce((s, v) => s + v, 0))

function intervalText(h: number | null): string {
  if (h == null) return '—'
  if (h < 1) return `${Math.round(h * 60)} 分钟`
  if (h < 48) return `${h} 小时`
  return `${Math.round((h / 24) * 10) / 10} 天`
}

// ── 历史与编辑 ──

function rowSub(r: BowelRecord): string {
  const parts: string[] = []
  if (r.bristol) parts.push(`${r.bristol} 型 · ${BRISTOL[r.bristol - 1]}`)
  if (r.color) parts.push(URINE_COLORS[r.color - 1])
  if (r.note) parts.push(r.note)
  return parts.join(' · ')
}

const sheet = reactive({
  show: false,
  fresh: false,
  id: 0,
  kind: 'stool' as BowelKind,
  date: '',
  time: '',
  bristol: null as number | null,
  color: null as number | null,
  note: '',
  saving: false
})

function openSheet(r: BowelRecord, fresh = false) {
  const at = r.occurredAt.replace(' ', 'T')
  Object.assign(sheet, {
    show: true,
    fresh,
    id: r.id,
    kind: r.kind,
    date: at.slice(0, 10),
    time: at.slice(11, 16),
    bristol: r.bristol ?? null,
    color: r.color ?? null,
    note: r.note ?? '',
    saving: false
  })
}

function onDate(e: { detail: { value: string } }) {
  sheet.date = e.detail.value
}

function onTime(e: { detail: { value: string } }) {
  sheet.time = e.detail.value
}

function pickBristol(n: number) {
  sheet.bristol = sheet.bristol === n ? null : n
}

function pickColor(n: number) {
  sheet.color = sheet.color === n ? null : n
}

async function saveSheet() {
  if (sheet.saving) return
  sheet.saving = true
  try {
    await updateBowel(sheet.id, {
      kind: sheet.kind,
      occurredAt: toIsoMinute(sheet.date, sheet.time),
      bristol: sheet.kind === 'stool' ? sheet.bristol : null,
      color: sheet.kind === 'urine' ? sheet.color : null,
      note: sheet.note.trim() || null
    })
    sheet.show = false
    await reload()
  } catch {
    // request 层已统一提示；弹层留着可重试
  } finally {
    sheet.saving = false
  }
}

async function removeSheet() {
  const ok = await ask({ title: '删除这条记录？', content: TRASH_HINT, confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await deleteBowel(sheet.id)
    sheet.show = false
    uni.showToast({ title: '已删除', icon: 'none' })
    await reload()
  } catch {
    // request 层已统一提示
  }
}
</script>

<template>
  <view class="bw">
    <view class="bw__body">
      <FtPageHeader title="排便记录" back />

      <!-- 一点就记 -->
      <view class="quick">
        <view class="quick__btn quick__btn--stool" role="button" :class="{ 'is-busy': adding }" @click="quickAdd('stool')">
          <text class="quick__t">大便</text>
          <text class="quick__s">按现在记一次</text>
        </view>
        <view class="quick__btn quick__btn--urine" role="button" :class="{ 'is-busy': adding }" @click="quickAdd('urine')">
          <text class="quick__t">小便</text>
          <text class="quick__s">按现在记一次</text>
        </view>
      </view>

      <view v-if="loading" class="hint">加载中…</view>

      <template v-else>
        <!-- 统计 -->
        <view v-if="stats" class="glass card">
          <view class="card__head">
            <text class="card__title">统计</text>
            <view class="seg" role="tablist">
              <view
                v-for="r in RANGES"
                :key="r.days"
                class="seg__item"
                :class="{ 'seg__item--on': range === r.days }"
                role="tab"
                :aria-selected="range === r.days"
                @click="setRange(r.days)"
              >
                {{ r.label }}
              </view>
            </view>
          </view>

          <view class="sum">
            <view class="sum__col">
              <text class="sum__k"><text class="dot dot--stool" />大便</text>
              <text class="sum__v ft-num">{{ stats.stool.count }} 次</text>
              <text class="sum__s ft-num">平均每天 {{ stats.stool.perDay }} · 间隔 {{ intervalText(stats.stool.avgIntervalHours) }}</text>
            </view>
            <view class="sum__col">
              <text class="sum__k"><text class="dot dot--urine" />小便</text>
              <text class="sum__v ft-num">{{ stats.urine.count }} 次</text>
              <text class="sum__s ft-num">平均每天 {{ stats.urine.perDay }} · 间隔 {{ intervalText(stats.urine.avgIntervalHours) }}</text>
            </view>
          </view>

          <text class="sub">每天几次</text>
          <view class="bars">
            <view v-for="d in dayBars" :key="d.date" class="bars__col" :aria-label="`${d.date} 大便 ${d.stool} 次 小便 ${d.urine} 次`">
              <view class="bars__seg bars__seg--urine" :style="{ height: d.urinePct + '%' }" />
              <view class="bars__seg bars__seg--stool" :style="{ height: d.stoolPct + '%' }" />
            </view>
          </view>
          <view class="axis ft-num">
            <text>{{ stats.from.slice(5) }}</text>
            <text>{{ stats.to.slice(5) }}</text>
          </view>

          <text class="sub">一天里的钟点</text>
          <view class="bars bars--hours">
            <view v-for="h in hourBars" :key="h.hour" class="bars__col" :aria-label="`${h.hour} 点`">
              <view class="bars__seg bars__seg--urine" :style="{ height: h.urinePct + '%' }" />
              <view class="bars__seg bars__seg--stool" :style="{ height: h.stoolPct + '%' }" />
            </view>
          </view>
          <view class="axis axis--hours ft-num">
            <text>0</text>
            <text>6</text>
            <text>12</text>
            <text>18</text>
            <text>23</text>
          </view>

          <text class="sub">大便形态<text class="sub__aside">（布里斯托分型，3–4 型最理想）</text></text>
          <view v-if="bristolTotal === 0" class="muted">还没填过形态</view>
          <view v-for="b in bristolRows" v-else :key="b.type" class="bri">
            <text class="bri__k ft-num">{{ b.type }} 型</text>
            <text class="bri__label">{{ b.label }}</text>
            <view class="bri__track">
              <view class="bri__fill" :style="{ width: b.pct + '%' }" />
            </view>
            <text class="bri__n ft-num">{{ b.count }}</text>
          </view>

          <text class="sub">小便颜色<text class="sub__aside">（浅黄到黄最常见；持续偏深多喝水）</text></text>
          <view v-if="colorTotal === 0" class="muted">还没填过颜色</view>
          <view v-for="c in colorRows" v-else :key="c.n" class="bri">
            <view class="swatch" :class="'swatch--' + c.n" />
            <text class="bri__label">{{ c.label }}</text>
            <view class="bri__track">
              <view class="bri__fill bri__fill--urine" :style="{ width: c.pct + '%' }" />
            </view>
            <text class="bri__n ft-num">{{ c.count }}</text>
          </view>
        </view>

        <!-- 历史 -->
        <view class="glass card list">
          <text class="card__title">全部记录</text>
          <FtEmpty v-if="records.length === 0" title="还没有记录" sub="点上面的按钮记第一次。" />
          <view v-for="r in records" :key="r.id" class="lrow" role="button" @click="openSheet(r)">
            <text class="lrow__kind" :class="'lrow__kind--' + r.kind">{{ KIND_LABEL[r.kind] }}</text>
            <view class="lrow__main">
              <text class="lrow__time ft-num">{{ formatDateTimeLabel(r.occurredAt) }}</text>
              <text v-if="rowSub(r)" class="lrow__sub">{{ rowSub(r) }}</text>
            </view>
            <FtIcon name="chevron-right" :size="16" tone="subtle" />
          </view>
          <view v-if="loadingMore" class="list__more">加载中…</view>
          <view v-else-if="!hasMore && records.length > 0" class="list__more ft-num">共 {{ records.length }} 条</view>
        </view>
      </template>
    </view>

    <!-- 编辑：刚记的那条也在这里补形态和备注 -->
    <Transition name="ft-sheet">
      <FtSheet
        v-if="sheet.show"
        :title="sheet.fresh ? `已记一次${KIND_LABEL[sheet.kind]}` : '编辑记录'"
        :sub="sheet.fresh ? (sheet.kind === 'urine' ? '可以改时刻、补颜色和备注；不用改就直接关掉' : '可以改时刻、补形态和备注；不用改就直接关掉') : undefined"
        @close="sheet.show = false"
      >
        <scroll-view scroll-y class="sh__scroll" :class="{ 'is-bare': !sheet.fresh }">
          <view class="group">
            <view class="row">
              <text class="row__k">类型</text>
              <view class="seg">
                <view class="seg__item" :class="{ 'seg__item--on': sheet.kind === 'stool' }" @click="sheet.kind = 'stool'">大便</view>
                <view class="seg__item" :class="{ 'seg__item--on': sheet.kind === 'urine' }" @click="sheet.kind = 'urine'">小便</view>
              </view>
            </view>
            <view class="row">
              <text class="row__k">日期</text>
              <picker mode="date" :value="sheet.date" @change="onDate">
                <view class="row__picker ft-num">{{ sheet.date }}<FtIcon name="chevron-right" :size="16" tone="subtle" /></view>
              </picker>
            </view>
            <view class="row">
              <text class="row__k">时刻</text>
              <picker mode="time" :value="sheet.time" @change="onTime">
                <view class="row__picker ft-num">{{ sheet.time }}<FtIcon name="chevron-right" :size="16" tone="subtle" /></view>
              </picker>
            </view>
          </view>

          <view v-if="sheet.kind === 'stool'" class="group">
            <text class="group__k">形态（选填，再点一下取消）</text>
            <view class="chips">
              <view
                v-for="(label, i) in BRISTOL"
                :key="i"
                class="chip"
                :class="{ 'chip--on': sheet.bristol === i + 1 }"
                role="radio"
                :aria-checked="sheet.bristol === i + 1"
                @click="pickBristol(i + 1)"
              >
                <text class="chip__n ft-num">{{ i + 1 }}</text>
                <text class="chip__t">{{ label }}</text>
              </view>
            </view>
          </view>

          <!-- 小便颜色（选填，ADR 0061 修订）：每一档旁边一个色块 -->
          <view v-if="sheet.kind === 'urine'" class="group">
            <text class="group__k">颜色（选填，再点一下取消）</text>
            <view class="chips">
              <view
                v-for="(label, i) in URINE_COLORS"
                :key="i"
                class="chip chip--urine"
                :class="{ 'chip--on': sheet.color === i + 1 }"
                role="radio"
                :aria-checked="sheet.color === i + 1"
                @click="pickColor(i + 1)"
              >
                <view class="swatch" :class="'swatch--' + (i + 1)" />
                <text class="chip__t">{{ label }}</text>
              </view>
            </view>
          </view>

          <view class="group">
            <view class="row">
              <text class="row__k">备注</text>
              <input v-model="sheet.note" class="row__input" maxlength="200" placeholder="选填" placeholder-class="ph" />
            </view>
          </view>
        </scroll-view>
        <view class="sh__btns">
          <FtButton variant="danger" class="sh__btn" @click="removeSheet">删除</FtButton>
          <FtButton variant="primary" class="sh__btn sh__btn--main" :disabled="sheet.saving" @click="saveSheet">保存</FtButton>
        </view>
      </FtSheet>
    </Transition>
  </view>
</template>

<style lang="scss" scoped>
.bw__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(80rpx + env(safe-area-inset-bottom));
}

.hint {
  padding: 40rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.glass {
  @include glass-card;
}

/* ── 一点就记 ── */
.quick {
  display: flex;
  gap: 20rpx;
}

.quick__btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
  padding: 32rpx 0;
  border-radius: $radius-lg;
  @include press(dim);
}

.quick__btn--stool {
  background: $bowel-stool-bg;
  color: $bowel-stool;
}

.quick__btn--urine {
  background: $bowel-urine-bg;
  color: $bowel-urine;
}

.quick__btn.is-busy {
  opacity: 0.6;
}

.quick__t {
  font-size: $text-lg;
  font-weight: $weight-bold;
}

.quick__s {
  font-size: $text-xs;
  opacity: 0.8;
}

/* ── 卡片 ── */
.card {
  padding: 28rpx 32rpx;
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.card__title {
  @include card-title;
}

.seg {
  @include seg-track;
  display: flex;
}

.seg__item {
  @include seg-item(8rpx 22rpx);
  text-align: center;
}

.seg__item--on {
  @include seg-item-on;
}

.sum {
  display: flex;
  gap: 24rpx;
  margin-top: 24rpx;
}

.sum__col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.sum__k {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.sum__v {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $app-ink;
}

.sum__s {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.dot {
  display: inline-block;
  width: 16rpx;
  height: 16rpx;
  margin-right: 8rpx;
  border-radius: 50%;
}

.dot--stool {
  background: $bowel-stool;
}

.dot--urine {
  background: $bowel-urine;
}

.sub {
  display: block;
  margin: 32rpx 0 12rpx;
  font-size: $text-sm;
  font-weight: $weight-bold;
  color: $app-ink;
}

.sub__aside {
  font-weight: normal;
  color: $app-ink-muted;
}

.muted {
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* 柱状图：每根柱子下段大便、上段小便，按范围内最多的那天（或那个钟点）定满格 */
.bars {
  display: flex;
  align-items: flex-end;
  gap: 2rpx;
  height: 160rpx;
  border-bottom: 1rpx solid $app-line;
}

.bars__col {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.bars__seg {
  width: 100%;
}

.bars__seg--stool {
  background: $bowel-stool;
}

.bars__seg--urine {
  background: $bowel-urine;
  opacity: 0.6;
}

.axis {
  display: flex;
  justify-content: space-between;
  padding-top: 6rpx;
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.bri {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 8rpx 0;
}

.bri__k {
  flex-shrink: 0;
  width: 72rpx;
  font-size: $text-sm;
  color: $app-ink;
}

.bri__label {
  flex-shrink: 0;
  width: 200rpx;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.bri__track {
  flex: 1;
  height: 14rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  overflow: hidden;
}

.bri__fill {
  height: 100%;
  border-radius: $radius-pill;
  background: $bowel-stool;
}

.bri__fill--urine {
  background: $bowel-urine;
}

/* 小便颜色色块：浅色块靠描边在白底上显形 */
.swatch {
  flex-shrink: 0;
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  box-shadow: inset 0 0 0 2rpx $urine-swatch-ring;
}

.bri > .swatch {
  margin: 0 22rpx;
}

@for $i from 1 through 7 {
  .swatch--#{$i} {
    background: map-get(
      (
        1: $urine-1,
        2: $urine-2,
        3: $urine-3,
        4: $urine-4,
        5: $urine-5,
        6: $urine-6,
        7: $urine-7
      ),
      $i
    );
  }
}

.bri__n {
  flex-shrink: 0;
  width: 48rpx;
  text-align: right;
  font-size: $text-sm;
  color: $app-ink;
}

/* ── 历史 ── */
.list {
  padding-bottom: 8rpx;
}

.lrow {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 22rpx 4rpx;
  border-top: 1rpx solid $app-line-soft;
  @include press(dim);
}

.list .card__title + .lrow {
  margin-top: 16rpx;
}

.lrow__kind {
  flex-shrink: 0;
  padding: 4rpx 16rpx;
  border-radius: $radius-pill;
  font-size: $text-xs;
  font-weight: $weight-bold;
}

.lrow__kind--stool {
  background: $bowel-stool-bg;
  color: $bowel-stool;
}

.lrow__kind--urine {
  background: $bowel-urine-bg;
  color: $bowel-urine;
}

.lrow__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.lrow__time {
  font-size: $text-md;
  color: $app-ink;
}

.lrow__sub {
  font-size: $text-xs;
  color: $app-ink-muted;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list__more {
  padding: 20rpx 0;
  text-align: center;
  font-size: $text-xs;
  color: $app-ink-subtle;
}

/* ── 编辑弹层 ── */
.sh__scroll {
  flex: 1;
  min-height: 0;
  max-height: 60vh;
}

.sh__scroll.is-bare {
  margin-top: 28rpx;
}

.group {
  @include sheet-card;
  padding: 4rpx 28rpx;
}

.group + .group {
  margin-top: 20rpx;
}

.group__k {
  display: block;
  padding: 20rpx 0 12rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  min-height: 92rpx;
}

.row + .row {
  border-top: 1rpx solid $app-line-soft;
}

.row__k {
  flex-shrink: 0;
  font-size: $text-base;
  color: $app-ink-muted;
}

.row__picker {
  display: flex;
  align-items: center;
  gap: 4rpx;
  font-size: $text-md;
  color: $app-ink;
}

.row__input {
  flex: 1;
  text-align: right;
  font-size: $text-md;
  color: $app-ink;
}

.ph {
  color: $app-ink-subtle;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  padding-bottom: 20rpx;
}

.chip {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 10rpx 18rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-sm;
  color: $app-ink-2;
}

.chip--on {
  background: $bowel-stool-bg;
  color: $bowel-stool;
  box-shadow: inset 0 0 0 2rpx $bowel-stool;
}

.chip--urine.chip--on {
  background: $bowel-urine-bg;
  color: $bowel-urine;
  box-shadow: inset 0 0 0 2rpx $bowel-urine;
}

.chip__n {
  font-weight: $weight-bold;
}

.sh__btns {
  flex-shrink: 0;
  display: flex;
  gap: 16rpx;
  margin-top: 28rpx;
}

.sh__btn {
  flex: 1;
}

.sh__btn--main {
  flex: 2;
}
</style>
