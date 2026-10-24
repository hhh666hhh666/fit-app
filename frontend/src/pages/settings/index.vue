<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useDietStore } from '@/stores/diet'
import { ensureLogin } from '@/utils/guard'
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
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch {
    // request 层已统一提示
  } finally {
    saving.value = false
  }
}

function goBack() {
  uni.navigateBack()
}
</script>

<template>
  <view class="settings">
    <view class="settings__body">
      <view class="settings__head">
        <text class="settings__back" @click="goBack">‹</text>
        <text class="settings__title">设置</text>
        <view class="settings__head-space" />
      </view>

      <!-- 图片压缩 -->
      <view class="ft-card block">
        <text class="block__title">图片上传压缩</text>
        <text class="block__hint">餐照在本机压缩后再上传（存服务器）</text>
        <view v-for="q in QUALITIES" :key="q.key" class="qrow" @click="photoQuality = q.key">
          <view class="qrow__mid">
            <text class="qrow__label">{{ q.label }}</text>
            <text class="qrow__desc">{{ q.desc }}</text>
          </view>
          <view class="radio" :class="{ 'radio--on': photoQuality === q.key }">
            <view v-if="photoQuality === q.key" class="radio__dot" />
          </view>
        </view>
      </view>

      <!-- 饮食目标 -->
      <view class="ft-card block">
        <text class="block__title">饮食目标</text>
        <text class="block__hint">每日完成度按这些值算；留空 = 不设</text>
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

      <!-- 训练进行中动效（放最后：纯观感偏好，排在业务设置之后） -->
      <view class="ft-card block">
        <text class="block__title">训练进行中动效</text>
        <text class="block__hint">训练没结束时，底栏中间圆钮外圈的动效；仅本机，点选即生效</text>
        <view v-for="o in RESUME_FX_OPTIONS" :key="o.key" class="qrow" @click="pickResumeFx(o.key)">
          <view class="qrow__mid">
            <text class="qrow__label">{{ o.label }}</text>
            <text class="qrow__desc">{{ o.desc }}</text>
          </view>
          <view class="radio" :class="{ 'radio--on': resumeFx === o.key }">
            <view v-if="resumeFx === o.key" class="radio__dot" />
          </view>
        </view>
      </view>

      <view class="settings__pad" />
    </view>

    <view class="settings__bottom">
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
.settings__body {
  padding: 28rpx 32rpx 0;
}

.settings__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4rpx 0 20rpx;
}

.settings__back {
  font-size: 56rpx;
  line-height: 1;
  color: $app-ink;
  width: 64rpx;
}

.settings__title {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.settings__head-space {
  width: 64rpx;
}

.block {
  margin-bottom: 24rpx;
  padding: 26rpx 28rpx 14rpx;
}

.block__title {
  font-size: $text-base;
  font-weight: 600;
  color: $app-ink;
}

.block__hint {
  display: block;
  font-size: $text-xs;
  color: $app-ink-subtle;
  padding: 8rpx 0 10rpx;
}

.qrow {
  display: flex;
  align-items: center;
  padding: 22rpx 0;
  border-bottom: 1rpx solid $app-line-soft;
}

.qrow__mid {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.qrow__label {
  font-size: $text-md;
  color: $app-ink;
}

.qrow__desc {
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.radio {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  border: 2rpx solid $app-line;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.radio--on {
  border-color: $app-ink;
}

.radio__dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background: $app-ink;
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

.ph {
  color: $app-ink-subtle;
}

.settings__pad {
  height: 220rpx;
}

.settings__bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: $app-canvas;
  padding: 20rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
}
</style>
