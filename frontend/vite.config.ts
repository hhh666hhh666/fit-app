import { defineConfig } from 'vite'
import uniPlugin from '@dcloudio/vite-plugin-uni'
import { fileURLToPath, URL } from 'node:url'

// ⚠️ @dcloudio/vite-plugin-uni 是 CJS 包，工厂函数挂在 exports.default 上（带 __esModule）。
// 本项目 package.json 有 "type": "module"，配置文件以原生 ESM 加载，此时默认导入拿到的是
// module.exports 整个对象而不是那个函数，uni() 会直接报 "uni is not a function"。
// 这里做一次归一化，两条加载路径（原生 ESM / esbuild 打包成 CJS）都成立。
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const uni = (typeof uniPlugin === 'function'
  ? uniPlugin
  : (uniPlugin as unknown as { default: () => unknown }).default) as unknown as () => any

export default defineConfig({
  plugins: [uni()],
  css: {
    preprocessorOptions: {
      scss: {
        // wot-design-uni 的 scss 用了 @import 与 sass 全局内置函数（nth/unquote 等），
        // 在 Dart Sass 1.80+ 上是 deprecation warning。库不归我们改，静音掉以保持日志干净；
        // 这三类是「库的历史写法」，新写的样式不受影响。
        silenceDeprecations: ['legacy-js-api', 'import', 'global-builtin', 'if-function']
      }
    }
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    // 5175 避开 quizzy-web 的 5173 与 quizzy-mobile 的 5174
    port: 5175,
    // host: true 让同网段的手机能直接打开，用于真机预览
    host: true,
    proxy: {
      '/api': {
        // 本机 dev 后端端口 8083（见 backend application.yml 的注释：
        // 避开 8080=quizzy dev、8081/8082=容器 web）。
        target: 'http://127.0.0.1:8083',
        changeOrigin: true
      }
    }
  }
})
