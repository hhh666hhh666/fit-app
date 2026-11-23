<!--
  食物列表的一行（ADR 0063）：食物库页、记一餐的选食物列表共用。
  三行：名字 ＋ 生熟标签 ／ 份量（「1份（200g）」或「每100g」）／ 碳水 · 蛋白 · 脂肪；右边是大卡。
  左边一格缩略图：有图显示图，没图显示统一的占位底；自定义食物在右下角挂浅橙铅笔角标（预置不标）。
  临时条目（AI 识别记过的，没有食物库条目）也用这一行：没有图、标「临时」、份量写上次吃的量。
  已经记下的餐次条目（entry）也用这一行（ADR 0066）：份量按记下时的单位写「10 个（208.3g）」，营养是实际摄入，
  加餐再带上时刻；不挂自定义角标。
  addable：右边多一个「+」（记一餐列表用），点它发 add，点行的其余位置发 tap。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { COOK_STATE_LABEL, type FoodItem, type MealItem, type TempPick } from '@/api/nutrition'
import FoodThumb from '@/components/FoodThumb.vue'
import FtIcon from '@/components/FtIcon.vue'
import { matchedAlias } from '@/utils/food-match'
import { macroGram, portionUnit } from '@/utils/diet'

const props = withDefaults(
  defineProps<{
    food?: FoodItem | null
    temp?: TempPick | null
    /** 已经记下的一条餐次条目（营养是实际摄入，不再换算）。 */
    entry?: MealItem | null
    /** 条目的时刻（加餐每条一个；三餐整餐共用，不传）。 */
    time?: string | null
    keyword?: string
    addable?: boolean
  }>(),
  { food: null, temp: null, entry: null, time: null, keyword: '', addable: false }
)
const emit = defineEmits<{ (e: 'tap'): void; (e: 'add'): void }>()

/** 这一行按多少克（毫升）算：有常用份按一份，临时条目按上次的量，否则按 100。 */
const view = computed(() => {
  const e = props.entry
  if (e) return { name: e.foodName, amount: '', g: 0, per100: e }
  const f = props.food
  if (f) {
    const unit = f.unit === 'ml' ? 'ml' : 'g'
    const pg = f.portionGrams && f.portionGrams > 0 ? Number(f.portionGrams) : null
    const g = pg ?? 100
    return {
      name: f.name,
      amount: pg ? `1${portionUnit(f.portionLabel)}（${trim(pg)}${unit}）` : `每100${unit}`,
      g,
      per100: f
    }
  }
  const t = props.temp as TempPick
  return { name: t.name, amount: `上次 ${trim(Number(t.grams))}g`, g: Number(t.grams), per100: t }
})

/** 条目：按份记的写「10 个（208.3g）」，否则写克数；加餐带上时刻。 */
const entryAmount = computed(() => {
  const e = props.entry
  if (!e) return ''
  const g = `${trim(Number(e.grams))}g`
  const pg = Number(e.portionGrams || 0)
  const amt = e.portionUnit && pg > 0 ? `${trim(Number(e.grams) / pg)} ${e.portionUnit}（${g}）` : g
  return props.time ? `${props.time} · ${amt}` : amt
})

const scaled = (v: number | null | undefined) => (Number(v || 0) * view.value.g) / 100
const kcal = computed(() => (props.entry ? Math.round(Number(props.entry.kcal)) : Math.round(scaled(view.value.per100.kcal))))
const carb = computed(() => macroGram(props.entry ? Number(props.entry.carb) : scaled(view.value.per100.carb)))
const protein = computed(() => macroGram(props.entry ? Number(props.entry.protein) : scaled(view.value.per100.protein)))
const fat = computed(() => macroGram(props.entry ? Number(props.entry.fat) : scaled(view.value.per100.fat)))
const alias = computed(() => (props.food ? matchedAlias(props.food, props.keyword) : ''))
const thumb = computed(() => {
  if (props.food?.imageKey) return { id: props.food.id, key: props.food.imageKey }
  if (props.entry?.foodId && props.entry.imageKey) return { id: props.entry.foodId, key: props.entry.imageKey }
  return null
})
const cookState = computed(() => props.food?.cookState ?? props.entry?.cookState ?? null)
const isTemp = computed(() => !!props.temp || !!props.entry?.temporary)
const custom = computed(() => !!props.food && !props.food.preset)

function trim(n: number): string {
  return String(Math.round(n * 100) / 100)
}
</script>

<template>
  <view class="frw" @click="emit('tap')">
    <view class="frw__thumb">
      <FoodThumb v-if="thumb" :id="thumb.id" :image-key="thumb.key" :size="40" />
      <view v-else class="frw__empty">
        <FtIcon name="bowl" :size="20" />
      </view>
      <view v-if="custom" class="frw__mine" aria-label="自定义">
        <FtIcon name="pencil" :size="10" :stroke="2.2" />
      </view>
    </view>
    <view class="frw__mid">
      <view class="frw__line">
        <text class="frw__name">{{ view.name }}</text>
        <text v-if="cookState" class="frw__cook" :class="'frw__cook--' + cookState">
          {{ COOK_STATE_LABEL[cookState] }}
        </text>
        <text v-if="food && food.archived" class="frw__badge">已下架</text>
        <text v-if="isTemp" class="frw__badge">临时</text>
      </view>
      <text v-if="alias" class="frw__alias">别名：{{ alias }}</text>
      <text class="frw__sub ft-num">{{ entry ? entryAmount : view.amount }}</text>
      <view class="frw__macros ft-num">
        <text class="frw__m"><text class="frw__dot frw__dot--carb" />碳水 {{ carb }}g</text>
        <text class="frw__m"><text class="frw__dot frw__dot--protein" />蛋白 {{ protein }}g</text>
        <text class="frw__m"><text class="frw__dot frw__dot--fat" />脂肪 {{ fat }}g</text>
      </view>
    </view>
    <view class="frw__right">
      <text class="frw__kcal ft-num">{{ kcal }} 大卡</text>
      <view v-if="addable" class="frw__add" role="button" :aria-label="`记一份${view.name}`" @click.stop="emit('add')">
        <FtIcon name="plus" :size="20" :stroke="2.2" />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.frw {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 0;
  @include press(dim);
}

.frw__thumb {
  position: relative;
  flex: none;
  width: 40px;
  height: 40px;
}

.frw__empty {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-sm;
  background: $food-thumb-empty-bg;
  color: $food-thumb-empty-fg;
}

.frw__mine {
  position: absolute;
  right: -6rpx;
  bottom: -6rpx;
  width: 32rpx;
  height: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: 3rpx solid $app-surface;
  background: $food-custom-badge-bg;
  color: $food-custom-badge-fg;
}

.frw__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2rpx;
}

.frw__line {
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-width: 0;
}

.frw__name {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $app-ink;
}

.frw__cook,
.frw__badge {
  flex: none;
  padding: 0 10rpx;
  border-radius: $radius-pill;
  font-size: $text-xs;
  line-height: 32rpx;
}

.frw__badge {
  background: $app-surface-2;
  color: $app-ink-muted;
}

.frw__cook--raw {
  background: $cook-raw-bg;
  color: $cook-raw-fg;
}

.frw__cook--cooked {
  background: $cook-cooked-bg;
  color: $cook-cooked-fg;
}

.frw__alias,
.frw__sub {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-xs;
  line-height: 1.4;
  color: $app-ink-muted;
}

.frw__macros {
  display: flex;
  gap: 14rpx;
  font-size: $text-xs;
  line-height: 1.4;
  color: $app-ink-muted;
  white-space: nowrap;
}

.frw__dot {
  display: inline-block;
  width: 10rpx;
  height: 10rpx;
  margin-right: 6rpx;
  border-radius: 50%;
  vertical-align: 2rpx;
}

.frw__dot--carb {
  background: $ring-carb;
}

.frw__dot--protein {
  background: $ring-protein;
}

.frw__dot--fat {
  background: $ring-fat;
}

.frw__right {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12rpx;
}

.frw__kcal {
  font-size: $text-sm;
  color: $app-ink-2;
}

/* 只有一个橙色「+」，不带圈（2026-11-23 主人要求）；点击区仍是 52rpx 见方 */
.frw__add {
  width: 52rpx;
  height: 52rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: $accent;
  @include press(shrink);
}
</style>
