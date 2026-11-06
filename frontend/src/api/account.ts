import request, { unwrap } from './request'
import { mutating, read } from './cache'
import type { LoginResult } from './auth'

// ── 账号（ADR 0050）：昵称、改用户名、改密码、注销 ──

export interface AccountInfo {
  userId: number
  username: string
  /** 没设为 null */
  nickname: string | null
  /** 当前头像；没设为 null（显示首字母，ADR 0051） */
  avatarId: number | null
}

/** 历史头像里的一项：宽高与裁剪框都按摆正后的原图像素算（ADR 0051）。 */
export interface AvatarItem {
  id: number
  width: number
  height: number
  cropX: number
  cropY: number
  cropSize: number
  current: boolean
  usedAt: string
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

// ── 头像与历史头像（ADR 0051）：上传与取图走 utils/photo.ts（multipart 与 blob 不经 request 封装）──

export function listAvatars() {
  return read<AvatarItem[]>('/account/avatars', undefined, 'account')
}

/** 重裁：切回历史里的某张，或重新裁剪当前头像。 */
export function recropAvatar(id: number, crop: { x: number; y: number; size: number }) {
  return mutating(['account'], () => unwrap(request.put<AvatarItem>(`/account/avatars/${id}/crop`, crop)))
}

/** 从历史里删一张（进回收站）。 */
export function deleteAvatar(id: number) {
  return mutating(['account', 'diet'], () => unwrap(request.delete<void>(`/account/avatars/${id}`)))
}

/** 不用头像：回到首字母，历史都还在。 */
export function clearAvatar() {
  return mutating('account', () => unwrap(request.delete<void>('/account/avatars/current')))
}
