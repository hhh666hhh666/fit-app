<!--
  头像裁剪框（ADR 0051）：全屏深色层，中间一个固定的正方形框（按圆形预览），图在下面拖动和缩放。
  手机上单指拖动、双指捏合缩放；电脑上鼠标拖动、滚轮缩放。图始终盖满框，不会露出空白。

  坐标一律用「摆正后的原图像素」：显示的可能是本机原图，也可能是服务端的预览图（长边 1280），
  只要宽高比一致就行——组件只按 width / height 换算，不看显示图本身的像素。
-->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import FtIcon from '@/components/FtIcon.vue'
import { useBackClose } from '@/utils/backStack'
import type { CropBox } from '@/utils/photo'

const props = defineProps<{
  /** 显示用的图；null＝还在加载 */
  src: string | null
  /** 摆正后的原图宽高（px） */
  width: number
  height: number
  /** 初始裁剪框；不给就居中取短边 */
  crop?: CropBox | null
  title?: string
  confirmText?: string
  busy?: boolean
}>()
const emit = defineEmits<{ confirm: [crop: CropBox]; cancel: [] }>()

useBackClose(() => emit('cancel'))

/** 最多放大到「框里只剩短边的 1/8」。 */
const MAX_ZOOM = 8

const vw = ref(window.innerWidth)
const vh = ref(window.innerHeight)
const frame = computed(() => {
  const size = Math.round(Math.min(vw.value - 48, vh.value - 280, 420))
  return { size, left: (vw.value - size) / 2, top: (vh.value - size) / 2 - 24 }
})

/** 屏幕像素 / 原图像素 */
const scale = ref(1)
/** 图左上角在屏幕上的位置 */
const tx = ref(0)
const ty = ref(0)

const minScale = computed(() => frame.value.size / Math.min(props.width, props.height))

function clamp() {
  const f = frame.value
  scale.value = Math.min(Math.max(scale.value, minScale.value), minScale.value * MAX_ZOOM)
  const w = props.width * scale.value
  const h = props.height * scale.value
  tx.value = Math.min(f.left, Math.max(f.left + f.size - w, tx.value))
  ty.value = Math.min(f.top, Math.max(f.top + f.size - h, ty.value))
}

function reset() {
  const f = frame.value
  const c = props.crop
  if (c && c.size > 0) {
    scale.value = f.size / c.size
    tx.value = f.left - c.x * scale.value
    ty.value = f.top - c.y * scale.value
  } else {
    scale.value = minScale.value
    tx.value = f.left + (f.size - props.width * scale.value) / 2
    ty.value = f.top + (f.size - props.height * scale.value) / 2
  }
  clamp()
}

/** 以屏幕上的 (px, py) 为不动点缩放 */
function zoomAt(px: number, py: number, next: number) {
  const ix = (px - tx.value) / scale.value
  const iy = (py - ty.value) / scale.value
  scale.value = Math.min(Math.max(next, minScale.value), minScale.value * MAX_ZOOM)
  tx.value = px - ix * scale.value
  ty.value = py - iy * scale.value
  clamp()
}

// ── 触摸：单指拖、双指捏 ──
let start: { x: number; y: number; tx: number; ty: number; scale: number; dist: number; ix: number; iy: number } | null =
  null

function mid(t: TouchList) {
  return { x: (t[0].clientX + t[1].clientX) / 2, y: (t[0].clientY + t[1].clientY) / 2 }
}

function onTouchStart(e: TouchEvent) {
  const t = e.touches
  if (t.length >= 2) {
    const m = mid(t)
    start = {
      x: m.x,
      y: m.y,
      tx: tx.value,
      ty: ty.value,
      scale: scale.value,
      dist: Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY) || 1,
      ix: (m.x - tx.value) / scale.value,
      iy: (m.y - ty.value) / scale.value
    }
  } else if (t.length === 1) {
    start = { x: t[0].clientX, y: t[0].clientY, tx: tx.value, ty: ty.value, scale: scale.value, dist: 0, ix: 0, iy: 0 }
  }
}

function onTouchMove(e: TouchEvent) {
  if (!start) return
  const t = e.touches
  if (t.length >= 2 && start.dist > 0) {
    const m = mid(t)
    const d = Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY)
    scale.value = Math.min(Math.max(start.scale * (d / start.dist), minScale.value), minScale.value * MAX_ZOOM)
    tx.value = m.x - start.ix * scale.value
    ty.value = m.y - start.iy * scale.value
  } else if (t.length === 1 && start.dist === 0) {
    tx.value = start.tx + (t[0].clientX - start.x)
    ty.value = start.ty + (t[0].clientY - start.y)
  }
  clamp()
}

function onTouchEnd(e: TouchEvent) {
  // 两指松开一指时，从剩下那根手指重新开始拖，不跳
  start = null
  if (e.touches.length) onTouchStart(e)
}

// ── 鼠标：拖动 ＋ 滚轮 ──
let mouse: { x: number; y: number; tx: number; ty: number } | null = null

function onMouseDown(e: MouseEvent) {
  mouse = { x: e.clientX, y: e.clientY, tx: tx.value, ty: ty.value }
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

function onMouseMove(e: MouseEvent) {
  if (!mouse) return
  tx.value = mouse.tx + (e.clientX - mouse.x)
  ty.value = mouse.ty + (e.clientY - mouse.y)
  clamp()
}

function onMouseUp() {
  mouse = null
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
}

function onWheel(e: WheelEvent) {
  e.preventDefault()
  zoomAt(e.clientX, e.clientY, scale.value * Math.exp(-e.deltaY * 0.0015))
}

const rootRef = ref<unknown>(null)

function rootEl(): HTMLElement | null {
  const raw = rootRef.value as { $el?: HTMLElement } | HTMLElement | null
  return ((raw as { $el?: HTMLElement } | null)?.$el ?? raw) as HTMLElement | null
}

function onResize() {
  vw.value = window.innerWidth
  vh.value = window.innerHeight
  reset()
}

onMounted(() => {
  reset()
  // 滚轮要能 preventDefault，只能自己挂非 passive 的监听
  rootEl()?.addEventListener('wheel', onWheel, { passive: false })
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  rootEl()?.removeEventListener('wheel', onWheel)
  window.removeEventListener('resize', onResize)
  onMouseUp()
})

const imgStyle = computed(() => ({
  width: `${props.width * scale.value}px`,
  height: `${props.height * scale.value}px`,
  transform: `translate(${tx.value}px, ${ty.value}px)`
}))

const frameStyle = computed(() => ({
  width: `${frame.value.size}px`,
  height: `${frame.value.size}px`,
  left: `${frame.value.left}px`,
  top: `${frame.value.top}px`
}))

function confirm() {
  if (props.busy || !props.src) return
  const f = frame.value
  const size = Math.round(f.size / scale.value)
  const x = Math.round((f.left - tx.value) / scale.value)
  const y = Math.round((f.top - ty.value) / scale.value)
  const s = Math.max(1, Math.min(size, props.width, props.height))
  emit('confirm', {
    x: Math.max(0, Math.min(x, props.width - s)),
    y: Math.max(0, Math.min(y, props.height - s)),
    size: s
  })
}
</script>

<template>
  <view
    ref="rootRef"
    class="crop ft-overlay ft-overlay--fade"
    @touchstart="onTouchStart"
    @touchmove.stop.prevent="onTouchMove"
    @touchend="onTouchEnd"
    @mousedown.prevent="onMouseDown"
  >
    <image v-if="src" class="crop__img" :src="src" mode="scaleToFill" :style="imgStyle" />
    <text v-else class="crop__loading">加载中…</text>

    <!-- 框：外面压暗，圆形是头像会显示的样子，方框是实际存下的范围 -->
    <view class="crop__frame" :style="frameStyle">
      <view class="crop__circle" />
    </view>

    <view class="crop__top" @touchstart.stop @mousedown.stop>
      <text class="crop__title">{{ title || '裁剪头像' }}</text>
      <view class="crop__close" role="button" aria-label="取消" @click="emit('cancel')">
        <FtIcon name="close" :size="20" :stroke="2" tone="inverse" />
      </view>
    </view>

    <view class="crop__foot" @touchstart.stop @mousedown.stop>
      <text class="crop__hint">拖动调整位置，双指或滚轮缩放</text>
      <view class="crop__actions">
        <slot />
        <view class="crop__ok" :class="{ 'crop__ok--off': busy || !src }" role="button" @click="confirm">
          {{ confirmText || '使用' }}
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.crop {
  position: fixed;
  inset: 0;
  z-index: 200;
  overflow: hidden;
  background: $viewer-bg;
  touch-action: none;
  user-select: none;
  cursor: grab;
}

.crop__img {
  position: absolute;
  left: 0;
  top: 0;
  transform-origin: 0 0;
  pointer-events: none;
}

.crop__loading {
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  text-align: center;
  font-size: $text-sm;
  color: $viewer-ink-muted;
}

.crop__frame {
  position: absolute;
  pointer-events: none;
  outline: 1px solid $viewer-edge;
}

.crop__circle {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  border: 2px solid $viewer-ink;
  box-sizing: border-box;
  /* 圆外压暗：一圈超大阴影盖住整屏，圆里留空 */
  box-shadow: 0 0 0 9999px $viewer-scrim;
}

.crop__top {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  padding: calc(24rpx + env(safe-area-inset-top)) 32rpx 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: default;
}

.crop__title {
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $viewer-ink;
}

.crop__close {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: $viewer-chip;
  display: flex;
  align-items: center;
  justify-content: center;
  @include press(dim);
}

.crop__foot {
  position: absolute;
  left: $tabbar-side;
  right: $tabbar-side;
  bottom: calc(#{$tabbar-gap} + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
  cursor: default;
}

.crop__hint {
  font-size: $text-sm;
  color: $viewer-ink-muted;
}

.crop__actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
}

.crop__ok {
  height: 88rpx;
  padding: 0 64rpx;
  border-radius: $radius-pill;
  background: $viewer-ink;
  color: $viewer-on-ink;
  font-size: $text-md;
  font-weight: $weight-bold;
  display: flex;
  align-items: center;
  @include press(dim);
}

.crop__ok--off {
  opacity: 0.4;
}
</style>
