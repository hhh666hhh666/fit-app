/// <reference types="vite/client" />

/** 自定义构建期变量：见 `src/api/base.ts` 与 `scripts/build-apk-web.sh`。 */
interface ImportMetaEnv {
  /** 后端 API 基址；不设则回落到同源相对路径 `/api`。apk 内置版构建时必须显式给。 */
  readonly VITE_API_BASE?: string
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

/** 构建期注入的应用版本（`vite.config.ts` 的 define，取自 package.json）；缓存快照的版本闸用它。 */
declare const __APP_VERSION__: string
