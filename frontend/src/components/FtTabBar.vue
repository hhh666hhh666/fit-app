/**
 * 底部主 tab 栏。四页各渲染一份（uni-app 没有共享 layout），页内位置由页面决定。
 *
 * 切换走 uni.reLaunch：页面会被销毁重建，所以这里既是「发起方」也是「方向信号的
 * 唯一来源」——跳转前记下从哪来，目标页 setup 时取用（见 utils/nav.ts）。
 */
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { markTabNav, type TabKey } from '@/utils/nav'

const props = defineProps<{
  active: TabKey
  /** 本页的入场方向：由页面从 utils/nav 取到后透传，用来让指示条从同侧滑入 */
  enterDir?: 'left' | 'right' | null
}>()

const tabs = [
  { key: 'home', label: '首页', icon: 'home', url: '/pages/home/index' },
  { key: 'log', label: '日志', icon: 'list', url: '/pages/log/index' },
  { key: 'stats', label: '统计', icon: 'chart-bar', url: '/pages/stats/index' },
  { key: 'profile', label: '我的', icon: 'user', url: '/pages/profile/index' }
] as const

const activeIdx = computed(() => Math.max(0, tabs.findIndex((t) => t.key === props.active)))

// 指示条：先落在「上一页那一格」，挂载后下一帧过渡到当前格。
// tabbar 随页面一起重建、旧页已销毁，做不到从上一格的真实位置滑过来——
// 这段位移是伪连续，但方向必须对：内容从右侧进场是因为整个容器在往左移，
// 而指示条标的是「选中哪一格」，所以它和内容位移**反向**——首页→日志是
// 从左往右走一格（起点＝上一页那边，即 dir=right 时取 activeIdx - 1）。
const indIdx = ref(
  props.enterDir ? activeIdx.value + (props.enterDir === 'right' ? -1 : 1) : activeIdx.value
)

onMounted(() => {
  if (indIdx.value === activeIdx.value) return
  // 双 rAF：等首帧（已在偏侧位置）画上去，再改目标值，transition 才会真的跑
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      indIdx.value = activeIdx.value
    })
  })
})

function go(t: (typeof tabs)[number]) {
  if (t.key === props.active) return
  markTabNav(props.active)
  uni.reLaunch({ url: t.url })
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
    <view class="ft-tabbar__ind" :style="{ transform: `translateX(${indIdx * 100}%)` }" />
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
  transition: transform $dur-base $ease-out;
}

.ft-tabbar__ind::before {
  content: '';
  width: 32rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: $app-ink;
}

@media (prefers-reduced-motion: reduce) {
  .ft-tabbar__item,
  .ft-tabbar__ind {
    transition: none;
  }
}
</style>
