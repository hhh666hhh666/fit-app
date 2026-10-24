<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import {
  createFood,
  getFood,
  updateFood,
  type FoodDraft,
  type FoodPayload
} from '@/api/nutrition'
import { useRecognitionStore } from '@/stores/recognition'
import { MAIN_FIELDS, OPTIONAL_GROUPS } from '@/utils/food-fields'
import { ensureLogin } from '@/utils/guard'
import FtButton from '@/components/FtButton.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'

// ── 食物新增 / 编辑 / 复制为自定义（营养 45 项：主组必填 ＋ 五类折叠；另有扩展字段 GI，常显）──
//    另承接「拍照识别」的草稿预填（recognized=1；ADR 0018）：低置信度字段标「待核对」，撞名给二选一。

const editId = ref<number | null>(null)
/** 「复制为自定义」的来源（预置食物 id）；有值时表单预填但保存走新建。 */
const copyFromId = ref<number | null>(null)
/** 识别草稿来源（食物库页「拍照识别」经 store 一次性通道传来）。 */
const fromRecognition = ref(false)
/** 低置信度字段 key（识别不确定，保存前请核对）。 */
const uncertainKeys = ref<string[]>([])
const isEdit = computed(() => editId.value != null)
const saving = ref(false)
const pageTitle = computed(() =>
  isEdit.value ? '编辑食物'
    : copyFromId.value != null ? '复制为自定义'
    : fromRecognition.value ? '确认识别结果'
    : '新增食物'
)

/** 表单：数值字段一律字符串（空串 = 未填），提交时转换。 */
const form = reactive<Record<string, string>>({
  name: '',
  unit: 'g',
  kcal: '',
  protein: '',
  fat: '',
  carb: '',
  sodium: '',
  // 扩展字段（不在 45 项正表内）：常显、选填
  gi: '',
  portionLabel: '',
  portionGrams: ''
})

/** 折叠状态（默认全收起）。 */
const openGroups = ref<Record<string, boolean>>({})

function toggleGroup(title: string) {
  openGroups.value[title] = !openGroups.value[title]
}

onLoad((query) => {
  const q = (query || {}) as Record<string, string | undefined>
  const id = Number(q.id)
  if (id) {
    editId.value = id
    void loadExisting()
    return
  }
  const copy = Number(q.copyFrom)
  if (copy) {
    copyFromId.value = copy
    void loadExisting()
    return
  }
  if (q.recognized) {
    // 识别草稿：取走即清（刷新不残留）；失效就退回食物库重拍
    const d = useRecognitionStore().take()
    if (!d) {
      uni.showToast({ title: '识别草稿已失效，请重新拍照', icon: 'none' })
      setTimeout(() => uni.navigateBack(), 800)
      return
    }
    fromRecognition.value = true
    uncertainKeys.value = d.uncertain ?? []
    fillForm(d.draft)
    if (d.duplicateFoodId) showDuplicateChoice(d)
  }
})

onShow(() => {
  if (!ensureLogin()) return
})

async function loadExisting() {
  const id = editId.value ?? copyFromId.value
  if (id == null) return
  try {
    const f = await getFood(id)
    if (f.preset && editId.value != null) {
      // 防御：编辑目标若指到预置（正常入口不会），自动降级为「复制」；服务端同样会挡改
      copyFromId.value = editId.value
      editId.value = null
    }
    fillForm(f)
    if (copyFromId.value != null) {
      uni.showToast({ title: '已带出预设数据，可修改后保存', icon: 'none' })
    }
  } catch {
    // request 层已统一提示
  }
}

function fillForm(f: FoodPayload) {
  const toStr = (v: unknown) => (v == null ? '' : String(v))
  form.name = f.name ?? ''
  form.unit = f.unit
  form.kcal = toStr(f.kcal)
  form.protein = toStr(f.protein)
  form.fat = toStr(f.fat)
  form.carb = toStr(f.carb)
  form.sodium = toStr(f.sodium)
  form.gi = toStr(f.gi)
  form.portionLabel = f.portionLabel ?? ''
  form.portionGrams = toStr(f.portionGrams)
  for (const g of OPTIONAL_GROUPS) {
    for (const fd of g.fields) {
      form[fd.key] = toStr((f as unknown as Record<string, unknown>)[fd.key])
    }
  }
}

/** 字段是否低置信度（识别不确定，标「待核对」）。 */
function isUncertain(key: string): boolean {
  return uncertainKeys.value.includes(key)
}

/** 待核对字段的中文名（顶部提示行用）。 */
const uncertainLabels = computed(() => {
  const labelOf = new Map<string, string>()
  labelOf.set('name', '食物名称')
  for (const f of MAIN_FIELDS) labelOf.set(f.key, f.label)
  for (const g of OPTIONAL_GROUPS) for (const f of g.fields) labelOf.set(f.key, f.label)
  return uncertainKeys.value.map((k) => labelOf.get(k) ?? k)
})

/** 撞名二选一（ADR 0018）：打开已有的 / 仍然新建（保存会被同名校验拦下，需改名）。 */
function showDuplicateChoice(d: FoodDraft) {
  uni.showModal({
    title: '库里有同名食物',
    content: `「${d.duplicateFoodName || '同名食物'}」已在食物库里。可打开已有的查看编辑；仍新建的话，保存时会被拦下要求改名。`,
    confirmText: '打开已有的',
    cancelText: '仍然新建',
    success: ({ confirm }) => {
      if (confirm) {
        uni.redirectTo({ url: `/pages/food-detail/index?id=${d.duplicateFoodId}` })
      }
    }
  })
}

/** 字符串 → 数字：'' → null（未填）；非法 → undefined。 */
function numOrNull(key: string): number | null | undefined {
  const t = form[key].trim()
  if (!t) return null
  const n = Number(t)
  if (!Number.isFinite(n) || n < 0) return undefined
  return n
}

function buildPayload(): FoodPayload | null {
  const name = form.name.trim()
  if (!name) {
    uni.showToast({ title: '先给食物起个名字', icon: 'none' })
    return null
  }

  const required: { key: string; label: string }[] = [
    { key: 'kcal', label: '热量' },
    { key: 'protein', label: '蛋白质' },
    { key: 'fat', label: '脂肪' },
    { key: 'carb', label: '碳水化合物' },
    { key: 'sodium', label: '钠' }
  ]
  const main: Record<string, number> = {}
  for (const r of required) {
    const v = numOrNull(r.key)
    if (v == null) {
      uni.showToast({ title: `${r.label}不能为空`, icon: 'none' })
      return null
    }
    if (v === undefined) {
      uni.showToast({ title: `${r.label}数字不正确`, icon: 'none' })
      return null
    }
    main[r.key] = v
  }

  const payload: Record<string, unknown> = {
    name,
    unit: form.unit === 'ml' ? 'ml' : 'g',
    ...main,
    portionLabel: form.portionLabel.trim() || null,
    portionGrams: numOrNull('portionGrams') ?? null
  }
  // 扩展字段 GI：选填（空串＝清空），非法数字当场拦下
  const gi = numOrNull('gi')
  if (gi === undefined) {
    uni.showToast({ title: 'GI 数字不正确', icon: 'none' })
    return null
  }
  payload.gi = gi
  for (const g of OPTIONAL_GROUPS) {
    for (const fd of g.fields) {
      const v = numOrNull(fd.key)
      if (v === undefined) {
        uni.showToast({ title: `${fd.label}数字不正确`, icon: 'none' })
        return null
      }
      payload[fd.key] = v
    }
  }
  return payload as unknown as FoodPayload
}

async function save() {
  if (saving.value) return
  const payload = buildPayload()
  if (!payload) return
  saving.value = true
  try {
    if (isEdit.value) {
      await updateFood(editId.value as number, payload)
    } else {
      await createFood(payload)
    }
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch {
    // request 层已统一提示；留在本页可重试
  } finally {
    saving.value = false
  }
}

</script>

<template>
  <view class="fe">
    <view class="fe__body">
      <FtPageHeader :title="pageTitle" back />

      <!-- 识别草稿提示（ADR 0018）：来源说明 ＋ 名字缺失警告 ＋ 待核对字段点名 -->
      <view v-if="fromRecognition" class="glass block rec-note">
        <text class="block__title">来自拍照识别</text>
        <text v-if="!form.name.trim()" class="rec-note__text rec-note__text--warn">
          没能认出食物名称——其余数值已按标签填好，补个名字就能保存
        </text>
        <text v-if="uncertainKeys.length" class="rec-note__text">
          这些字段识别不确定：{{ uncertainLabels.join('、') }}。保存前请对照包装核对。
        </text>
        <text v-else-if="form.name.trim()" class="rec-note__text">已按包装营养成分表预填，核对无误即可保存。</text>
      </view>

      <!-- 名称 + 单位 -->
      <view class="glass block">
        <text class="block__label">食物名称<text v-if="isUncertain('name')" class="fld__warn"> 待核对</text></text>
        <input
          v-model="form.name"
          class="name-input"
          placeholder="如：鸡胸肉（生）"
          placeholder-class="ph"
          maxlength="64"
        />
        <view class="unit-row">
          <text class="block__label">数值基准</text>
          <view class="units">
            <view
              class="unit"
              :class="{ 'unit--active': form.unit !== 'ml' }"
              @click="form.unit = 'g'"
            >
              每 100g
            </view>
            <view
              class="unit"
              :class="{ 'unit--active': form.unit === 'ml' }"
              @click="form.unit = 'ml'"
            >
              每 100ml
            </view>
          </view>
        </view>
      </view>

      <!-- 主组（必填） -->
      <view class="glass block">
        <text class="block__title">主要营养（必填）</text>
        <view class="fld">
          <text class="fld__label">热量（大卡）<text v-if="isUncertain('kcal')" class="fld__warn"> 待核对</text></text>
          <input v-model="form.kcal" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
        <view class="fld">
          <text class="fld__label">蛋白质（g）<text v-if="isUncertain('protein')" class="fld__warn"> 待核对</text></text>
          <input v-model="form.protein" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
        <view class="fld">
          <text class="fld__label">脂肪（g）<text v-if="isUncertain('fat')" class="fld__warn"> 待核对</text></text>
          <input v-model="form.fat" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
        <view class="fld">
          <text class="fld__label">碳水化合物（g）<text v-if="isUncertain('carb')" class="fld__warn"> 待核对</text></text>
          <input v-model="form.carb" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
        <view class="fld">
          <text class="fld__label">钠（mg）<text v-if="isUncertain('sodium')" class="fld__warn"> 待核对</text></text>
          <input v-model="form.sodium" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
      </view>

      <!-- 升糖指数（扩展字段，常显） -->
      <view class="glass block">
        <text class="block__title">升糖指数（选填）</text>
        <view class="fld">
          <text class="fld__label">GI（葡萄糖＝100）</text>
          <input
            v-model="form.gi"
            type="digit"
            class="fld__input ft-num"
            placeholder="–"
            placeholder-class="ph"
          />
        </view>
      </view>

      <!-- 常用份 -->
      <view class="glass block">
        <text class="block__title">常用份（可选）</text>
        <view class="fld">
          <text class="fld__label">份标签</text>
          <input
            v-model="form.portionLabel"
            class="fld__input"
            placeholder="如：1个"
            placeholder-class="ph"
            maxlength="16"
          />
        </view>
        <view class="fld">
          <text class="fld__label">一份多少（g/ml）</text>
          <input v-model="form.portionGrams" type="digit" class="fld__input ft-num" placeholder="如：50" placeholder-class="ph" />
        </view>
      </view>

      <!-- 更多营养（折叠） -->
      <view v-for="g in OPTIONAL_GROUPS" :key="g.title" class="glass block">
        <view class="group-head" @click="toggleGroup(g.title)">
          <text class="block__title">{{ g.title }}（选填）</text>
          <view class="group-head__arrow" :class="{ 'group-head__arrow--open': openGroups[g.title] }">
            <FtIcon name="chevron-right" :size="18" tone="muted" />
          </view>
        </view>
        <template v-if="openGroups[g.title]">
          <view v-for="fd in g.fields" :key="fd.key" class="fld">
            <text class="fld__label">{{ fd.label }}<text v-if="isUncertain(fd.key)" class="fld__warn"> 待核对</text></text>
            <input
              v-model="form[fd.key]"
              type="digit"
              class="fld__input ft-num"
              placeholder="–"
              placeholder-class="ph"
            />
          </view>
        </template>
      </view>

    </view>

    <!-- 保存：贴底悬浮的主按钮，与汇总页「完成」同一位置 -->
    <view class="fe__bottom">
      <FtButton variant="primary" block :disabled="saving" class="fe__save" @click="save">保存</FtButton>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.fe__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(200rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

.block {
  padding-top: 28rpx;
  padding-bottom: 12rpx;
}

.block__label {
  display: block;
  padding-bottom: 12rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.block__title {
  padding-bottom: 8rpx;
  font-size: $text-title;
  font-weight: $weight-bold;
  color: $app-ink;
}

.name-input {
  width: 100%;
  height: 92rpx;
  box-sizing: border-box;
  padding: 0 24rpx;
  border-radius: $radius-md;
  background: $app-surface;
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $app-ink;
}

.ph {
  color: $app-ink-subtle;
  font-weight: $weight-regular;
}

.unit-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0 16rpx;
}

.unit-row .block__label {
  padding-bottom: 0;
}

/* 数值基准：和全站分段控件同一套（选中＝深字 ＋ 浅胶囊底） */
.units {
  @include seg-track-solid;
}

.unit {
  padding: 10rpx 24rpx;
  border-radius: $radius-pill;
  font-size: $text-base;
  color: $app-ink-muted;
}

.unit--active {
  background: $app-surface-2;
  color: $app-ink;
  font-weight: $weight-bold;
}

.fld {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0;
}

.fld + .fld,
.block__title + .fld,
.group-head + .fld {
  border-top: 1rpx solid $app-line-soft;
}

.fld__label {
  flex-shrink: 0;
  font-size: $text-base;
  color: $app-ink-muted;
}

.fld__input {
  flex: 1;
  margin-left: 20rpx;
  text-align: right;
  font-size: $text-md;
  color: $app-ink;
}

/* 识别低置信度字段的「待核对」角标（ADR 0018） */
.fld__warn {
  margin-left: 8rpx;
  padding: 2rpx 12rpx;
  border-radius: $radius-pill;
  background: $accent-soft;
  font-size: $text-xs;
  color: $app-danger;
}

/* 识别草稿提示卡 */
.rec-note__text {
  display: block;
  padding-top: 8rpx;
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

/* 名字没认出的警告行 */
.rec-note__text--warn {
  color: $app-danger;
}

.group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12rpx;
}

.group-head__arrow {
  display: flex;
  transition: transform $dur-fast $ease-out;
}

.group-head__arrow--open {
  transform: rotate(90deg);
}

.fe__bottom {
  position: fixed;
  left: $tabbar-side;
  right: $tabbar-side;
  bottom: calc(#{$tabbar-gap} + env(safe-area-inset-bottom));
}

.fe__bottom .fe__save {
  height: 104rpx;
  font-size: 30rpx;
}

@media (prefers-reduced-motion: reduce) {
  .group-head__arrow {
    transition: none;
  }
}
</style>
