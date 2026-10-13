<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import FtTabBar from '@/components/FtTabBar.vue'
import { batchDeleteWorkouts, listWorkouts } from '@/api/workout'
import type { WorkoutListItem } from '@/api/workout'
import { formatDateLabel, formatThousands } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'

const items = ref<WorkoutListItem[]>([])
const loading = ref(true)

// ── 多选删除：长按记录进入多选态；多选态下点击行＝切换勾选（不再进详情）──
const selectMode = ref(false)
const selected = ref<Set<number>>(new Set())
const selectedCount = computed(() => selected.value.size)
const allSelected = computed(
  () => items.value.length > 0 && selected.value.size === items.value.length
)

const WEEK_CHARS = ['日', '一', '二', '三', '四', '五', '六']

const monthLabel = (() => {
  const d = new Date()
  return `${d.getFullYear()}年${d.getMonth() + 1}月`
})()

const totalVolume = computed(() =>
  items.value.reduce((sum, s) => sum + Number(s.totalVolume || 0), 0)
)

async function load() {
  loading.value = true
  try {
    items.value = await listWorkouts()
  } catch {
    // 401 时 request 层已跳登录页；其它错误已 toast
  } finally {
    loading.value = false
  }
}

onShow(() => {
  if (!ensureLogin()) return
  load()
})

function weekdayOf(iso: string): string {
  return `周${WEEK_CHARS[new Date(iso).getDay()]}`
}

function minutes(item: WorkoutListItem): number {
  return Math.round(item.durationSec / 60)
}

function lineOf(item: WorkoutListItem): string {
  return item.firstExercise
    ? `${item.firstExercise} · ${formatThousands(Number(item.totalVolume))} kg`
    : `${formatThousands(Number(item.totalVolume))} kg`
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
        items.value = items.value.filter((x) => !selected.value.has(x.id))
        exitSelect()
        uni.showToast({ title: `已删除 ${ids.length} 条`, icon: 'none' })
      } catch {
        // request 层已统一提示
      }
    }
  })
}

function monthPending() {
  uni.showToast({ title: '月份筛选建设中', icon: 'none' })
}
</script>

<template>
  <view class="log">
    <view class="log__body">
      <!-- 顶栏：常规＝标题＋月份；多选态＝取消＋已选＋全选/删除 -->
      <view class="log__head">
        <template v-if="!selectMode">
          <text class="log__title">训练日志</text>
          <text class="log__month ft-num" @click="monthPending">{{ monthLabel }} ▾</text>
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

      <!-- 月概览条 -->
      <view class="overview">
        <text class="overview__caps">MONTH OVERVIEW</text>
        <text class="overview__nums ft-num">
          本月 {{ items.length }} 练 · 总容量 {{ formatThousands(totalVolume) }} kg
        </text>
      </view>

      <!-- 时间线列表 -->
      <view v-if="loading" class="ft-loading">加载中…</view>
      <view v-else-if="items.length === 0" class="ft-empty">还没有训练记录——去练一次吧</view>
      <view v-else class="ledger">
        <view class="ledger__thead">
          <view v-if="selectMode" class="col-check" />
          <text class="col-date">日期 / DATE</text>
          <text class="col-mid">项目 &amp; 主项容量 / DETAILS</text>
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
            <wd-icon v-if="selected.has(item.id)" name="check" size="13px" color="#ffffff" />
          </view>
          <view class="lrow__date">
            <text class="lrow__date-main ft-num">{{ formatDateLabel(item.createdAt) }}</text>
            <text class="lrow__date-week">{{ weekdayOf(item.createdAt) }}</text>
          </view>
          <view class="lrow__mid">
            <view class="lrow__title-line">
              <text class="lrow__title">{{ item.title }}</text>
            </view>
            <text class="lrow__detail ft-num">{{ lineOf(item) }}</text>
          </view>
          <view class="lrow__time">
            <text class="ft-num">{{ minutes(item) }} 分钟</text>
          </view>
        </view>
      </view>

      <view class="log__pad" />
    </view>

    <view class="log__bottom">
      <FtTabBar active="log" />
    </view>
  </view>
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
  width: 130rpx;
  text-align: right;
  font-size: $text-sm;
  color: $app-ink-muted;
  flex-shrink: 0;
}

.log__pad {
  height: 240rpx;
}

/* ── 底部 tab ── */
.log__bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
}
</style>
