---
name: fit-app · 晴窗
description: 一个人的健身记录工具；冷调浅蓝灰底上的苹果式玻璃面板，橙色只给动作。
colors:
  canvas: "#eff4f9"
  canvas-top: "#e4ecf5"
  canvas-bottom: "#f6f9fc"
  surface: "#ffffff"
  surface-tint: "rgba(120, 140, 170, 0.12)"
  glass: "rgba(255, 255, 255, 0.62)"
  glass-strong: "rgba(250, 252, 254, 0.72)"
  glass-edge: "rgba(255, 255, 255, 0.85)"
  mask: "rgba(29, 29, 31, 0.32)"
  line: "rgba(60, 80, 110, 0.14)"
  line-soft: "rgba(60, 80, 110, 0.08)"
  line-dash: "#c7ccd4"
  ink: "#1d1d1f"
  ink-2: "#3a3a3c"
  ink-muted: "#6e6e73"
  ink-subtle: "#8e8e93"
  ember: "#d9521b"
  ember-deep: "#b8410f"
  ember-top: "#e8642c"
  ember-bottom: "#d24b15"
  ember-wash: "#fde8dc"
  ember-row: "#fcede3"
  ember-ring: "#f2c7aa"
  danger: "#c0392b"
  star: "#e0a93b"
  protein: "#e99a5e"
  carb: "#86a9d9"
  fat: "#e08f8f"
  water: "#74bdb5"
  meal-breakfast-bg: "#fdefe4"
  meal-breakfast-fg: "#c2581f"
  meal-lunch-bg: "#fdf3dc"
  meal-lunch-fg: "#b07a12"
  meal-dinner-bg: "#eceaf6"
  meal-dinner-fg: "#6257a8"
  meal-snack-bg: "#fbe7e7"
  meal-snack-fg: "#b24b4b"
  meal-water-bg: "#e2f2f0"
  meal-water-fg: "#2f8178"
  meal-weight-bg: "#e6f4e1"
  meal-weight-fg: "#3d8b37"
  viewer-bg: "rgba(10, 11, 13, 0.96)"
  viewer-ink: "#ffffff"
  viewer-chip: "rgba(255, 255, 255, 0.14)"
typography:
  display:
    fontFamily: "Nunito, -apple-system, 'PingFang SC', 'HarmonyOS Sans SC', 'MiSans', 'Noto Sans SC', sans-serif"
    fontSize: "40px"
    fontWeight: 800
    lineHeight: 1.05
    letterSpacing: "-0.02em"
  headline:
    fontFamily: "-apple-system, 'SF Pro Text', 'PingFang SC', 'HarmonyOS Sans SC', 'MiSans', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: "30px"
    fontWeight: 700
    lineHeight: 1.25
  title:
    fontFamily: "-apple-system, 'SF Pro Text', 'PingFang SC', 'HarmonyOS Sans SC', 'MiSans', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: "15px"
    fontWeight: 700
    lineHeight: 1.6
  body:
    fontFamily: "-apple-system, 'SF Pro Text', 'PingFang SC', 'HarmonyOS Sans SC', 'MiSans', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.6
  label:
    fontFamily: "-apple-system, 'SF Pro Text', 'PingFang SC', 'HarmonyOS Sans SC', 'MiSans', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: "12px"
    fontWeight: 400
    lineHeight: 1.6
  number:
    fontFamily: "Nunito, -apple-system, 'PingFang SC', sans-serif"
    fontSize: "14px"
    fontWeight: 700
    lineHeight: 1.4
rounded:
  xs: "8px"
  sm: "10px"
  md: "16px"
  lg: "24px"
  xl: "28px"
  pill: "999px"
spacing:
  hairline: "4px"
  tight: "8px"
  stack: "12px"
  card-x: "18px"
  card-y: "16px"
  gutter: "20px"
  float-side: "16px"
  float-gap: "14px"
components:
  button-primary:
    backgroundColor: "{colors.ember}"
    textColor: "{colors.surface}"
    rounded: "{rounded.pill}"
    height: "46px"
    padding: "0 20px"
  button-primary-floating:
    backgroundColor: "{colors.ember}"
    textColor: "{colors.surface}"
    rounded: "{rounded.pill}"
    height: "52px"
  button-secondary:
    backgroundColor: "{colors.glass-strong}"
    textColor: "{colors.ink-2}"
    rounded: "{rounded.pill}"
    height: "46px"
    padding: "0 20px"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.ink-muted}"
    rounded: "{rounded.pill}"
    height: "46px"
  button-danger:
    backgroundColor: "transparent"
    textColor: "{colors.danger}"
    rounded: "{rounded.pill}"
    height: "46px"
  card-glass:
    backgroundColor: "{colors.glass}"
    rounded: "{rounded.lg}"
    padding: "16px 18px"
  sheet:
    backgroundColor: "{colors.canvas-bottom}"
    rounded: "{rounded.xl}"
    padding: "10px 20px 20px"
  sheet-group:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.lg}"
    padding: "2px 14px"
  segment-item:
    textColor: "{colors.ink-muted}"
    rounded: "{rounded.pill}"
    padding: "6px 14px"
  segment-item-on:
    backgroundColor: "{colors.surface-tint}"
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
  badge:
    backgroundColor: "{colors.surface-tint}"
    textColor: "{colors.ink-muted}"
    rounded: "{rounded.pill}"
    padding: "1px 6px"
  badge-pr:
    backgroundColor: "{colors.ember-wash}"
    textColor: "{colors.ember-deep}"
    rounded: "{rounded.pill}"
    padding: "1px 6px"
  input-field:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    height: "46px"
    padding: "0 14px"
  tabbar:
    backgroundColor: "{colors.glass-strong}"
    rounded: "{rounded.pill}"
    height: "62px"
  tabbar-item-on:
    backgroundColor: "{colors.surface-tint}"
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
---

# Design System: fit-app · 晴窗

<!-- 从 frontend/src/uni.scss 与 components/Ft*.vue 派生（impeccable document，2026-10-24）。
     实现才是真相：改颜色／间距／圆角只改 uni.scss；本文件过时了就重新生成，不要单独手改。
     定稿理由与被否方案见 docs/adr/0041-design-language-qingchuang.md。 -->

## Overview

**Creative North Star: "晴窗"（Clear-Sky Window）**

晴空下的一扇扇玻璃窗：冷调浅蓝灰的天光铺满整屏，内容是浮在上面的半透明玻璃面板，透出底下几团柔和的光晕。界面本身尽量安静，冷中性灰的字、大圆角、柔和投影，唯一会「喊」的颜色是橙色，它只出现在用户要做的那个动作上：开始训练、完成本组、继续、PR、当前组。

这是一个在健身房组间单手操作的工具（PRODUCT.md：一组记录约 30 秒内完成），所以密度偏松、点击区偏大、一屏只有一个焦点。数字是主角：重量、次数、容量、体重都用 Nunito 粗体，0–9 等宽，计时器跳秒不抖。中文只用 400／700 两档字重，靠字号和颜色分层，不靠中间字重。

只做浅色，不做深色模式；全站唯一的深色面是全屏看图浮层（照片要压在黑底上看）。已否掉的方向：纸感墨黑加荧光笔（旧「训练日志」）、电光蓝运动风、Win11 Fluent 小圆角、深色液态玻璃、同心环营养图。

**Key Characteristics:**

- 冷调浅蓝灰竖向渐变底 ＋ 角落光晕，钉在视口上不随内容滚动
- 苹果式玻璃面板：半透白 ＋ 背景模糊与饱和 ＋ 白色亮边 ＋ 极细外描边 ＋ 柔和投影
- 橙色只给动作，一屏最多一颗橙色主按钮
- 选中态一律「深字 ＋ 浅灰蓝胶囊底」，不用橙
- 大圆角（卡片 24px、弹层 28px、按钮与控件全胶囊）
- 底部弹层是实底，里面的分组是不透明白卡
- 悬浮件（底栏、主按钮、汇总条、多选操作条）统一贴底悬浮，左右 16px、离底 14px ＋ 安全区

## Colors

冷中性的天光与墨色打底，一种饱和的余烬橙负责全部「动作」，营养与餐次各有一组低饱和的识别色。

### Primary

- **余烬橙 Ember**（ember，主按钮渐变从 ember-top 到 ember-bottom）：开始训练、完成本组、继续训练、保存这类「本屏唯一的主动作」；会话页当前组的圆钮。按钮是从上到下的竖向渐变，下方带同色的柔光投影。
- **深余烬 Ember Deep**（ember-deep）：PR 角标与 PR 组数值的文字色、浅橙底上的字。
- **余烬薄雾 Ember Wash**（ember-wash）：PR 角标底、「待核对」「失败」这类提示角标的底。
- **余烬行 Ember Row／余烬圈 Ember Ring**（ember-row、ember-ring）：会话页当前组整行的浅橙底与描边，只在那一行出现。

### Secondary

- **营养四色**（protein 杏橙、carb 雾蓝、fat 玫粉、water 青绿）：营养素进度条，首页与饮食页共用；每色另有一条极浅的轨道色（见 `uni.scss` 的 `$ring-*-track`）。kcal 进度条是一条从浅橙到深橙的横向渐变（`$ring-kcal`）。
- **餐次六组**（breakfast 晨橙、lunch 麦黄、dinner 暮紫、snack 饼干红、water 湖青、weight 草绿，各一对 bg／fg）：餐次入口图标块、相册照片的餐次角标。每组都是「浅色底 ＋ 同色系深字」，六个入口各占一个色相。

### Tertiary

- **危险红 Danger**（danger）、**星标金 Star**（star）：状态色。危险红只给删除（确认弹窗、批量删除按钮、页头的删除字）、失败与待核对提示、存储接近上限的进度条；星标金只给食物收藏的实心星。JS 里需要危险红（确认框的确认钮）时从 `utils/palette.ts` 取，守卫逐行核对两边一致；确认框一律走 `utils/confirm.ts` 的 `ask()`，传 `danger` 即标红。

### Neutral

- **天光 Canvas**（canvas，渐变从 canvas-top 到 canvas-bottom）：页面底；纯色兜底给切页克隆层。弹层的实底用 canvas-bottom。
- **纸白 Surface**（surface）：弹层里的分组白卡、玻璃卡里的输入框、勾选圈底。
- **灰蓝薄纱 Surface Tint**（surface-tint）：所有选中态的胶囊底、次要小按钮底、角标底、占位块。
- **玻璃 Glass／强玻璃 Glass Strong**（glass、glass-strong）：卡片面板／页头胶囊按钮、搜索框、底栏、悬浮操作条；玻璃亮边是 glass-edge。
- **墨 Ink 四档**（ink 正文与标题、ink-2 胶囊按钮与入口文字、ink-muted 次要文字、ink-subtle 占位与装饰）：ink-subtle 不用于正文；12px 以下的字一律过 4.5:1。
- **线三档**（line 主分隔、line-soft 卡内行间细线、line-dash 虚线占位与未选中的勾选圈）。
- **遮罩 Mask**（mask）：弹层遮罩。
- **看图深色组**（viewer-bg、viewer-ink、viewer-chip）：只给全屏看图浮层。

### Named Rules

**The One Ember Rule.** 橙色只给动作，一屏最多一颗橙色主按钮。导航选中、分段控件、装饰、图表都不用橙。

**The Calm Selection Rule.** 选中＝深字 ＋ 灰蓝薄纱胶囊底（surface-tint），加粗；底栏、分段控件、筛选、单位切换、组号选择全站一个样子。

**The Single Source Rule.** 色值只写在 `frontend/src/uni.scss`；页面与组件里写死一个色值，CI 的写死颜色守卫就失败。JS 里只能用字符串色值的地方从 `utils/palette.ts` 取。

## Typography

**Display Font:** Nunito 700／800（自托管拉丁子集，回落系统字体）
**Body Font:** 系统中文字体栈（-apple-system、PingFang SC、HarmonyOS Sans SC、MiSans、Noto Sans SC）
**Label/Mono Font:** 数字、单位与拉丁字符一律走 Nunito（含 `kg`、`×`）

**Character:** 圆润厚实的 Nunito 数字配克制的系统中文，数字负责「进步看得见」，中文只负责说清楚。

### Hierarchy

- **Display**（Nunito 800，40px，行高 1.05，字距 -0.02em）：首页主数字、当前体重这类整卡主角。汇总／详情三格指标用同族 22px。
- **Headline**（中文 700，30px，行高 1.25）：页头大标题；上方可带一行 13px 的灰色上标（日期、所属）。
- **Title**（中文 700，15px）：卡片标题；弹层标题用 20px 700。
- **Body**（中文 400，14px，行高 1.6）：正文、列表主行（列表行名称用 700）。
- **Label**（中文 400，11–13px）：副标题、说明、单位、角标。
- **Number**（Nunito 700，随所在层级取字号）：所有重量、次数、日期、时长、容量。

### Named Rules

**The Two Weights Rule.** 中文只用 400 与 700；800 只给 Nunito 大数字。安卓各家中文字体的中间字重不可靠，层级靠字号和颜色拉开。

**The Numbers Speak Nunito Rule.** 凡是数字（含日期、计时、单位符号）都加数字字体；0–9 本身等宽，不依赖 `tnum`。

## Layout

单列移动布局，设计宽 375px（750rpx，1px＝2rpx）。正文左右留白 20px，卡片之间统一 12px 纵向间距，卡片内边距约 16px × 18px；行与行之间用 line-soft 细线分隔，不用额外间距。二级页顶部是页头（返回 ＋ 大标题 ＋ 右侧胶囊按钮），四个主页面底部钉着悬浮底栏，正文末尾为底栏让出高度。

悬浮件统一几何：左右 16px、离屏底 14px ＋ 安全区，主按钮高 52px。正文末尾要让出悬浮件的高度（约 100px ＋ 安全区）；末尾若有居中元素，还要多让出底栏中间圆钮高出胶囊的约 34px。电脑浏览器打开同一份 H5，不做独立的桌面布局。

## Elevation & Depth

深度来自「玻璃叠在光晕上」，不是阴影堆叠。页面只有三层：钉在视口上的渐变底，浮在上面的玻璃面板，以及贴底悬浮的件和底部弹层。阴影都很柔、偏冷蓝灰，负的扩散半径让它只在面板下方晕开；玻璃的立体感主要靠顶边一条白色内亮边。

### Shadow Vocabulary

- **玻璃卡**（`box-shadow: inset 0 1px 0 rgba(255,255,255,.95), 0 0 0 .5px rgba(30,50,80,.06), 0 12px 32px -16px rgba(30,50,80,.22)`）：所有玻璃卡片。
- **轻浮起**（`box-shadow: 0 4px 12px -6px rgba(30,50,80,.2)`）：页头胶囊按钮、搜索框、分段控件。
- **悬浮件**（`box-shadow: 0 12px 24px -8px rgba(30,50,80,.22)`）：底栏、多选操作条、会话页汇总条。
- **弹层**（`box-shadow: 0 -8px 24px -12px rgba(30,50,80,.25)`）：底部弹层上缘。
- **橙色柔光**（`box-shadow: inset 0 1px 0 rgba(255,255,255,.3), 0 10px 20px -8px rgba(217,82,27,.65)`）：橙色主按钮与圆钮，只跟着橙色出现。

### Named Rules

**The Sliding Glass Rule.** 主 tab 切页滑动的那 260ms 里，玻璃去掉背景模糊、换成更实的白（`glass` mixin 自动处理），安卓 WebView 才不掉帧；会动的玻璃一律用 mixin，不手写 `backdrop-filter`。

**The Solid Sheet Rule.** 底部弹层是实底，不做玻璃：底下压着遮罩，没有可透的东西。弹层里的分组用不透明白卡。

## Shapes

全站大圆角、无直角：卡片 24px，弹层上缘 28px，输入框 16px，小输入格与照片格 10px；按钮、分段控件、角标、搜索框、底栏全部是胶囊。勾选与单选是 20px 圆圈。底栏顶边正中有一个带圆角过渡的真凹口，中间的橙色圆钮（60px）嵌在里面。虚线框（2px dashed line-dash，16px 圆角）只表示「在这里添加」：添加动作、加一组、添加食物。

## Components

### Buttons

- **Shape:** 胶囊（999px）；标准高 46px，悬浮主按钮 52px，小号 32px。
- **Primary:** 余烬橙竖向渐变 ＋ 白字 700 ＋ 橙色柔光投影；一屏最多一颗。
- **Hover / Focus:** 移动端没有悬停；按下统一缩到 0.97，150ms 快出缓收。禁用＝40% 不透明度。
- **Secondary / Ghost:** 次级是强玻璃胶囊 ＋ ink-2 字（如「知道了」「去填写」）；幽灵是透明底 ＋ line 描边 ＋ ink-muted 字（如「先不练」）。
- **页头胶囊:** 页头右侧的「新建／编辑／取消」用强玻璃小胶囊；危险动作（删除计划）只把字换成危险红，不上色块。
- **危险实心:** 只有批量删除（多选操作条上）用危险红实心胶囊 ＋ 白字。
- **危险次要（danger）:** 透明底 ＋ line 描边 ＋ 危险红字，给弹层里和主按钮并列、会丢数据的那个选项（如「结束并开新的」）；点了还要再确认一次。

### Chips

- **Style:** 灰蓝薄纱底 ＋ ink-muted 小字的胶囊角标（预设、自定义、循环A、今天）；PR 角标是余烬薄雾底 ＋ 深余烬字。
- **State:** 可选的筛选片（如肌群）未选中是 line 描边 ＋ ink-muted，选中按 The Calm Selection Rule 去描边、上灰蓝胶囊底。

### Cards / Containers

- **Corner Style:** 24px。
- **Background:** 玻璃（glass），白色亮边。
- **Shadow Strategy:** 玻璃卡阴影（见 Elevation）。
- **Border:** 0.5px 白色亮边（glass-edge）。
- **Internal Padding:** 约 16px × 18px；列表卡左右收到 14px、上下 4px，让行自己撑出节奏。

### Inputs / Fields

- **Style:** 玻璃卡里是纸白底、16px 圆角、无描边的输入框，标签在左或在上；弹层白卡里的小输入格反过来用灰蓝薄纱底、10px 圆角。数值右对齐、走数字字体。搜索框是强玻璃胶囊 ＋ 放大镜图标 ＋ 圆形清空钮。
- **Focus:** 沿用系统光标，不画焦点环。
- **Error / Disabled:** 校验错误用 toast 说明，不染红输入框；识别不确定的字段旁挂「待核对」角标（余烬薄雾底 ＋ 危险红字）。

### Navigation

- **底栏:** 悬浮强玻璃胶囊，高 62px，五等分：首页、日志、中间训练圆钮、统计、我的。选中项＝深色字 ＋ 填充图标 ＋ 灰蓝胶囊底，这块胶囊底在切页时滑动（跳过中间格）。中间圆钮（直径 60px）圆心正好落在胶囊顶边上——过圆心的水平线与顶边重合，一半露在胶囊上方，凹口与它同心、四周留 6px；平时是哑铃（开始训练），有进行中的训练时变播放图标（继续），外圈绕一颗流星。
- **页头:** 二级页是「返回 ＋ 30px 大标题（可带上标）＋ 右侧胶囊按钮」；主 tab 页没有返回。
- **分段控件:** 玻璃胶囊壳 ＋ 6px 内边距，选中项灰蓝胶囊底 ＋ 深字 700；项目多时壳不动、里面横滑。

### Bottom Sheet

实底弹层（canvas-bottom），上缘 28px 圆角 ＋ 顶部把手，从底部 220ms 滑入、遮罩淡入；标题 20px 700，右侧一枚灰底圆形关闭钮，可带一行灰色副标题。内容是一组组白卡，长内容在中间滚动，主按钮固定在最下面。

- **关法五种、走同一个口子:** 点遮罩、关闭钮、按住顶部（把手 ＋ 标题行）往下拉、安卓返回键、电脑 Esc；都先 220ms 滑出再消失。
- **表单弹层防误触:** 里面的输入框改过之后，上面任何一种关法都先问「放弃填写的内容？」（危险红「放弃」）。
- **二选一不用确认框:** 要在两个动作之间选（如「继续上次／结束并开新的」）就做成弹层里的两枚按钮，危险的那个用 danger 次要钮。

### Confirm Dialog / Action Sheet / Toast

uni 自带的三件套在 H5 是运行时渲染的，皮在 `App.vue` 里按类名覆盖成晴窗：

- **确认框:** 实底 24px 圆角卡居中，标题 16px 700、正文 ink-muted；底部两枚胶囊——左「取消」灰蓝薄纱底 ＋ ink-muted 字，右「确认」纸白底 ＋ line 内描边 ＋ ink 字（删除、放弃类换危险红字）。左边的取消位只放「不做」：安卓返回键会替用户点它。
- **操作菜单:** 贴底实底面板（与悬浮件同位：左右 16px、离底 14px ＋ 安全区），24px 圆角，菜单项 ink、取消 ink-muted 700；「删除」项不单独标红，点了再走红色确认框。
- **轻提示:** ink 深色底 ＋ 纸白字，16px 圆角，长文案折行居中；成功提示一律纯文字，不带勾。

### Floating Action Bar

贴底悬浮的强玻璃胶囊，与主按钮同一位置：多选时左边「已选 N 项」，右边「全选」文字按钮 ＋ 危险红实心「删除」。进入多选时页头右侧换成「取消」，页头本身不换。

### Session Current Set（Signature）

会话页里正在做的那一组整行浅橙底（ember-row）＋ 浅橙描边（ember-ring）＋ 白色输入框，右侧橙色圆钮＝完成本组，是这一屏唯一的橙色焦点；已完成的组只读并带浅橙勾，还没轮到的组只读并带虚线圈。

## Do's and Don'ts

### Do:

- **Do** 改颜色、间距、圆角只改 `frontend/src/uni.scss`，新增 token 后重启 dev 服务器。
- **Do** 会动的玻璃用 `@include glass`，玻璃卡统一「玻璃底 ＋ 白色亮边 ＋ 24px 圆角 ＋ 玻璃卡阴影」。
- **Do** 页面里的共用形状（玻璃卡、页头胶囊、分段控件、卡片标题、角标、多选操作条、弹层白卡）类名写在页面里，声明体 `@include` `uni.scss` 里的同名 mixin（`glass-card`、`glass-pill`、`seg-*`、`card-title`、`badge`、`selbar`、`sheet-card`），不在页面里重抄一遍。
- **Do** 每屏只留一个橙色主动作，其余动作用强玻璃胶囊、幽灵按钮或文字按钮。
- **Do** 所有数字（含日期、计时、单位）加数字字体；数字带对比基准时，差值用 ink-2，不用红绿。
- **Do** 悬浮件统一左右 16px、离底 14px ＋ 安全区，正文末尾让出它的高度。
- **Do** 底部弹层用 `FtSheet`，里面用白卡分组；没有副标题时内容自己往下让 14px。
- **Do** 空状态用 `FtEmpty`：一句标题 ＋ 一句「怎么办」，按钮放插槽里。

### Don't:

- **Don't** 在页面或组件里写死色值（CI 守卫硬失败）。
- **Don't** 用橙色做选中态、装饰、图表线或第二颗主按钮。
- **Don't** 中文用 500／600 这类中间字重。
- **Don't** 做深色模式，也不把 token 迁成 CSS 变量。
- **Don't** 用同心环表示营养素，营养素一律是「名称 ＋ 数值／目标 ＋ 进度条」。
- **Don't** 把会丢数据的动作放进确认框的取消位（安卓返回键会点到它）；二选一用弹层的两枚按钮。
- **Don't** 给底部弹层做玻璃，也不要把 `FtSheet` 放进带 `backdrop-filter`／`transform` 的祖先里（fixed 会跟着祖先定位）。
- **Don't** 在 `scroll-view` 里的卡片上用外投影画描边，会被裁掉，改用 border。
- **Don't** 加英文装饰行（如 `FIT-APP LEDGER`、`SEC.04_LIB` 这类旧设计的上标）。
