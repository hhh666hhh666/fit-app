<script setup lang="ts">
import { getCurrentInstance, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

// ── 可缩放趋势图（ADR 0015）：横轴是**精确日期**、逐日绘制 ──
//    手势：单指按住滑动＝准星（显示该天数值、抬手消失）；双指捏合＝缩放；双指拖动＝平移。
//    边界：单屏最少 3 天、最多 365 天；横轴日期标签最多竖屏 6 个 / 横屏 12 个。
//
//    ⚠️ 坐标系：一律用**画布实测的 CSS 像素**（不沿用写死的 rpx 常量）。
//       触摸事件给的坐标也是 CSS 像素，两把尺子必须是同一把——否则准星会指错位置、
//       捏合缩放的锚点会漂。

const props = defineProps<{
  /** 逐日数据（按日期升序）；value 已按当前指标取好。 */
  points: { date: string; value: number }[]
  /** 目标值（画一条虚线）；null 表示不画。 */
  target?: number | null
  /** 画布高度（px）。 */
  height?: number
  /** y 轴是否从 0 起（默认 true）。体重这类值域很窄的序列要传 false，否则曲线会挤成一条直线。 */
  zeroBased?: boolean
}>()

const MIN_SPAN = 3
const MAX_SPAN = 365
/** 打开时的默认视野：最近多少天。 */
const DEFAULT_SPAN = 30

const PL = 40
const PR = 14
const PT = 16
const PB = 24

const instance = getCurrentInstance()
const canvasId = `trend_${Math.random().toString(36).slice(2, 8)}`

const W = ref(0)
const H = ref(0)
/** 视口两端在 points 里的下标（浮点，便于平滑缩放/平移）。 */
const vFrom = ref(0)
const vTo = ref(0)
/** 准星所在下标；null ＝ 不显示。 */
const cross = ref<number | null>(null)

function clamp(v: number, lo: number, hi: number) {
  return Math.min(hi, Math.max(lo, v))
}

const total = () => props.points.length

/** 视口跨度的硬上限：不超过数据总量，也不超过 365 天。 */
function maxSpan() {
  return Math.min(MAX_SPAN, Math.max(MIN_SPAN, total() - 1))
}

function dateAt(i: number): Date {
  const s = props.points[clamp(Math.round(i), 0, total() - 1)]?.date ?? ''
  return new Date(`${s.slice(0, 10)}T00:00:00`)
}

/** 把视口收进合法范围（跨度、左右边界）。 */
function clampView(from: number, to: number) {
  const n = total()
  if (n < 2) return
  let span = clamp(to - from, Math.min(MIN_SPAN, n - 1), maxSpan())
  const center = (from + to) / 2
  let f = center - span / 2
  let t = center + span / 2
  if (f < 0) {
    f = 0
    t = span
  }
  if (t > n - 1) {
    t = n - 1
    f = t - span
  }
  vFrom.value = Math.max(0, f)
  vTo.value = Math.min(n - 1, t)
}

/** 数据到达 / 换指标：默认看最近 DEFAULT_SPAN 天。 */
function resetView() {
  const n = total()
  if (n < 2) {
    vFrom.value = 0
    vTo.value = 0
    return
  }
  const span = Math.min(n - 1, DEFAULT_SPAN)
  vFrom.value = n - 1 - span
  vTo.value = n - 1
}

// ── 测量与绘制 ──

function measure(): Promise<void> {
  return new Promise((resolve) => {
    uni
      .createSelectorQuery()
      .in(instance?.proxy)
      .select(`#${canvasId}`)
      .boundingClientRect((r: any) => {
        if (r && r.width) {
          W.value = r.width
          H.value = r.height
        }
        resolve()
      })
      .exec()
  })
}

let raf = 0
/** 跟随屏幕刷新率重绘：连续手势下不会因为「一帧多次」而掉帧。 */
function scheduleDraw() {
  if (raf) return
  raf = requestAnimationFrame(() => {
    raf = 0
    draw()
  })
}

function draw() {
  const n = total()
  if (n < 2 || W.value <= 0) return
  const w = W.value
  const h = H.value

  const from = clamp(vFrom.value, 0, n - 1)
  const to = clamp(vTo.value, 0, n - 1)
  const i0 = Math.max(0, Math.floor(from))
  const i1 = Math.min(n - 1, Math.ceil(to))

  // y 轴只按**视口内**的数据定标：缩放后曲线始终撑满
  let dataMin = Infinity
  let dataMax = -Infinity
  for (let i = i0; i <= i1; i++) {
    const v = props.points[i].value
    dataMin = Math.min(dataMin, v)
    dataMax = Math.max(dataMax, v)
  }
  if (props.target != null) {
    dataMax = Math.max(dataMax, props.target)
    dataMin = Math.min(dataMin, props.target)
  }
  if (dataMin === Infinity) {
    dataMin = 0
    dataMax = 1
  }
  let lo = 0
  let hi = 1
  if (props.zeroBased === false) {
    // 值域窄的序列（如体重）：上下各留 20% 余量，别从 0 起
    const pad = (dataMax - dataMin) * 0.2 || 1
    lo = dataMin - pad
    hi = dataMax + pad
  } else {
    hi = dataMax * 1.15 || 1
  }

  const x = (i: number) => PL + ((i - from) / Math.max(1e-6, to - from)) * (w - PL - PR)
  const y = (v: number) => PT + (1 - (v - lo) / Math.max(1e-6, hi - lo)) * (h - PT - PB)

  const ctx: any = uni.createCanvasContext(canvasId, instance?.proxy)

  // 网格 + 左侧刻度
  ctx.setFontSize(10)
  ctx.setFillStyle('#9aa0a8')
  for (const t of [hi, (lo + hi) / 2, lo]) {
    const gy = y(t)
    ctx.beginPath()
    ctx.setLineDash([4, 5])
    ctx.setStrokeStyle('#d8dad6')
    ctx.moveTo(PL, gy)
    ctx.lineTo(w - PR, gy)
    ctx.stroke()
    ctx.fillText(String(Math.round(t)), 6, gy + 3)
  }

  // 目标线
  const target = props.target
  if (target != null && target > lo && target < hi) {
    const ty = y(target)
    ctx.beginPath()
    ctx.setLineDash([6, 6])
    ctx.setStrokeStyle('#9aa0a8')
    ctx.moveTo(PL, ty)
    ctx.lineTo(w - PR, ty)
    ctx.stroke()
  }
  ctx.setLineDash([])

  // 折线（只画视口内的点；缩到一年时点很密，靠线宽与抗锯齿撑住可读性）
  ctx.beginPath()
  ctx.setStrokeStyle('#17191d')
  ctx.setLineWidth(1.5)
  for (let i = i0; i <= i1; i++) {
    const px = x(i)
    const py = y(props.points[i].value)
    if (i === i0) ctx.moveTo(px, py)
    else ctx.lineTo(px, py)
  }
  ctx.stroke()

  // x 轴日期标签：数量按屏幕方向与可用宽度取上限，避免挤成一团
  const info = uni.getSystemInfoSync()
  const maxLabels = info.windowWidth > info.windowHeight ? 12 : 6
  const count = Math.max(2, Math.min(maxLabels, Math.floor((w - PL - PR) / 64)))
  ctx.setFillStyle('#9aa0a8')
  ctx.setFontSize(10)
  for (let k = 0; k < count; k++) {
    const i = from + ((to - from) * k) / (count - 1)
    const d = dateAt(i)
    const label = `${d.getMonth() + 1}.${d.getDate()}`
    const tx = clamp(x(i) - 14, 2, w - 32)
    ctx.fillText(label, tx, h - 8)
  }

  // 准星：竖线 + 实心点 + 气泡（日期 · 数值）
  const ci = cross.value
  if (ci != null && ci >= i0 && ci <= i1) {
    const cx = x(ci)
    const cv = props.points[ci].value
    const cy = y(cv)

    ctx.beginPath()
    ctx.setLineDash([3, 4])
    ctx.setStrokeStyle('#9aa0a8')
    ctx.moveTo(cx, PT)
    ctx.lineTo(cx, h - PB)
    ctx.stroke()
    ctx.setLineDash([])

    ctx.beginPath()
    ctx.arc(cx, cy, 4.5, 0, 2 * Math.PI)
    ctx.setFillStyle('#17191d')
    ctx.fill()

    const d = dateAt(ci)
    const text = `${d.getMonth() + 1}/${d.getDate()} · ${Math.round(cv)}`
    ctx.setFontSize(11)
    const tw = text.length * 6.2 + 16
    const bx = clamp(cx - tw / 2, 2, w - tw - 2)
    const by = clamp(cy - 30, PT, h - PB - 22)
    ctx.setFillStyle('#17191d')
    ctx.fillRect(bx, by, tw, 22)
    ctx.setFillStyle('#ffffff')
    ctx.fillText(text, bx + 8, by + 15)
  }

  ctx.draw()
}

// ── 手势 ──

function touchX(t: any): number {
  // uni-h5 的 canvas 会把每个触点补上画布本地 x/y；兜底用 clientX
  return typeof t?.x === 'number' ? t.x : (t?.clientX ?? 0)
}

function xToIdx(px: number): number {
  const w = W.value
  const t = (px - PL) / Math.max(1, w - PL - PR)
  const i = vFrom.value + t * (vTo.value - vFrom.value)
  return clamp(Math.round(i), 0, total() - 1)
}

let pinchDist = 0
let pinchFrom = 0
let pinchTo = 0
let pinchCenterX = 0

function onTouchStart(e: any) {
  const ts = e.touches || []
  if (ts.length >= 2) {
    pinchDist = Math.hypot(touchX(ts[0]) - touchX(ts[1]), ts[0].y - ts[1].y) || 1
    pinchFrom = vFrom.value
    pinchTo = vTo.value
    pinchCenterX = (touchX(ts[0]) + touchX(ts[1])) / 2
    cross.value = null
    return
  }
  if (ts.length === 1) {
    cross.value = xToIdx(touchX(ts[0]))
    scheduleDraw()
  }
}

function onTouchMove(e: any) {
  const ts = e.touches || []
  if (ts.length >= 2 && pinchDist > 0) {
    const d = Math.hypot(touchX(ts[0]) - touchX(ts[1]), ts[0].y - ts[1].y) || 1
    const startSpan = pinchTo - pinchFrom
    const span = clamp(startSpan * (pinchDist / d), Math.min(MIN_SPAN, total() - 1), maxSpan())
    const center = (pinchFrom + pinchTo) / 2
    // 双指中点的位移 → 视口平移（「一像素代表几天」随跨度自然变化）
    const pxPerIdx = (W.value - PL - PR) / Math.max(1e-6, startSpan)
    const shift = (pinchCenterX - (touchX(ts[0]) + touchX(ts[1])) / 2) / pxPerIdx
    const mid = center + shift
    clampView(mid - span / 2, mid + span / 2)
    scheduleDraw()
    return
  }
  if (ts.length === 1) {
    cross.value = xToIdx(touchX(ts[0]))
    scheduleDraw()
  }
}

function onTouchEnd() {
  pinchDist = 0
  // 抬手淡出准星
  cross.value = null
  scheduleDraw()
}

// ── 生命周期 ──

onMounted(async () => {
  await measure()
  resetView()
  scheduleDraw()
})

onUnmounted(() => {
  if (raf) cancelAnimationFrame(raf)
})

watch(
  () => props.points,
  () => {
    nextTick(async () => {
      await measure()
      resetView()
      scheduleDraw()
    })
  }
)

watch(
  () => props.target,
  () => scheduleDraw()
)
</script>

<template>
  <view class="tc">
    <canvas
      :id="canvasId"
      :canvas-id="canvasId"
      class="tc__canvas"
      :style="{ height: (height ?? 180) + 'px' }"
      @touchstart="onTouchStart"
      @touchmove.stop.prevent="onTouchMove"
      @touchend="onTouchEnd"
      @touchcancel="onTouchEnd"
    />
    <text v-if="points.length < 2" class="tc__empty">还没有数据</text>
  </view>
</template>

<style lang="scss" scoped>
.tc {
  position: relative;
  width: 100%;
}

.tc__canvas {
  width: 100%;
  display: block;
  touch-action: none;
}

.tc__empty {
  position: absolute;
  left: 0;
  right: 0;
  top: 60rpx;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}
</style>
