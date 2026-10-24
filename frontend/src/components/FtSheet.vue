<!--
  底部弹层（晴窗）：遮罩 ＋ 上圆角面板，从底部滑入。开关由父组件的 v-if 控制，要关时发 close。

  关法五种，走同一个口子（requestClose）：点遮罩、右上角关闭钮、按住顶部（把手 ＋ 标题）往下拉、
  安卓返回键、电脑上的 Esc（后两种经 utils/backStack.ts）。这几种关法都先播退出动画再发 close；
  父组件自己把 v-if 置 false（比如保存成功）则直接消失。
  表单弹层传 `guard`：里面的输入框被改过之后，上面几种关法都先问一句「放弃填写的内容？」。

  ⚠️ 别把它放进带 backdrop-filter / transform / filter 的祖先里：那样 fixed 会
  相对那个祖先定位，弹层就不贴屏幕底了。
-->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import FtIcon from '@/components/FtIcon.vue'
import { useBackClose } from '@/utils/backStack'
import { ask } from '@/utils/confirm'

const props = defineProps<{ title?: string; sub?: string; guard?: boolean }>()
const emit = defineEmits<{ close: [] }>()

/** 退出动画时长：与 $dur-base 一致 */
const LEAVE_MS = 220
/** 往下拉超过这么多（px）松手就关 */
const DRAG_CLOSE_PX = 120

const sheetRef = ref<unknown>(null)
const leaving = ref(false)
const dragY = ref(0)
const dragging = ref(false)
/** 有没有输入过（guard 用）：听面板里冒上来的原生 input 事件 */
const touched = ref(false)

let closing = false

async function requestClose() {
  if (closing || leaving.value) return
  closing = true
  try {
    if (props.guard && touched.value) {
      const ok = await ask({ title: '放弃填写的内容？', content: '填的内容还没保存，关掉就没了。', confirmText: '放弃', danger: true })
      if (!ok) {
        dragY.value = 0
        return
      }
    }
    leaving.value = true
    setTimeout(() => emit('close'), LEAVE_MS)
  } finally {
    closing = false
  }
}

// ── 下拉关闭：按住顶部那一条往下拖 ──
let startY = 0

function onDragStart(e: TouchEvent) {
  startY = e.touches[0].clientY
  dragging.value = true
}

function onDragMove(e: TouchEvent) {
  if (!dragging.value) return
  dragY.value = Math.max(0, e.touches[0].clientY - startY)
}

function onDragEnd() {
  if (!dragging.value) return
  dragging.value = false
  if (dragY.value > DRAG_CLOSE_PX) void requestClose()
  else dragY.value = 0
}

const sheetStyle = computed(() => (dragY.value > 0 && !leaving.value ? { transform: `translateY(${dragY.value}px)` } : {}))

function markTouched() {
  touched.value = true
}

function rootEl(): HTMLElement | null {
  const raw = sheetRef.value as { $el?: HTMLElement } | HTMLElement | null
  return ((raw as { $el?: HTMLElement } | null)?.$el ?? raw) as HTMLElement | null
}

useBackClose(() => void requestClose())

onMounted(() => rootEl()?.addEventListener('input', markTouched, true))
onBeforeUnmount(() => rootEl()?.removeEventListener('input', markTouched, true))
</script>

<template>
  <view class="ft-sheet-mask" :class="{ 'is-leaving': leaving }" @click="requestClose" @touchmove.stop.prevent />
  <!-- touchmove 截在面板上：冒到页面的 uni-page-wrapper 会被当成「下拉刷新」（里面的列表滑到顶再往下拉就触发）。
       只截 touchmove、不截 touchstart——uni 的长按是靠 window 上的 touchstart 模拟的 -->
  <view
    ref="sheetRef"
    class="ft-sheet"
    :class="{ 'is-leaving': leaving, 'is-dragging': dragging }"
    :style="sheetStyle"
    role="dialog"
    aria-modal="true"
    :aria-label="title"
    @touchmove.stop
  >
    <view
      class="ft-sheet__grip"
      @touchstart="onDragStart"
      @touchmove.stop.prevent="onDragMove"
      @touchend="onDragEnd"
      @touchcancel="onDragEnd"
    >
      <view class="ft-sheet__handle" />
      <view class="ft-sheet__head">
        <text v-if="title" class="ft-sheet__title">{{ title }}</text>
        <view class="ft-sheet__close" role="button" aria-label="关闭" @click.stop="requestClose">
          <FtIcon name="close" :size="18" :stroke="2" />
        </view>
      </view>
    </view>
    <text v-if="sub" class="ft-sheet__sub">{{ sub }}</text>
    <slot />
  </view>
</template>

<style lang="scss" scoped>
.ft-sheet-mask {
  position: fixed;
  inset: 0;
  background: $app-mask;
  z-index: 90;
  animation: ft-sheet-fade $dur-base $ease-out;
  transition: opacity $dur-base $ease-out;
}

.ft-sheet-mask.is-leaving {
  opacity: 0;
}

.ft-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 91;
  box-sizing: border-box;
  max-height: 86vh;
  display: flex;
  flex-direction: column;
  background: $app-canvas-bottom;
  border-radius: $radius-xl $radius-xl 0 0;
  box-shadow: $shadow-sheet;
  padding: 20rpx 40rpx calc(40rpx + env(safe-area-inset-bottom));
  animation: ft-sheet-up $dur-base $ease-out;
  transition: transform $dur-base $ease-out;
}

// 拖动时跟手，不要过渡
.ft-sheet.is-dragging {
  transition: none;
}

.ft-sheet.is-leaving {
  transform: translateY(100%);
}

// 顶部这一条（把手 ＋ 标题行）是下拉的抓手
.ft-sheet__grip {
  flex-shrink: 0;
}

.ft-sheet__handle {
  width: 72rpx;
  height: 8rpx;
  border-radius: $radius-pill;
  background: $app-line;
  margin: 0 auto 28rpx;
}

.ft-sheet__head {
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-height: 56rpx;
}

.ft-sheet__title {
  flex: 1;
  min-width: 0;
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $app-ink;
}

.ft-sheet__close {
  flex-shrink: 0;
  margin-left: auto;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: $app-surface-2;
  color: $app-ink-2;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ft-sheet__close:active {
  opacity: 0.7;
}

.ft-sheet__sub {
  display: block;
  font-size: $text-base;
  color: $app-ink-muted;
  padding: 8rpx 0 28rpx;
}

@keyframes ft-sheet-up {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

@keyframes ft-sheet-fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ft-sheet,
  .ft-sheet-mask {
    animation: none;
    transition: none;
  }
}
</style>
