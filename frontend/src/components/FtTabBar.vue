<!--
  底栏（晴窗，ADR 0041）：悬浮玻璃胶囊，五等分——首页、日志、**中间训练圆钮**、统计、我的。
  四页各渲染一份（uni-app 没有共享 layout），页内位置由页面决定。

  - 切换走 `uni.switchTab`：四个主页面是 tabBar 页，框架按 KeepAlive 缓存、切页不销毁（ADR 0038）。
    框架另渲染的内置 `uni-tabbar` 已在 App.vue 里压掉。
  - 选中项的浅色胶囊底就是 ADR 0037 那条会滑的指示条，滑动按「列」算，中间那列只是被跨过去。
  - 胶囊顶边正中是**真凹口**：玻璃层用 `clip-path: path()` 裁，亮边和投影用同一条路径在 SVG 里另画。
    投影不能挂 `filter` 在玻璃层的父级上——那会让 backdrop-filter 失效。
  - 中间圆钮：没有进行中的训练＝哑铃，点开选计划；有＝播放图标，点一下直接继续，
    **长按**才弹「继续上次／结束并开新的」（ADR 0020 的二选一，SessionConflictSheet）。外圈动效见 FtMeteorTrace。
  - 选计划弹层随训练入口从首页挪到这里，四个主页面都能直接开练。
-->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import FtIcon, { type FtIconName } from '@/components/FtIcon.vue'
import FtMeteorTrace from '@/components/FtMeteorTrace.vue'
import FtSheet from '@/components/FtSheet.vue'
import FtListRow from '@/components/FtListRow.vue'
import FtButton from '@/components/FtButton.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import SessionConflictSheet from '@/components/SessionConflictSheet.vue'
import { useScheduleStore } from '@/stores/schedule'
import { formatDateLabel } from '@/utils/format'
import { clearSession, loadSession, type Session } from '@/utils/session'
import { getResumeFx, type ResumeFx } from '@/utils/resume-fx'
import {
  animateWhenConnected,
  lastTabFromIndex,
  markTabNav,
  SLIDE_EASE,
  SLIDE_MS,
  startSlide
} from '@/utils/nav'
import { TABS, type Tab, type TabKey } from '@/utils/tabs'

const props = defineProps<{
  active: TabKey
  /** 本页这次显示的入场方向；页面在 onShow 里取到后透传，用来让指示条从上一格滑过来 */
  enterDir?: 'left' | 'right' | null
  /** 每次 onShow 自增一次：重放指示条、重读进行中的会话（页面保活，不能用挂载时机） */
  enterSeq?: number
}>()

// 四个 tab 的定义只在 utils/tabs.ts：body＝切走时克隆、滑动的正文；col＝五等分里的列（2 是圆钮）
const tabs: readonly (Tab & { icon: FtIconName; iconOn: FtIconName })[] = TABS

const activeIdx = computed(() => Math.max(0, tabs.findIndex((t) => t.key === props.active)))
const activeCol = computed(() => tabs[activeIdx.value].col)

// ── 凹口几何（px）：胶囊高 62、两端半圆；凹口是圆心在顶边上方 4px、半径 36 的圆，
//    与顶边之间用半径 8 的圆角过渡。宽度随屏宽变，所以路径在运行时按实测宽度生成。──
const BAR_H = 62
const barRef = ref<unknown>(null)
/** 整个底栏（含圆钮）：切页时克隆一份钉住，补上新页底栏还没画出来的那几帧 */
const rootRef = ref<unknown>(null)
const barW = ref(0)

const notchPath = computed(() => {
  const W = barW.value
  if (!W) return ''
  const r = BAR_H / 2
  const c = W / 2
  const f = (n: number) => Math.round(n * 100) / 100
  return (
    `M${r},0 H${f(c - 42.33)} A8,8 0 0 1 ${f(c - 34.63)},5.82 ` +
    `A36,36 0 0 0 ${f(c + 34.63)},5.82 A8,8 0 0 1 ${f(c + 42.33)},0 ` +
    `H${f(W - r)} A${r},${r} 0 0 1 ${f(W - r)},${BAR_H} H${r} A${r},${r} 0 0 1 ${r},0 Z`
  )
})

const glassStyle = computed(() => {
  const d = notchPath.value
  if (!d) return {}
  return { clipPath: `path('${d}')`, WebkitClipPath: `path('${d}')`, borderRadius: '0' }
})

// 四个页面各有一份底栏、同时都在文档里（保活），SVG 滤镜 id 必须各自唯一：
// 引用到一份被 display:none 藏起来的滤镜，Chrome 会把投影整个丢掉
const shadowId = `ftb-shadow-${Math.random().toString(36).slice(2, 8)}`

function measure() {
  const raw = barRef.value as { $el?: HTMLElement } | HTMLElement | null
  const el = ((raw as { $el?: HTMLElement } | null)?.$el ?? raw) as HTMLElement | null
  const w = el?.offsetWidth ?? 0
  // 保活页被藏起来时量到 0，别拿 0 覆盖掉上一次的有效宽度
  if (w > 0) barW.value = w
}

// ── 指示条：静止位置永远是当前列；「上一页那一列」只是动画的起点 ──
const indRef = ref<unknown>(null)

function slideIndicator(dir: 'left' | 'right' | null) {
  const el = indRef.value
  if (!dir || !el) return
  if (typeof window !== 'undefined' && window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    return
  }
  // 从真正的来源格出发：首页→统计要滑过两格（中间那列圆钮被跨过去），不能只从邻格冒出来
  const fromIdx = lastTabFromIndex() ?? activeIdx.value + (dir === 'right' ? -1 : 1)
  const fromCol = tabs[Math.min(Math.max(fromIdx, 0), tabs.length - 1)].col
  animateWhenConnected(
    el,
    [
      { transform: `translateX(${fromCol * 100}%)` },
      { transform: `translateX(${activeCol.value * 100}%)` }
    ],
    { duration: SLIDE_MS, easing: SLIDE_EASE }
  )
}

// ── 进行中的会话（ADR 0020：会话存本机；回程入口从首页横幅挪到这颗圆钮）──
const session = ref<Session | null>(null)
const resumeFx = ref<ResumeFx>(getResumeFx())

function refreshSession() {
  session.value = loadSession()
  resumeFx.value = getResumeFx()
}

const doneCount = computed(() =>
  (session.value?.blocks ?? []).reduce((sum, b) => sum + b.sets.filter((s) => s.done).length, 0)
)

const btnLabel = computed(() => (session.value ? `继续训练，已完成 ${doneCount.value} 组` : '开始训练'))

// ── 选计划（ADR 0016：周安排只决定谁置顶，不自动执行）──
const schedule = useScheduleStore()
const picker = ref(false)

const pickerHint = computed(() => {
  if (schedule.todayPlan) return `今天 · ${schedule.todayPlan.name}`
  if (schedule.isRest) return '今天休息'
  return '今天没有安排'
})

/** 今天的置顶，其余保持原序 */
const plansOrdered = computed(() => {
  const todayId = schedule.todayPlan?.id
  const list = [...schedule.plans]
  if (todayId == null) return list
  const i = list.findIndex((p) => p.id === todayId)
  return i <= 0 ? list : [list[i], ...list.slice(0, i), ...list.slice(i + 1)]
})

function openPicker() {
  void schedule.load()
  picker.value = true
}

/** 新会话带 `fresh=1`：会话页据此放弃接续、重新开一场 */
function startPlan(id: number) {
  picker.value = false
  uni.navigateTo({ url: `/pages/session/index?plan=${id}&fresh=1` })
}

function freeTrain() {
  picker.value = false
  uni.navigateTo({ url: '/pages/session/index?fresh=1' })
}

/** 没有计划时的出口：去「我的 → 训练计划」新建（改计划只走那里） */
function goPlans() {
  picker.value = false
  uni.navigateTo({ url: '/pages/plans/index' })
}

// 长按之后松手，浏览器多半还会补发一次 click——用这个标记吞掉，免得「弹框」和「直接继续」同时发生
let swallowClick = false

function onCenterTap() {
  if (swallowClick) {
    swallowClick = false
    return
  }
  refreshSession()
  if (session.value) {
    uni.navigateTo({ url: '/pages/session/index' })
    return
  }
  openPicker()
}

/** 长按：只在有进行中的会话时有意义——开新的要先确认丢掉旧的（ADR 0020） */
function onCenterLongPress() {
  refreshSession()
  const s = session.value
  if (!s) return
  swallowClick = true
  setTimeout(() => (swallowClick = false), 800)
  conflict.value = true
}

/** 「继续上次 / 结束并开新的」弹层（SessionConflictSheet） */
const conflict = ref(false)

function resumeLast() {
  conflict.value = false
  uni.navigateTo({ url: '/pages/session/index' })
}

function startOver() {
  conflict.value = false
  clearSession()
  session.value = null
  openPicker()
}

function go(t: (typeof tabs)[number]) {
  if (t.key === props.active) return
  markTabNav(props.active)
  // 去右边的 tab：旧页往左滑出、新页从右进来（反过来同理）
  const toIdx = tabs.findIndex((x) => x.key === t.key)
  const dir: 'left' | 'right' = toIdx > activeIdx.value ? 'left' : 'right'
  const raw = rootRef.value as { $el?: HTMLElement } | HTMLElement | null
  const root = ((raw as { $el?: HTMLElement } | null)?.$el ?? raw) as HTMLElement | null
  startSlide(dir, () => uni.switchTab({ url: t.url }), [tabs[activeIdx.value].body], root ? [root] : [])
}

// 首次挂载：冷启动没有方向信号，直接落在当前格；从别的 tab 切来的首访则带着方向——
// 页面 onShow 先于本组件挂载就把 enterSeq 加过了，下面的 watch 看不到这次变化，得在这里补播
onMounted(() => {
  measure()
  window.addEventListener('resize', measure)
  refreshSession()
  slideIndicator(props.enterDir ?? null)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', measure)
})

// 之后每次页面被显示，页面把 enterSeq 加一：重量宽度（藏起来期间可能转过屏）、重读会话、重放指示条
watch(
  () => props.enterSeq,
  () => {
    measure()
    refreshSession()
    picker.value = false
    conflict.value = false
    slideIndicator(props.enterDir ?? null)
  }
)
</script>

<template>
  <view ref="rootRef" class="ft-tabbar">
    <view ref="barRef" class="ft-tabbar__bar">
      <!-- 投影：按凹口路径画的模糊影子，垫在玻璃下面 -->
      <svg v-if="notchPath" class="ft-tabbar__svg" :viewBox="`0 0 ${barW} ${BAR_H}`" aria-hidden="true">
        <defs>
          <filter :id="shadowId" x="-20%" y="-40%" width="140%" height="200%">
            <feGaussianBlur stdDeviation="12" />
          </filter>
        </defs>
        <path :d="notchPath" class="ft-tabbar__shadow" transform="translate(0 12)" :filter="`url(#${shadowId})`" />
      </svg>

      <view class="ft-tabbar__glass" :style="glassStyle" />

      <!-- 外描边 ＋ 白亮边：同一条路径描两遍 -->
      <svg v-if="notchPath" class="ft-tabbar__svg" :viewBox="`0 0 ${barW} ${BAR_H}`" aria-hidden="true">
        <path :d="notchPath" class="ft-tabbar__outline" />
        <path :d="notchPath" class="ft-tabbar__edge" />
      </svg>

      <view class="ft-tabbar__track">
        <view
          ref="indRef"
          class="ft-tabbar__ind"
          :style="{ transform: `translateX(${activeCol * 100}%)` }"
        />
      </view>

      <view class="ft-tabbar__items" role="tablist" aria-label="主导航">
        <template v-for="(t, i) in tabs" :key="t.key">
          <view v-if="i === 2" class="ft-tabbar__hole" />
          <view
            class="ft-tabbar__item"
            :class="{ 'is-active': t.key === active }"
            role="tab"
            :aria-selected="t.key === active"
            @click="go(t)"
          >
            <FtIcon :name="t.key === active ? t.iconOn : t.icon" :size="22" />
            <text class="ft-tabbar__label">{{ t.label }}</text>
          </view>
        </template>
      </view>

      <view class="ft-tabbar__center">
        <FtMeteorTrace v-if="session && resumeFx !== 'off'" :fx="resumeFx === 'glow' ? 'glow' : 'meteor'" />
        <view
          class="ft-tabbar__btn"
          role="button"
          :aria-label="btnLabel"
          @click="onCenterTap"
          @longpress="onCenterLongPress"
        >
          <FtIcon v-if="session" name="play" :size="26" tone="inverse" />
          <FtIcon v-else name="dumbbell" :size="28" :stroke="2.2" tone="inverse" />
        </view>
      </view>
    </view>

    <!-- 选计划弹层：放在玻璃层外面（带 backdrop-filter 的祖先会改掉 fixed 的定位基准） -->
    <view v-if="picker" class="ft-tabbar__sheet">
      <FtSheet title="选择训练计划" :sub="pickerHint" @close="picker = false">
        <scroll-view v-if="plansOrdered.length" scroll-y class="ft-tabbar__plans">
          <FtListRow
            v-for="p in plansOrdered"
            :key="p.id"
            :title="p.name"
            :sub="`${p.exerciseCount} 个动作${p.lastTrainedAt ? ` · 上次 ${formatDateLabel(p.lastTrainedAt)}` : ''}`"
            :highlight="p.id === schedule.todayPlan?.id"
            arrow
            @click="startPlan(p.id)"
          >
            <template #badge>
              <text v-if="p.id === schedule.todayPlan?.id" class="ft-tabbar__badge">今天 · 建议</text>
            </template>
          </FtListRow>
        </scroll-view>

        <FtEmpty v-else title="还没有训练计划" sub="在「我的 → 训练计划」里建一个，之后就能在这里选。" />

        <view class="ft-tabbar__actions">
          <FtButton v-if="!plansOrdered.length" variant="primary" block @click="goPlans">去新建计划</FtButton>
          <FtButton variant="ghost" block @click="freeTrain">自由训练</FtButton>
        </view>
      </FtSheet>
    </view>

    <view v-if="conflict && session" class="ft-tabbar__sheet">
      <SessionConflictSheet :session="session" @resume="resumeLast" @fresh="startOver" @close="conflict = false" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.ft-tabbar {
  /* 外层透明、不接点击：只有胶囊和圆钮挡手，底下的内容照样滚 */
  pointer-events: none;
  padding: 0 $tabbar-side calc(#{$tabbar-gap} + env(safe-area-inset-bottom));
}

.ft-tabbar__bar {
  position: relative;
  height: $tabbar-h;
  pointer-events: auto;
}

.ft-tabbar__svg {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: $tabbar-h;
  overflow: visible;
  pointer-events: none;
}

.ft-tabbar__shadow {
  fill: $tabbar-shadow-color;
}

.ft-tabbar__outline {
  fill: none;
  stroke: $tabbar-outline;
  stroke-width: 1.5;
}

.ft-tabbar__edge {
  fill: none;
  stroke: $glass-edge;
  stroke-width: 1;
}

/* 宽度没量到之前（首帧）先按普通胶囊画，量到后换成带凹口的路径 */
.ft-tabbar__glass {
  position: absolute;
  inset: 0;
  border-radius: $radius-pill;
  background: $glass-bg-strong;
  -webkit-backdrop-filter: $glass-blur;
  backdrop-filter: $glass-blur;
}

/* 指示条轨道与五列对齐：内边距 6px，每列 20% */
.ft-tabbar__track {
  position: absolute;
  inset: 6px;
  pointer-events: none;
}

.ft-tabbar__ind {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 20%;
  display: flex;
  justify-content: center;
}

.ft-tabbar__ind::before {
  content: '';
  width: 58px;
  border-radius: $radius-pill;
  background: $app-surface-2;
}

.ft-tabbar__items {
  position: absolute;
  inset: 6px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  justify-items: center;
  align-items: stretch;
}

.ft-tabbar__item {
  width: 58px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: $app-ink-muted;
  transition: color $dur-fast $ease-out, transform $dur-fast $ease-out;
}

.ft-tabbar__item:active {
  transform: scale(0.94);
}

.ft-tabbar__label {
  font-size: $text-xs;
  line-height: 1.2;
}

.ft-tabbar__item.is-active {
  color: $app-ink;
}

.ft-tabbar__item.is-active .ft-tabbar__label {
  font-weight: $weight-bold;
}

/* 圆钮：嵌在凹口里，圆心对准凹口圆心（顶边上方 4px） */
.ft-tabbar__center {
  position: absolute;
  left: 50%;
  top: -34px;
  width: $tabbar-btn;
  height: $tabbar-btn;
  margin-left: calc(#{$tabbar-btn} / -2);
}

.ft-tabbar__btn {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: $accent-bg;
  box-shadow: $accent-shadow-round;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform $dur-fast $ease-out;
  /* 长按要用来弹二选一：别让系统弹「复制 / 选择」菜单 */
  -webkit-touch-callout: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.ft-tabbar__btn:active {
  transform: scale(0.93);
}

.ft-tabbar__sheet {
  pointer-events: auto;
}

.ft-tabbar__plans {
  max-height: 560rpx;
  margin-bottom: 24rpx;
}

.ft-tabbar__badge {
  flex-shrink: 0;
  font-size: $text-xs;
  font-weight: $weight-bold;
  color: $accent-strong;
  background: $accent-soft;
  border-radius: $radius-pill;
  padding: 2rpx 14rpx;
}

.ft-tabbar__actions {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

@media (prefers-reduced-motion: reduce) {
  .ft-tabbar__item,
  .ft-tabbar__btn {
    transition: none;
  }
}
</style>
