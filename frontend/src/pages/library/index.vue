<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { batchDeleteExercises, createExercise, listExercises, type ExerciseItem } from '@/api/exercise'
import { usePickStore } from '@/stores/pick'
import { muscleLabel } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import FtIcon from '@/components/FtIcon.vue'

// ── 动作库（设计稿：.workbuddy/stitch/screen/library.png）──
// 双模式：浏览（主页进入）/ 点选（计划编辑「添加动作」进入，点选即回填）

const pick = usePickStore()

const picking = ref(false)

const MUSCLES = [
  { code: 'chest', label: '胸', section: '胸部' },
  { code: 'back', label: '背', section: '背部' },
  { code: 'leg', label: '腿', section: '腿部' },
  { code: 'shoulder', label: '肩', section: '肩部' },
  { code: 'arm', label: '手臂', section: '手臂' },
  { code: 'core', label: '腹部', section: '腹部' },
  { code: 'cardio', label: '有氧', section: '有氧' }
]

const all = ref<ExerciseItem[]>([])
const loading = ref(false)
const keyword = ref('')
/** '' = 全部 */
const activeMuscle = ref('')
const selectMode = ref(false)
const selected = ref<Set<number>>(new Set())
const selectedCount = computed(() => selected.value.size)
const deletable = computed(() => filtered.value.filter((e) => !e.preset))
const allSelected = computed(
  () => deletable.value.length > 0 && deletable.value.every((e) => selected.value.has(e.id))
)
let clickLockUntil = 0

onLoad((query) => {
  const q = (query || {}) as Record<string, string | undefined>
  picking.value = q.picking === '1'
})

onShow(() => {
  if (!ensureLogin()) return
  load()
})

async function load() {
  loading.value = true
  try {
    // 动作总量小（预设 + 自定义），一次取全量，本地过滤 / 计数
    all.value = await listExercises()
  } catch {
    // unwrap 已提示
  } finally {
    loading.value = false
  }
}

const counts = computed(() => {
  const map: Record<string, number> = {}
  for (const e of all.value) map[e.muscle] = (map[e.muscle] ?? 0) + 1
  return map
})

const filtered = computed(() => {
  const kw = keyword.value.trim()
  return all.value.filter((e) => {
    if (activeMuscle.value && e.muscle !== activeMuscle.value) return false
    if (kw && !e.name.includes(kw)) return false
    return true
  })
})

const sectTitle = computed(() => {
  if (keyword.value.trim()) return `搜索结果 (${filtered.value.length})`
  const m = MUSCLES.find((x) => x.code === activeMuscle.value)
  return m ? `${m.section}动作 (${filtered.value.length})` : `全部动作 (${filtered.value.length})`
})

function toggleSelect(id: number) {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}

function onTapExercise(e: ExerciseItem) {
  if (Date.now() < clickLockUntil) return
  if (selectMode.value) {
    if (!e.preset) toggleSelect(e.id)
    return
  }
  if (!picking.value) {
    uni.showToast({ title: '动作详情建设中', icon: 'none' })
    return
  }
  pick.pick({ id: e.id, name: e.name, muscle: e.muscle })
  uni.navigateBack()
}

function onLongPressExercise(e: ExerciseItem) {
  if (e.preset || selectMode.value) return
  clickLockUntil = Date.now() + 400
  selectMode.value = true
  selected.value = new Set([e.id])
}

function exitSelect() {
  selectMode.value = false
  selected.value = new Set()
}

function toggleAll() {
  selected.value = allSelected.value
    ? new Set([...selected.value].filter((id) => !deletable.value.some((e) => e.id === id)))
    : new Set([...selected.value, ...deletable.value.map((e) => e.id)])
}

function doDelete() {
  const ids = [...selected.value]
  if (!ids.length) return
  uni.showModal({
    title: '删除自定义动作',
    content: `删除选中的 ${ids.length} 个动作？动作会从动作库下架，历史训练记录仍会保留。`,
    confirmText: '删除',
    confirmColor: '#C4453C',
    success: async ({ confirm }) => {
      if (!confirm) return
      try {
        await batchDeleteExercises(ids)
        all.value = all.value.filter((e) => !ids.includes(e.id))
        exitSelect()
        uni.showToast({ title: `已删除 ${ids.length} 个`, icon: 'none' })
      } catch {
        // request 层已统一提示
      }
    }
  })
}

// ── 新增自定义动作弹层 ──
const sheet = ref({ show: false, name: '', muscle: 'chest', saving: false })

function openCreate() {
  sheet.value = { show: true, name: '', muscle: activeMuscle.value || 'chest', saving: false }
}

async function confirmCreate() {
  const n = sheet.value.name.trim()
  if (!n) {
    uni.showToast({ title: '先给动作起个名字', icon: 'none' })
    return
  }
  sheet.value.saving = true
  try {
    await createExercise({ name: n, muscle: sheet.value.muscle })
    sheet.value.show = false
    uni.showToast({ title: '已创建', icon: 'success' })
    await load()
  } catch {
    // unwrap 已提示
  } finally {
    sheet.value.saving = false
  }
}

function goBack() {
  uni.navigateBack()
}
</script>

<template>
  <view class="lib">
    <!-- 顶部 -->
    <view v-if="!selectMode" class="lib__nav">
      <text class="lib__back" @click="goBack">‹</text>
      <text class="lib__meta ft-num">● TRAINING LOGBOOK // V3.2</text>
      <text class="lib__meta ft-num">SEC.04_LIB</text>
    </view>
    <view v-else class="lib__select-nav">
      <text class="lib__cancel" @click="exitSelect">取消</text>
      <text class="lib__selected ft-num">已选 {{ selectedCount }} 项</text>
      <view class="lib__select-actions">
        <text @click="toggleAll">{{ allSelected ? '取消全选' : '全选' }}</text>
        <text class="lib__delete" :class="{ 'lib__delete--off': selectedCount === 0 }" @click="doDelete">删除</text>
      </view>
    </view>

    <view v-if="!selectMode" class="lib__title-row">
      <text class="lib__title">动作库</text>
      <view class="lib__new" @click="openCreate">+ 新增</view>
    </view>

    <!-- 搜索 -->
    <view class="search">
      <FtIcon name="search" :size="16" tone="subtle" />
      <input
        v-model="keyword"
        class="search__input"
        placeholder="搜索动作"
        placeholder-class="search__ph"
        confirm-type="search"
      />
      <text v-if="keyword" class="search__clear" @click="keyword = ''">×</text>
    </view>

    <!-- 肌群 tab -->
    <scroll-view scroll-x class="mtabs">
      <view class="mtabs__inner">
        <view
          class="mtab"
          :class="{ 'mtab--active': activeMuscle === '' }"
          @click="activeMuscle = ''"
        >
          全部
        </view>
        <view
          v-for="m in MUSCLES"
          :key="m.code"
          class="mtab"
          :class="{ 'mtab--active': activeMuscle === m.code }"
          @click="activeMuscle = m.code"
        >
          {{ m.label }}
          <text v-if="counts[m.code]" class="mtab__n ft-num">({{ counts[m.code] }})</text>
        </view>
      </view>
    </scroll-view>

    <!-- 分区行 -->
    <view class="lib__sect">
      <text class="lib__sect-l ft-num">{{ sectTitle }} · SORT: NAME</text>
      <text v-if="picking" class="lib__sect-r">点选即加入计划</text>
    </view>

    <!-- 列表 -->
    <view class="elist">
      <view
        v-for="e in filtered"
        :key="e.id"
        class="erow"
        :class="{ 'erow--sel': selectMode && selected.has(e.id) }"
        @click="onTapExercise(e)"
        @longpress="onLongPressExercise(e)"
      >
        <view
          v-if="selectMode && !e.preset"
          class="erow__check"
          :class="{ 'erow__check--on': selected.has(e.id) }"
        >
          <FtIcon v-if="selected.has(e.id)" name="check" :size="13" :stroke="2.4" tone="inverse" />
        </view>
        <view class="erow__mid">
          <text class="erow__name">{{ e.name }}</text>
          <text class="erow__sub">{{ muscleLabel(e.muscle) }} · {{ e.preset ? '预设' : '自定义' }}</text>
        </view>
        <text class="erow__go">{{ picking ? '+' : '›' }}</text>
      </view>
      <view v-if="!loading && filtered.length === 0" class="elist__empty">没有匹配的动作</view>
    </view>

    <!-- 新增动作弹层 -->
    <view v-if="sheet.show" class="sheet-mask" @click="sheet.show = false" />
    <view v-if="sheet.show" class="sheet">
      <view class="sheet__handle" />
      <text class="sheet__title">新增动作</text>

      <view class="sheet__field">
        <text class="sheet__label">动作名称</text>
        <input
          v-model="sheet.name"
          class="sheet__input"
          placeholder="如：保加利亚深蹲"
          placeholder-class="search__ph"
          maxlength="32"
        />
      </view>

      <text class="sheet__label sheet__label--block">肌群</text>
      <view class="chips">
        <view
          v-for="m in MUSCLES"
          :key="m.code"
          class="chip"
          :class="{ 'chip--active': sheet.muscle === m.code }"
          @click="sheet.muscle = m.code"
        >
          {{ m.label }}
        </view>
      </view>

      <view
        class="ft-btn ft-btn--primary ft-btn--block sheet__btn"
        :class="{ 'ft-btn--disabled': sheet.saving }"
        @click="confirmCreate"
      >
        创建
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.lib {
  padding: 28rpx 32rpx calc(48rpx + env(safe-area-inset-bottom));
}

/* ── 顶部 ── */
.lib__nav {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 4rpx 0 24rpx;
}

.lib__back {
  font-size: 56rpx;
  line-height: 1;
  color: $app-ink;
  padding-right: 8rpx;
}

.lib__meta {
  font-size: 22rpx;
  letter-spacing: 2rpx;
  color: $app-ink-subtle;
}

.lib__meta:last-child {
  margin-left: auto;
}

.lib__select-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 64rpx;
  padding-bottom: 24rpx;
}

.lib__cancel,
.lib__selected,
.lib__select-actions text {
  font-size: $text-base;
  color: $app-ink-muted;
}

.lib__selected {
  color: $app-ink;
}

.lib__select-actions {
  display: flex;
  align-items: center;
  gap: 28rpx;
}

.lib__delete {
  color: #c4453c !important;
}

.lib__delete--off {
  opacity: 0.35;
}

.lib__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 28rpx;
}

.lib__title {
  font-size: 56rpx;
  font-weight: 700;
  color: $app-ink;
  letter-spacing: 1rpx;
}

.lib__new {
  font-size: $text-base;
  font-weight: 500;
  color: $app-ink;
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-md;
  padding: 10rpx 24rpx;
}

/* ── 搜索 ── */
.search {
  display: flex;
  align-items: center;
  gap: 14rpx;
  height: 84rpx;
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-md;
  padding: 0 24rpx;
  margin-bottom: 24rpx;
}

.search__input {
  flex: 1;
  font-size: $text-md;
  color: $app-ink;
}

.search__ph {
  color: $app-ink-subtle;
}

.search__clear {
  font-size: 36rpx;
  line-height: 1;
  color: $app-ink-subtle;
  padding: 0 6rpx;
}

/* ── 肌群 tab ── */
.mtabs {
  white-space: nowrap;
  margin-bottom: 8rpx;
}

.mtabs__inner {
  display: inline-flex;
  gap: 36rpx;
  padding: 0 4rpx 16rpx;
  border-bottom: 1rpx solid $app-line;
}

.mtab {
  font-size: $text-md;
  color: $app-ink-muted;
  padding-bottom: 12rpx;
  position: relative;
}

.mtab--active {
  color: $app-ink;
  font-weight: 600;
}

.mtab--active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1rpx;
  height: 4rpx;
  background: $app-ink;
  border-radius: 2rpx;
}

.mtab__n {
  font-size: $text-xs;
  font-weight: 400;
  color: $app-ink-subtle;
}

/* ── 分区行 ── */
.lib__sect {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26rpx 4rpx 16rpx;
}

.lib__sect-l {
  font-size: 22rpx;
  letter-spacing: 2rpx;
  color: $app-ink-subtle;
}

.lib__sect-r {
  font-size: $text-xs;
  color: $app-ink-muted;
}

/* ── 列表 ── */
.elist {
  background: $app-surface;
  border: 1rpx solid $app-line-soft;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  overflow: hidden;
}

.erow {
  display: flex;
  align-items: center;
  padding: 26rpx 28rpx;
}

.erow--sel {
  background: $app-canvas;
}

.erow__check {
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

.erow__check--on {
  background: $app-ink;
  border-color: $app-ink;
}

.erow + .erow {
  border-top: 1rpx solid $app-line;
}

.erow__mid {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  min-width: 0;
}

.erow__name {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.erow__sub {
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.erow__go {
  font-size: 36rpx;
  line-height: 1;
  color: $app-ink-muted;
  padding-left: 16rpx;
}

.elist__empty {
  padding: 80rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

/* ── 新增弹层 ── */
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
  margin: 0 auto 28rpx;
}

.sheet__title {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  color: $app-ink;
  padding-bottom: 28rpx;
}

.sheet__field {
  border-bottom: 2rpx solid $app-ink;
  padding-bottom: 16rpx;
  margin-bottom: 32rpx;
}

.sheet__label {
  font-size: 22rpx;
  color: $app-ink-subtle;
}

.sheet__label--block {
  display: block;
  padding-bottom: 16rpx;
}

.sheet__input {
  font-size: $text-lg;
  color: $app-ink;
  padding-top: 10rpx;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 40rpx;
}

.chip {
  padding: 12rpx 28rpx;
  border: 1rpx solid $app-line;
  border-radius: 999rpx;
  font-size: $text-base;
  color: $app-ink-muted;
}

.chip--active {
  background: $app-ink;
  border-color: $app-ink;
  color: #ffffff;
  font-weight: 500;
}

.sheet__btn {
  margin-bottom: 8rpx;
}
</style>
