<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
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
import { fetchFoodImage, pickCropOriginal, removeFoodImage, uploadFoodImage, type CropBox } from '@/utils/photo'
import AvatarCropper from '@/components/AvatarCropper.vue'
import FtButton from '@/components/FtButton.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import { ask } from '@/utils/confirm'
import { PORTION_UNITS, portionUnit } from '@/utils/diet'

// ── 食物新增 / 编辑 / 复制为自定义（营养 45 项：主组必填 ＋ 五类折叠；另有扩展字段 GI，常显）──
//    另承接「拍照识别」的草稿预填（recognized=1；ADR 0018）：低置信度字段标「待核对」，撞名给二选一。
//    别名（ADR 0056）一个输入框，顿号、逗号或空格分隔；食物图片（ADR 0057）用方形裁剪框——
//    编辑已有食物时选完就上传；新建 / 复制时先留在本机，保存成功拿到 id 再上传（复制时没换图就让服务端复制来源的图）。

const editId = ref<number | null>(null)
/** 「复制为自定义」的来源（预置食物 id）；有值时表单预填但保存走新建。 */
const copyFromId = ref<number | null>(null)
/** 识别草稿来源（食物库页「拍照识别」经 store 一次性通道传来）。 */
const fromRecognition = ref(false)
/** 低置信度字段 key（识别不确定，保存前请核对）。 */
const uncertainKeys = ref<string[]>([])
/** 标签上没找到的必填字段 key（4+1 中的）：标「待补」，补齐才能保存。 */
const missingKeys = ref<string[]>([])
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
  aliases: '',
  unit: 'g',
  kcal: '',
  protein: '',
  fat: '',
  carb: '',
  sodium: '',
  // 扩展字段（不在 45 项正表内）：常显、选填
  gi: '',
  portionLabel: '',
  portionGrams: '',
  // 生重／熟重（ADR 0063）：空串＝不标
  cookState: '',
  // 五类选填营养一并占位：否则没展开填过的键是 undefined，trim() 会抛错
  ...Object.fromEntries(OPTIONAL_GROUPS.flatMap((g) => g.fields.map((f) => [f.key, ''])))
})

/** 每 100g 热量的合理上限：纯油脂约 900 kcal，超过就在保存前提醒一次（每 100ml 不提醒）。 */
const KCAL_PER100G_MAX = 900

/** 当前填的常用份份量（正数才有效）。 */
function servingAmount(): number | null {
  const n = Number(form.portionGrams.trim())
  return form.portionGrams.trim() && Number.isFinite(n) && n > 0 ? n : null
}

/** 两位小数、去掉尾零。 */
function round2(n: number): number {
  return Math.round(n * 100) / 100
}

/**
 * 每份的营养只由每 100 推算、只读（ADR 0063）：填了常用份，每个营养格右边实时显示「每份 xx」。
 * 改常用份只会改这里的显示，不会动每 100 的数。
 */
function perServing(key: string): string {
  const g = servingAmount()
  const t = (form[key] ?? '').trim()
  const n = Number(t)
  if (g == null || !t || !Number.isFinite(n)) return ''
  return `每${unitShown.value} ${round2((n * g) / 100)}`
}

// ── 常用份的单位（ADR 0066）：常用单位点一下，也能自己打字；不选就是「份」──
const customUnit = ref(false)
const unitShown = computed(() => form.portionLabel.trim() || '份')
function pickUnit(u: string) {
  customUnit.value = false
  // 再点一下已选的＝不选（存的时候按「份」）
  form.portionLabel = form.portionLabel === u ? '' : u
}
function useCustomUnit() {
  if (!customUnit.value && (PORTION_UNITS as readonly string[]).includes(form.portionLabel)) form.portionLabel = ''
  customUnit.value = true
}

const COOK_OPTIONS: { value: '' | 'raw' | 'cooked'; label: string }[] = [
  { value: '', label: '不标' },
  { value: 'raw', label: '生重' },
  { value: 'cooked', label: '熟重' }
]

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
    missingKeys.value = d.missing ?? []
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
    showRemoteImage(f.id, f.imageKey)
    if (copyFromId.value != null) {
      uni.showToast({ title: '已带出预置数据，可修改后保存', icon: 'none' })
    }
  } catch {
    // request 层已统一提示
  }
}

function fillForm(f: FoodPayload) {
  const toStr = (v: unknown) => (v == null ? '' : String(v))
  form.name = f.name ?? ''
  form.aliases = (f.aliases ?? []).join('、')
  form.unit = f.unit
  form.kcal = toStr(f.kcal)
  form.protein = toStr(f.protein)
  form.fat = toStr(f.fat)
  form.carb = toStr(f.carb)
  form.sodium = toStr(f.sodium)
  form.gi = toStr(f.gi)
  // 份标签只存单位（ADR 0066）；迁移前的老写法「1个」显示成「个」
  form.portionLabel = f.portionLabel ? portionUnit(f.portionLabel) : ''
  customUnit.value = !!form.portionLabel && !(PORTION_UNITS as readonly string[]).includes(form.portionLabel)
  form.portionGrams = toStr(f.portionGrams)
  form.cookState = f.cookState ?? ''
  for (const g of OPTIONAL_GROUPS) {
    for (const fd of g.fields) {
      form[fd.key] = toStr((f as unknown as Record<string, unknown>)[fd.key])
    }
  }
}

// ── 食物图片 ──

/** 当前显示的图：remote＝库里已有（或复制来源）的图，local＝本机刚选好、还没传的图。 */
const image = ref<
  | { kind: 'none' }
  | { kind: 'remote'; foodId: number; imageKey: string; url: string | null }
  | { kind: 'local'; blob: Blob; crop: CropBox; url: string }
>({ kind: 'none' })
/** 裁剪框：选了图还没确认时打开。 */
const cropper = ref<{ blob: Blob; src: string; width: number; height: number } | null>(null)
const imageBusy = ref(false)

function showRemoteImage(foodId: number, imageKey: string | null | undefined) {
  if (!imageKey) {
    image.value = { kind: 'none' }
    return
  }
  image.value = { kind: 'remote', foodId, imageKey, url: null }
  void fetchFoodImage(foodId, imageKey, 'thumb').then((url) => {
    const cur = image.value
    if (cur.kind === 'remote' && cur.imageKey === imageKey) image.value = { ...cur, url }
  })
}

function dropLocalUrl() {
  if (image.value.kind === 'local') URL.revokeObjectURL(image.value.url)
}

onBeforeUnmount(() => {
  dropLocalUrl()
  if (cropper.value) URL.revokeObjectURL(cropper.value.src)
})

/** 本机图的宽高（浏览器按 EXIF 摆正后的，和服务端口径一致）。 */
function naturalSize(url: string): Promise<{ width: number; height: number }> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve({ width: img.naturalWidth, height: img.naturalHeight })
    img.onerror = () => reject(new Error('decode'))
    img.src = url
  })
}

async function pickImage() {
  const blob = await pickCropOriginal('食物图片')
  if (!blob) return
  const src = URL.createObjectURL(blob)
  try {
    const { width, height } = await naturalSize(src)
    cropper.value = { blob, src, width, height }
  } catch {
    URL.revokeObjectURL(src)
    uni.showToast({ title: '这张图读不出来，换一张试试', icon: 'none' })
  }
}

function closeCropper() {
  if (cropper.value) URL.revokeObjectURL(cropper.value.src)
  cropper.value = null
}

async function onCropConfirm(crop: CropBox) {
  const c = cropper.value
  if (!c) return
  if (isEdit.value) {
    // 已有的食物：直接传，服务端截好方图
    imageBusy.value = true
    try {
      const key = await uploadFoodImage(editId.value as number, c.blob, crop)
      if (key) showRemoteImage(editId.value as number, key)
    } finally {
      imageBusy.value = false
      closeCropper()
    }
    return
  }
  // 新建 / 复制：先留在本机，保存后再传；预览用本机原图按裁剪框摆出来
  dropLocalUrl()
  image.value = { kind: 'local', blob: c.blob, crop, url: c.src }
  cropper.value = null
}

const localImageBox = ref<{ width: number; height: number } | null>(null)
async function measureLocal() {
  const cur = image.value
  if (cur.kind !== 'local') return
  localImageBox.value = await naturalSize(cur.url).catch(() => null)
}

function removeImage() {
  const cur = image.value
  if (cur.kind === 'none') return
  if (!isEdit.value) {
    dropLocalUrl()
    image.value = { kind: 'none' }
    return
  }
  void ask({
    title: '去掉图片？',
    content: '旧图会直接删除，不进回收站。',
    confirmText: '去掉',
    danger: true
  }).then(async (ok) => {
    if (!ok || editId.value == null) return
    imageBusy.value = true
    try {
      if (await removeFoodImage(editId.value)) image.value = { kind: 'none' }
    } finally {
      imageBusy.value = false
    }
  })
}

/** 字段是否低置信度（识别不确定，标「待核对」）。 */
function isUncertain(key: string): boolean {
  return uncertainKeys.value.includes(key)
}

/** 字段是否「标签上看不到、还没补」——补上了标记就消失。 */
function isMissing(key: string): boolean {
  return missingKeys.value.includes(key) && !form[key].trim()
}

const labelOf = new Map<string, string>([['name', '食物名称']])
for (const f of MAIN_FIELDS) labelOf.set(f.key, f.label)
for (const g of OPTIONAL_GROUPS) for (const f of g.fields) labelOf.set(f.key, f.label)

/** 待核对字段的中文名（顶部提示行用）。 */
const uncertainLabels = computed(() => uncertainKeys.value.map((k) => labelOf.get(k) ?? k))

/** 还没补的缺项中文名（顶部提示行用；补一项少一项）。 */
const missingLabels = computed(() =>
  missingKeys.value.filter((k) => isMissing(k)).map((k) => (labelOf.get(k) ?? k).replace(/（.*）$/, ''))
)

/** 撞名二选一（ADR 0018）：打开已有的 / 仍然新建（保存会被同名校验拦下，需改名）。 */
function showDuplicateChoice(d: FoodDraft) {
  void ask({
    title: '库里有同名食物',
    content: `「${d.duplicateFoodName || '同名食物'}」已在食物库里。可打开已有的查看编辑；仍新建的话，保存时会被拦下要求改名。`,
    confirmText: '打开已有的',
    cancelText: '仍然新建'
  }).then((confirm) => {
    if (confirm) {
      uni.redirectTo({ url: `/pages/food-detail/index?id=${d.duplicateFoodId}` })
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

  // 别名：按分隔符拆开交给服务端规整（去重、去掉和名字一样的、上限 10 个各 16 字）
  const aliases = form.aliases
    .split(/[、，,；;/|\s]+/)
    .map((a) => a.trim())
    .filter(Boolean)
  const payload: Record<string, unknown> = {
    name,
    aliases,
    unit: form.unit === 'ml' ? 'ml' : 'g',
    ...main,
    // 常用份成对（ADR 0066）：份标签只存单位；填了克数没选单位就记成「份」（详情页、导出都要成对才显示）
    portionLabel: numOrNull('portionGrams') != null ? unitShown.value : null,
    portionGrams: numOrNull('portionGrams') ?? null,
    cookState: form.cookState === 'raw' || form.cookState === 'cooked' ? form.cookState : null
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
  // 兜底：每 100g 超过 900 kcal（纯油脂也就 900 上下）多半是把每份的数当成每 100 填了
  if (payload.unit === 'g' && Number(payload.kcal) > KCAL_PER100G_MAX) {
    const ok = await ask({
      title: '热量看起来过高',
      content: `每 100g ${payload.kcal} kcal，纯油脂也只有 900 左右。确认填对了吗？`,
      confirmText: '仍然保存',
      cancelText: '回去改'
    })
    if (!ok) return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateFood(editId.value as number, payload)
    } else {
      const cur = image.value
      // 复制为自定义而且没换图：让服务端把来源的图复制一份
      if (copyFromId.value != null && cur.kind === 'remote') payload.copyImageFrom = cur.foodId
      const created = await createFood(payload)
      // 本机选好的图：食物有了 id 才能传；传不成也不影响已经保存的食物（已 toast）
      if (cur.kind === 'local') await uploadFoodImage(created.id, cur.blob, cur.crop)
    }
    uni.showToast({ title: '已保存', icon: 'none' })
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

      <!-- 识别草稿提示（ADR 0018）：来源说明 ＋ 名字缺失警告 ＋ 必填缺项点名 ＋ 待核对字段点名 -->
      <view v-if="fromRecognition" class="glass block rec-note">
        <text class="block__title">来自拍照识别</text>
        <text v-if="!form.name.trim()" class="rec-note__text rec-note__text--warn">
          没能认出食物名称——其余数值已按标签填好，补个名字就能保存
        </text>
        <text v-if="missingLabels.length" class="rec-note__text rec-note__text--warn">
          标签上看不到：{{ missingLabels.join('、') }}——其余已按标签填好，补上这几项就能保存
        </text>
        <text v-if="uncertainKeys.length" class="rec-note__text">
          这些字段识别不确定：{{ uncertainLabels.join('、') }}。保存前请对照包装核对。
        </text>
        <text v-else-if="form.name.trim() && !missingLabels.length" class="rec-note__text">已按包装营养成分表预填，核对无误即可保存。</text>
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
        <text class="block__label block__label--gap">别名（选填）</text>
        <input
          v-model="form.aliases"
          class="alias-input"
          placeholder="如：肥肠、大肠头（顿号、逗号或空格隔开）"
          placeholder-class="ph"
          maxlength="200"
        />
        <view class="unit-row">
          <text class="block__label">数值基准</text>
          <view class="units">
            <view class="unit" :class="{ 'unit--active': form.unit !== 'ml' }" @click="form.unit = 'g'">每 100g</view>
            <view class="unit" :class="{ 'unit--active': form.unit === 'ml' }" @click="form.unit = 'ml'">每 100ml</view>
          </view>
        </view>
        <!-- 生重／熟重（ADR 0063）：营养数按生的还是熟的重量测的 -->
        <view class="unit-row">
          <text class="block__label">生熟</text>
          <view class="units">
            <view
              v-for="o in COOK_OPTIONS"
              :key="o.value"
              class="unit"
              :class="{ 'unit--active': form.cookState === o.value }"
              @click="form.cookState = o.value"
            >
              {{ o.label }}
            </view>
          </view>
        </view>
        <text class="serving-hint">营养一律按每 100{{ form.unit === 'ml' ? 'ml' : 'g' }} 填；包装只印了每份的，先换算成每 100 再填。</text>
      </view>

      <!-- 常用份（可选）：填了以后，下面每个营养格右边显示只读的「每份」（ADR 0063） -->
      <view class="glass block">
        <text class="block__title">常用份（可选）</text>
        <view class="fld fld--col">
          <text class="fld__label">按什么算一份</text>
          <view class="units">
            <text
              v-for="u in PORTION_UNITS"
              :key="u"
              class="unit"
              :class="{ 'unit--on': !customUnit && form.portionLabel === u }"
              @click="pickUnit(u)"
            >{{ u }}</text>
            <text class="unit" :class="{ 'unit--on': customUnit }" @click="useCustomUnit">自定义</text>
          </view>
          <input
            v-if="customUnit"
            v-model="form.portionLabel"
            class="unit-in"
            maxlength="4"
            placeholder="自己填单位，如：只"
            placeholder-class="ph"
          />
        </view>
        <view class="fld">
          <text class="fld__label">一{{ unitShown }}多少（{{ form.unit === 'ml' ? 'ml' : 'g' }}）</text>
          <input v-model="form.portionGrams" type="digit" class="fld__input ft-num" placeholder="如：50" placeholder-class="ph" />
        </view>
      </view>

      <!-- 食物图片（选填）：一张方图当封面，在列表里认得出它 -->
      <view class="glass block">
        <text class="block__title">图片（选填）</text>
        <view class="pic">
          <view class="pic__box" role="button" aria-label="选图片" @click="pickImage">
            <image v-if="image.kind === 'remote' && image.url" class="pic__img" :src="image.url" mode="aspectFill" />
            <view v-else-if="image.kind === 'local'" class="pic__local">
              <image
                class="pic__local-img"
                :src="image.url"
                :style="localImageBox ? {
                  width: `${(localImageBox.width / image.crop.size) * 100}%`,
                  height: `${(localImageBox.height / image.crop.size) * 100}%`,
                  left: `${(-image.crop.x / image.crop.size) * 100}%`,
                  top: `${(-image.crop.y / image.crop.size) * 100}%`
                } : {}"
                mode="scaleToFill"
                @load="measureLocal"
              />
            </view>
            <FtIcon v-else-if="image.kind === 'none'" name="plus" :size="26" tone="muted" />
          </view>
          <view class="pic__ops">
            <text class="pic__hint">{{ image.kind === 'none' ? '拍一张或从相册选，裁成方图' : '列表里会显示这张小图' }}</text>
            <view class="pic__btns">
              <text class="pic__btn" :class="{ 'pic__btn--off': imageBusy }" @click="pickImage">
                {{ image.kind === 'none' ? '选图片' : '换一张' }}
              </text>
              <text v-if="image.kind !== 'none'" class="pic__btn pic__btn--danger" :class="{ 'pic__btn--off': imageBusy }" @click="removeImage">去掉</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 主组（必填） -->
      <view class="glass block">
        <text class="block__title">主要营养（每 100{{ form.unit === 'ml' ? 'ml' : 'g' }}，必填）</text>
        <view class="fld">
          <text class="fld__label">热量（大卡）<text v-if="isMissing('kcal')" class="fld__warn"> 待补</text><text v-else-if="isUncertain('kcal')" class="fld__warn"> 待核对</text></text>
          <input v-model="form.kcal" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
          <text v-if="perServing('kcal')" class="fld__per ft-num">{{ perServing('kcal') }}</text>
        </view>
        <view class="fld">
          <text class="fld__label">蛋白质（g）<text v-if="isMissing('protein')" class="fld__warn"> 待补</text><text v-else-if="isUncertain('protein')" class="fld__warn"> 待核对</text></text>
          <input v-model="form.protein" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
          <text v-if="perServing('protein')" class="fld__per ft-num">{{ perServing('protein') }}</text>
        </view>
        <view class="fld">
          <text class="fld__label">脂肪（g）<text v-if="isMissing('fat')" class="fld__warn"> 待补</text><text v-else-if="isUncertain('fat')" class="fld__warn"> 待核对</text></text>
          <input v-model="form.fat" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
          <text v-if="perServing('fat')" class="fld__per ft-num">{{ perServing('fat') }}</text>
        </view>
        <view class="fld">
          <text class="fld__label">碳水化合物（g）<text v-if="isMissing('carb')" class="fld__warn"> 待补</text><text v-else-if="isUncertain('carb')" class="fld__warn"> 待核对</text></text>
          <input v-model="form.carb" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
          <text v-if="perServing('carb')" class="fld__per ft-num">{{ perServing('carb') }}</text>
        </view>
        <view class="fld">
          <text class="fld__label">钠（mg）<text v-if="isMissing('sodium')" class="fld__warn"> 待补</text><text v-else-if="isUncertain('sodium')" class="fld__warn"> 待核对</text></text>
          <input v-model="form.sodium" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
          <text v-if="perServing('sodium')" class="fld__per ft-num">{{ perServing('sodium') }}</text>
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
            <text v-if="perServing(fd.key)" class="fld__per ft-num">{{ perServing(fd.key) }}</text>
          </view>
        </template>
      </view>

    </view>

    <AvatarCropper
      v-if="cropper"
      :src="cropper.src"
      :width="cropper.width"
      :height="cropper.height"
      shape="square"
      title="裁剪食物图片"
      :busy="imageBusy"
      @confirm="onCropConfirm"
      @cancel="closeCropper"
    />

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

.block__label--gap {
  padding-top: 24rpx;
}

.alias-input {
  width: 100%;
  height: 80rpx;
  box-sizing: border-box;
  padding: 0 24rpx;
  border-radius: $radius-md;
  background: $app-surface;
  font-size: $text-base;
  color: $app-ink;
}

.pic {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 16rpx 0 20rpx;
}

.pic__box {
  position: relative;
  flex: none;
  width: 160rpx;
  height: 160rpx;
  overflow: hidden;
  border-radius: $radius-md;
  background: $app-surface-2;
  display: flex;
  align-items: center;
  justify-content: center;
  @include press(dim);
}

.pic__img {
  width: 100%;
  height: 100%;
}

.pic__local {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.pic__local-img {
  position: absolute;
  max-width: none;
}

.pic__ops {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.pic__hint {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.pic__btns {
  display: flex;
  gap: 32rpx;
}

.pic__btn {
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $app-ink;
}

.pic__btn--danger {
  color: $app-danger;
}

.pic__btn--off {
  opacity: 0.4;
  pointer-events: none;
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

.serving-hint {
  display: block;
  padding: 4rpx 0 12rpx;
  font-size: $text-sm;
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

/* 常用份的单位（ADR 0066）：一排可点的单位，选中＝深字 ＋ 灰蓝胶囊底 */
.fld--col {
  flex-direction: column;
  align-items: stretch;
  gap: 16rpx;
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

.unit-in {
  height: 80rpx;
  padding: 0 24rpx;
  border-radius: $radius-md;
  background: $app-surface;
  font-size: $text-md;
  color: $app-ink;
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

/* 每份（只读，由每 100 和常用份推算，ADR 0063） */
.fld__per {
  flex-shrink: 0;
  min-width: 120rpx;
  margin-left: 16rpx;
  text-align: right;
  font-size: $text-xs;
  color: $app-ink-muted;
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
  @include hide-on-keyboard;
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
