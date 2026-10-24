<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useDietStore } from '@/stores/diet'
import { ensureLogin } from '@/utils/guard'
import FtButton from '@/components/FtButton.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import { getResumeFx, RESUME_FX_OPTIONS, setResumeFx, type ResumeFx } from '@/utils/resume-fx'

// ── 设置：图片上传压缩（四档）＋ 饮食目标（饮水步进不在这里——在饮食页就地改）
//    ＋ 训练进行中动效（本机偏好，点选即存，不走下面的「保存」）──

const resumeFx = ref<ResumeFx>(getResumeFx())

function pickResumeFx(fx: ResumeFx) {
  resumeFx.value = fx
  setResumeFx(fx)
}

const diet = useDietStore()
const saving = ref(false)

type Quality = 'lossless' | 'high' | 'medium' | 'low'

const QUALITIES: { key: Quality; label: string; desc: string }[] = [
  { key: 'lossless', label: '无损', desc: '原图直传（最大、最清晰）' },
  { key: 'high', label: '高', desc: '长边 ≤1600px · JPEG 0.85' },
  { key: 'medium', label: '中', desc: '长边 ≤1600px · JPEG 0.70（默认）' },
  { key: 'low', label: '低', desc: '长边 ≤1600px · JPEG 0.50' }
]

const photoQuality = ref<Quality>('medium')
const waterStepMl = ref(250)
const targets = ref({ kcal: '', protein: '', carb: '', fat: '', water: '' })

onShow(() => {
  if (!ensureLogin()) return
  void load()
})

async function load() {
  await diet.load()
  const s = diet.settings
  if (!s) return
  photoQuality.value = s.photoQuality
  waterStepMl.value = s.waterStepMl
  const toStr = (v: unknown) => (v == null ? '' : String(v))
  targets.value = {
    kcal: toStr(s.targetKcal),
    protein: toStr(s.targetProtein),
    carb: toStr(s.targetCarb),
    fat: toStr(s.targetFat),
    water: toStr(s.targetWater)
  }
}

function badTarget(t: string): boolean {
  const v = t.trim()
  if (!v) return false
  const n = Number(v)
  return !Number.isFinite(n) || n < 0
}

function numOrNull(t: string): number | null {
  const v = t.trim()
  if (!v) return null
  return Number(v)
}

async function save() {
  if (saving.value) return
  const keys = ['kcal', 'protein', 'carb', 'fat', 'water'] as const
  if (keys.some((k) => badTarget(targets.value[k]))) {
    uni.showToast({ title: '目标数值不正确', icon: 'none' })
    return
  }
  const wv = targets.value.water.trim()
  if (wv && !/^\d+$/.test(wv)) {
    uni.showToast({ title: '饮水目标要填整数 ml', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await diet.save({
      photoQuality: photoQuality.value,
      waterStepMl: waterStepMl.value,
      targetKcal: numOrNull(targets.value.kcal),
      targetProtein: numOrNull(targets.value.protein),
      targetCarb: numOrNull(targets.value.carb),
      targetFat: numOrNull(targets.value.fat),
      targetWater: wv ? Number(wv) : null
    })
    uni.showToast({ title: '已保存', icon: 'none' })
  } catch {
    // request 层已统一提示
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <view class="settings">
    <view class="settings__body">
      <FtPageHeader title="设置" back />

      <!-- 图片压缩 -->
      <view class="glass block">
        <text class="card-title">图片上传压缩</text>
        <text class="block__hint">餐照在本机压缩后再上传（存服务器）</text>
        <view
          v-for="q in QUALITIES"
          :key="q.key"
          class="opt"
          role="radio"
          :aria-checked="photoQuality === q.key"
          @click="photoQuality = q.key"
        >
          <view class="opt__mid">
            <text class="opt__label">{{ q.label }}</text>
            <text class="opt__desc">{{ q.desc }}</text>
          </view>
          <view class="radio" :class="{ 'radio--on': photoQuality === q.key }">
            <FtIcon v-if="photoQuality === q.key" name="check" :size="13" :stroke="2.4" tone="inverse" />
          </view>
        </view>
      </view>

      <!-- 饮食目标 -->
      <view class="glass block">
        <text class="card-title">饮食目标</text>
        <text class="block__hint">每日完成度按这些值算；留空＝不设</text>
        <view class="fld">
          <text class="fld__label">热量（大卡）</text>
          <input v-model="targets.kcal" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
        <view class="fld">
          <text class="fld__label">蛋白质（g）</text>
          <input v-model="targets.protein" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
        <view class="fld">
          <text class="fld__label">碳水（g）</text>
          <input v-model="targets.carb" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
        <view class="fld">
          <text class="fld__label">脂肪（g）</text>
          <input v-model="targets.fat" type="digit" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
        <view class="fld">
          <text class="fld__label">饮水（ml）</text>
          <input v-model="targets.water" type="number" class="fld__input ft-num" placeholder="–" placeholder-class="ph" />
        </view>
      </view>

      <!-- 训练进行中动效（放最后：纯观感偏好，排在业务设置之后；点选即存，不走下面的「保存」） -->
      <view class="glass block">
        <text class="card-title">训练进行中动效</text>
        <text class="block__hint">训练没结束时，底栏中间圆钮外圈的动效；仅本机，点选即生效</text>
        <view
          v-for="o in RESUME_FX_OPTIONS"
          :key="o.key"
          class="opt"
          role="radio"
          :aria-checked="resumeFx === o.key"
          @click="pickResumeFx(o.key)"
        >
          <view class="opt__mid">
            <text class="opt__label">{{ o.label }}</text>
            <text class="opt__desc">{{ o.desc }}</text>
          </view>
          <view class="radio" :class="{ 'radio--on': resumeFx === o.key }">
            <FtIcon v-if="resumeFx === o.key" name="check" :size="13" :stroke="2.4" tone="inverse" />
          </view>
        </view>
      </view>
    </view>

    <!-- 保存：贴底悬浮的主按钮，与编辑食物页同一位置 -->
    <view class="settings__bottom">
      <FtButton variant="primary" block :disabled="saving" class="settings__save" @click="save">保存</FtButton>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.settings__body {
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

.card-title {
  @include card-title;
}

.block__hint {
  display: block;
  padding: 4rpx 0 12rpx;
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

/* ── 单选行 ── */
.opt {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 22rpx 0;
  border-top: 1rpx solid $app-line-soft;
  @include press(dim);
}

.opt__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.opt__label {
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.opt__desc {
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* 选中：深色实心圈 ＋ 白勾（与动作库多选同一个勾） */
.radio {
  width: 40rpx;
  height: 40rpx;
  flex-shrink: 0;
  box-sizing: border-box;
  border-radius: 50%;
  border: 2rpx solid $app-line-dash;
  display: flex;
  align-items: center;
  justify-content: center;
}

.radio--on {
  background: $app-ink;
  border-color: $app-ink;
}

/* ── 数值行 ── */
.fld {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0;
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

.ph {
  color: $app-ink-subtle;
  font-weight: $weight-regular;
}

.settings__bottom {
  position: fixed;
  left: $tabbar-side;
  right: $tabbar-side;
  bottom: calc(#{$tabbar-gap} + env(safe-area-inset-bottom));
}

.settings__bottom .settings__save {
  height: 104rpx;
  font-size: 30rpx;
}
</style>
