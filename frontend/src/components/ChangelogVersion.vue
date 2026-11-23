<!--
  更新日志里的一个版本（ADR 0068）：版本号 · 日期，下面按「新增 / 改动 / 修复」分组列条目；
  条目里的 **粗体** 照显示。dev＝false 时去掉开发字眼（只在显示时去，原文不改）。
  collapsible：可以折叠成一行「改了几处」，点头部展开 / 收起。
-->
<script setup lang="ts">
import { computed } from 'vue'
import FtIcon from '@/components/FtIcon.vue'
import FtMdInline from '@/components/FtMdInline.vue'
import { stripDev, type ChangeVersion } from '@/utils/changelog'
import { parseInline } from '@/utils/markdown-lite'

const props = withDefaults(defineProps<{ v: ChangeVersion; dev: boolean; open?: boolean; collapsible?: boolean }>(), {
  open: true,
  collapsible: false
})
const emit = defineEmits<{ toggle: [] }>()

const dateText = computed(() => {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(props.v.date)
  return m ? `${Number(m[2])}月${Number(m[3])}日` : ''
})

const sections = computed(() =>
  props.v.sections.map((s) => ({
    ...s,
    rows: s.items.map((t) => parseInline(props.dev ? t : stripDev(t)))
  }))
)
</script>

<template>
  <view class="cv">
    <view class="cv__head" :role="collapsible ? 'button' : undefined" :aria-expanded="collapsible ? open : undefined" @click="collapsible && emit('toggle')">
      <text class="cv__ver ft-num">v{{ v.version }}</text>
      <text v-if="dateText" class="cv__date ft-num">{{ dateText }}</text>
      <text v-if="collapsible && !open" class="cv__count ft-num">改了 {{ v.count }} 处</text>
      <FtIcon v-if="collapsible" name="chevron-right" :size="16" tone="subtle" class="cv__arrow" :class="{ 'cv__arrow--open': open }" />
    </view>
    <view v-if="open" class="cv__body">
      <view v-for="(s, i) in sections" :key="i" class="cv__sec">
        <text class="cv__tag" :class="'cv__tag--' + s.kind">{{ s.label }}</text>
        <view v-for="(row, j) in s.rows" :key="j" class="cv__item">
          <text class="cv__dot" :class="'cv__dot--' + s.kind" />
          <view class="cv__text"><FtMdInline :inlines="row" /></view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.cv__head {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
}

.cv__ver {
  font-size: $text-xl;
  font-weight: $weight-heavy;
  color: $app-ink;
}

.cv__date,
.cv__count {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.cv__count {
  margin-left: auto;
}

.cv__arrow {
  align-self: center;
  transition: transform $dur-fast $ease-out;

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
}

.cv__date + .cv__arrow {
  margin-left: auto;
}

.cv__arrow--open {
  transform: rotate(90deg);
}

.cv__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  margin-top: 20rpx;
}

.cv__sec {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

// 三类各一个色标：新增草绿、改动石板蓝、修复浅橙（借 PR 角标那一套）
.cv__tag {
  @include badge(null, null);
  align-self: flex-start;
  padding: 4rpx 16rpx;
  font-size: $text-sm;
  font-weight: $weight-bold;
}

.cv__tag--added {
  background: $meal-weight-bg;
  color: $meal-weight-fg;
}

.cv__tag--changed {
  background: $meal-count-bg;
  color: $meal-count-fg;
}

.cv__tag--fixed {
  background: $accent-soft;
  color: $accent-strong;
}

.cv__tag--other {
  background: $app-surface-2;
  color: $app-ink-muted;
}

.cv__item {
  display: flex;
  gap: 16rpx;
}

.cv__dot {
  flex: none;
  width: 10rpx;
  height: 10rpx;
  margin-top: 18rpx;
  border-radius: 50%;
  background: $app-line-dash;
}

.cv__dot--added {
  background: $meal-weight-fg;
}

.cv__dot--changed {
  background: $meal-count-fg;
}

.cv__dot--fixed {
  background: $accent-ring;
}

.cv__text {
  flex: 1;
  min-width: 0;
  font-size: $text-md;
  line-height: 1.65;
  color: $app-ink-2;
}
</style>
