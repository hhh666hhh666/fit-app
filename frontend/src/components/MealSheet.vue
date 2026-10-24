<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  addMeal,
  addMealsBatch,
  deleteMeal,
  getDay,
  listCombos,
  listFoods,
  updateMeal,
  type ComboItem,
  type FoodComboVO,
  type FoodItem,
  type MealItem,
  type MealKey
} from '@/api/nutrition'
import { deletePhoto, fetchPhotoUrl, pickAndUploadPhoto } from '@/utils/photo'
import PhotoViewer from '@/components/PhotoViewer.vue'
import { useDietStore } from '@/stores/diet'
import ComboEditSheet from '@/components/ComboEditSheet.vue'
import FtIcon from '@/components/FtIcon.vue'

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
/** 全屏看图开关（单张：给 PhotoViewer 传长度 1 的数组）。 */
const viewer = ref(false)

// ── 选食物 ──
/** 轻量列表条数上限（预置 1800+ 条：搜索走服务端，初始只带前 N 条）。 */
const FOOD_LIMIT = 200
const keyword = ref('')
const foods = ref<FoodItem[]>([])
const selected = ref<FoodItem | null>(null)
const grams = ref('')

// ── 改份量（行内） ──
const editingId = ref<number | null>(null)
const editGrams = ref('')

// ── 食物组合：横滑条「整份加入」＋ 「把这一餐存为组合」 ──
const combos = ref<FoodComboVO[]>([])
/** 刚整份加入的条目 id（供「撤销」用）。 */
const lastBatch = ref<number[]>([])
const comboSheet = ref({ show: false, preset: null as ComboItem[] | null })

onMounted(() => {
  void init()
})

// ⚠️ 卸载时不释放照片地址：objectURL 归 utils/photo-cache.ts 的 LRU 管（ADR 0035），
//    在这里 revoke 会让缓存里那条地址失效，下次打开又得重拉——正是要消掉的那个「重新加载一下」。

async function init() {
  loading.value = true
  try {
    const day = await getDay(props.date)
    const group = day.meals.find((m) => m.meal === props.meal)
    items.value = group ? group.items : []
    await Promise.all([loadPhoto(), searchFoods(), loadCombos()])
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
    // 预置＋自有合并；lite 轻量字段 + 条数上限（搜索交给服务端）
    foods.value = await listFoods(keyword.value.trim() || undefined, {
      lite: true,
      limit: FOOD_LIMIT
    })
  } catch {
    // request 层已统一提示
  }
}

// ── 照片 ──

async function loadPhoto() {
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

/** 点图看大图（单张，可双指缩放）。 */
function viewPhoto() {
  if (photoUrl.value) viewer.value = true
}

/** 删除这一餐的照片（二次确认、不可恢复）。 */
function removePhoto() {
  uni.showModal({
    title: '删除这张照片？',
    content: '删除后不可恢复；这一餐的饮食条目不受影响。',
    confirmText: '删除',
    cancelText: '取消',
    success: async ({ confirm }) => {
      if (!confirm) return
      const ok = await deletePhoto(props.date, props.meal)
      if (!ok) return
      photoUrl.value = null
      uni.showToast({ title: '已删除', icon: 'none' })
      emit('changed')
    }
  })
}

// ── 食物组合 ──

async function loadCombos() {
  try {
    combos.value = await listCombos()
  } catch {
    // request 层已统一提示
  }
}

/** 整份加入：一次事务落多条普通条目，可立即撤销。 */
async function applyCombo(c: FoodComboVO) {
  const payload = c.items
    .filter((i) => i.foodId != null)
    .map((i) => ({ foodId: i.foodId as number, grams: i.grams }))
  if (payload.length === 0) {
    uni.showToast({ title: '这个组合里的食物都已被删', icon: 'none' })
    return
  }
  try {
    const added = await addMealsBatch({ date: props.date, meal: props.meal, items: payload })
    items.value.push(...added)
    lastBatch.value = added.map((a) => a.id)
    uni.showToast({ title: `已加入 ${added.length} 条`, icon: 'success' })
    emit('changed')
  } catch {
    // request 层已统一提示
  }
}

/** 撤销上一次整份加入。 */
async function undoBatch() {
  const ids = lastBatch.value
  lastBatch.value = []
  for (const id of ids) {
    try {
      await deleteMeal(id)
    } catch {
      // 单条失败不阻断其余
    }
  }
  items.value = items.value.filter((x) => !ids.includes(x.id))
  emit('changed')
}

/** 把当前这一餐的条目存成组合（食物已被删的条目跳过）。 */
function saveAsCombo() {
  const preset: ComboItem[] = []
  for (const it of items.value) {
    if (it.foodId == null) continue
    preset.push({ foodId: it.foodId, foodName: it.foodName, grams: it.grams })
  }
  if (preset.length === 0) {
    uni.showToast({ title: '这一餐没有可保存的食物', icon: 'none' })
    return
  }
  comboSheet.value = { show: true, preset }
}

async function onComboSaved() {
  comboSheet.value = { show: false, preset: null }
  await loadCombos()
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
  <view class="ms" role="dialog" :aria-label="MEAL_LABEL[meal]">
    <view class="ms__handle" />
    <view class="ms__head">
      <view class="ms__ico" :class="'ms__ico--' + meal">
        <FtIcon :name="meal" :size="20" />
      </view>
      <text class="ms__title">{{ MEAL_LABEL[meal] }}</text>
      <text class="ms__date ft-num">{{ date }}</text>
      <view class="ms__close" role="button" aria-label="关闭" @click="emit('close')">
        <FtIcon name="close" :size="18" :stroke="2" />
      </view>
    </view>

    <scroll-view scroll-y class="ms__body">
      <view v-if="loading" class="ms__loading">加载中…</view>

      <template v-else>
        <!-- 照片：点图看大图；角标给「更换」「删除」（ADR 0014） -->
        <view class="photo">
          <view v-if="photoUrl" class="photo__box">
            <image class="photo__img" :src="photoUrl" mode="aspectFit" @click="viewPhoto" />
            <view class="photo__ops">
              <text class="photo__op" @click.stop="shoot">更换</text>
              <text class="photo__op" @click.stop="removePhoto">删除</text>
            </view>
          </view>
          <view v-else class="photo__empty" role="button" @click="shoot">
            <FtIcon name="plus" :size="18" :stroke="2" />
            <text>拍照记录这一餐</text>
          </view>
        </view>

        <!-- 组合横滑条：点一下整份加入（可撤销） -->
        <scroll-view v-if="combos.length" scroll-x class="combar">
          <view class="combar__inner">
            <view v-for="c in combos" :key="c.id" class="combar__chip" @click="applyCombo(c)">
              <text class="combar__name">{{ c.name }}</text>
              <text class="combar__sub ft-num">
                {{ c.items.length }} 样 · {{ Math.round(c.totalKcal) }} 大卡
              </text>
            </view>
          </view>
        </scroll-view>

        <!-- 添加区 -->
        <view class="add">
          <view v-if="!selected" class="asearch">
            <FtIcon name="search" :size="16" tone="subtle" />
            <input
              v-model="keyword"
              class="asearch__input"
              placeholder="搜索食物库"
              placeholder-class="asearch__ph"
              confirm-type="search"
            />
            <view v-if="keyword" class="asearch__clear" role="button" aria-label="清空" @click="keyword = ''">
              <FtIcon name="close" :size="14" :stroke="2" />
            </view>
          </view>

          <!-- 选中食物 → 份量行；「添加」是这一屏唯一的橙色动作 -->
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
                <text class="pick__portion ft-num" @click="usePortion(2)">×2</text>
              </view>
              <view class="pick__input">
                <input v-model="grams" type="digit" class="pick__input-field ft-num" />
                <text class="pick__unit">{{ unitText(selected) }}</text>
              </view>
              <view
                class="pick__btn"
                :class="{ 'pick__btn--disabled': saving }"
                role="button"
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
                <view class="frow__line">
                  <text class="frow__name">{{ f.name }}</text>
                  <text v-if="f.preset" class="frow__badge">预设</text>
                </view>
                <text class="frow__sub ft-num">
                  每100{{ unitText(f) }} · 蛋白 {{ f.protein }}g
                </text>
              </view>
              <text class="frow__kcal ft-num">{{ Math.round(f.kcal) }} 大卡</text>
            </view>
            <view v-if="foods.length === 0" class="foods__empty">
              没有找到匹配的食物——可去「食物库」新增
            </view>
          </view>
        </view>

        <!-- 本餐条目 -->
        <view class="list">
          <view class="list__head">
            <text class="list__title">这一餐</text>
            <text class="list__sum ft-num">合计 {{ mealKcal }} 大卡</text>
          </view>
          <view v-if="items.length === 0" class="list__empty">还没有记录</view>
          <view v-for="it in items" :key="it.id" class="irow" @click="onItemTap(it)">
            <template v-if="editingId === it.id">
              <text class="irow__name">{{ it.foodName }}</text>
              <view class="irow__edit">
                <input v-model="editGrams" type="digit" class="irow__edit-input ft-num" />
                <text class="irow__edit-unit">g</text>
                <view class="irow__ok" role="button" aria-label="保存份量" @click.stop="confirmEdit">
                  <FtIcon name="check" :size="16" :stroke="2.4" />
                </view>
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

        <view v-if="items.length > 0" class="savecombo">
          <view class="savecombo__btn" role="button" @click="saveAsCombo">
            <FtIcon name="plus" :size="16" :stroke="2" />
            <text>把这一餐存为组合</text>
          </view>
        </view>

        <view class="ms__pad" />
      </template>
    </scroll-view>

    <!-- 整份加入后的撤销条 -->
    <view v-if="lastBatch.length" class="undo">
      <text class="undo__text ft-num">已加入 {{ lastBatch.length }} 条</text>
      <text class="undo__btn" @click="undoBatch">撤销</text>
    </view>

    <!-- 「存为组合」与「新建 / 编辑组合」共用同一弹层 -->
    <ComboEditSheet
      v-if="comboSheet.show"
      :preset-items="comboSheet.preset"
      @close="comboSheet.show = false"
      @saved="onComboSaved"
    />

    <!-- 全屏看图（单张；与相册共用同一组件） -->
    <PhotoViewer
      v-if="viewer && photoUrl"
      :urls="[photoUrl]"
      :caption="`${date} · ${MEAL_LABEL[meal]}`"
      @close="viewer = false"
    />
  </view>
</template>

<style lang="scss" scoped>
/* 弹层是实底（晴窗：底部弹层不做玻璃——底下压着遮罩，没有可透的东西）；
   弹层里的分组用不透明白卡，和页面上的玻璃卡区分开 */
.ms-mask {
  position: fixed;
  inset: 0;
  background: $app-mask;
  z-index: 90;
  animation: ms-fade $dur-base $ease-out;
}

.ms {
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
  animation: ms-up $dur-base $ease-out;
}

.ms__handle {
  flex-shrink: 0;
  width: 72rpx;
  height: 8rpx;
  margin: 0 auto 20rpx;
  border-radius: $radius-pill;
  background: $app-line;
}

.ms__head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding-bottom: 24rpx;
}

.ms__ico {
  width: 64rpx;
  height: 64rpx;
  border-radius: $radius-sm;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ms__ico--breakfast {
  background: $meal-breakfast-bg;
  color: $meal-breakfast-fg;
}

.ms__ico--lunch {
  background: $meal-lunch-bg;
  color: $meal-lunch-fg;
}

.ms__ico--dinner {
  background: $meal-dinner-bg;
  color: $meal-dinner-fg;
}

.ms__ico--snack {
  background: $meal-snack-bg;
  color: $meal-snack-fg;
}

.ms__title {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $app-ink;
}

.ms__date {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.ms__close {
  margin-left: auto;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: $app-surface-2;
  color: $app-ink-2;
  display: flex;
  align-items: center;
  justify-content: center;
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
  color: $app-ink-muted;
}

.ms__pad {
  height: 40rpx;
}

/* ── 照片 ── */
.photo {
  margin-bottom: 24rpx;
}

.photo__box {
  position: relative;
}

/* aspectFit：按原图比例完整显示、不裁切（竖拍的餐盘不再被拦腰切） */
.photo__img {
  width: 100%;
  height: 480rpx;
  border-radius: $radius-lg;
  background: $app-surface-2;
}

/* 角标：更换 / 删除——压在照片上，用遮罩色的深底白字 */
.photo__ops {
  position: absolute;
  right: 16rpx;
  bottom: 16rpx;
  display: flex;
  gap: 12rpx;
}

.photo__op {
  padding: 8rpx 22rpx;
  border-radius: $radius-pill;
  background: $app-mask;
  font-size: $text-sm;
  color: $app-surface;
}

.photo__empty {
  height: 112rpx;
  border: 2rpx dashed $app-line-dash;
  border-radius: $radius-lg;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: $text-base;
  color: $app-ink-muted;
}

.photo__empty:active {
  background: $app-surface-2;
}

/* ── 组合横滑条 ── */
.combar {
  white-space: nowrap;
  margin-bottom: 24rpx;
}

.combar__inner {
  display: inline-flex;
  gap: 16rpx;
  padding-bottom: 4rpx;
}

.combar__chip {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  padding: 16rpx 28rpx;
  background: $app-surface;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}

.combar__chip:active {
  opacity: 0.7;
}

.combar__name {
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $app-ink;
}

.combar__sub {
  font-size: $text-xs;
  color: $app-ink-muted;
}

/* ── 添加区 ── */
.asearch {
  display: flex;
  align-items: center;
  gap: 12rpx;
  height: 84rpx;
  padding: 0 28rpx;
  background: $app-surface;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
}

.asearch__input {
  flex: 1;
  font-size: $text-md;
  color: $app-ink;
}

.asearch__ph {
  color: $app-ink-subtle;
}

.asearch__clear {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: $app-surface-2;
  color: $app-ink-muted;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pick {
  padding: 24rpx 28rpx;
  background: $app-surface;
  border-radius: $radius-lg;
  /* 用描边不用外投影：外投影画在盒子外面，会被 scroll-view 的边裁掉 */
  border: 3rpx solid $accent-ring;
}

.pick__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16rpx;
}

.pick__name {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $app-ink;
}

.pick__cancel {
  flex-shrink: 0;
  font-size: $text-base;
  color: $app-ink-muted;
}

.pick__sub {
  padding-top: 6rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.pick__row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding-top: 20rpx;
}

.pick__portions {
  display: flex;
  gap: 12rpx;
}

.pick__portion {
  padding: 10rpx 20rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-sm;
  color: $app-ink-2;
}

.pick__input {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8rpx;
  height: 76rpx;
  padding: 0 20rpx;
  border-radius: $radius-sm;
  background: $app-surface-2;
}

.pick__input-field {
  flex: 1;
  min-width: 0;
  font-size: $text-lg;
  font-weight: $weight-heavy;
  color: $app-ink;
}

.pick__unit {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.pick__btn {
  flex-shrink: 0;
  height: 76rpx;
  padding: 0 36rpx;
  border-radius: $radius-pill;
  background: $accent-bg;
  box-shadow: $accent-shadow;
  display: flex;
  align-items: center;
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-surface;
}

.pick__btn:active {
  transform: scale(0.97);
}

.pick__btn--disabled {
  opacity: 0.5;
}

.foods {
  max-height: 420rpx;
  overflow-y: auto;
  margin-top: 16rpx;
  padding: 0 24rpx;
  background: $app-surface;
  border-radius: $radius-lg;
}

.frow {
  display: flex;
  align-items: center;
  padding: 22rpx 0;
}

.frow + .frow {
  border-top: 1rpx solid $app-line-soft;
}

.frow:active {
  opacity: 0.6;
}

.frow__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.frow__line {
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-width: 0;
}

.frow__name {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-md;
  color: $app-ink;
}

.frow__badge {
  flex: none;
  padding: 2rpx 12rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.frow__sub {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.frow__kcal {
  flex-shrink: 0;
  margin-left: 16rpx;
  font-size: $text-sm;
  color: $app-ink;
}

.foods__empty {
  padding: 40rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* ── 本餐条目 ── */
.list {
  margin-top: 28rpx;
  padding: 8rpx 28rpx;
  background: $app-surface;
  border-radius: $radius-lg;
}

.list__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 20rpx 0 12rpx;
}

.list__title {
  font-size: $text-title;
  font-weight: $weight-bold;
  color: $app-ink;
}

.list__sum {
  font-size: $text-base;
  color: $app-ink;
}

.list__empty {
  padding: 16rpx 0 24rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.irow {
  display: flex;
  align-items: center;
  padding: 22rpx 0;
  border-top: 1rpx solid $app-line-soft;
}

.irow:active {
  opacity: 0.6;
}

.irow__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.irow__name {
  font-size: $text-md;
  color: $app-ink;
}

.irow__sub {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.irow__kcal {
  flex-shrink: 0;
  margin-left: 16rpx;
  font-size: $text-md;
  color: $app-ink;
}

.irow__edit {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.irow__edit-input {
  width: 140rpx;
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

.irow__edit-unit {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.irow__ok {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: $app-ink;
  color: $app-surface;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ── 存为组合 / 整份加入的撤销条 ── */
.savecombo {
  display: flex;
  justify-content: center;
  padding-top: 24rpx;
}

.savecombo__btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 14rpx 28rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-base;
  color: $app-ink-2;
}

.savecombo__btn:active {
  opacity: 0.7;
}

/* 撤销条：深底白字，像一条 toast 钉在弹层底部 */
.undo {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
  padding: 20rpx 32rpx;
  border-radius: $radius-pill;
  background: $app-ink;
}

.undo__text {
  font-size: $text-base;
  color: $app-surface;
}

.undo__btn {
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $accent-soft;
}

@keyframes ms-up {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

@keyframes ms-fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ms,
  .ms-mask {
    animation: none;
  }
}
</style>
