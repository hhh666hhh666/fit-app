<!--
  沿父元素圆角边框匀速对跑的两颗流星（首页「训练进行中」横幅用）。

  为什么是 Canvas（docs/todo 里「训练横幅流光重做」的结论）：
  - 旋转 conic 圆盘在扁长卡片上是「角速度匀速」，光在长边中段慢爬、到转角猛甩；
  - SVG 描边叠段做彗星，平头端点＋几档宽度在真机上是一节节方块；
  只有逐帧按周长取点、把尾巴切成几十小段画，宽度与透明度才能连续收尖、严格贴圆角。

  用法：放在 `position: relative` 的父元素里，父元素的内容层要 `z-index: 1` 压在它上面
  （画布在下，白卡片盖住内侧一半，光晕只往外渗）。
-->
<script setup lang="ts">
import { onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'

const props = withDefaults(defineProps<{ radius?: number }>(), { radius: 12 })

// 参数由主人在预览里调定（2026-10-22）：尾长 160px、头宽 3px、5 秒一圈
const TAIL = 160
const HEAD_W = 3
const LAP_MS = 5000
/** 画布向外多留的边，给头部光晕 */
const PAD = 10
/** 轨迹在卡片边缘外侧多少 px */
const OFF = 0.5
const GLOW_R = 6

type RGB = [number, number, number]
const hex = (h: string): RGB => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16)) as RGB
// 一绿一青，相隔半圈。配色同旧流光：草绿→荧光绿、深青→浅青
const COMETS = [
  { head: hex('#3b6d11'), tail: hex('#97c459'), phase: 0 },
  { head: hex('#0f6e56'), tail: hex('#5dcaa5'), phase: 0.5 }
]

const hostRef = ref<unknown>(null)
let host: HTMLElement | null = null
let canvas: HTMLCanvasElement | null = null
let ctx: CanvasRenderingContext2D | null = null
let ro: ResizeObserver | null = null
let raf = 0
let running = false
let elapsed = 0
let last = 0

let W = 0
let H = 0
let dpr = 1
let geo: { x: number; y: number; w: number; h: number; r: number; segs: number[]; total: number } | null = null

function reducedMotion() {
  return typeof window !== 'undefined' && !!window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function layout() {
  const card = host?.parentElement
  if (!card || !canvas) return
  const cw = card.offsetWidth
  const ch = card.offsetHeight
  if (!cw || !ch) return
  dpr = window.devicePixelRatio || 1
  W = cw + PAD * 2
  H = ch + PAD * 2
  canvas.width = Math.round(W * dpr)
  canvas.height = Math.round(H * dpr)
  canvas.style.width = `${W}px`
  canvas.style.height = `${H}px`
  const r = Math.min(props.radius, ch / 2, cw / 2) + OFF
  const x = PAD - OFF
  const y = PAD - OFF
  const w = cw + OFF * 2
  const h = ch + OFF * 2
  const arc = (Math.PI * r) / 2
  const segs = [w - 2 * r, arc, h - 2 * r, arc, w - 2 * r, arc, h - 2 * r, arc]
  geo = { x, y, w, h, r, segs, total: segs.reduce((s, v) => s + v, 0) }
  if (!running) draw()
}

/** 周长参数 s → 坐标，从左上圆角之后的上边起点顺时针 */
function pointAt(s: number): [number, number] {
  const g = geo!
  s = ((s % g.total) + g.total) % g.total
  const { x, y, w, h, r } = g
  let i = 0
  while (i < 7 && s > g.segs[i]) {
    s -= g.segs[i]
    i++
  }
  switch (i) {
    case 0: return [x + r + s, y]
    case 1: { const t = -Math.PI / 2 + s / r; return [x + w - r + r * Math.cos(t), y + r + r * Math.sin(t)] }
    case 2: return [x + w, y + r + s]
    case 3: { const t = s / r; return [x + w - r + r * Math.cos(t), y + h - r + r * Math.sin(t)] }
    case 4: return [x + w - r - s, y + h]
    case 5: { const t = Math.PI / 2 + s / r; return [x + r + r * Math.cos(t), y + h - r + r * Math.sin(t)] }
    case 6: return [x, y + h - r - s]
    default: { const t = Math.PI + s / r; return [x + r + r * Math.cos(t), y + r + r * Math.sin(t)] }
  }
}

function roundRect(c: CanvasRenderingContext2D) {
  const { x, y, w, h, r } = geo!
  c.beginPath()
  c.moveTo(x + r, y)
  c.arcTo(x + w, y, x + w, y + h, r)
  c.arcTo(x + w, y + h, x, y + h, r)
  c.arcTo(x, y + h, x, y, r)
  c.arcTo(x, y, x + w, y, r)
  c.closePath()
}

function draw() {
  const c = ctx
  if (!c || !geo) return
  c.setTransform(dpr, 0, 0, dpr, 0, 0)
  c.clearRect(0, 0, W, H)

  // 底边：流星跑到别处时卡片也有轮廓
  roundRect(c)
  c.strokeStyle = 'rgba(59, 109, 17, 0.14)'
  c.lineWidth = 1
  c.stroke()

  // 尾巴切成约 2px 一段：宽度与透明度沿长度连续衰减，颜色从头色渐到尾色
  const n = Math.round(TAIL / 2)
  const step = TAIL / n
  c.lineCap = 'butt'
  for (const comet of COMETS) {
    const head = ((elapsed / LAP_MS + comet.phase) % 1) * geo.total
    for (let i = n - 1; i >= 0; i--) {
      const t = i / n
      const k = 1 - t
      const [x1, y1] = pointAt(head - i * step)
      const [x2, y2] = pointAt(head - (i + 1) * step)
      const col = comet.head.map((v, j) => Math.round(v + (comet.tail[j] - v) * t))
      c.strokeStyle = `rgba(${col.join(',')},${Math.pow(k, 1.8).toFixed(3)})`
      c.lineWidth = 0.3 + (HEAD_W - 0.3) * Math.pow(k, 1.3)
      c.beginPath()
      c.moveTo(x1, y1)
      c.lineTo(x2, y2)
      c.stroke()
    }
    // 头：柔光晕＋实心亮点
    const [hx, hy] = pointAt(head)
    const rgb = comet.head.join(',')
    const glow = c.createRadialGradient(hx, hy, 0, hx, hy, GLOW_R)
    glow.addColorStop(0, `rgba(${rgb},0.35)`)
    glow.addColorStop(1, `rgba(${rgb},0)`)
    c.fillStyle = glow
    c.beginPath()
    c.arc(hx, hy, GLOW_R, 0, Math.PI * 2)
    c.fill()
    c.fillStyle = `rgb(${rgb})`
    c.beginPath()
    c.arc(hx, hy, HEAD_W * 0.75, 0, Math.PI * 2)
    c.fill()
  }
}

function tick(now: number) {
  if (!running) return
  elapsed += Math.min(now - last, 100) // 掉帧／切后台回来不要一下跳一大截
  last = now
  draw()
  raf = requestAnimationFrame(tick)
}

function start() {
  if (running || !ctx) return
  // 减少动态效果：只画一帧静态（流星停在起始相位）
  if (reducedMotion()) {
    draw()
    return
  }
  running = true
  last = performance.now()
  raf = requestAnimationFrame(tick)
}

function stop() {
  running = false
  cancelAnimationFrame(raf)
}

onMounted(() => {
  // uni H5 的 `<view ref>` 是组件实例，取 `$el`；画布直接用 DOM 建，不走 uni 的 <canvas> 组件
  host = ((hostRef.value as { $el?: HTMLElement } | null)?.$el ?? hostRef.value) as HTMLElement | null
  if (!host || typeof document === 'undefined') return
  canvas = document.createElement('canvas')
  canvas.style.position = 'absolute'
  canvas.style.left = `${-PAD}px`
  canvas.style.top = `${-PAD}px`
  canvas.style.pointerEvents = 'none'
  host.appendChild(canvas)
  ctx = canvas.getContext('2d')
  layout()
  if (typeof ResizeObserver !== 'undefined' && host.parentElement) {
    ro = new ResizeObserver(() => layout())
    ro.observe(host.parentElement)
  } else {
    window.addEventListener('resize', layout)
  }
  start()
})

// 首页是保活的 tab 页：切走时停帧，回来再续上
onActivated(() => {
  layout()
  start()
})
onDeactivated(stop)

onBeforeUnmount(() => {
  stop()
  ro?.disconnect()
  window.removeEventListener('resize', layout)
  canvas?.remove()
})
</script>

<template>
  <view ref="hostRef" class="ft-meteor" />
</template>

<style lang="scss" scoped>
.ft-meteor {
  position: absolute;
  left: 0;
  top: 0;
  width: 0;
  height: 0;
  pointer-events: none;
}
</style>
