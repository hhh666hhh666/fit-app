// ── JS 侧要用的那几个颜色：`uni.scss` token 的镜像 ──
// 有些地方只能拿字符串色值、读不到 SCSS 变量：Canvas 画图（FtTrendChart）、
// `uni.showModal` 的 confirmColor。这些地方一律从这里取，别在页面里写色值字面量。
//
// ⚠️ 每一行行尾的注释写明它镜像的是哪个 token，`scripts/check-hardcoded-colors.sh`
//    会逐行核对两边的值一致（改了 uni.scss 忘了改这里，CI 会挂）。新增一项照同样的格式写。

export const PALETTE = {
  ink: '#1d1d1f', // $app-ink
  inkMuted: '#6e6e73', // $app-ink-muted
  inkSubtle: '#8e8e93', // $app-ink-subtle
  line: 'rgba(60, 80, 110, 0.14)', // $app-line
  surface: '#ffffff', // $app-surface
  danger: '#c0392b' // $app-danger
} as const
