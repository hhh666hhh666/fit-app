import { defineConfig } from 'vite'
import uniPlugin from '@dcloudio/vite-plugin-uni'
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

// 构建期版本号：缓存快照靠它判断「这份数据是不是上个版本写的」（ADR 0036）。
// 直接读 package.json，不引 JSON 导入断言——那条路径在不同 ESM 加载器上行为不一致。
const PKG = JSON.parse(
  readFileSync(new URL('./package.json', import.meta.url), 'utf8')
) as { version: string }

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
  define: {
    // 只给 `api/cache.ts` 用；读取处用 typeof 兜底，构建期没注入也不会抛 ReferenceError
    __APP_VERSION__: JSON.stringify(PKG.version)
  },
  css: {
    preprocessorOptions: {
      scss: {
        // uni-app 自带的 scss 与旧版 sass 调用方式在 Dart Sass 1.80+ 上是 deprecation warning
        // （当初主要是 wot-design-uni 的 @import 与全局内置函数，wot 已随「晴窗」移除，ADR 0041）。
        // 不归我们改的历史写法，静音掉以保持日志干净；新写的样式不受影响。
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
        changeOrigin: true,
        // changeOrigin 只改 Host，浏览器的 Origin 会原样转发。后端一旦配了 CORS 白名单
        // （.env 里给 apk 用的 FITAPP_CORS_ALLOWED_ORIGINS），带 dev 源的请求就被判跨域 403。
        // 经代理本来就是同源，去掉 Origin 让后端按非跨域请求处理（真机预览的局域网源同理）。
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
        }
      }
    }
  }
})
