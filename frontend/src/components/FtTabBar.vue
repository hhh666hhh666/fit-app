/**
 * 底部主 tab 栏。四页各渲染一份（uni-app 没有共享 layout），页内位置由页面决定。
 *
 * 切换走 `uni.switchTab`：四个主页面在 `pages.json` 里注册成 tabBar 页，框架用
 * KeepAlive 把它们按 `type: "tabBar"` 缓存住、永不 prune——所以切页**不再销毁页面**，
 * `reLaunch` 那段「旧页已拆、新页未建」的白屏空档随之消失（ADR 0038）。
 *
 * 框架会在 H5 上另渲染一条内置 `uni-tabbar`（它不认 `custom: true`），已在 App.vue
 * 里用样式压掉；这里这份才是真正显示的。
 */
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { markTabNav, type TabKey } from '@/utils/nav'

const props = defineProps<{
  active: TabKey
  /** 本页这次显示的入场方向；页面在 onShow 里取到后透传，用来让指示条从上一格滑过来 */
  enterDir?: 'left' | 'right' | null
  /** 每次 onShow 自增一次，用来触发指示条重放（页面保活，不能用挂载时机） */
  enterSeq?: number
}>()

const tabs = [
  { key: 'home', label: '首页', icon: 'home', url: '/pages/home/index' },
  { key: 'log', label: '日志', icon: 'list', url: '/pages/log/index' },
  { key: 'stats', label: '统计', icon: 'chart-bar', url: '/pages/stats/index' },
  { key: 'profile', label: '我的', icon: 'user', url: '/pages/profile/index' }
] as const

const activeIdx = computed(() => Math.max(0, tabs.findIndex((t) => t.key === props.active)))

// 指示条：静止位置永远是当前格；「上一页那一格」只是动画的起点。
// 方向与内容位移相反——内容从右进场是因为整个容器在往左移，而指示条标的是
// 「选中哪一格」，所以首页→日志它从左往右走一格。
const indRef = ref<HTMLElement | null>(null)
const indOffset = ref(activeIdx.value)

function slideIndicator(dir: 'left' | 'right' | null) {
  const el = indRef.value
  indOffset.value = activeIdx.value
  if (!dir || !el || typeof el.animate !== 'function') return
  if (typeof window !== 'undefined' && window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    return
  }
  const from = activeIdx.value + (dir === 'right' ? -1 : 1)
  el.getAnimations().forEach((a) => a.cancel())
  el.animate(
    [
      { transform: `translateX(${from * 100}%)` },
      { transform: `translateX(${activeIdx.value * 100}%)` }
    ],
    { duration: 220, easing: 'cubic-bezier(0.22, 0.61, 0.36, 1)' }
  )
}

// 首次挂载（冷启动）没有方向信号，指示条直接落在当前格，不要滑
onMounted(() => {
  indOffset.value = activeIdx.value
})

// 之后每次页面被显示，页面把 enterSeq 加一，这里据此重放
watch(
  () => props.enterSeq,
  () => slideIndicator(props.enterDir ?? null)
)

function go(t: (typeof tabs)[number]) {
  if (t.key === props.active) return
  markTabNav(props.active)
  uni.switchTab({ url: t.url })
}
</script>

<template>
  <view class="ft-tabbar">
    <view class="ft-tabbar__items">
      <view
        v-for="t in tabs"
        :key="t.key"
        class="ft-tabbar__item"
        :class="{ 'is-active': t.key === active }"
        @click="go(t)"
      >
        <wd-icon :name="t.icon" size="21px" />
        <text class="ft-tabbar__label">{{ t.label }}</text>
      </view>
    </view>

    <!-- 当前项短横：用墨黑而不是荧光笔——`$app-highlight` 在白底上对比度约 1.1:1，
         细线放上去基本看不见（首页流光第①条同款教训），荧光仍留给内容级高亮 -->
    <view
      ref="indRef"
      class="ft-tabbar__ind"
      :style="{ transform: `translateX(${indOffset * 100}%)` }"
    />
  </view>
</template>

<style lang="scss" scoped>
.ft-tabbar {
  position: relative;
  overflow: hidden;
  background: $app-surface;
  border-top: 1rpx solid $app-line;
  padding: 14rpx 0 calc(10rpx + env(safe-area-inset-bottom));
}

.ft-tabbar__items {
  display: flex;
}

.ft-tabbar__item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  color: $app-ink-subtle;
  font-size: $text-xs;
  line-height: 1.2;
  transition: color $dur-fast $ease-out;
}

.ft-tabbar__item.is-active {
  color: $app-ink;
  font-weight: 600;
}

.ft-tabbar__ind {
  position: absolute;
  left: 0;
  bottom: calc(4rpx + env(safe-area-inset-bottom));
  width: 25%;
  display: flex;
  justify-content: center;
  pointer-events: none;
}

.ft-tabbar__ind::before {
  content: '';
  width: 32rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: $app-ink;
}

@media (prefers-reduced-motion: reduce) {
  .ft-tabbar__item {
    transition: none;
  }
}
</style>
