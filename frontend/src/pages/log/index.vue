<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { batchDeleteWorkouts, getMonthSummary, listWorkouts } from '@/api/workout'
import { clearAll } from '@/api/cache'
import type { MonthSummary, WorkoutListItem } from '@/api/workout'
import { formatDateLabel, formatThousands, formatTimeLabel } from '@/utils/format'
import { metricKindOf, metricText } from '@/utils/metric'
import { ensureLogin } from '@/utils/guard'
import FtTabPage from '@/components/FtTabPage.vue'
import { useTabPage } from '@/utils/tab-page'
import FtIcon from '@/components/FtIcon.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import { ask } from '@/utils/confirm'

// 切页入场与底栏接线（ADR 0038／0040）：onShow 里登录守卫之后调 tab.enter()
const tab = useTabPage('log')

const PAGE_SIZE = 50

const items = ref<WorkoutListItem[]>([])
const loading = ref(true)
const loadingMore = ref(false)
const hasMore = ref(false)

// ── 多选删除：长按记录进入多选态；多选态下点击行＝切换勾选（不再进详情）──
const selectMode = ref(false)
const selected = ref<Set<number>>(new Set())
const selectedCount = computed(() => selected.value.size)
const allSelected = computed(
  () => items.value.length > 0 && selected.value.size === items.value.length
)

const WEEK_CHARS = ['日', '一', '二', '三', '四', '五', '六']

// ── 按月（scope #26）：默认当月、可左右翻月；概览数字按当月算并给环比 ──
const now = new Date()
const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
const month = ref(currentMonth)
const summary = ref<MonthSummary | null>(null)

const monthLabel = computed(() => {
  const [y, m] = month.value.split('-')
  return `${y}年${Number(m)}月`
})
/** 不许翻到未来：当月已是最新（右边箭头置灰）。 */
const canNext = computed(() => month.value < currentMonth)

const emptyText = computed(() =>
  month.value === currentMonth ? '这个月还没有训练记录——去练一次吧' : `${monthLabel.value}没有训练记录`
)

/**
 * 概览文案：次数与总容量按**当月**算（不再拿列表长度跟封顶列表求和冒充）。
 * 容量位规则＝**有容量才显示数字**：容量口径已排除有氧（ADR 0023），
 * 所以空月份与纯有氧月份都显示「—」，不显示 `0 kg`。
 */
const overviewCount = computed(() => (summary.value ? String(summary.value.count) : '—'))
const overviewVolume = computed(() => {
  const total = Math.round(Number(summary.value?.totalVolume ?? 0))
  return total > 0 ? formatThousands(total) : '—'
})

/**
 * 环比：百分比 ＋ 绝对量（绝对值才是推着人加量的那个数）。
 * ⚠️ **没有基准就不给差值**（ADR 0023）：上月没记录、或上月容量为 0（纯有氧月／没练力量）时一律不显示——
 * 拿 0 当基准会算出「+0 kg」「+∞%」这种没意义的东西。
 */
const deltaText = computed(() => {
  const s = summary.value
  if (!s) return ''
  const prevVol = Number(s.prevTotalVolume)
  if (s.prevCount === 0 || prevVol <= 0) return ''
  const diff = Math.round(Number(s.totalVolume) - prevVol)
  const sign = diff >= 0 ? '+' : '-'
  const pct = Math.round((diff / prevVol) * 100)
  return `比上月 ${pct >= 0 ? '+' : ''}${pct}%（${sign}${formatThousands(Math.abs(diff))} kg）`
})

/** 翻月：换月即重载第一页，并退出多选（跨月选择只会让人困惑）。 */
function shiftMonth(delta: number) {
  const [y, m] = month.value.split('-').map(Number)
  const d = new Date(y, m - 1 + delta, 1)
  const next = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  if (next > currentMonth) return
  month.value = next
  exitSelect()
  void load(true)
}

/** `reset` ＝ 从头加载（换月／首次进页），否则是触底加下一页。 */
async function load(reset = false) {
  if (reset) {
    loading.value = true
    items.value = []
  } else {
    loadingMore.value = true
  }
  try {
    const [list, sum] = await Promise.all([
      listWorkouts(PAGE_SIZE, reset ? 0 : items.value.length, month.value),
      reset ? getMonthSummary(month.value) : Promise.resolve(null)
    ])
    items.value = reset ? list : [...items.value, ...list]
    if (sum) summary.value = sum
    hasMore.value = list.length === PAGE_SIZE
  } catch {
    // 401 时 request 层已跳登录页；其它错误已 toast
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

onShow(() => {
  if (!ensureLogin()) return
  tab.enter()
  void load(true)
})

/** 下拉＝清缓存后重取当月第一页（ADR 0035 的逃生门；切页本身不会再发请求）。 */
onPullDownRefresh(async () => {
  clearAll()
  await load(true)
  uni.stopPullDownRefresh()
})

/** 触底加载当月的下一页（本月条目多于 50 条时才用得上）。 */
onReachBottom(() => {
  if (!hasMore.value || loading.value || loadingMore.value) return
  void load()
})

function weekdayOf(iso: string): string {
  return `周${WEEK_CHARS[new Date(iso).getDay()]}`
}

function minutes(item: WorkoutListItem): number {
  return Math.round(item.durationSec / 60)
}

/**
 * 主项成绩文案，口径与详情页／汇总页同一套（ADR 0025，`utils/metric.ts`）：
 * 有力量动作 → 容量；纯层数口径（爬楼）→ 总层数；都没有 → 「—」。
 * ⚠️ 纯有氧场次不许写「0 kg」（ADR 0023）——那看着像故障。
 */
function lineOf(item: WorkoutListItem): string {
  const floors = item.totalFloors ?? 0
  const kind = metricKindOf(item.hasStrength !== false, floors)
  const val = metricText(kind, Number(item.totalVolume), floors)
  return item.firstExercise ? `${item.firstExercise} · ${val}` : val
}

/** 点一行 → 训练详情（带 id；detail 页自己拉 GET /api/workouts/{id}） */
function openDetail(id: number) {
  uni.navigateTo({ url: `/pages/detail/index?id=${id}` })
}

// ── 多选态操作 ──

function toggleSelect(id: number) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

/**
 * H5 上长按触发后，浏览器还会补发一次 click——若不挡，刚被长按选中的那条
 * 会被这次 click 又取消掉（选数从 1 变 0，表现为「长按没反应」）。
 * 长按期间挂一个短窗口，窗口内的 click 一律吞掉。
 */
let clickLockUntil = 0

function onRowClick(item: WorkoutListItem) {
  if (Date.now() < clickLockUntil) return
  if (selectMode.value) toggleSelect(item.id)
  else openDetail(item.id)
}

/** 长按任一记录进入多选态并选中它。 */
function onRowLongPress(item: WorkoutListItem) {
  if (selectMode.value) return
  clickLockUntil = Date.now() + 400
  selectMode.value = true
  selected.value = new Set([item.id])
}

function exitSelect() {
  selectMode.value = false
  selected.value = new Set()
}

/** 全选＝当前**已加载**的这些（当月条目多于 50 条时，未加载的下一页不在其中）。 */
function toggleAll() {
  selected.value = allSelected.value ? new Set() : new Set(items.value.map((i) => i.id))
}

function doDelete() {
  const ids = [...selected.value]
  if (ids.length === 0) return
  void ask({
    title: '删除训练记录',
    content: `删除选中的 ${ids.length} 条记录？删除后不可恢复。`,
    confirmText: '删除',
    danger: true
  }).then(async (confirm) => {
    if (!confirm) return
    try {
      await batchDeleteWorkouts(ids)
      exitSelect()
      // 重载而不是本地剔除：概览的次数与容量也跟着变，不能只动列表
      await load(true)
      uni.showToast({ title: `已删除 ${ids.length} 条`, icon: 'none' })
    } catch {
      // request 层已统一提示
    }
  })
}
</script>

<template>
  <FtTabPage :page="tab" class="log">
    <view class="log__body">
      <!-- 页头：常规＝标题＋月份胶囊；多选态＝取消＋已选＋全选/删除 -->
      <view class="log__head">
        <template v-if="!selectMode">
          <text class="log__title">训练日志</text>
          <!-- 月份胶囊：左右翻月，当月封顶不再往后 -->
          <view class="monthbar">
            <view class="monthbar__arrow" role="button" aria-label="上个月" @click="shiftMonth(-1)">
              <FtIcon name="chevron-left" :size="18" :stroke="2" />
            </view>
            <text class="monthbar__label ft-num">{{ monthLabel }}</text>
            <view
              class="monthbar__arrow"
              :class="{ 'monthbar__arrow--off': !canNext }"
              role="button"
              aria-label="下个月"
              @click="shiftMonth(1)"
            >
              <FtIcon name="chevron-right" :size="18" :stroke="2" />
            </view>
          </view>
        </template>
        <template v-else>
          <view class="pill" role="button" @click="exitSelect">取消</view>
          <text class="log__sel ft-num">已选 {{ selectedCount }} 项</text>
          <view class="log__acts">
            <text class="log__all" @click="toggleAll">{{ allSelected ? '取消全选' : '全选' }}</text>
            <view
              class="pill pill--danger"
              :class="{ 'pill--off': selectedCount === 0 }"
              role="button"
              @click="doDelete"
            >
              删除
            </view>
          </view>
        </template>
      </view>

      <!-- 月概览：数字按当月算，环比要给基准（ADR 0023） -->
      <view class="glass overview">
        <view class="overview__cell">
          <text class="overview__label">本月训练</text>
          <text class="overview__num ft-num">{{ overviewCount }}<text class="overview__unit">练</text></text>
        </view>
        <view class="overview__cell">
          <text class="overview__label">总容量</text>
          <text class="overview__num ft-num">
            {{ overviewVolume }}<text v-if="overviewVolume !== '—'" class="overview__unit">kg</text>
          </text>
        </view>
        <text v-if="deltaText" class="overview__delta ft-num">{{ deltaText }}</text>
      </view>

      <!-- 记录列表 -->
      <view v-if="loading" class="ft-loading">加载中…</view>
      <view v-else-if="items.length === 0" class="glass">
        <FtEmpty :title="emptyText" />
      </view>
      <view v-else class="glass ledger">
        <view
          v-for="item in items"
          :key="item.id"
          class="lrow"
          :class="{ 'lrow--sel': selectMode && selected.has(item.id) }"
          @click="onRowClick(item)"
          @longpress="onRowLongPress(item)"
        >
          <view
            v-if="selectMode"
            class="lrow__check"
            :class="{ 'lrow__check--on': selected.has(item.id) }"
          >
            <FtIcon v-if="selected.has(item.id)" name="check" :size="13" :stroke="2.4" tone="inverse" />
          </view>
          <view class="lrow__date">
            <text class="lrow__date-main ft-num">{{ formatDateLabel(item.createdAt) }}</text>
            <!-- 时分放在副行：日期列窄，挤进主行会把中间标题列压扁（2026-10-19） -->
            <text class="lrow__date-week ft-num">
              {{ formatTimeLabel(item.createdAt) }} · {{ weekdayOf(item.createdAt) }}
            </text>
          </view>
          <view class="lrow__mid">
            <text class="lrow__title">{{ item.title }}</text>
            <text class="lrow__detail ft-num">{{ lineOf(item) }}</text>
          </view>
          <view class="lrow__time">
            <text class="ft-num">{{ minutes(item) }} 分钟</text>
            <!-- 消耗（ADR 0029）：没填就不显示，别让「—」挤进这一列 -->
            <text v-if="item.calories != null" class="lrow__kcal ft-num">{{ item.calories }} 大卡</text>
          </view>
        </view>
      </view>

      <view v-if="loadingMore" class="log__more">加载中…</view>
    </view>
  </FtTabPage>
</template>

<style lang="scss" scoped>
.log__body {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
  padding: calc(40rpx + env(safe-area-inset-top)) 40rpx 0;
}

.glass {
  @include glass-card;
}

/* ── 页头 ── */
.log__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  min-height: 84rpx;
  padding: 12rpx 8rpx 4rpx;
}

.log__title {
  font-size: $text-hero;
  font-weight: $weight-bold;
  line-height: 1.25;
  color: $app-ink;
}

/* 月份胶囊：自绘（全站顶部栏都是自绘，不用原生 picker，免得破风格） */
.monthbar {
  @include glass;
  display: flex;
  align-items: center;
  padding: 4rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
}

.monthbar__arrow {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $app-ink-2;
  @include press(fill);
}

.monthbar__arrow--off {
  opacity: 0.25;
}

.monthbar__label {
  padding: 0 4rpx;
  font-size: $text-base;
  color: $app-ink;
}

/* ── 多选态页头 ── */
.pill {
  @include glass-pill(14rpx 28rpx);
}

.pill--danger {
  color: $app-danger;
}

.pill--off {
  opacity: 0.4;
}

.log__sel {
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $app-ink;
}

.log__acts {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.log__all {
  font-size: $text-base;
  color: $app-ink-muted;
}

/* ── 月概览 ── */
.overview {
  display: flex;
  flex-wrap: wrap;
  row-gap: 12rpx;
}

.overview__cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.overview__cell + .overview__cell {
  padding-left: 32rpx;
  border-left: 1rpx solid $app-line-soft;
}

.overview__label {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.overview__num {
  font-size: 52rpx;
  font-weight: $weight-heavy;
  letter-spacing: -0.02em;
  line-height: 1.2;
  color: $app-ink;
}

.overview__unit {
  margin-left: 6rpx;
  font-size: $text-sm;
  font-weight: $weight-bold;
  letter-spacing: 0;
  color: $app-ink-muted;
}

.overview__delta {
  flex-basis: 100%;
  padding-top: 16rpx;
  border-top: 1rpx solid $app-line-soft;
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* ── 记录列表 ── */
.ledger {
  padding: 8rpx 28rpx;
}

.lrow {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: 0 -12rpx;
  padding: 24rpx 12rpx;
  border-radius: $radius-md;
  @include press(fill);
}

.lrow + .lrow {
  border-top: 1rpx solid $app-line-soft;
}

/* 多选态：选中行浅底 */
.lrow--sel {
  background: $app-surface-2;
}

.lrow__check {
  width: 40rpx;
  height: 40rpx;
  flex-shrink: 0;
  box-sizing: border-box;
  border-radius: 50%;
  border: 2rpx solid $app-line-dash;
  display: flex;
  align-items: center;
  justify-content: center;
}

.lrow__check--on {
  background: $app-ink;
  border-color: $app-ink;
}

.lrow__date {
  width: 156rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.lrow__date-main {
  font-size: $text-md;
  color: $app-ink;
}

.lrow__date-week {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.lrow__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.lrow__title {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $app-ink;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lrow__detail {
  font-size: $text-sm;
  color: $app-ink-muted;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lrow__time {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* 消耗那一行：比时长小一号，扫列表时不抢注意力（ADR 0029） */
.lrow__kcal {
  font-size: $text-xs;
}

.log__more {
  padding: 8rpx 0 24rpx;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}
</style>
