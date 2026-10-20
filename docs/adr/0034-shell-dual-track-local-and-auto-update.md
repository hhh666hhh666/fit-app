# 壳改为双轨加载：本地产物优先 ＋ 后台自动更新

Status: accepted · 2026-10-20

起因：主人反馈 App「点图标后白屏几秒、切页与点按钮要等一下」。实测排除了后端（`/api` TTFB 67~96 ms）、压缩（主 JS gzip 后 98 KB）、静态缓存（`/assets/` 已 immutable）与 CSS 特效（`backdrop-filter` 0 处），真凶是纯客户端渲染的加载路径：每次冷启动都要重新把 HTML 与主包拉完才出首帧，而 `<div id="app">` 是空的、没有任何骨架，于是整段等待都表现为纯白。

本卡**收窄而非推翻** [ADR 0002](./0002-android-app-uni-app-shell.md)：客户端形态仍是「uni-app 网页 ＋ 自写 WebView 壳」，被改掉的只是「壳只加载线上地址」这一条加载策略。

## Considered Options

- **只加载线上地址（ADR 0002 原策略）**：否——网站更新即时生效的优点保留，但每次冷启动都要等网络把 HTML 与 JS 拉完，白屏几秒无法根治。
- **纯内嵌，`app_url` 直接指向本地产物**：否——白屏确实消失，但从此**每改一行前端就要重打重装 apk**。这个项目一天可能改数次前端，这个摩擦会迅速超过白屏本身，等于把 ADR 0002 最主要的收益丢掉。
- **换原生渲染（nvue／app-plus 或 Kotlin 重写）**：否——要重做整个前端，而实测证明瓶颈不是渲染引擎。
- **双轨：本地产物优先 ＋ 后台探测版本自动更新**（选定）——启动零网络等待，同时保住「网站更新、App 不必重发」。

## Consequences

- **加载顺序**：`filesDir/web`（已下载的新版）→ `assets/web`（apk 内置兜底）→ 线上 `app_url`（两侧都没产物时）。`LocalWeb.entryUrl()` 是这条顺序的真相源。
- **本地资源经虚拟域提供**：`WebViewAssetLoader` 把本地目录映射到 `https://appassets.androidplatform.net`，前端产物的 `base` 保持 `/` 不变。不用 `file://` 是因为它会同时丢同源策略、绝对路径与 Secure Context。
- **API 因此变成跨域**：页面 origin 与接口域名不同源，后端必须配 `FITAPP_CORS_ALLOWED_ORIGINS=https://appassets.androidplatform.net`。漏配的症状是「页面能开、数据全是网络异常」，而网页版完全不受影响——排查时先看这一项。
- **前端 API 基址改为构建期常量**：`frontend/src/api/base.ts` 的 `API_BASE` 由 `VITE_API_BASE` 注入；网页版仍是相对 `/api`。切换环境只能重新构建，不能靠运行期配置。
- **更新分两步，不打断运行**：下载只解到 `web-staging`，真正搬进 `web` 放在下次启动的最早期（`WebUpdater.applyPending()`），靠 `renameTo` 原子切换。所以**新版总是下次启动才生效**——这一点是刻意的，不是缺陷。
- **升级后要重新登录一次**：localStorage 按 origin 隔离，原先存在线上域名下的 token 在虚拟域里读不到。只此一次。
- **产物构建与打包绑在一起**：`scripts/build-apk-web.sh` 负责注入基址、复制进 `assets/web`、生成 `web-<版本>.zip` 与 `version.json`。版本号＝`package.json` 的 version ＋ 短 commit，带上 commit 是为了「每次提交都能触发更新」。
- **`assets/web` 不入库**（`.gitignore`），由脚本生成；它不存在时壳回落线上地址，仍可用。

> 关联：ADR 0002（被本卡收窄加载策略）、ADR 0020（会话本机持久化，与本次的 localStorage origin 变化无关但同属「本机存东西」）
