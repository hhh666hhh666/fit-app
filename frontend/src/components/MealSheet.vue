<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  addMeal,
  deleteMeal,
  getDay,
  listFoods,
  updateMeal,
  type FoodItem,
  type MealItem,
  type MealKey
} from '@/api/nutrition'
import { fetchPhotoUrl, pickAndUploadPhoto, releasePhotoUrl } from '@/utils/photo'
import { useDietStore } from '@/stores/diet'

// ── 记录弹层：某餐的条目列表 ＋ 选食物/份量 ＋ 拍照（首页卡与「饮食」页共用；v-if 挂载）──

const props = defineProps<{ date: string; meal: MealKey }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'changed'): void }>()

const MEAL_LABEL: Record<MealKey, string> = {
  breakfast: '早餐',
  lunch: '午餐',
  dinner: '晚餐',
  snack: '加餐'
}

const diet = useDietStore()

// ── 本餐条目 ──
const items = ref<MealItem[]>([])
const loading = ref(true)
const saving = ref(false)

// ── 照片 ──
const photoUrl = ref<string | null>(null)

// ── 选食物 ──
const keyword = ref('')
const foods = ref<FoodItem[]>([])
const selected = ref<FoodItem | null>(null)
const grams = ref('')

// ── 改份量（行内） ──
const editingId = ref<number | null>(null)
const editGrams = ref('')

onMounted(() => {
  void init()
})

onUnmounted(() => {
  releasePhotoUrl(photoUrl.value)
})

async function init() {
  loading.value = true
  try {
    const day = await getDay(props.date)
    const group = day.meals.find((m) => m.meal === props.meal)
    items.value = group ? group.items : []
    await Promise.all([loadPhoto(), searchFoods()])
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

// 搜索防抖
let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => void searchFoods(), 250)
})

async function searchFoods() {
  try {
    foods.value = await listFoods(keyword.value.trim() || undefined)
  } catch {
    // request 层已统一提示
  }
}

// ── 照片 ──

async function loadPhoto() {
  releasePhotoUrl(photoUrl.value)
  photoUrl.value = await fetchPhotoUrl(props.date, props.meal)
}

async function shoot() {
  const quality = diet.settings?.photoQuality ?? 'medium'
  const ok = await pickAndUploadPhoto(props.date, props.meal, quality)
  if (ok) {
    await loadPhoto()
    emit('changed')
  }
}

// ── 添加 ──

function selectFood(f: FoodItem) {
  selected.value = f
  // 默认份量：有常用份按 1 份，否则 100（g / ml）
  grams.value = String(f.portionGrams ?? 100)
}

function usePortion(n: number) {
  const f = selected.value
  if (!f || !f.portionGrams) return
  grams.value = String(Number((f.portionGrams * n).toFixed(2)))
}

function cancelSelect() {
  selected.value = null
  grams.value = ''
}

async function confirmAdd() {
  const f = selected.value
  if (!f || saving.value) return
  const g = Number(grams.value)
  if (!Number.isFinite(g) || g <= 0) {
    uni.showToast({ title: '份量要大于 0', icon: 'none' })
    return
  }
  saving.value = true
  try {
    const item = await addMeal({ date: props.date, meal: props.meal, foodId: f.id, grams: g })
    items.value.push(item)
    cancelSelect()
    keyword.value = ''
    uni.showToast({ title: '已记录', icon: 'success' })
    emit('changed')
  } catch {
    // request 层已统一提示
  } finally {
    saving.value = false
  }
}

// ── 条目操作：点行 → 修改份量 / 删除 ──

function onItemTap(it: MealItem) {
  uni.showActionSheet({
    itemList: ['修改份量', '删除'],
    success: ({ tapIndex }) => {
      if (tapIndex === 0) {
        editingId.value = it.id
        editGrams.value = String(it.grams)
      } else if (tapIndex === 1) {
        void removeItem(it)
      }
    }
  })
}

async function confirmEdit() {
  const id = editingId.value
  if (id == null || saving.value) return
  const g = Number(editGrams.value)
  if (!Number.isFinite(g) || g <= 0) {
    uni.showToast({ title: '份量要大于 0', icon: 'none' })
    return
  }
  saving.value = true
  try {
    const updated = await updateMeal(id, g)
    const idx = items.value.findIndex((x) => x.id === id)
    if (idx >= 0) items.value[idx] = updated
    editingId.value = null
    emit('changed')
  } catch {
    // request 层已统一提示
  } finally {
    saving.value = false
  }
}

async function removeItem(it: MealItem) {
  try {
    await deleteMeal(it.id)
    items.value = items.value.filter((x) => x.id !== it.id)
    uni.showToast({ title: '已删除', icon: 'none' })
    emit('changed')
  } catch {
    // request 层已统一提示
  }
}

const mealKcal = computed(() => Math.round(items.value.reduce((s, i) => s + Number(i.kcal || 0), 0)))

const unitText = (f: FoodItem) => (f.unit === 'ml' ? 'ml' : 'g')
</script>

<template>
  <view class="ms-mask" @click="emit('close')" />
  <view class="ms">
    <view class="ms__handle" />
    <view class="ms__head">
      <text class="ms__title">{{ MEAL_LABEL[meal] }}</text>
      <text class="ms__date ft-num">{{ date }}</text>
      <text class="ms__close" @click="emit('close')">×</text>
    </view>

    <scroll-view scroll-y class="ms__body">
      <view v-if="loading" class="ms__loading">加载中…</view>

      <template v-else>
        <!-- 照片 -->
        <view class="photo">
          <image
            v-if="photoUrl"
            class="photo__img"
            :src="photoUrl"
            mode="aspectFill"
            @click="shoot"
          />
          <view v-else class="photo__empty" @click="shoot">+ 拍照记录这一餐</view>
        </view>

        <!-- 添加区 -->
        <view class="add">
          <view v-if="!selected" class="asearch">
            <wd-icon name="search" size="15px" color="#8a8f98" />
            <input
              v-model="keyword"
              class="asearch__input"
              placeholder="搜索食物库"
              placeholder-class="asearch__ph"
              confirm-type="search"
            />
            <text v-if="keyword" class="asearch__clear" @click="keyword = ''">×</text>
          </view>

          <!-- 选中食物 → 份量行 -->
          <view v-if="selected" class="pick">
            <view class="pick__head">
              <text class="pick__name">{{ selected.name }}</text>
              <text class="pick__cancel" @click="cancelSelect">取消</text>
            </view>
            <view class="pick__sub ft-num">
              每100{{ unitText(selected) }} · {{ Math.round(selected.kcal) }} 大卡 · 蛋白
              {{ selected.protein }}g
            </view>
            <view class="pick__row">
              <view v-if="selected.portionGrams" class="pick__portions">
                <text class="pick__portion" @click="usePortion(1)">
                  {{ selected.portionLabel || '1份' }}
                </text>
                <text class="pick__portion" @click="usePortion(2)">×2</text>
              </view>
              <view class="pick__input">
                <input v-model="grams" type="digit" class="pick__input-field ft-num" />
                <text class="pick__unit">{{ unitText(selected) }}</text>
              </view>
              <view
                class="pick__btn"
                :class="{ 'pick__btn--disabled': saving }"
                @click="confirmAdd"
              >
                添加
              </view>
            </view>
          </view>

          <!-- 食物列表 -->
          <view v-else class="foods">
            <view v-for="f in foods" :key="f.id" class="frow" @click="selectFood(f)">
              <view class="frow__mid">
                <text class="frow__name">{{ f.name }}</text>
                <text class="frow__sub ft-num">
                  每100{{ unitText(f) }} · 蛋白 {{ f.protein }}g
                </text>
              </view>
              <text class="frow__kcal ft-num">{{ Math.round(f.kcal) }} 大卡</text>
            </view>
            <view v-if="foods.length === 0" class="foods__empty">
              食物库还是空的——先去「食物库」添加
            </view>
          </view>
        </view>

        <!-- 本餐条目 -->
        <view class="list">
          <view v-if="items.length === 0" class="list__empty">这一餐还没有记录</view>
          <view v-for="it in items" :key="it.id" class="irow" @click="onItemTap(it)">
            <template v-if="editingId === it.id">
              <text class="irow__name">{{ it.foodName }}</text>
              <view class="irow__edit">
                <input v-model="editGrams" type="digit" class="irow__edit-input ft-num" />
                <text class="irow__edit-unit">g</text>
                <view class="irow__ok" @click.stop="confirmEdit">✓</view>
              </view>
            </template>
            <template v-else>
              <view class="irow__mid">
                <text class="irow__name">{{ it.foodName }}</text>
                <text class="irow__sub ft-num">
                  {{ it.grams }}g · 蛋白 {{ it.protein }}g · 碳水 {{ it.carb }}g · 脂肪 {{ it.fat }}g
                </text>
              </view>
              <text class="irow__kcal ft-num">{{ Math.round(it.kcal) }} 大卡</text>
            </template>
          </view>
        </view>

        <!-- 合计 -->
        <view class="sum">
          <text class="sum__label">本餐合计</text>
          <text class="sum__num ft-num">{{ mealKcal }} 大卡</text>
        </view>

        <view class="ms__pad" />
      </template>
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
.ms-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(23, 25, 29, 0.4);
  z-index: 90;
}

.ms {
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

.ms__handle {
  width: 72rpx;
  height: 8rpx;
  border-radius: 4rpx;
  background: $app-line;
  margin: 0 auto 18rpx;
  flex-shrink: 0;
}

.ms__head {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  padding-bottom: 16rpx;
  flex-shrink: 0;
}

.ms__title {
  font-size: 34rpx;
  font-weight: 600;
  color: $app-ink;
}

.ms__date {
  font-size: $text-sm;
  color: $app-ink-subtle;
}

.ms__close {
  margin-left: auto;
  font-size: 44rpx;
  line-height: 1;
  color: $app-ink-muted;
  padding: 0 8rpx;
}

.ms__body {
  flex: 1;
  min-height: 0;
  max-height: 72vh;
}

.ms__loading {
  padding: 60rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

.ms__pad {
  height: 40rpx;
}

/* ── 照片 ── */
.photo {
  margin-bottom: 22rpx;
}

.photo__img {
  width: 100%;
  height: 260rpx;
  border-radius: $radius-lg;
  border: 1rpx solid $app-line-soft;
}

.photo__empty {
  height: 112rpx;
  border: 2rpx dashed $app-line;
  border-radius: $radius-lg;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* ── 添加区 ── */
.asearch {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: $app-canvas;
  border: 1rpx solid $app-line;
  border-radius: $radius-lg;
  padding: 14rpx 20rpx;
}

.asearch__input {
  flex: 1;
  font-size: $text-base;
  color: $app-ink;
}

.asearch__ph {
  color: $app-ink-subtle;
}

.asearch__clear {
  font-size: $text-md;
  color: $app-ink-subtle;
  padding: 0 6rpx;
}

.pick {
  border: 2rpx solid $app-ink;
  border-radius: $radius-lg;
  padding: 20rpx 24rpx;
}

.pick__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.pick__name {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.pick__cancel {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.pick__sub {
  font-size: $text-xs;
  color: $app-ink-muted;
  padding-top: 6rpx;
}

.pick__row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding-top: 18rpx;
}

.pick__portions {
  display: flex;
  gap: 12rpx;
}

.pick__portion {
  font-size: $text-xs;
  color: $app-ink;
  border: 1rpx solid $app-line;
  border-radius: 20rpx;
  padding: 6rpx 16rpx;
  background: $app-canvas;
}

.pick__input {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8rpx;
  border-bottom: 2rpx solid $app-ink;
  padding: 6rpx 4rpx;
}

.pick__input-field {
  flex: 1;
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.pick__unit {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.pick__btn {
  background: $app-ink;
  color: #ffffff;
  font-size: $text-base;
  font-weight: 500;
  border-radius: $radius-lg;
  padding: 14rpx 32rpx;
  flex-shrink: 0;
}

.pick__btn--disabled {
  opacity: 0.5;
}

.foods {
  max-height: 420rpx;
  overflow-y: auto;
}

.frow {
  display: flex;
  align-items: center;
  padding: 22rpx 8rpx;
  border-bottom: 1rpx solid $app-line-soft;
}

.frow__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.frow__name {
  font-size: $text-base;
  color: $app-ink;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.frow__sub {
  font-size: 20rpx;
  color: $app-ink-subtle;
}

.frow__kcal {
  font-size: $text-sm;
  color: $app-ink-muted;
  margin-left: 16rpx;
  flex-shrink: 0;
}

.foods__empty {
  padding: 40rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

/* ── 条目列表 ── */
.list {
  margin-top: 24rpx;
  border-top: 1rpx solid $app-line;
}

.list__empty {
  padding: 32rpx 0 12rpx;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

.irow {
  display: flex;
  align-items: center;
  padding: 24rpx 4rpx;
  border-bottom: 1rpx solid $app-line-soft;
}

.irow__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.irow__name {
  font-size: $text-base;
  color: $app-ink;
}

.irow__sub {
  font-size: 20rpx;
  color: $app-ink-subtle;
}

.irow__kcal {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
  margin-left: 16rpx;
  flex-shrink: 0;
}

.irow__edit {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.irow__edit-input {
  width: 120rpx;
  border-bottom: 2rpx solid $app-ink;
  text-align: right;
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}

.irow__edit-unit {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.irow__ok {
  width: 48rpx;
  height: 48rpx;
  border-radius: 8rpx;
  background: $app-ink;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $text-base;
}

/* ── 合计 ── */
.sum {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 24rpx 4rpx 8rpx;
}

.sum__label {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.sum__num {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
}
</style>
