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
  keystore 在本仓 `.workbuddy/android-secrets/`（**不入库**）。

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
`gradle.properties` 配了 v2rayN 代理（兜底请求走代理分流）。

## 两个版本号，别混

| 版本 | 在哪 | 怎么推 |
|------|------|--------|
| **apk 版本** | `app/build.gradle` 的 `versionCode` / `versionName` | `versionName` 跟 git tag；`versionCode` 单调 +1（同一 keystore 下覆盖安装才认它）。当前 4 / `0.1.3` |
| **H5 产物版本** | 构建脚本自动生成：`package.json` 的 version ＋ 短 commit | 不用手工维护；带 commit 是为了「每次提交都能触发壳的自动更新」 |

改前端**不必**动 apk 版本号——壳会自动下载新产物。只有改了壳本身（Java、清单、依赖）才需要重打 apk 并 +1。

## 更新链路怎么走

1. `scripts/build-apk-web.sh` 生成 `dist/build/h5/web/version.json` 与 `web-<版本>.zip`；
2. 站点部署时这两项随 `dist` 一起上线（`/web/version.json` 已配成不缓存）；
3. 手机上打开 App → 后台拉到清单 → 版本不同就下载 zip → sha256 校验 → 解到 `web-staging`；
4. **下次启动**最早期 `renameTo` 切成 `web`，原子生效。

所以：部署完成后，手机上要**再打开一次** App 才是新版（第一次是下载，第二次生效）。

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
- ⚠️ keystore 丢了就没法覆盖安装升级——备份 `.workbuddy/android-secrets/`。
- **升级后要重新登录一次**：localStorage 按 origin 隔离，原先存在线上域名下的 token 在虚拟域里读不到。只此一次。
- WebView 的返回键语义：页面内可后退则后退，到根页退出（见 `MainActivity`）。
- 新版**下次启动**才生效，不打断当前使用——刻意如此，避免替换正在被读取的文件。
- 壳只是「容器」：前端迭代不动它；改 `app_url`、Java、依赖才需要重打包。
