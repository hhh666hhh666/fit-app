# 客户端形态：Android 装机 App（uni-app 网页 ＋ WebView 壳）

Status: accepted

客户端形态定为 Android 装机 App：前端用 uni-app 工程实现（编译为 H5 网页），外面套一个极简 WebView 壳（加载线上地址）打成 apk，自用侧载、不上架。与 quizzy 移动端（quizzy-mobile ＋ android-shell）完全同构，配方可直接复用。现有的 Vue3 ＋ shadcn-vue 网页骨架（`frontend/`）退役——它只有 hello world、无业务损失；组件库改用移动生态的 wot-design-uni。桌面端不另做，由 uni-app 的 H5 在浏览器兼顾。

## Considered Options

- **纯响应式网页（Web 先行）**：否——明确要装机体验（桌面图标兼将来小组件等长期能力），且壳路线相对现有工程的增量很小。
- **Capacitor 套壳现有 Web 工程**：否——本项目无先例；quizzy 已验证「自写壳」更轻（无框架依赖、几乎零维护）。
- **微信小程序**：否——自用价值≈0、审核是负担；uni-app 保留了将来开启它的可能。
- **uni-app 重搭 ＋ 自写 WebView 壳**（选定）——与 quizzy 全栈同构，移动组件生态已经过实战。

## Consequences

- `frontend/`（Vue3 ＋ shadcn-vue）退役，新建 uni-app 工程；请求封装、设计变量的写法可搬运。
- 壳加载线上地址：网站更新手机端即时生效、无需重发 apk；前提是网站已上线（执行顺序：先做网站 → 上云 → 打壳）。
- 构建走本机工具链（JDK 21 ／ Gradle 8.9 ／ Android SDK 已就绪）；apk 侧载安装；keystore 不入库、务必备份（丢失将无法覆盖升级）。
- 小组件／手环／真离线等原生能力不在壳的现成能力内，需要时另行评估（见 `docs/todo/` 待办）。
- iOS 无路径（自用设备为 Android，且 Windows 本机不可构建）。

> ⚠️ **后续（2026-10-12）**：壳已落地——`android-shell/` 建仓并构建出**可安装 apk**
> （`com.fitapp.app`，versionCode 2 / versionName 0.1.1，签名 keystore 在 `.workbuddy/android-secrets/` 不入库）。
> 加载地址 `https://fit.11-2.cn/`（站点已于同日上线）。执行顺序与 Consequences 里预想的一致：
> 先网站 → 上云 → 打壳。apk 未随版本发布流水线走（本机构建、人工交付），
> 将来若要「每次发版自动出 apk」，再评估把 Gradle 构建搬进 CI。
>
> ⚠️ **后续（2026-10-13）**：壳补上**文件选择器**能力——WebView **默认不接管** `<input type="file">`
> 的点击（而 H5 版 `uni.chooseImage` 底层正是「隐藏 input ＋ click()」这条路），于是 **App 里
> 点「拍照记录这一餐」毫无反应、手机浏览器却正常**。已在 `MainActivity` 实现
> `WebChromeClient.onShowFileChooser`：选择器提供「相册 ＋ 拍照」两个来源，拍照输出走
> FileProvider 授权应用私有目录，因此**无需申请相机/存储权限**。
> 口径：**壳的能力清单随用随补**——凡是 H5 依赖「浏览器接管」的行为（文件选择、下载、分享等），
> 都要先确认壳有没有对应实现，不能默认 H5 能跑就等于 App 能跑。
