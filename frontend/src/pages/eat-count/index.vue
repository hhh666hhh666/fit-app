<script setup lang="ts">
// ── 边吃边数（ADR 0067）──
//    不知道会吃多少时，吃一个点一下：一种食物一张卡，整张卡都是「＋1」，小「−」纠正误点；
//    卡上一排十格小珠子，满十颗并成一颗大珠子；底部实时显示总热量，「吃完了」整批记进开始时定好的那一餐。
//    进行中的计数只存在这台手机上（utils/eatCount.ts），App 被杀掉重启还在。
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import {
  addCountedMeals,
  getFood,
  listMealPicks,
  updateFood,
  type MealKey,
  type MealPick
} from '@/api/nutrition'
import FoodRow from '@/components/FoodRow.vue'
import FoodThumb from '@/components/FoodThumb.vue'
import FtButton from '@/components/FtButton.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import FtRollNumber from '@/components/FtRollNumber.vue'
import FtSheet from '@/components/FtSheet.vue'
import { ask } from '@/utils/confirm'
import { macroGram, PORTION_UNITS, trim2 } from '@/utils/diet'
import {
  clearCount,
  eatCount,
  foodKey,
  itemFromFood,
  itemFromTemp,
  itemGrams,
  itemNutrient,
  persist,
  startCount,
  tempKey,
  type CountItem
} from '@/utils/eatCount'
import { ensureLogin } from '@/utils/guard'
import { tickCount, tickTen } from '@/utils/haptics'

const MEALS: { key: MealKey; label: string }[] = [
  { key: 'breakfast', label: '早餐' },
  { key: 'lunch', label: '午餐' },
  { key: 'dinner', label: '晚餐' },
  { key: 'snack', label: '加餐' }
]
const MEAL_LABEL = Object.fromEntries(MEALS.map((m) => [m.key, m.label])) as Record<MealKey, string>
/** 卡片识别色的个数（uni.scss 的 $count-hues）。 */
const HUES = 5
const VIBE_KEY = 'ft.eatCount.vibe'

const items = computed(() => eatCount.value?.items ?? [])
const meal = computed<MealKey>(() => eatCount.value?.meal ?? 'lunch')

// ── 进出页面 ──

onLoad((q) => {
  if (!ensureLogin()) return
  const m = (q?.meal as MealKey) || 'lunch'
  startCount(MEALS.some((x) => x.key === m) ? m : 'lunch')
  // 刚开始、一种都还没选：直接弹出选食物
  if (!items.value.length) pickerOpen.value = true
})

const now = ref(Date.now())
let clock: ReturnType<typeof setInterval> | null = null
onShow(() => {
  now.value = Date.now()
  clock = setInterval(() => (now.value = Date.now()), 30_000)
})
function stopClock() {
  if (clock) clearInterval(clock)
  clock = null
}
/** 一种都没选就离开：这一场等于没开始，别在首页留一条空的常驻条。 */
function dropIfEmpty() {
  stopClock()
  if (eatCount.value && !eatCount.value.items.length) clearCount()
}
onHide(dropIfEmpty)
onUnload(dropIfEmpty)
onBeforeUnmount(stopClock)

const elapsed = computed(() => {
  const s = eatCount.value
  if (!s) return ''
  const min = Math.max(0, Math.floor((now.value - s.startedAt) / 60000))
  if (min < 1) return ''
  if (min < 60) return `吃了 ${min} 分钟`
  return `吃了 ${Math.floor(min / 60)} 小时 ${min % 60} 分`
})

// ── 震动开关（默认开，只记在这台手机上）──

const vibe = ref(true)
try {
  vibe.value = uni.getStorageSync(VIBE_KEY) !== 'off'
} catch {
  // 读不到就按默认开
}
function toggleVibe() {
  vibe.value = !vibe.value
  try {
    uni.setStorageSync(VIBE_KEY, vibe.value ? 'on' : 'off')
  } catch {
    // 存不下就这次有效
  }
}

const reduceMotion = (() => {
  try {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches
  } catch {
    return false
  }
})()

// ── 点一下：＋1 ──

/** 每张卡的点按效果：涟漪与飘起来的「＋1」（坐标相对卡片）。 */
interface Fx {
  id: number
  x: number
  y: number
  text: string
}
const fx = reactive<Record<string, Fx[]>>({})
/** 正在「十颗并一颗」的卡。 */
const merging = reactive<Record<string, boolean>>({})
/** 刚满十、数字弹一下的卡。 */
const popping = reactive<Record<string, boolean>>({})
let fxSeq = 0
/** 手指按下的位置：click 事件在 H5 里拿不到坐标，按下时先记住。 */
let lastDown: { key: string; x: number; y: number } | null = null
/** 长按弹了菜单：松手那一下的 click 不算 +1。 */
let swallowClick = false

function onDown(it: CountItem, i: number, e: TouchEvent) {
  const t = e.touches?.[0]
  const el = typeof document !== 'undefined' ? document.getElementById(`cc-${i}`) : null
  if (!t || !el) {
    lastDown = null
    return
  }
  const r = el.getBoundingClientRect()
  lastDown = { key: it.key, x: t.clientX - r.left, y: t.clientY - r.top }
}

function bump(it: CountItem, delta: 1 | -1) {
  if (delta === 1 && swallowClick) {
    swallowClick = false
    return
  }
  if (delta === -1 && it.count <= 0) return
  it.count += delta
  persist()

  const hitTen = delta === 1 && it.count % 10 === 0
  if (vibe.value) (hitTen ? tickTen : tickCount)()
  if (reduceMotion) return

  if (delta === 1) {
    const at = lastDown && lastDown.key === it.key ? lastDown : null
    const list = (fx[it.key] ||= [])
    const f: Fx = { id: ++fxSeq, x: at?.x ?? 0, y: at?.y ?? 0, text: hitTen ? `${it.count}！` : '+1' }
    if (!at) {
      // 键盘、读屏点的：从卡片中间冒
      f.x = -1
      f.y = -1
    }
    list.push(f)
    setTimeout(() => {
      const arr = fx[it.key]
      if (arr) arr.splice(arr.indexOf(f) >>> 0, 1)
    }, 760)
  }
  if (hitTen) {
    merging[it.key] = true
    popping[it.key] = true
    setTimeout(() => (merging[it.key] = false), 520)
    setTimeout(() => (popping[it.key] = false), 600)
  }
}

/** 一排十格：满十的那一下先亮满十颗再并走，之后从空的十格重新开始。 */
function ones(it: CountItem): number {
  if (merging[it.key]) return 10
  return it.count % 10
}
function tens(it: CountItem): number {
  const t = Math.floor(it.count / 10)
  return merging[it.key] ? t - 1 : t
}

// ── 卡片菜单（长按）──

function cardMenu(it: CountItem) {
  swallowClick = true
  setTimeout(() => (swallowClick = false), 600)
  const actions = ['改单位和克数', '清零', '不数这一种了']
  uni.showActionSheet({
    itemList: actions,
    success: async ({ tapIndex }) => {
      const a = actions[tapIndex]
      if (a === '改单位和克数') openPiece(it)
      else if (a === '清零') {
        if (it.count && !(await ask({ title: `把${it.name}清零？`, content: `已经数的 ${it.count} ${it.unit}会归零。`, confirmText: '清零', danger: true })))
          return
        it.count = 0
        persist()
      } else if (a === '不数这一种了') {
        if (it.count && !(await ask({ title: `不数${it.name}了？`, content: `已经数的 ${it.count} ${it.unit}不会记下。`, confirmText: '移除', danger: true })))
          return
        const s = eatCount.value
        if (!s) return
        s.items.splice(s.items.indexOf(it), 1)
        persist()
      }
    }
  })
}

// ── 换餐次 ──

function switchMeal() {
  uni.showActionSheet({
    itemList: MEALS.map((m) => (m.key === meal.value ? `${m.label}（现在）` : m.label)),
    success: ({ tapIndex }) => {
      const s = eatCount.value
      if (!s) return
      s.meal = MEALS[tapIndex].key
      persist()
    }
  })
}

// ── 加一种食物 ──

const pickerOpen = ref(false)
const pickQ = ref('')
const picks = ref<MealPick[]>([])
const picksLoading = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | null = null

async function loadPicks() {
  picksLoading.value = true
  try {
    picks.value = await listMealPicks(pickQ.value.trim() || undefined, 80)
  } catch {
    // request 层已统一提示
  } finally {
    picksLoading.value = false
  }
}
function onPickQ() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => void loadPicks(), 220)
}
function openPicker() {
  pickerOpen.value = true
}
watch(pickerOpen, (open) => {
  if (open && !picks.value.length) void loadPicks()
})

const pickedKeys = computed(() => new Set(items.value.map((it) => it.key)))
const shownPicks = computed(() =>
  picks.value.filter((p) => !pickedKeys.value.has(p.type === 'food' ? foodKey(p.food) : tempKey(p.temp)))
)
const pickKey = (p: MealPick) => (p.type === 'food' ? foodKey(p.food) : tempKey(p.temp))

function choose(p: MealPick) {
  const s = eatCount.value
  if (!s) return
  s.items.push(p.type === 'food' ? itemFromFood(p.food) : itemFromTemp(p.temp))
  persist()
  pickerOpen.value = false
  pickQ.value = ''
}

// ── 单位和一个多少 g ──

const piece = reactive({ open: false, key: '', unit: '个', custom: '', grams: '', save: false })
const pieceItem = computed(() => items.value.find((it) => it.key === piece.key) ?? null)
const customUnit = ref(false)

function openPiece(it: CountItem) {
  piece.key = it.key
  piece.unit = it.unit
  piece.grams = it.pieceGrams ? trim2(it.pieceGrams) : ''
  piece.save = it.own && (it.hadPortion ? false : it.savePortion)
  customUnit.value = !(PORTION_UNITS as readonly string[]).includes(it.unit)
  piece.custom = customUnit.value ? it.unit : ''
  piece.open = true
}

function pickUnit(u: string) {
  customUnit.value = false
  piece.unit = u
}

function confirmPiece() {
  const it = pieceItem.value
  if (!it) return
  const unit = (customUnit.value ? piece.custom : piece.unit).trim()
  if (!unit) {
    uni.showToast({ title: '单位不能空着', icon: 'none' })
    return
  }
  const raw = piece.grams.trim()
  const g = Number(raw)
  if (raw && (!Number.isFinite(g) || g <= 0)) {
    uni.showToast({ title: `一${unit}要大于 0${it.base}`, icon: 'none' })
    return
  }
  it.unit = unit.slice(0, 4)
  it.pieceGrams = raw ? Math.round(g * 100) / 100 : null
  it.savePortion = it.own && piece.save
  persist()
  piece.open = false
}

// ── 合计 ──

const totals = computed(() => {
  const t = { kcal: 0, protein: 0, carb: 0, fat: 0 }
  for (const it of items.value) {
    t.kcal += itemNutrient(it, 'kcal')
    t.protein += itemNutrient(it, 'protein')
    t.carb += itemNutrient(it, 'carb')
    t.fat += itemNutrient(it, 'fat')
  }
  return t
})
const counted = computed(() => items.value.filter((it) => it.count > 0))
const missing = computed(() => counted.value.filter((it) => itemGrams(it) == null))

function itemLine(it: CountItem): string {
  const g = itemGrams(it)
  if (!it.count) return '吃一个，点一下'
  if (g == null) return `一${it.unit}多少 ${it.base} 还没填`
  return `共 ${trim2(g)}${it.base}`
}

// ── 吃完了 ──

const finishOpen = ref(false)
const saving = ref(false)

function openFinish() {
  if (!counted.value.length) {
    uni.showToast({ title: '还一个都没数', icon: 'none' })
    return
  }
  const lack = missing.value[0]
  if (lack) {
    uni.showToast({ title: `先补上一${lack.unit}${lack.name}多少 ${lack.base}`, icon: 'none' })
    openPiece(lack)
    return
  }
  finishOpen.value = true
}

async function submit() {
  const s = eatCount.value
  if (!s || saving.value) return
  saving.value = true
  try {
    await addCountedMeals({
      date: s.date,
      meal: s.meal,
      time: s.time,
      items: counted.value.map((it) => ({
        ...(it.foodId != null
          ? { foodId: it.foodId }
          : { name: it.name, kcal: it.per100.kcal, protein: it.per100.protein, fat: it.per100.fat, carb: it.per100.carb, sodium: it.per100.sodium }),
        grams: itemGrams(it) as number,
        portionUnit: it.unit,
        portionGrams: it.pieceGrams as number
      }))
    })
    // 补的「一个多少 g」存回自己建的食物；存不成不影响已经记下的
    await Promise.allSettled(counted.value.filter((it) => it.own && it.savePortion && it.foodId != null).map(savePortion))
    const label = MEAL_LABEL[s.meal]
    finishOpen.value = false
    clearCount()
    uni.showToast({ title: `已记进${label}`, icon: 'none' })
    setTimeout(() => uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/home/index' }) }), 400)
  } catch {
    // request 层已统一提示；计数还在，可以再试
  } finally {
    saving.value = false
  }
}

async function savePortion(it: CountItem) {
  const f = await getFood(it.foodId as number)
  const { id: _id, imageKey: _img, ...rest } = f
  await updateFood(f.id, { ...rest, portionLabel: it.unit, portionGrams: it.pieceGrams })
}

async function giveUp() {
  const n = counted.value.length
  const ok = await ask({
    title: '放弃这次计数？',
    content: n ? '已经数的都不会记下，也找不回来。' : '这一场会直接结束。',
    confirmText: '放弃',
    danger: true
  })
  if (!ok) return
  clearCount()
  uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/home/index' }) })
}

const hue = (i: number) => `cc--h${i % HUES}`
</script>

<template>
  <view class="ec">
    <view class="ec__body">
      <FtPageHeader title="边吃边数" back>
        <template #right>
          <view class="mealpill" :class="'mealpill--' + meal" role="button" :aria-label="`记进${MEAL_LABEL[meal]}，点一下换`" @click="switchMeal">
            <FtIcon :name="meal" :size="18" />
            <text class="mealpill__t">{{ MEAL_LABEL[meal] }}</text>
          </view>
        </template>
      </FtPageHeader>

      <view v-if="eatCount" class="meta">
        <text class="ft-num">{{ eatCount.time }} 开始<text v-if="elapsed"> · {{ elapsed }}</text></text>
        <view class="vibe" :class="{ 'vibe--off': !vibe }" role="switch" :aria-checked="vibe" @click="toggleVibe">
          <text class="vibe__dot" />
          <text>震动{{ vibe ? '开' : '关' }}</text>
        </view>
      </view>

      <!-- 一种都还没选 -->
      <view v-if="!items.length" class="first" role="button" @click="openPicker">
        <view class="first__ring">
          <FtIcon name="plus" :size="34" :stroke="2" />
        </view>
        <text class="first__t">选第一种要数的食物</text>
        <text class="first__s">之后吃一个，点一下</text>
      </view>

      <!-- 计数卡：整张卡都是 +1 -->
      <view
        v-for="(it, i) in items"
        :id="`cc-${i}`"
        :key="it.key"
        class="cc"
        :class="[hue(i), { 'cc--zero': !it.count }]"
        role="button"
        :aria-label="`${it.name}，已经 ${it.count} ${it.unit}，点一下加一`"
        @touchstart="onDown(it, i, $event)"
        @click="bump(it, 1)"
        @longpress="cardMenu(it)"
      >
        <view class="cc__head">
          <view class="cc__thumb">
            <FoodThumb v-if="it.foodId && it.imageKey" :id="it.foodId" :image-key="it.imageKey" :size="44" />
            <FtIcon v-else name="bowl" :size="22" />
          </view>
          <view class="cc__info">
            <text class="cc__name">{{ it.name }}</text>
            <view class="cc__piece" :class="{ 'cc__piece--ask': !it.pieceGrams }" role="button" @click.stop="openPiece(it)">
              <text v-if="it.pieceGrams" class="ft-num">一{{ it.unit }} {{ trim2(it.pieceGrams) }}{{ it.base }}</text>
              <text v-else>一{{ it.unit }}多少 {{ it.base }}？</text>
              <FtIcon name="pencil" :size="12" :stroke="2" />
            </view>
          </view>
          <!-- 右上角：这一种的小计热量（克数没填时不显示） -->
          <view v-if="it.count && itemGrams(it) != null" class="cc__kcal ft-num">
            <FtRollNumber :value="Math.round(itemNutrient(it, 'kcal'))" />
            <text class="cc__kcal-u">大卡</text>
          </view>
        </view>

        <!-- 十颗一排；满十并成一颗大珠子放到左边 -->
        <view class="beads" aria-hidden="true">
          <view v-for="t in tens(it)" :key="'t' + t" class="bead-ten">
            <text class="bead-ten__n ft-num">10</text>
          </view>
          <view class="ones" :class="{ 'is-merging': merging[it.key] }">
            <view v-for="k in 10" :key="k" class="bead" :class="{ 'bead--on': k <= ones(it) }" />
          </view>
        </view>

        <view class="cc__foot">
          <text class="cc__line ft-num">{{ itemLine(it) }}</text>
          <!-- 步进器：− 数字 ＋ 在同一行，改的和被改的挨在一起；减号小而淡，和加号隔着数字 -->
          <view class="step">
            <view class="cc__minus" :class="{ 'is-off': !it.count }" role="button" :aria-label="`${it.name}减一`" @click.stop="bump(it, -1)">
              <FtIcon name="minus" :size="20" :stroke="2.2" />
            </view>
            <view class="cc__num ft-num" :class="{ 'is-pop': popping[it.key] }">
              <FtRollNumber :value="it.count" />
              <text class="cc__unit">{{ it.unit }}</text>
            </view>
            <view class="cc__plus" role="button" :aria-label="`${it.name}加一`" @click.stop="bump(it, 1)">
              <FtIcon name="plus" :size="22" :stroke="2.4" />
            </view>
          </view>
        </view>

        <!-- 点按效果 -->
        <view class="cc__fx" aria-hidden="true">
          <view
            v-for="f in fx[it.key] || []"
            :key="f.id"
            class="fx"
            :class="{ 'fx--center': f.x < 0 }"
            :style="f.x >= 0 ? { left: `${f.x}px`, top: `${f.y}px` } : {}"
          >
            <view class="fx__ripple" />
            <text class="fx__plus ft-num">{{ f.text }}</text>
          </view>
        </view>
      </view>

      <view v-if="items.length" class="addbox" role="button" @click="openPicker">
        <FtIcon name="plus" :size="18" />
        <text>再数一种</text>
      </view>

      <text v-if="items.length" class="hint">长按卡片可以改单位、清零或不数这一种</text>
      <text class="giveup" role="button" @click="giveUp">放弃这次计数</text>
    </view>

    <!-- 底部：总热量 ＋ 吃完了 -->
    <view v-if="items.length" class="bar">
      <view class="bar__sum">
        <view class="bar__kcal ft-num">
          <FtRollNumber :value="Math.round(totals.kcal)" />
          <text class="bar__kcal-u">大卡</text>
        </view>
        <text class="bar__macros ft-num">
          蛋白 {{ macroGram(totals.protein) }}g · 碳水 {{ macroGram(totals.carb) }}g · 脂肪 {{ macroGram(totals.fat) }}g
        </text>
      </view>
      <view class="bar__go" :class="{ 'is-off': !counted.length }" role="button" @click="openFinish">吃完了</view>
    </view>

    <!-- 选食物 -->
    <Transition name="ft-sheet">
      <FtSheet v-if="pickerOpen" title="数哪一种" sub="食物库的食物和吃过的临时条目都能数" @close="pickerOpen = false">
        <view class="psearch">
          <FtIcon name="search" :size="18" tone="muted" />
          <input v-model="pickQ" class="psearch__in" placeholder="搜食物名、别名或拼音首字母" placeholder-class="ph" @input="onPickQ" />
        </view>
        <view class="plist">
          <FoodRow
            v-for="p in shownPicks"
            :key="pickKey(p)"
            :food="p.type === 'food' ? p.food : null"
            :temp="p.type === 'temp' ? p.temp : null"
            :keyword="pickQ"
            @tap="choose(p)"
          />
          <text v-if="picksLoading && !picks.length" class="plist__none">加载中…</text>
          <text v-else-if="!picksLoading && !shownPicks.length" class="plist__none">{{ pickQ ? '没有搜到' : '食物库里还没有食物' }}</text>
        </view>
      </FtSheet>
    </Transition>

    <!-- 单位和一个多少 g -->
    <Transition name="ft-sheet">
      <FtSheet v-if="piece.open && pieceItem" :title="pieceItem.name" sub="按什么数、一个有多少" @close="piece.open = false">
        <view class="psheet">
          <text class="psheet__label">按什么数</text>
          <view class="units">
            <text
              v-for="u in PORTION_UNITS"
              :key="u"
              class="unit"
              :class="{ 'unit--on': !customUnit && piece.unit === u }"
              @click="pickUnit(u)"
            >{{ u }}</text>
            <text class="unit" :class="{ 'unit--on': customUnit }" @click="customUnit = true">自定义</text>
          </view>
          <input v-if="customUnit" v-model="piece.custom" class="psheet__in" maxlength="4" placeholder="自己填单位，如：只" placeholder-class="ph" />

          <text class="psheet__label">一{{ customUnit ? piece.custom || '个' : piece.unit }}多少 {{ pieceItem.base }}</text>
          <view class="gin">
            <input v-model="piece.grams" type="digit" class="gin__in ft-num" placeholder="如：20" placeholder-class="ph" />
            <text class="gin__u ft-num">{{ pieceItem.base }}</text>
          </view>
          <text class="psheet__note">可以先空着，吃完前补上就行</text>

          <view v-if="pieceItem.own" class="save" role="checkbox" :aria-checked="piece.save" @click="piece.save = !piece.save">
            <view class="save__box" :class="{ 'save__box--on': piece.save }">
              <FtIcon v-if="piece.save" name="check" :size="14" :stroke="2.4" />
            </view>
            <text>{{ pieceItem.hadPortion ? '同时改这条食物的常用份' : '存为这条食物的常用份，下次不用再填' }}</text>
          </view>
          <text v-else class="psheet__note">{{ pieceItem.foodId ? '预置食物不能存常用份，只在这次用' : '临时条目没有常用份，只在这次用' }}</text>

          <FtButton variant="primary" block class="psheet__ok" @click="confirmPiece">好</FtButton>
        </view>
      </FtSheet>
    </Transition>

    <!-- 吃完了：核对一遍再记 -->
    <Transition name="ft-sheet">
      <FtSheet v-if="finishOpen && eatCount" :title="`记进${MEAL_LABEL[eatCount.meal]}`" :sub="`${eatCount.date.slice(5).replace('-', '月')}日 · ${eatCount.time} 开始吃的`" @close="finishOpen = false">
        <view class="fin">
          <view v-for="it in counted" :key="it.key" class="fin__row">
            <view class="fin__mid">
              <text class="fin__name">{{ it.name }}</text>
              <text class="fin__sub ft-num">{{ it.count }} {{ it.unit }}（{{ trim2(itemGrams(it) || 0) }}{{ it.base }}）</text>
            </view>
            <text class="fin__kcal ft-num">{{ Math.round(itemNutrient(it, 'kcal')) }} 大卡</text>
          </view>
          <view class="fin__total">
            <text>合计</text>
            <text class="ft-num">{{ Math.round(totals.kcal) }} 大卡</text>
          </view>
        </view>
        <FtButton variant="primary" block :disabled="saving" class="fin__ok" @click="submit">记下</FtButton>
      </FtSheet>
    </Transition>
  </view>
</template>

<style lang="scss" scoped>
.ec__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(260rpx + env(safe-area-inset-bottom));
}

// ── 页头右侧：记进哪一餐（餐次色，点一下换）──
.mealpill {
  @include glass-pill(14rpx 26rpx 14rpx 20rpx);
  @include press(dim);
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.mealpill__t {
  font-size: $text-base;
  font-weight: $weight-bold;
}

.mealpill--breakfast { color: $meal-breakfast-fg; }
.mealpill--lunch { color: $meal-lunch-fg; }
.mealpill--dinner { color: $meal-dinner-fg; }
.mealpill--snack { color: $meal-snack-fg; }

.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: -8rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.vibe {
  @include press(dim);
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 8rpx 18rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  color: $app-ink-2;
}

.vibe__dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: $app-ok;
}

.vibe--off {
  color: $app-ink-muted;

  .vibe__dot {
    background: $app-line-dash;
  }
}

// ── 第一种 ──
.first {
  @include press(dim);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  padding: 96rpx 0 88rpx;
  border: 4rpx dashed $app-line-dash;
  border-radius: $radius-lg;
  color: $app-ink-muted;
}

.first__ring {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 128rpx;
  height: 128rpx;
  margin-bottom: 8rpx;
  border-radius: 50%;
  background: $app-surface;
  box-shadow: $shadow-sm;
  color: $app-ink-2;
}

.first__t {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $app-ink;
}

.first__s {
  font-size: $text-base;
}

// ── 计数卡 ──
.cc {
  @include glass-card;
  position: relative;
  overflow: hidden;
  padding: 32rpx 32rpx 24rpx;
  transition: transform $dur-fast $ease-out;
  -webkit-tap-highlight-color: transparent;
  user-select: none;

  &:active {
    transition-duration: 0s;
    transform: scale(0.985);
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;

    &:active {
      transform: none;
    }
  }
}

// 识别色：小珠子、涟漪、名字旁的缩略图底
@for $i from 1 through length($count-hues) {
  $pair: nth($count-hues, $i);
  .cc--h#{$i - 1} {
    --hue-fg: #{nth($pair, 1)};
    --hue-bg: #{nth($pair, 2)};
  }
}

.cc__head {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.cc__thumb {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88rpx;
  height: 88rpx;
  overflow: hidden;
  border-radius: $radius-md;
  background: var(--hue-bg);
  color: var(--hue-fg);
}

.cc__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6rpx;
}

.cc__name {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $app-ink;
}

.cc__piece {
  @include press(dim);
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin: -6rpx -12rpx;
  padding: 6rpx 12rpx;
  border-radius: $radius-pill;
  font-size: $text-sm;
  color: $app-ink-muted;
}

// 还没填克数：提示在这里补（不用橙，用识别色，免得和「吃完了」抢）
.cc__piece--ask {
  background: var(--hue-bg);
  color: var(--hue-fg);
  font-weight: $weight-bold;
}

.cc__kcal {
  flex: none;
  align-self: flex-start;
  display: flex;
  align-items: baseline;
  gap: 6rpx;
  margin-top: 4rpx;
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $app-ink-2;
}

.cc__kcal-u {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.step {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-left: auto;
}

.cc__num {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 6rpx;
  min-width: 132rpx;
  font-family: $font-num;
  font-size: 56rpx;
  font-weight: $weight-heavy;
  line-height: 1;
  color: $app-ink;
}

.cc__num.is-pop {
  animation: num-pop 560ms $ease-emphasis;
}

@keyframes num-pop {
  0% { transform: scale(1); }
  30% { transform: scale(1.18); }
  100% { transform: scale(1); }
}

.cc--zero .cc__num {
  color: $app-ink-subtle;
}

.cc__unit {
  font-family: $font-text;
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $app-ink-muted;
}

// ── 珠子 ──
.beads {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14rpx;
  min-height: 52rpx;
  margin-top: 28rpx;
}

.bead-ten {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  background: var(--hue-fg);
  box-shadow: 0 6rpx 12rpx -6rpx var(--hue-fg);
  animation: ten-in 520ms $ease-emphasis;

  @media (prefers-reduced-motion: reduce) {
    animation: none;
  }
}

.bead-ten__n {
  font-size: 20rpx;
  font-weight: $weight-heavy;
  color: $app-surface;
}

@keyframes ten-in {
  0% { transform: scale(0.2); opacity: 0; }
  60% { transform: scale(1.15); opacity: 1; }
  100% { transform: scale(1); }
}

.ones {
  display: flex;
  gap: 12rpx;
}

.bead {
  width: 26rpx;
  height: 26rpx;
  box-sizing: border-box;
  border-radius: 50%;
  border: 3rpx solid $app-line-dash;
  transition: background $dur-fast $ease-out, border-color $dur-fast $ease-out, transform 260ms $ease-emphasis;

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
}

.bead--on {
  border-color: var(--hue-fg);
  background: var(--hue-fg);
  animation: bead-in 260ms $ease-emphasis;

  @media (prefers-reduced-motion: reduce) {
    animation: none;
  }
}

@keyframes bead-in {
  0% { transform: scale(0.3); }
  70% { transform: scale(1.25); }
  100% { transform: scale(1); }
}

// 满十：十颗一起往左缩进去
.ones.is-merging .bead {
  animation: bead-merge 480ms $ease-in forwards;
}

@for $k from 1 through 10 {
  .ones.is-merging .bead:nth-child(#{$k}) {
    --dx: #{-($k - 1) * 38rpx - 30rpx};
  }
}

@keyframes bead-merge {
  0% { transform: translateX(0) scale(1); opacity: 1; }
  100% { transform: translateX(var(--dx)) scale(0.4); opacity: 0; }
}

.cc__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
}

.cc__line {
  font-size: $text-sm;
  color: $app-ink-muted;
}

// 减一个：和「＋」一样大的圆角方钮，灰蓝薄纱底、淡色图标；没数过时压淡、点了也不动
.cc__minus {
  @include press(shrink);
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 76rpx;
  height: 76rpx;
  border-radius: $radius-sm;
  background: $app-surface-2;
  color: $app-ink-muted;
}

.cc__minus.is-off {
  opacity: 0.35;
}

// 加一个：识别色实心圆角方钮（整张卡点一下也是加一个，这颗给想点准的时候）
.cc__plus {
  @include press(shrink);
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 76rpx;
  height: 76rpx;
  border-radius: $radius-sm;
  background: var(--hue-fg);
  box-shadow: 0 12rpx 24rpx -12rpx var(--hue-fg);
  color: $app-surface;
}

// ── 点按效果 ──
.cc__fx {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.fx {
  position: absolute;
  width: 0;
  height: 0;
}

.fx--center {
  left: 50%;
  top: 50%;
}

.fx__ripple {
  position: absolute;
  left: -200rpx;
  top: -200rpx;
  width: 400rpx;
  height: 400rpx;
  border-radius: 50%;
  background: var(--hue-bg);
  opacity: 0;
  animation: ripple 620ms $ease-out;
}

@keyframes ripple {
  0% { transform: scale(0.1); opacity: 0.9; }
  100% { transform: scale(1.6); opacity: 0; }
}

.fx__plus {
  position: absolute;
  left: 0;
  top: 0;
  transform: translate(-50%, -50%);
  font-size: $text-2xl;
  font-weight: $weight-heavy;
  color: var(--hue-fg);
  white-space: nowrap;
  animation: plus-up 740ms $ease-out forwards;
}

@keyframes plus-up {
  0% { transform: translate(-50%, -30%) scale(0.6); opacity: 0; }
  20% { transform: translate(-50%, -80%) scale(1.1); opacity: 1; }
  100% { transform: translate(-50%, -230%) scale(1); opacity: 0; }
}

// ── 再数一种 ──
.addbox {
  @include press(dim);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  height: 104rpx;
  border: 4rpx dashed $app-line-dash;
  border-radius: $radius-md;
  font-size: $text-md;
  color: $app-ink-muted;
}

.hint {
  font-size: $text-sm;
  color: $app-ink-muted;
  text-align: center;
}

.giveup {
  @include press(dim);
  align-self: center;
  padding: 16rpx 28rpx;
  font-size: $text-base;
  color: $app-danger;
}

// ── 底部汇总条 ──
.bar {
  @include glass($glass-bg-strong);
  @include hide-on-keyboard;
  position: fixed;
  left: $tabbar-side;
  right: $tabbar-side;
  bottom: calc(#{$tabbar-gap} + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 16rpx 16rpx 16rpx 36rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $glass-shadow-float;
}

.bar__sum {
  flex: 1;
  min-width: 0;
}

.bar__kcal {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  font-size: $text-2xl;
  font-weight: $weight-heavy;
  color: $app-ink;
}

.bar__kcal-u {
  font-size: $text-sm;
  font-weight: $weight-bold;
  color: $app-ink-muted;
}

.bar__macros {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.bar__go {
  @include press(shrink);
  flex: none;
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 44rpx;
  border-radius: $radius-pill;
  background: $accent-bg;
  box-shadow: $accent-shadow;
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $app-surface;
}

.bar__go.is-off {
  opacity: 0.4;
}

// ── 选食物弹层 ──
.psearch {
  display: flex;
  align-items: center;
  gap: 12rpx;
  height: 84rpx;
  padding: 0 28rpx;
  border-radius: $radius-pill;
  background: $app-surface;
}

.psearch__in {
  flex: 1;
  font-size: $text-md;
}

.plist {
  max-height: 58vh;
  overflow-y: auto;
  margin-top: 16rpx;
  padding: 0 24rpx;
  @include sheet-card;
}

.plist__none {
  display: block;
  padding: 48rpx 0;
  font-size: $text-base;
  color: $app-ink-muted;
  text-align: center;
}

// ── 单位与克数弹层 ──
.psheet {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding: 28rpx;
  @include sheet-card;
}

.psheet__label {
  margin-top: 8rpx;
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $app-ink;
}

.units {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.unit {
  @include press(dim);
  min-width: 56rpx;
  padding: 10rpx 22rpx;
  border: 1rpx solid $app-line;
  border-radius: $radius-pill;
  font-size: $text-md;
  color: $app-ink-muted;
  text-align: center;
}

.unit--on {
  @include seg-item-on;
  border-color: transparent;
}

.psheet__in {
  height: 84rpx;
  padding: 0 24rpx;
  border-radius: $radius-sm;
  background: $app-surface-2;
  font-size: $text-md;
}

.gin {
  display: flex;
  align-items: center;
  height: 92rpx;
  padding: 0 28rpx;
  border-radius: $radius-sm;
  background: $app-surface-2;
}

.gin__in {
  flex: 1;
  font-size: $text-xl;
  font-weight: $weight-bold;
}

.gin__u {
  font-size: $text-md;
  color: $app-ink-muted;
}

.psheet__note {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.save {
  @include press(dim);
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 8rpx 0;
  font-size: $text-base;
  color: $app-ink-2;
}

.save__box {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40rpx;
  height: 40rpx;
  box-sizing: border-box;
  border-radius: 50%;
  border: 3rpx solid $app-line-dash;
  background: $app-surface;
}

.save__box--on {
  border-color: $app-ink;
  background: $app-ink;
  color: $app-surface;
}

.psheet__ok {
  margin-top: 12rpx;
}

// ── 吃完了 ──
.fin {
  padding: 8rpx 28rpx;
  @include sheet-card;
}

.fin__row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 0;
  border-bottom: 1rpx solid $app-line-soft;
}

.fin__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.fin__name {
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.fin__sub {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.fin__kcal {
  font-size: $text-md;
  color: $app-ink;
}

.fin__total {
  display: flex;
  justify-content: space-between;
  padding: 24rpx 0 20rpx;
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.fin__ok {
  margin-top: 28rpx;
}
</style>
