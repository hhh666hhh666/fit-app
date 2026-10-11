<script setup lang="ts">
const props = defineProps<{ active: 'home' | 'log' | 'stats' | 'profile' }>()

const tabs = [
  { key: 'home', label: '首页', icon: 'home', url: '/pages/home/index' },
  { key: 'log', label: '日志', icon: 'list', url: '/pages/log/index' },
  { key: 'stats', label: '统计', icon: 'chart-bar', url: '/pages/stats/index' },
  { key: 'profile', label: '我的', icon: 'user', url: '/pages/profile/index' }
] as const

function go(t: (typeof tabs)[number]) {
  if (t.key === props.active) return
  uni.reLaunch({ url: t.url })
}
</script>

<template>
  <view class="ft-tabbar">
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
</template>

<style lang="scss" scoped>
.ft-tabbar {
  display: flex;
  background: $app-surface;
  border-top: 1rpx solid $app-line;
  padding: 14rpx 0 calc(10rpx + env(safe-area-inset-bottom));
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
}

.ft-tabbar__item.is-active {
  color: $app-ink;
  font-weight: 600;
}
</style>
