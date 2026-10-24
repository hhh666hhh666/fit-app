<script setup lang="ts">
import { PALETTE } from '@/utils/palette'
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { batchDeleteExercises, createExercise, listExercises, type ExerciseItem } from '@/api/exercise'
import { usePickStore } from '@/stores/pick'
import { muscleLabel } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import FtButton from '@/components/FtButton.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import FtSheet from '@/components/FtSheet.vue'

// ── 动作库（晴窗，ADR 0041）──
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
  const n = filtered.value.length
  if (keyword.value.trim()) return `找到 ${n} 个`
  const m = MUSCLES.find((x) => x.code === activeMuscle.value)
  return `${m ? m.section : '全部'} · ${n} 个`
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
    confirmColor: PALETTE.danger,
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
</script>

<template>
  <view class="lib">
    <view class="lib__body" :class="{ 'is-selecting': selectMode }">
      <FtPageHeader :title="picking ? '添加动作' : '动作库'" back>
        <template #right>
          <view v-if="selectMode" class="pill" role="button" @click="exitSelect">取消</view>
          <view v-else class="pill" role="button" @click="openCreate">
            <FtIcon name="plus" :size="16" :stroke="2" />
            <text>新增</text>
          </view>
        </template>
      </FtPageHeader>

      <!-- 搜索 -->
      <view class="search">
        <FtIcon name="search" :size="18" tone="subtle" />
        <input
          v-model="keyword"
          class="search__input"
          placeholder="搜索动作"
          placeholder-class="search__ph"
          confirm-type="search"
        />
        <view v-if="keyword" class="search__clear" role="button" aria-label="清空" @click="keyword = ''">
          <FtIcon name="close" :size="14" :stroke="2" />
        </view>
      </view>

      <!-- 肌群：可横滑的分段控件 -->
      <view class="seg">
        <scroll-view scroll-x class="seg__scroll">
          <view class="seg__inner" role="tablist">
            <view
              class="seg__item"
              :class="{ 'seg__item--on': activeMuscle === '' }"
              role="tab"
              :aria-selected="activeMuscle === ''"
              @click="activeMuscle = ''"
            >
              全部
            </view>
            <view
              v-for="m in MUSCLES"
              :key="m.code"
              class="seg__item"
              :class="{ 'seg__item--on': activeMuscle === m.code }"
              role="tab"
              :aria-selected="activeMuscle === m.code"
              @click="activeMuscle = m.code"
            >
              {{ m.label }}<text v-if="counts[m.code]" class="seg__n ft-num">{{ counts[m.code] }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <!-- 列表 -->
      <view class="glass elist">
        <view class="elist__head">
          <text class="elist__count">{{ sectTitle }}</text>
          <text v-if="picking" class="elist__tip">点选即加入计划</text>
          <text v-else-if="!selectMode" class="elist__tip">长按自定义动作可批量删除</text>
        </view>
        <view
          v-for="e in filtered"
          :key="e.id"
          class="erow"
          :class="{ 'is-dim': selectMode && e.preset }"
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
            <view class="erow__line">
              <text class="erow__name">{{ e.name }}</text>
              <text v-if="!e.preset" class="badge">自定义</text>
            </view>
            <text class="erow__sub">{{ muscleLabel(e.muscle) }}</text>
          </view>
          <view v-if="picking && !selectMode" class="erow__add">
            <FtIcon name="plus" :size="16" :stroke="2" />
          </view>
          <FtIcon v-else-if="!selectMode" name="chevron-right" :size="18" tone="subtle" />
        </view>
        <FtEmpty
          v-if="!loading && filtered.length === 0"
          :title="keyword.trim() ? '没有匹配的动作' : '这一类还没有动作'"
          sub="点右上角「新增」，建一个自定义动作。"
        />
      </view>
    </view>

    <!-- 多选：贴底悬浮的操作条（与悬浮主按钮同一位置） -->
    <view v-if="selectMode" class="selbar">
      <text class="selbar__count ft-num">已选 {{ selectedCount }} 项</text>
      <view class="selbar__all" role="button" @click="toggleAll">{{ allSelected ? '取消全选' : '全选' }}</view>
      <view
        class="selbar__del"
        :class="{ 'is-off': selectedCount === 0 }"
        role="button"
        @click="doDelete"
      >
        删除
      </view>
    </view>

    <!-- 新增动作弹层 -->
    <FtSheet v-if="sheet.show" title="新增动作" @close="sheet.show = false">
      <view class="ns">
        <view class="field">
          <text class="field__label">动作名称</text>
          <input
            v-model="sheet.name"
            class="field__input"
            placeholder="如：保加利亚深蹲"
            placeholder-class="field__ph"
            maxlength="32"
          />
        </view>

        <view class="group">
          <text class="group__label">肌群</text>
          <view class="chips">
            <view
              v-for="m in MUSCLES"
              :key="m.code"
              class="chip"
              :class="{ 'chip--on': sheet.muscle === m.code }"
              role="radio"
              :aria-checked="sheet.muscle === m.code"
              @click="sheet.muscle = m.code"
            >
              {{ m.label }}
            </view>
          </view>
        </view>
      </view>

      <FtButton variant="primary" block :disabled="sheet.saving" class="ns__btn" @click="confirmCreate">
        创建
      </FtButton>
    </FtSheet>
  </view>
</template>

<style lang="scss" scoped>
.lib__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(64rpx + env(safe-area-inset-bottom));
}

/* 多选时底下多了悬浮操作条，正文末尾要让出它的高度 */
.lib__body.is-selecting {
  padding-bottom: calc(200rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-lg;
  box-shadow: $glass-shadow;
  padding: 32rpx 36rpx;
}

/* 页头右侧的胶囊按钮 */
.pill {
  @include glass($glass-bg-strong);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 16rpx 28rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $app-ink-2;
}

.pill:active {
  opacity: 0.7;
}

/* ── 搜索 ── */
.search {
  @include glass($glass-bg-strong);
  display: flex;
  align-items: center;
  gap: 12rpx;
  height: 88rpx;
  padding: 0 28rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
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
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: $app-surface-2;
  color: $app-ink-muted;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ── 肌群分段：八项放不下一行，壳不动、里面横滑 ── */
.seg {
  @include glass;
  padding: 6rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
  overflow: hidden;
}

.seg__scroll {
  white-space: nowrap;
}

.seg__inner {
  display: inline-flex;
}

.seg__item {
  flex-shrink: 0;
  padding: 12rpx 28rpx;
  border-radius: $radius-pill;
  font-size: $text-base;
  color: $app-ink-muted;
  transition: background $dur-fast $ease-out, color $dur-fast $ease-out;
}

.seg__item--on {
  background: $app-surface-2;
  color: $app-ink;
  font-weight: $weight-bold;
}

.seg__n {
  margin-left: 6rpx;
  font-size: $text-xs;
  color: $app-ink-subtle;
}

/* ── 列表 ── */
.elist {
  padding: 8rpx 28rpx;
}

.elist__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 16rpx 0 8rpx;
}

.elist__count {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.elist__tip {
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.erow {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 0;
}

.erow + .erow,
.elist__head + .erow {
  border-top: 1rpx solid $app-line-soft;
}

.erow:active {
  opacity: 0.6;
}

/* 多选时预设动作不能删：压淡，免得以为漏了勾 */
.erow.is-dim {
  opacity: 0.4;
}

.erow__check {
  width: 40rpx;
  height: 40rpx;
  flex-shrink: 0;
  box-sizing: border-box;
  border-radius: 50%;
  border: 2rpx solid $app-line-dash;
  background: $app-surface;
  display: flex;
  align-items: center;
  justify-content: center;
}

.erow__check--on {
  background: $app-ink;
  border-color: $app-ink;
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

/* 点选模式的「加入」：圆形浅底，不用橙（整页的动作是「点哪个」，不是这颗按钮） */
.erow__add {
  width: 56rpx;
  height: 56rpx;
  flex-shrink: 0;
  border-radius: 50%;
  background: $app-surface-2;
  color: $app-ink-2;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ── 多选操作条 ── */
.selbar {
  @include glass($glass-bg-strong);
  position: fixed;
  left: $tabbar-side;
  right: $tabbar-side;
  bottom: calc(#{$tabbar-gap} + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  gap: 16rpx;
  height: 104rpx;
  padding: 0 12rpx 0 36rpx;
  box-sizing: border-box;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $glass-shadow-float;
}

.selbar__count {
  flex: 1;
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.selbar__all {
  padding: 16rpx 20rpx;
  font-size: $text-base;
  color: $app-ink-2;
}

.selbar__del {
  height: 80rpx;
  padding: 0 40rpx;
  display: flex;
  align-items: center;
  border-radius: $radius-pill;
  background: $app-danger;
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-surface;
}

.selbar__del.is-off {
  opacity: 0.35;
}

.selbar__all:active,
.selbar__del:active {
  opacity: 0.7;
}

/* ── 新增动作弹层（壳是 FtSheet，分组是白卡） ── */
/* 弹层没有副标题：标题下自己让出副标题那一段的距离 */
.ns {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 28rpx;
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
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.field__ph {
  color: $app-ink-subtle;
  font-weight: $weight-regular;
}

.group {
  padding: 24rpx 28rpx 28rpx;
  background: $app-surface;
  border-radius: $radius-lg;
}

.group__label {
  display: block;
  padding-bottom: 16rpx;
  font-size: $text-base;
  color: $app-ink-muted;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.chip {
  padding: 12rpx 28rpx;
  border: 1rpx solid $app-line;
  border-radius: $radius-pill;
  font-size: $text-base;
  color: $app-ink-muted;
}

/* 选中：深字 ＋ 浅胶囊底（与分段控件同一套），描边让掉 */
.chip--on {
  border-color: transparent;
  background: $app-surface-2;
  color: $app-ink;
  font-weight: $weight-bold;
}

.lib .ns__btn {
  flex-shrink: 0;
  margin-top: 28rpx;
}

@media (prefers-reduced-motion: reduce) {
  .seg__item {
    transition: none;
  }
}
</style>
