<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import {
  createFood,
  getFood,
  updateFood,
  type FoodItem,
  type FoodPayload
} from '@/api/nutrition'
import { OPTIONAL_GROUPS } from '@/utils/food-fields'
import { ensureLogin } from '@/utils/guard'

// ── 食物新增 / 编辑 / 复制为自定义（营养 45 项：主组必填 ＋ 五类折叠；另有扩展字段 GI，常显）──

const editId = ref<number | null>(null)
/** 「复制为自定义」的来源（预置食物 id）；有值时表单预填但保存走新建。 */
const copyFromId = ref<number | null>(null)
const isEdit = computed(() => editId.value != null)
const saving = ref(false)
const pageTitle = computed(() =>
  isEdit.value ? '编辑食物' : copyFromId.value != null ? '复制为自定义' : '新增食物'
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

function fillForm(f: FoodItem) {
  const toStr = (v: unknown) => (v == null ? '' : String(v))
  form.name = f.name
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

function goBack() {
  uni.navigateBack()
}
</script>

<template>
  <view class="fe">
    <view class="fe__body">
      <view class="fe__head">
        <text class="fe__back" @click="goBack">‹</text>
        <text class="fe__title">{{ pageTitle }}</text>
        <view class="fe__head-space" />
      </view>

      <!-- 名称 + 单位 -->
      <view class="ft-card block">
        <text class="block__label">食物名称</text>
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
      <view class="ft-card block">
        <text class="block__title">主要营养（必填）</text>
        <view class="fld">
          <text class="fld__label">热量（大卡）</text>
          <input v-model="form.kcal" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
        <view class="fld">
          <text class="fld__label">蛋白质（g）</text>
          <input v-model="form.protein" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
        <view class="fld">
          <text class="fld__label">脂肪（g）</text>
          <input v-model="form.fat" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
        <view class="fld">
          <text class="fld__label">碳水化合物（g）</text>
          <input v-model="form.carb" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
        <view class="fld">
          <text class="fld__label">钠（mg）</text>
          <input v-model="form.sodium" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
      </view>

      <!-- 升糖指数（扩展字段，常显） -->
      <view class="ft-card block">
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
      <view class="ft-card block">
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
      <view v-for="g in OPTIONAL_GROUPS" :key="g.title" class="ft-card block">
        <view class="group-head" @click="toggleGroup(g.title)">
          <text class="block__title">{{ g.title }}（选填）</text>
          <text class="group-head__arrow">{{ openGroups[g.title] ? '▾' : '▸' }}</text>
        </view>
        <template v-if="openGroups[g.title]">
          <view v-for="fd in g.fields" :key="fd.key" class="fld">
            <text class="fld__label">{{ fd.label }}</text>
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

      <view class="fe__pad" />
    </view>

    <view class="fe__bottom">
      <view
        class="ft-btn ft-btn--primary ft-btn--block"
        :class="{ 'ft-btn--disabled': saving }"
        @click="save"
      >
        保存
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.fe__body {
  padding: 28rpx 32rpx 0;
}

.fe__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4rpx 0 20rpx;
}

.fe__back {
  font-size: 56rpx;
  line-height: 1;
  color: $app-ink;
  width: 64rpx;
}

.fe__title {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.fe__head-space {
  width: 64rpx;
}

.block {
  margin-bottom: 24rpx;
  padding: 26rpx 28rpx 12rpx;
}

.block__label {
  display: block;
  font-size: 22rpx;
  color: $app-ink-subtle;
  padding-bottom: 10rpx;
}

.block__title {
  font-size: $text-base;
  font-weight: 600;
  color: $app-ink;
  padding-bottom: 8rpx;
}

.name-input {
  width: 100%;
  font-size: 40rpx;
  font-weight: 600;
  color: $app-ink;
  border-bottom: 2rpx solid $app-ink;
  padding: 12rpx 0;
  box-sizing: border-box;
}

.ph {
  color: $app-ink-subtle;
  font-weight: 400;
}

.unit-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 0 14rpx;
}

.units {
  display: flex;
  gap: 14rpx;
}

.unit {
  font-size: $text-sm;
  color: $app-ink-muted;
  border: 1rpx solid $app-line;
  border-radius: 30rpx;
  padding: 8rpx 24rpx;
  background: $app-canvas;
}

.unit--active {
  color: #ffffff;
  background: $app-ink;
  border-color: $app-ink;
  font-weight: 600;
}

.fld {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0;
  border-bottom: 1rpx solid $app-line-soft;
}

.fld__label {
  font-size: $text-sm;
  color: $app-ink-muted;
  flex-shrink: 0;
}

.fld__input {
  text-align: right;
  font-size: $text-md;
  color: $app-ink;
  flex: 1;
  margin-left: 20rpx;
}

.group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.group-head__arrow {
  font-size: $text-md;
  color: $app-ink-muted;
}

.fe__pad {
  height: 220rpx;
}

.fe__bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: $app-canvas;
  padding: 20rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
}
</style>
