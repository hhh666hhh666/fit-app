import request, { unwrap } from './request'
import { mutating, read } from './cache'
import type { LoginResult } from './auth'

// ── 账号（ADR 0050）：昵称、改用户名、改密码、注销 ──

export interface AccountInfo {
  userId: number
  username: string
  /** 没设为 null */
  nickname: string | null
}

/** 显示用的名字：有昵称用昵称，没有用用户名。 */
export function displayName(a: Pick<AccountInfo, 'username' | 'nickname'> | null, fallback = '训练者'): string {
  return a?.nickname || a?.username || fallback
}

export function getAccount() {
  return read<AccountInfo>('/account', undefined, 'account')
}

export function updateNickname(nickname: string) {
  return mutating('account', () => unwrap(request.put<AccountInfo>('/account/nickname', { nickname })))
}

export async function updateUsername(username: string, password: string) {
  const a = await mutating('account', () =>
    unwrap(request.put<AccountInfo>('/account/username', { username, password }))
  )
  uni.setStorageSync('fitapp_username', a.username)
  return a
}

/** 改密码：换上返回的新令牌，本机继续登录；其他设备的旧令牌已经失效。 */
export async function changePassword(oldPassword: string, newPassword: string) {
  const res = await unwrap(request.put<LoginResult>('/account/password', { oldPassword, newPassword }))
  uni.setStorageSync('fitapp_token', res.token)
  return res
}

/** 注销：立刻彻底删除全部数据和图片，不进回收站。 */
export function deleteAccount(password: string, username: string) {
  return unwrap(request.post<void>('/account/delete', { password, username }))
}
