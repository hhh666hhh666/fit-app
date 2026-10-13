<script setup lang="ts">
import { ref } from 'vue'
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
    uni.showToast({ title: '已保存', icon: 'success' })
    emit('saved')
  } catch {
    // request 层已统一提示；留在弹层可重试
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <view class="cs-mask" @click="emit('close')" />
  <view class="cs">
    <view class="cs__handle" />
    <view class="cs__head">
      <text class="cs__title">{{ isNew ? '新建组合' : '编辑组合' }}</text>
      <text class="cs__close" @click="emit('close')">×</text>
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
          <text class="drow__del" @click="removeAt(i)">删除</text>
        </view>
      </view>

      <view v-if="!picking" class="addbtn" @click="startPick">+ 添加食物</view>

      <view v-else class="picker">
        <view class="psearch">
          <wd-icon name="search" size="15px" color="#8a8f98" />
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

    <view
      class="ft-btn ft-btn--primary ft-btn--block cs__save"
      :class="{ 'ft-btn--disabled': saving }"
      @click="save"
    >
      保存
    </view>
  </view>
</template>

<style lang="scss" scoped>
.cs-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(23, 25, 29, 0.4);
  z-index: 90;
}

.cs {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: $app-surface;
  border-radius: 40rpx 40rpx 0 0;
  padding: 20rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
  z-index: 91;
  max-height: 88vh;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.cs__handle {
  width: 72rpx;
  height: 8rpx;
  border-radius: 4rpx;
  background: $app-line;
  margin: 0 auto 18rpx;
  flex-shrink: 0;
}

.cs__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 20rpx;
  flex-shrink: 0;
}

.cs__title {
  font-size: 34rpx;
  font-weight: 600;
  color: $app-ink;
}

.cs__close {
  font-size: 44rpx;
  line-height: 1;
  color: $app-ink-muted;
  padding: 0 8rpx;
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
  padding: 6rpx 0 22rpx;
}

.field__label {
  font-size: $text-sm;
  color: $app-ink-muted;
  flex-shrink: 0;
}

.field__input {
  flex: 1;
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
  border-bottom: 2rpx solid $app-ink;
  padding: 6rpx 4rpx;
}

.field__ph {
  color: $app-ink-subtle;
  font-weight: 400;
}

/* ── 明细 ── */
.dlist {
  border-top: 1rpx solid $app-line;
}

.dlist__empty {
  padding: 36rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

.drow {
  display: flex;
  align-items: center;
  padding: 22rpx 4rpx;
  border-bottom: 1rpx solid $app-line-soft;
}

.drow__name {
  flex: 1;
  min-width: 0;
  font-size: $text-base;
  color: $app-ink;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drow__grams {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
}

.drow__input {
  width: 120rpx;
  text-align: right;
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
  border-bottom: 1rpx solid $app-line;
  padding: 4rpx 2rpx;
}

.drow__unit {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.drow__del {
  font-size: $text-xs;
  color: $app-ink-muted;
  margin-left: 24rpx;
  flex-shrink: 0;
}

/* ── 添加食物 ── */
.addbtn {
  padding: 26rpx 8rpx 6rpx;
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink;
}

.picker {
  padding-top: 18rpx;
}

.psearch {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: $app-canvas;
  border: 1rpx solid $app-line;
  border-radius: $radius-lg;
  padding: 14rpx 20rpx;
}

.psearch__input {
  flex: 1;
  font-size: $text-base;
  color: $app-ink;
}

.psearch__ph {
  color: $app-ink-subtle;
}

.psearch__cancel {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.plist {
  max-height: 420rpx;
  overflow-y: auto;
}

.prow {
  display: flex;
  align-items: center;
  padding: 22rpx 8rpx;
  border-bottom: 1rpx solid $app-line-soft;
}

.prow__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.prow__name {
  font-size: $text-base;
  color: $app-ink;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prow__sub {
  font-size: 20rpx;
  color: $app-ink-subtle;
}

.prow__kcal {
  font-size: $text-sm;
  color: $app-ink-muted;
  margin-left: 16rpx;
  flex-shrink: 0;
}

.plist__empty {
  padding: 40rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

.cs__save {
  margin-top: 20rpx;
  flex-shrink: 0;
}
</style>
