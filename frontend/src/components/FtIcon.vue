<!--
  自带 SVG 线性图标（晴窗，ADR 0041）：取代 wot-design-uni 的 `wd-icon`（全站只用了它）。

  一套线宽（1.75，24 网格），颜色默认跟随父元素的 `color`；要换色用 `tone`，
  别在模板里传色值——色值只认 `uni.scss`。
  新增图标：往 ICONS 里加一条，路径按 24×24 画，圆 / 矩形也写成 path（模板只渲染 path）。
-->
<script lang="ts">
type Path = { d: string; sw?: number }
type Def = { paths: Path[]; fill?: boolean }

const p = (...ds: string[]): Path[] => ds.map((d) => ({ d }))

const ICONS = {
  // ── 通用 ──
  search: { paths: p('M17.5 11a6.5 6.5 0 1 1-13 0 6.5 6.5 0 1 1 13 0z', 'M20 20l-4.2-4.2') },
  check: { paths: p('m5 12.5 4.5 4.5L19 7.5') },
  plus: { paths: p('M12 5v14M5 12h14') },
  close: { paths: p('M6 6l12 12M18 6 6 18') },
  'chevron-right': { paths: p('m9.5 6 6 6-6 6') },
  'chevron-left': { paths: p('m14.5 6-6 6 6 6') },
  sliders: {
    paths: p('M4 7h9M17 7h3M4 17h3M11 17h9', 'M17 7a2 2 0 1 1-4 0 2 2 0 1 1 4 0z', 'M11 17a2 2 0 1 1-4 0 2 2 0 1 1 4 0z')
  },
  star: { paths: p('M12 3.8l2.5 5.1 5.6.8-4 3.9 1 5.6L12 16.5l-5.1 2.7 1-5.6-4-3.9 5.6-.8z') },
  'star-fill': { fill: true, paths: p('M12 3.8l2.5 5.1 5.6.8-4 3.9 1 5.6L12 16.5l-5.1 2.7 1-5.6-4-3.9 5.6-.8z') },

  // ── 底栏 ──
  home: { paths: p('M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z') },
  'home-fill': { fill: true, paths: p('M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z') },
  list: { paths: p('M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01') },
  'list-fill': { paths: [{ d: 'M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01', sw: 2.4 }] },
  chart: { paths: p('M5 20v-8M12 20V5M19 20v-5') },
  'chart-fill': { paths: [{ d: 'M5 20v-8M12 20V5M19 20v-5', sw: 3 }] },
  user: { paths: p('M16 8a4 4 0 1 1-8 0 4 4 0 1 1 8 0z', 'M4.5 20c1.4-3.4 4.3-5 7.5-5s6.1 1.6 7.5 5') },
  'user-fill': {
    fill: true,
    paths: p('M16 8a4 4 0 1 1-8 0 4 4 0 1 1 8 0z', 'M4.5 20c1.4-3.4 4.3-5 7.5-5s6.1 1.6 7.5 5z')
  },

  // ── 训练 ──
  dumbbell: { paths: p('M6.5 6.5v11M17.5 6.5v11M3.5 9.5v5M20.5 9.5v5M6.5 12h11') },
  // 三角按重心居中（不是外接框居中），视觉上才不偏左
  play: { fill: true, paths: p('M8.5 5.8v12.4a.8.8 0 0 0 1.2.7l9.6-6.2a.8.8 0 0 0 0-1.4L9.7 5.1a.8.8 0 0 0-1.2.7z') },
  pause: { paths: p('M9 6v12M15 6v12') },

  // ── 餐次入口（首页 / 饮食） ──
  breakfast: { paths: p('M3 18h18M7 18a5 5 0 0 1 10 0M12 5v3M5.6 9.6 7 11M18.4 9.6 17 11') },
  lunch: {
    paths: p(
      'M16 12a4 4 0 1 1-8 0 4 4 0 1 1 8 0z',
      'M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4'
    )
  },
  dinner: { paths: p('M19.5 14.5A7.5 7.5 0 1 1 9.5 4.5a6 6 0 0 0 10 10z') },
  snack: {
    paths: [
      { d: 'M20 12.5A8.5 8.5 0 1 1 11.5 4a3 3 0 0 0 3.5 3.5 3 3 0 0 0 5 5z' },
      { d: 'M8.5 10h.01M12.5 15.5h.01M8 15h.01M16 14.5h.01M11.5 11.5h.01', sw: 2.6 }
    ]
  },
  water: { paths: p('M12 3.5c3.5 4 6 7.2 6 10.5a6 6 0 0 1-12 0c0-3.3 2.5-6.5 6-10.5z') },
  weight: {
    paths: p(
      'M8 4h8a4.5 4.5 0 0 1 4.5 4.5v7A4.5 4.5 0 0 1 16 20H8a4.5 4.5 0 0 1-4.5-4.5v-7A4.5 4.5 0 0 1 8 4z',
      'M8.5 10a5 5 0 0 1 7 0L13 12.5'
    )
  }
} satisfies Record<string, Def>

export type FtIconName = keyof typeof ICONS
</script>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    name: FtIconName
    /** 边长，数字按 px */
    size?: number | string
    /** 线宽覆盖；不传＝1.75 */
    stroke?: number
    /** inherit＝跟父元素；其余映射到 uni.scss 的颜色 */
    tone?: 'inherit' | 'muted' | 'subtle' | 'inverse' | 'accent' | 'gold'
  }>(),
  { size: 22, stroke: 1.75, tone: 'inherit' }
)

const def = computed<Def>(() => ICONS[props.name])
const px = computed(() => (typeof props.size === 'number' ? `${props.size}px` : props.size))
</script>

<template>
  <svg
    class="ft-icon"
    :class="'ft-icon--' + tone"
    :style="{ width: px, height: px }"
    viewBox="0 0 24 24"
    aria-hidden="true"
  >
    <path
      v-for="(seg, i) in def.paths"
      :key="i"
      :d="seg.d"
      :fill="def.fill ? 'currentColor' : 'none'"
      :stroke="def.fill ? 'none' : 'currentColor'"
      :stroke-width="seg.sw ?? stroke"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
</template>

<style lang="scss" scoped>
.ft-icon {
  display: inline-block;
  flex-shrink: 0;
  vertical-align: middle;
}

.ft-icon--muted {
  color: $app-ink-muted;
}

.ft-icon--subtle {
  color: $app-ink-subtle;
}

.ft-icon--inverse {
  color: $app-surface;
}

.ft-icon--accent {
  color: $accent;
}

.ft-icon--gold {
  color: $app-star;
}
</style>
