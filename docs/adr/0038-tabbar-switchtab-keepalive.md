# 主 tab 导航改为 tabBar + switchTab 保活

Status: accepted · 2026-10-21

起因：真机连着报三件事——切页**极短暂白屏**、滚动位置回到顶部、日志页选中月份回到当月。三件事是**同一个根**：四个主页面之间走 `uni.reLaunch`（[ADR 0035](./0035-client-cache-and-invalidation.md) 定的），而 `reLaunch` 会**先把旧页拆掉、再建新页**，中间必然有一段「DOM 上一个页面都没有」的空档（录屏 30fps 里是 1 帧 ≈ 33ms，人眼读成「闪一下」）。

2026-10-21 15:14 的截图证据：那一帧**连底栏都不存在**、整页只剩画布底色——排除了「数据没回来」（那种情况标题与 tabbar 会先在）。ADR 0037 的入场动画与 0.17.3 的 chunk 预热只能削掉「等网络」那一段，挂载空档消不掉。

本卡定的是：把导航形态从「每次重建」换成「保活」，一次解决白屏、滚动位置、月份三件事。

## Considered Options

- **保持 `reLaunch`，只把滚动位置与月份存进 Pinia 复原**：否——治不了白屏。页面照样销毁重建，空档照样存在；而且滚动复原还要赌「DOM 高度稳定」的时序。
- **接受这 1 帧，只保证空白帧是设计底色**：否——空白帧里**底栏也一起消失**，视觉上是「整页白」而不是「内容区闪一下」，这正是最刺眼的部分；不做结构改动就没法让底栏留在原地。
- **壳页 ＋ 横向 track（四页并排、`translateX`）**：否——上限最高（还能顺手加左右滑动手势），但等于重写导航层：四页的文档级滚动要改独立 `scroll-view`、下拉刷新要自实现、`onShow` 取数语义要重建、懒加载变成首屏全量。与「消除一帧白屏」不成比例。
- **`tabBar` ＋ `switchTab` 保活（选定）**：四页在 `pages.json` 里注册成 tabBar 页，框架按 `type: "tabBar"` 缓存页面、**永不 prune**，切页不再卸载。

## Consequences

- **保活的实现依据是框架的缓存策略**，不是自造：`RouterView` 外面套的 `KeepAlive` 用 `routeCache`，而 `pruneRouteCache()` 对 `type === "tabBar"` 的缓存条目**直接跳过**——所以 tab 页一旦挂过就一直在。`pages.json` 声明 `tabBar.list` 之后，四条路由的 `meta` 会带上 `isTabBar: true` 与 `tabBarIndex`，这是开关。
- **H5 不认 `custom: true`**，必须自己压掉内置栏：运行期只判 `route.meta.isTabBar && tabBar.shown`，两个都为真就渲染一条 `uni-tabbar`，并给 `uni-page-wrapper` 扣掉 `--tab-bar-height` 的高度。所以 `App.vue` 里三件事一起做：`uni-tabbar { display: none }`、wrapper 高度复位 100%、`::after` 占位块去掉。
  ⚠️ **不用 `uni.hideTabBar()`**：它是异步 API，会晚一拍，先闪一条内置栏；CSS 才能保证首帧就生效。
- **切页动效改成 `onShow` 驱动**（WAAPI，`utils/nav.ts` 的 `playTabEnter`）：页面不再重建，靠「挂载时带 class」只能播一次；WAAPI 可以每次 `onShow` 直接重放，也不用读 `offsetWidth` 强制重排。`fill: backwards` 保证结束后不残留 `transform`（0.17.3 的教训）。指示条同理，由页面把 `enterSeq` 传下去触发重放。
- **跳转 API 的边界变了**：`navigateTo` / `redirectTo` 指向 tabBar 页会被框架直接拒掉（`can not navigateTo a tabbar page`）；**跳这四个页面一律 `switchTab`（不能带参数）**，跳非 tab 页仍用 `reLaunch` / `navigateTo`。本轮同步改了三处：登录成功、会话页返回失败兜底、汇总页回首页。
- **`getCurrentPages()` 只返回当前 active 的那个 tab 页**（框架对 tabBar 页做了过滤），依赖页面栈的判断要避开这条。
- **滚动位置与日志页月份天然保留**——它们不再丢，是因为页面没被销毁，不需要任何「存了再复原」的代码。`docs/todo/archive/2026-10-21-TODO-切页体验后续.md` 里的方案甲/丙因此作废。
- **本地冒烟结论**（headless Chrome + 打桩 `/api`）：点日志后 hash 变 `#/pages/log/index` 且日志页渲染；点回首页后 `.home` 是**同一个 DOM 节点**（切走前打的标记还在）——保活成立；`uni-tabbar` 计算样式为 `display: none`；无 console 报错。
- **明知的代价**：首次切到某个 tab 仍要现拉 chunk（预热只是缩短，不是消除）；`switchTab` 丢 query，将来若要带参数得走 store。

> 关联：ADR 0035（导航形态与缓存）、ADR 0037（切页动效）
