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
const overviewText = computed(() => {
  const s = summary.value
  if (!s) return '—'
  const total = Math.round(Number(s.totalVolume))
  return `${s.count} 练 · 总容量 ${total > 0 ? `${formatThousands(total)} kg` : '—'}`
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
  uni.showModal({
    title: '删除训练记录',
    content: `删除选中的 ${ids.length} 条记录？删除后不可恢复。`,
    confirmText: '删除',
    confirmColor: '#C4453C',
    success: async ({ confirm }) => {
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
    }
  })
}
</script>

<template>
  <FtTabPage :page="tab" class="log">
    <view class="log__body">
      <!-- 顶栏：常规＝标题＋月份；多选态＝取消＋已选＋全选/删除 -->
      <view class="log__head">
        <template v-if="!selectMode">
          <text class="log__title">训练日志</text>
          <!-- 月份条（自绘）：左右翻月，当月封顶不再往后 -->
          <view class="log__monthbar">
            <text class="log__arrow" @click="shiftMonth(-1)">‹</text>
            <text class="log__month ft-num">{{ monthLabel }}</text>
            <text class="log__arrow" :class="{ 'log__arrow--off': !canNext }" @click="shiftMonth(1)">›</text>
          </view>
        </template>
        <template v-else>
          <text class="log__cancel" @click="exitSelect">取消</text>
          <text class="log__sel ft-num">已选 {{ selectedCount }} 项</text>
          <view class="log__acts">
            <text class="log__all" @click="toggleAll">{{ allSelected ? '取消全选' : '全选' }}</text>
            <text
              class="log__del"
              :class="{ 'log__del--off': selectedCount === 0 }"
              @click="doDelete"
            >
              删除
            </text>
          </view>
        </template>
      </view>

      <!-- 月概览条：数字按当月算，环比要给基准（ADR 0023） -->
      <view class="overview">
        <text class="overview__caps">MONTH OVERVIEW</text>
        <view class="overview__right">
          <text class="overview__nums ft-num">{{ overviewText }}</text>
          <text v-if="deltaText" class="overview__delta ft-num">{{ deltaText }}</text>
        </view>
      </view>

      <!-- 时间线列表 -->
      <view v-if="loading" class="ft-loading">加载中…</view>
      <view v-else-if="items.length === 0" class="ft-empty">{{ emptyText }}</view>
      <view v-else class="ledger">
        <view class="ledger__thead">
          <view v-if="selectMode" class="col-check" />
          <text class="col-date">日期 / DATE</text>
          <text class="col-mid">项目 &amp; 主项成绩 / DETAILS</text>
          <text class="col-time">用时 / TIME</text>
        </view>
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
            <!-- 时分放在副行：日期列只有 170rpx，挤进主行会把中间标题列压扁（2026-10-19） -->
            <text class="lrow__date-week ft-num">
              {{ formatTimeLabel(item.createdAt) }} · {{ weekdayOf(item.createdAt) }}
            </text>
          </view>
          <view class="lrow__mid">
            <view class="lrow__title-line">
              <text class="lrow__title">{{ item.title }}</text>
            </view>
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
  padding: 36rpx 32rpx 0;
}

.log__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 24rpx;
}

.log__title {
  font-size: 48rpx;
  font-weight: 700;
  color: $app-ink;
}

.log__month {
  font-size: $text-sm;
  color: $app-ink-muted;
  border: 1rpx solid $app-line;
  border-radius: 8rpx;
  padding: 8rpx 16rpx;
}

/* ── 月份条：自绘（全站顶部栏都是自绘，不用原生 picker，免得破风格）── */
.log__monthbar {
  display: flex;
  align-items: center;
  gap: 6rpx;
}

.log__arrow {
  font-size: $text-lg;
  line-height: 1;
  color: $app-ink-muted;
  padding: 8rpx 10rpx;
}

.log__arrow--off {
  opacity: 0.25;
}

/* ── 多选态顶栏 ── */
.log__cancel {
  font-size: $text-base;
  color: $app-ink-muted;
  padding: 8rpx 0;
}

.log__sel {
  font-size: $text-base;
  font-weight: 600;
  color: $app-ink;
}

.log__acts {
  display: flex;
  align-items: center;
  gap: 28rpx;
}

.log__all {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.log__del {
  font-size: $text-sm;
  font-weight: 500;
  color: #c4453c;
  padding: 8rpx 0;
}

.log__del--off {
  opacity: 0.35;
}

/* ── 月概览条 ── */
.overview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: $app-surface;
  border: 1rpx solid $app-line;
  padding: 20rpx 24rpx;
  margin-bottom: 20rpx;
}

.overview__caps {
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: $app-ink-muted;
}

.overview__nums {
  font-size: $text-sm;
  color: $app-ink;
}

.overview__right {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
}

.overview__delta {
  font-size: 22rpx;
  color: $app-ink-muted;
}

/* ── 时间线列表 ── */
.ledger {
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-lg;
  overflow: hidden;
  margin-bottom: 24rpx;
}

.ledger__thead {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  background: $app-canvas;
  border-bottom: 1rpx solid $app-line;
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: $app-ink-muted;
}

.col-check {
  width: 56rpx;
  flex-shrink: 0;
}

.col-date {
  width: 170rpx;
  flex-shrink: 0;
}

.col-mid {
  flex: 1;
}

.col-time {
  width: 130rpx;
  text-align: right;
  flex-shrink: 0;
}

.lrow {
  display: flex;
  align-items: center;
  padding: 24rpx;
}

.lrow + .lrow {
  border-top: 1rpx solid $app-line-soft;
}

/* 多选态：选中行浅底；勾选圈与行等高对齐 */
.lrow--sel {
  background: $app-canvas;
}

.lrow__check {
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
}

.lrow__check--on {
  background: $app-ink;
  border-color: $app-ink;
}

.lrow__date {
  width: 170rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.lrow__date-main {
  font-size: $text-md;
  font-weight: 500;
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
  gap: 6rpx;
  padding-right: 12rpx;
}

.lrow__title-line {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.lrow__title {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.lrow__detail {
  font-size: $text-sm;
  color: $app-ink-muted;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lrow__time {
  width: 150rpx;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4rpx;
  text-align: right;
  font-size: $text-sm;
  color: $app-ink-muted;
  flex-shrink: 0;
}

/* 消耗那一行：比时长小一号，扫列表时不抢注意力（ADR 0029） */
.lrow__kcal {
  font-size: 20rpx;
  color: $app-ink-subtle;
}

.log__more {
  padding: 24rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}
</style>
