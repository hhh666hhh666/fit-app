/**
 * 登录守卫：进页面时先看本地有没有 token，没有就直接去登录页。
 *
 * 只判断「有没有」，**不校验有效性**——token 过期、被服务端拒绝时仍然只能靠
 * `api/request.ts` 的 401 分支兜底（前端解不开 JWT 的过期时间，除非后端加 /api/auth/me）。
 *
 * 用法：页面 `onShow`（或只有 `onLoad` 的页面用 onLoad）首行：
 *   `if (!ensureLogin()) return`
 *
 * ⚠️ **必须 return**：`reLaunch` 是异步的，不 return 的话本页会继续往下发请求，
 *    于是「每个接口各 401 一次 + 重复 reLaunch」的老毛病又回来了——守卫就白加了。
 *
 * ⚠️ 为什么不在 `App.vue` 的 `onLaunch` 里做：那时页面栈还没就绪，reLaunch 有时序问题；
 *    放在每个页面的 onShow 里最稳（读一次本地 storage，开销可忽略）。
 *
 * ⚠️ 新建页面时记得加这一行——守卫是**逐页**的，没有全局钩子（uni-app 不提供页面级 mixin）。
 */
export function ensureLogin(): boolean {
  const token = (uni.getStorageSync('fitapp_token') as string) || ''
  if (token) return true
  uni.reLaunch({ url: '/pages/login/index' })
  return false
}
