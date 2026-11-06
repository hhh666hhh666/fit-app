// ── 当前登录的是谁：从登录令牌里读用户 id（令牌的 sub 就是用户 id）──
//    本机按用户分开存的东西（日记草稿、数据快照）一律用它做键，不用用户名：
//    用户名能改（ADR 0050），改了以后按用户名存的草稿就找不到了。

const TOKEN_KEY = 'fitapp_token'

/** 当前用户 id；没登录或令牌读不出来时返回空串。 */
export function currentUserId(): string {
  try {
    const token = (uni.getStorageSync(TOKEN_KEY) as string) || ''
    const payload = token.split('.')[1]
    if (!payload) return ''
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as { sub?: unknown }
    return json.sub == null ? '' : String(json.sub)
  } catch {
    return ''
  }
}
