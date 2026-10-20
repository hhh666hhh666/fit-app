/**
 * 后端 API 的基址——全前端只有这一处定义，其余一律引用它。
 *
 * - **网页部署**（`https://fit.11-2.cn/`）：前端与 `/api` 同源，用相对路径；
 *   本机 dev 由 vite 代理 `/api` 到 8083，也是同源。
 * - **apk 内置版**：页面从虚拟域 `https://appassets.androidplatform.net/` 加载
 *   （见 `android-shell` 的 WebViewAssetLoader），与 API 不同源，必须用绝对地址。
 *   构建时由 `VITE_API_BASE` 注入，见 `scripts/build-apk-web.sh`。
 *
 * ⚠️ 这是一个**构建期常量**：Vite 在打包时把 `import.meta.env.VITE_API_BASE`
 * 静态替换成字面量，运行期读不到、也改不了。所以切换环境只能重新构建，
 * 不能靠改本机配置。
 */
export const API_BASE = import.meta.env.VITE_API_BASE || '/api'
