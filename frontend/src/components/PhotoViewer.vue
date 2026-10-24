<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import FtIcon from '@/components/FtIcon.vue'

// ── 全屏看图浮层（ADR 0014）：记录弹层与相册共用 ──
//    手势分工：单指左右滑＝翻页、单指上下滑＝关闭、双指捏合＝缩放、双击＝放大/还原。
//    缩放态（scale>1）下单指拖动＝平移图片（此时不翻页）。
//    统一以「地址数组 + 当前索引」为入参：单张场景传长度 1 的数组即可。

const props = defineProps<{
  /** 图片地址（objectURL）数组；元素可为 null ＝ 尚未加载，由调用方按索引补齐。 */
  urls: (string | null)[]
  /** 初始看第几张。 */
  index?: number
  /** 顶部说明（如「2026-10-13 · 早餐」）。 */
  caption?: string
}>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'change', index: number): void }>()

const MIN_SCALE = 1
const MAX_SCALE = 4
/** 翻页与关闭的滑动阈值（px）。 */
const SWIPE_X = 60
const SWIPE_Y = 90

const cur = ref(props.index ?? 0)
const scale = ref(1)
const tx = ref(0)
const ty = ref(0)

watch(
  () => props.index,
  (i) => {
    if (i != null) {
      cur.value = i
      reset()
    }
  }
)

const current = computed(() => props.urls[cur.value] ?? null)
const total = computed(() => props.urls.length)

// 翻页时通知调用方按需加载该张的原图（列表态只预先拿到缩略图）
watch(cur, (i) => emit('change', i))

function reset() {
  scale.value = 1
  tx.value = 0
  ty.value = 0
}

function next() {
  if (cur.value < total.value - 1) {
    cur.value++
    reset()
  }
}

function prev() {
  if (cur.value > 0) {
    cur.value--
    reset()
  }
}

// ── 手势 ──

let startX = 0
let startY = 0
let lastX = 0
let lastY = 0
let pinchStartDist = 0
let pinchStartScale = 1
let pinching = false

function dist(a: any, b: any) {
  return Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY)
}

function clamp(v: number, lo: number, hi: number) {
  return Math.min(hi, Math.max(lo, v))
}

function onTouchStart(e: any) {
  const ts = e.touches || []
  if (ts.length >= 2) {
    pinching = true
    pinchStartDist = dist(ts[0], ts[1])
    pinchStartScale = scale.value
    return
  }
  pinching = false
  const t = ts[0]
  if (!t) return
  startX = t.clientX
  startY = t.clientY
  lastX = t.clientX
  lastY = t.clientY
}

function onTouchMove(e: any) {
  const ts = e.touches || []
  if (ts.length >= 2 && pinchStartDist > 0) {
    pinching = true
    const ratio = dist(ts[0], ts[1]) / pinchStartDist
    scale.value = clamp(pinchStartScale * ratio, MIN_SCALE, MAX_SCALE)
    return
  }
  // 缩放态：单指拖动＝平移图片
  if (scale.value > 1 && ts.length === 1) {
    tx.value += ts[0].clientX - lastX
    ty.value += ts[0].clientY - lastY
    lastX = ts[0].clientX
    lastY = ts[0].clientY
  }
}

function onTouchEnd(e: any) {
  if (pinching) {
    pinching = false
    pinchStartDist = 0
    // 收手时几乎回到原大小 → 归位，免得留一个歪着的半缩放态
    if (scale.value <= 1.05) reset()
    return
  }
  if (scale.value > 1) return
  const t = (e.changedTouches || [])[0]
  if (!t) return
  const dx = t.clientX - startX
  const dy = t.clientY - startY
  // 上下滑 → 关闭（竖向位移更大时才算）
  if (Math.abs(dy) > SWIPE_Y && Math.abs(dy) > Math.abs(dx)) {
    emit('close')
    return
  }
  // 左右滑 → 翻页
  if (Math.abs(dx) > SWIPE_X && Math.abs(dx) > Math.abs(dy)) {
    if (dx < 0) next()
    else prev()
  }
}

/** 双击放大 / 还原（轻点不做任何事，避免误关）。 */
let lastTap = 0
function onTap() {
  const now = Date.now()
  if (now - lastTap < 300) {
    lastTap = 0
    if (scale.value > 1) reset()
    else scale.value = 2.5
    return
  }
  lastTap = now
}
</script>

<template>
  <view
    class="pv"
    @click="onTap"
    @touchstart="onTouchStart"
    @touchmove.stop.prevent="onTouchMove"
    @touchend="onTouchEnd"
  >
    <image
      v-if="current"
      class="pv__img"
      :src="current"
      mode="aspectFit"
      :style="{ transform: `translate(${tx}px, ${ty}px) scale(${scale})` }"
    />
    <text v-else class="pv__loading">加载中…</text>

    <view class="pv__close" role="button" aria-label="关闭" @click.stop="emit('close')">
      <FtIcon name="close" :size="20" :stroke="2" tone="inverse" />
    </view>

    <!-- 底部：说明胶囊 ＋ 调用方附加操作（如相册的「删除」） -->
    <view class="pv__foot" @click.stop>
      <view class="pv__bar">
        <text class="pv__cap">{{ caption }}</text>
        <text v-if="total > 1" class="pv__count ft-num">{{ cur + 1 }} / {{ total }}</text>
      </view>
      <slot />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.pv {
  position: fixed;
  inset: 0;
  background: $viewer-bg;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.pv__img {
  width: 100%;
  height: 100%;
  transition: transform 0.12s ease-out;
}

.pv__loading {
  font-size: $text-sm;
  color: $viewer-ink-muted;
}

.pv__close {
  position: absolute;
  right: 32rpx;
  top: calc(24rpx + env(safe-area-inset-top));
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: $viewer-chip;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pv__close:active {
  opacity: 0.6;
}

.pv__foot {
  position: absolute;
  left: $tabbar-side;
  right: $tabbar-side;
  bottom: calc(#{$tabbar-gap} + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
}

.pv__bar {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 20rpx;
  height: 80rpx;
  padding: 0 32rpx;
  box-sizing: border-box;
  border-radius: $radius-pill;
  background: $viewer-chip;
}

.pv__cap {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-base;
  color: $viewer-ink-muted;
}

.pv__count {
  flex-shrink: 0;
  font-size: $text-sm;
  color: $viewer-ink-subtle;
}

@media (prefers-reduced-motion: reduce) {
  .pv__img {
    transition: none;
  }
}
</style>
