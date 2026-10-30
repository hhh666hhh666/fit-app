<script setup lang="ts">
import type { Inline } from '@/utils/markdown-lite'
import { ask } from '@/utils/confirm'

// ── 一段行内内容（ADR 0044）：壳把所有跳转留在 App 内，打开外链会顶掉当前页，所以点链接只给复制 ──

defineProps<{ inlines: Inline[] }>()

async function onLink(href?: string) {
  if (!href) return
  const ok = await ask({ title: '复制链接？', content: href, confirmText: '复制' })
  if (ok) uni.setClipboardData({ data: href })
}
</script>

<template>
  <template v-for="(s, i) in inlines" :key="i">
    <text v-if="s.kind === 'link'" class="in in--link" role="link" @click="onLink(s.href)">{{ s.text }}</text>
    <text v-else class="in" :class="'in--' + s.kind" selectable>{{ s.text }}</text>
  </template>
</template>

<style lang="scss" scoped>
.in {
  white-space: pre-wrap;
  word-break: break-word;
}

.in--bold {
  font-weight: $weight-bold;
}

.in--italic {
  font-style: italic;
}

.in--code {
  padding: 2rpx 10rpx;
  border-radius: $radius-xs;
  background: $app-surface-2;
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 0.92em;
}

.in--link {
  color: $accent-strong;
  text-decoration: underline;
}
</style>
