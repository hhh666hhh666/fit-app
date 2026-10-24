# android-shell · fit-app 的 Android 壳

一个极简 WebView 壳，走**双轨加载**（[ADR 0034](../docs/adr/0034-shell-dual-track-local-and-auto-update.md)）：

1. **本地产物优先**——`filesDir/web`（已下载的新版）或 apk 内置的 `assets/web`，经虚拟域
   `https://appassets.androidplatform.net` 加载。**零网络等待**，这是「点开即用」的关键。
2. **线上兜底**——两侧都没有产物时，回落 `app_url`（`res/values/strings.xml`）。
3. **后台自动更新**——探测 `/web/version.json`，有新版就悄悄下好，**下次启动生效**。
   所以「网站更新、App 不必重发」这条仍然是成立的，只是新版要等下一次启动。

⚠️ 内置产物与线上产物**不是同一份构建**：内置版把 API 基址固化成了绝对地址（因为虚拟域与
接口不同源），必须用 `scripts/build-apk-web.sh` 生成，不能直接拷 `npm run build:h5` 的产物。

## 构建

前置（本机已就绪，与 quizzy 壳共用同一套环境）：

- JDK 21（`E:\jdk21`）、Gradle 8.9（`E:\develop\gradle-8.9`）、Android SDK
  （`E:\develop\android-sdk`：platform-tools + platforms;android-34 + build-tools;34.0.0）
- `local.properties`（**不入库**）：`sdk.dir` 与签名四件套
  （`fitapp.storeFile` / `fitapp.storePassword` / `fitapp.keyAlias` / `fitapp.keyPassword`）。
- **签名文件不入库**，现有三处：
  - 最早那台开发机的本仓 `.workbuddy/android-secrets/`（原件）；
  - 部署服务器 `~/secrets/`（异地备份兼新机器取用点：`fitapp.jks` ＋ 只存三行口令的 `fitapp-signing.properties`，
    目录 `700`、文件 `600`、属主 root）；
  - 其余开发机从服务器拉的工作副本 `~/.android-keys/`。`fitapp.storeFile` 写它的绝对路径（正斜杠，
    如 `C:/Users/<用户名>/.android-keys/fitapp.jks`），三个口令项照抄 properties：

  ```bash
  mkdir -p ~/.android-keys && scp "aliyun:~/secrets/fitapp*" ~/.android-keys/
  ```

```bash
# 1. 先产出内置 H5（注入 API 基址 + 复制进 assets/web + 生成自更新包与清单）
bash scripts/build-apk-web.sh

# 2. 再打 apk
cd android-shell
export JAVA_HOME=E:/jdk21
"E:/develop/gradle-8.9/bin/gradle" assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

`assets/web` 是生成物、不入库。**漏了第 1 步** 也能构建，只是没内置产物——壳会回落线上地址，
行为等同旧版（慢但能用）。

依赖拉取：`settings.gradle` 里阿里云镜像优先、google/central 兜底；
仓库的 `gradle.properties` **不写代理**：哪台机器要走本机代理（如 v2rayN），在那台的
`~/.gradle/gradle.properties` 里配 `systemProp.http(s).proxyHost/proxyPort`。流量本来就被透明代理接管的机器什么都不用配。

## 两个版本号，别混

| 版本 | 在哪 | 怎么推 |
|------|------|--------|
| **apk 版本** | `app/build.gradle` 的 `versionCode` / `versionName` | `versionName` 跟 git tag；`versionCode` 单调 +1（同一 keystore 下覆盖安装才认它）。当前 6 / `0.1.5` |
| **H5 产物版本** | 构建脚本自动生成：`package.json` 的 version ＋ 短 commit | 不用手工维护；带 commit 是为了「每次提交都能触发壳的自动更新」 |

改前端**不必**动 apk 版本号——壳会自动下载新产物。只有改了壳本身（Java、清单、依赖）才需要重打 apk 并 +1。

## 更新链路怎么走

1. `scripts/build-apk-web.sh` 生成 `dist/build/h5/web/version.json` 与 `web-<版本>.zip`；
2. 站点部署时这两项随 `dist` 一起上线（`/web/version.json` 已配成不缓存）；
3. 手机上打开 App → 后台拉到清单 → 版本不同就下载 zip → sha256 校验 → 解到 `web-staging`；
4. 下好后**弹窗提示**「新版本已下载」，给「立即重启 / 稍后」：
   - 「立即重启」→ 起一个全新实例（`CLEAR_TASK`），切换发生在它的 `onCreate` 最早期；
   - 「稍后」→ 下次启动 App 时生效。

所以：部署完成后，手机上打开一次 App 就会下载，弹窗点「立即重启」即是新版——**不需要重装 apk**。

⚠️ 弹窗里的「立即重启」用的是**全新实例**而不是 `recreate()`：后者会把 `savedInstanceState` 带回来、
走 `restoreState()`，结果是「资源是新的、页面快照是旧的」，看起来像没更新。

## 返回键：先关弹层，再后退

`onBackPressed` 先 `evaluateJavascript` 调 H5 的 `window.__ftBack()`（`frontend/src/utils/backStack.ts`）：
有底部弹层、确认框、操作菜单开着，它关掉最上面一层并返回 `true`，壳就到此为止；返回 `false`
（或 H5 是没有这个函数的旧版）才走「页面内可后退就后退、到根页退出」。

这条要 0.1.5 起的 apk 才有。旧 apk 装着新 H5 也不出错，只是返回键照旧直接后退——
弹层开着按返回会离开页面。

## 必须一起配的后端开关

apk 里的页面 origin 是 `https://appassets.androidplatform.net`，与接口域名不同源：

```
FITAPP_CORS_ALLOWED_ORIGINS=https://appassets.androidplatform.net
```

漏配的症状很好认：**页面能开、按钮有反应，但所有数据都是「网络异常」**；同时网页版一切正常。
这一項已写进根 `.env.example`。

## 验证

```bash
"E:/develop/android-sdk/build-tools/34.0.0/aapt2.exe" dump badging \
  app/build/outputs/apk/release/app-release.apk

# 确认内置产物真的进包了（应为几十个文件）
unzip -l app/build/outputs/apk/release/app-release.apk | grep -c "assets/web/"
```

## 已知边界

- 安装需允许「未知来源」（自签名）；上架需换正式签名。
- ⚠️ keystore 丢了就没法覆盖安装升级（只能卸载重装：本机登录态与没结束的训练会丢）。三处副本见「构建」一节；
  服务器那份会随 VPS 重装、换服务商一起没，别让它成为唯一的一份——原件所在的开发机退役前，另在密码管理器或网盘留一份。
- **升级后要重新登录一次**：localStorage 按 origin 隔离，原先存在线上域名下的 token 在虚拟域里读不到。只此一次。
- WebView 的返回键语义：页面内可后退则后退，到根页退出（见 `MainActivity`）。
- **新版不自动立刻生效**：下载完弹窗提示，点「立即重启」即刻切换、点「稍后」则下次启动生效。
  刻意如此——运行中替换 `filesDir/web` 会把 WebView 正在读取的文件抽掉。
- **下载失败是静默的**：只写 Logcat（tag `WebUpdater`），不弹任何东西。症状是「发了版但手机端没动静」，
  用 `adb logcat -s WebUpdater` 看那一刻到底探测到没有。
- 壳只是「容器」：前端迭代不动它；改 `app_url`、Java、依赖才需要重打包。
