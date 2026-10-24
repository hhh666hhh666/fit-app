<script setup lang="ts">
import FtIcon from '@/components/FtIcon.vue'
import FtButton from '@/components/FtButton.vue'
import { ref } from 'vue'
import { useBackClose } from '@/utils/backStack'
import { ask } from '@/utils/confirm'
import {
  createCombo,
  listFoods,
  updateCombo,
  type ComboItem,
  type FoodComboVO,
  type FoodItem
} from '@/api/nutrition'

// ── 组合编辑弹层：组合名 ＋ 明细（食物 ＋ 固定克数）；新建与编辑共用（v-if 挂载）──
//    明细为「先加后调」：点食物即以常用份/100g 加入，克数在行内改。
//    「存为组合」走同一组件：不传 combo 而传 presetItems，即以当前这一餐预填、仍视作新建。

const props = defineProps<{
  combo?: FoodComboVO | null
  /** 新建时的预填明细。 */
  presetItems?: ComboItem[] | null
}>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved'): void }>()

// 关法（遮罩、关闭钮、安卓返回键、Esc）都走 requestClose：改过就先问一句（同 FtSheet 的 guard）
useBackClose(() => void requestClose())

type DraftRow = { foodId: number; foodName: string; grams: string; unit: 'g' | 'ml' }

const isNew = !props.combo
const name = ref(props.combo?.name ?? '')
const draft = ref<DraftRow[]>(
  (props.combo?.items ?? props.presetItems ?? [])
    .filter((i) => i.foodId != null)
    .map((i) => ({
      foodId: i.foodId as number,
      foodName: i.foodName,
      grams: String(i.grams),
      unit: i.unit === 'ml' ? 'ml' : 'g'
    }))
)
const saving = ref(false)

// ── 防误触：和打开时比，组合名或明细（食物、克数）变了就算改过 ──
// 加食物是点列表、不打字，不能像 FtSheet 那样听 input 事件，只能比快照。
// 「存为组合」带进来的预填明细算初始状态：没动过就直接关。
const snapshot = () => JSON.stringify([name.value.trim(), draft.value.map((r) => [r.foodId, r.grams.trim()])])
const initial = snapshot()
let closing = false

async function requestClose() {
  if (closing) return
  closing = true
  try {
    if (snapshot() !== initial) {
      const ok = await ask({ title: '放弃填写的内容？', content: '填的内容还没保存，关掉就没了。', confirmText: '放弃', danger: true })
      if (!ok) return
    }
    emit('close')
  } finally {
    closing = false
  }
}

// ── 添加食物 ──
const picking = ref(false)
const keyword = ref('')
const foods = ref<FoodItem[]>([])
const FOOD_LIMIT = 200

function startPick() {
  picking.value = true
  void searchFoods()
}

async function searchFoods() {
  try {
    foods.value = await listFoods(keyword.value.trim() || undefined, {
      lite: true,
      limit: FOOD_LIMIT
    })
  } catch {
    // request 层已统一提示
  }
}

let searchTimer: ReturnType<typeof setTimeout> | undefined
function onKeyword() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => void searchFoods(), 250)
}

/** 点一条食物即以「常用份（否则 100）」加入，克数在明细里改。 */
function pickFood(f: FoodItem) {
  draft.value.push({
    foodId: f.id,
    foodName: f.name,
    grams: String(f.portionGrams ?? 100),
    unit: f.unit === 'ml' ? 'ml' : 'g'
  })
  picking.value = false
  keyword.value = ''
}

function removeAt(i: number) {
  draft.value.splice(i, 1)
}

function unitText(f: FoodItem) {
  return f.unit === 'ml' ? 'ml' : 'g'
}

async function save() {
  if (saving.value) return
  const n = name.value.trim()
  if (!n) {
    uni.showToast({ title: '组合名不能为空', icon: 'none' })
    return
  }
  if (draft.value.length === 0) {
    uni.showToast({ title: '至少添加一样食物', icon: 'none' })
    return
  }
  const items = draft.value.map((r) => ({ foodId: r.foodId, grams: Number(r.grams) }))
  if (items.some((i) => !Number.isFinite(i.grams) || i.grams <= 0)) {
    uni.showToast({ title: '克数要大于 0', icon: 'none' })
    return
  }
  saving.value = true
  try {
    const payload = { name: n, items }
    if (props.combo) {
      await updateCombo(props.combo.id, payload)
    } else {
      await createCombo(payload)
    }
    uni.showToast({ title: '已保存', icon: 'none' })
    emit('saved')
  } catch {
    // request 层已统一提示；留在弹层可重试
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <!-- touchmove 截在弹层自己身上：冒到页面上会被当成「下拉刷新」（不截 touchstart，uni 的长按靠它） -->
  <view class="cs-mask" @click="requestClose" @touchmove.stop.prevent />
  <view
    class="cs"
    role="dialog"
    :aria-label="isNew ? '新建组合' : '编辑组合'"
    @touchmove.stop
  >
    <view class="cs__handle" />
    <view class="cs__head">
      <text class="cs__title">{{ isNew ? '新建组合' : '编辑组合' }}</text>
      <view class="cs__close" role="button" aria-label="关闭" @click="requestClose">
        <FtIcon name="close" :size="18" :stroke="2" />
      </view>
    </view>

    <scroll-view scroll-y class="cs__body">
      <view class="field">
        <text class="field__label">组合名</text>
        <input
          v-model="name"
          class="field__input"
          placeholder="如：减脂早餐"
          placeholder-class="field__ph"
        />
      </view>

      <view class="dlist">
        <view v-if="draft.length === 0" class="dlist__empty">还没有添加食物</view>
        <view v-for="(it, i) in draft" :key="i" class="drow">
          <text class="drow__name">{{ it.foodName }}</text>
          <view class="drow__grams">
            <input v-model="it.grams" type="digit" class="drow__input ft-num" />
            <text class="drow__unit">{{ it.unit }}</text>
          </view>
          <view class="drow__del" role="button" aria-label="删除这一样" @click="removeAt(i)">
            <FtIcon name="close" :size="14" :stroke="2" />
          </view>
        </view>
      </view>

      <view v-if="!picking" class="addbtn" role="button" @click="startPick">
        <FtIcon name="plus" :size="16" :stroke="2" />
        <text>添加食物</text>
      </view>

      <view v-else class="picker">
        <view class="psearch">
          <FtIcon name="search" :size="16" tone="subtle" />
          <input
            v-model="keyword"
            class="psearch__input"
            placeholder="搜索食物库"
            placeholder-class="psearch__ph"
            confirm-type="search"
            @input="onKeyword"
          />
          <text class="psearch__cancel" @click="picking = false">收起</text>
        </view>
        <view class="plist">
          <view v-for="f in foods" :key="f.id" class="prow" @click="pickFood(f)">
            <view class="prow__mid">
              <text class="prow__name">{{ f.name }}</text>
              <text class="prow__sub ft-num">每100{{ unitText(f) }} · 蛋白 {{ f.protein }}g</text>
            </view>
            <text class="prow__kcal ft-num">{{ Math.round(f.kcal) }} 大卡</text>
          </view>
          <view v-if="foods.length === 0" class="plist__empty">
            没有找到匹配的食物——可去「食物库」新增
          </view>
        </view>
      </view>

      <view class="cs__pad" />
    </scroll-view>

    <FtButton variant="primary" block :disabled="saving" class="cs__save" @click="save">保存</FtButton>
  </view>
</template>

<style lang="scss" scoped>
/* 和 MealSheet 同一套弹层皮：实底面板 ＋ 不透明白卡分组（弹层里不做玻璃） */
.cs-mask {
  position: fixed;
  inset: 0;
  background: $app-mask;
  z-index: 90;
  animation: cs-fade $dur-base $ease-out;
}

.cs {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 91;
  max-height: 88vh;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  padding: 20rpx 40rpx calc(24rpx + env(safe-area-inset-bottom));
  background: $app-canvas-bottom;
  border-radius: $radius-xl $radius-xl 0 0;
  box-shadow: $shadow-sheet;
  animation: cs-up $dur-base $ease-out;
}

.cs__handle {
  flex-shrink: 0;
  width: 72rpx;
  height: 8rpx;
  margin: 0 auto 20rpx;
  border-radius: $radius-pill;
  background: $app-line;
}

.cs__head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 24rpx;
}

.cs__title {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $app-ink;
}

.cs__close {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: $app-surface-2;
  color: $app-ink-2;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cs__body {
  flex: 1;
  min-height: 0;
  max-height: 62vh;
}

.cs__pad {
  height: 24rpx;
}

/* ── 组合名 ── */
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

/* ── 明细 ── */
.dlist {
  margin-top: 20rpx;
  padding: 0 28rpx;
  background: $app-surface;
  border-radius: $radius-lg;
}

.dlist__empty {
  padding: 36rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.drow {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 0;
}

.drow + .drow {
  border-top: 1rpx solid $app-line-soft;
}

.drow__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-md;
  color: $app-ink;
}

.drow__grams {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.drow__input {
  width: 128rpx;
  height: 64rpx;
  padding: 0 16rpx;
  box-sizing: border-box;
  border-radius: $radius-sm;
  background: $app-surface-2;
  text-align: right;
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.drow__unit {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.drow__del {
  flex-shrink: 0;
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: $app-surface-2;
  color: $app-ink-muted;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ── 添加食物 ── */
.addbtn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  height: 84rpx;
  margin-top: 20rpx;
  border: 2rpx dashed $app-line-dash;
  border-radius: $radius-lg;
  font-size: $text-base;
  color: $app-ink-2;
}

.addbtn:active {
  background: $app-surface-2;
}

.picker {
  margin-top: 20rpx;
}

.psearch {
  display: flex;
  align-items: center;
  gap: 12rpx;
  height: 84rpx;
  padding: 0 28rpx;
  background: $app-surface;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
}

.psearch__input {
  flex: 1;
  font-size: $text-md;
  color: $app-ink;
}

.psearch__ph {
  color: $app-ink-subtle;
}

.psearch__cancel {
  font-size: $text-base;
  color: $app-ink-muted;
}

.plist {
  max-height: 420rpx;
  overflow-y: auto;
  margin-top: 16rpx;
  padding: 0 24rpx;
  background: $app-surface;
  border-radius: $radius-lg;
}

.prow {
  display: flex;
  align-items: center;
  padding: 22rpx 0;
}

.prow + .prow {
  border-top: 1rpx solid $app-line-soft;
}

.prow:active {
  opacity: 0.6;
}

.prow__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.prow__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-md;
  color: $app-ink;
}

.prow__sub {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.prow__kcal {
  flex-shrink: 0;
  margin-left: 16rpx;
  font-size: $text-sm;
  color: $app-ink;
}

.plist__empty {
  padding: 40rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.cs .cs__save {
  flex-shrink: 0;
  margin-top: 20rpx;
}

@keyframes cs-up {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

@keyframes cs-fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .cs,
  .cs-mask {
    animation: none;
  }
}
</style>
