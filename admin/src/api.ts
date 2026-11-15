import { ElMessage } from 'element-plus'
import { router } from './router'
import type { Result } from './types'

// 管理端的令牌单独存一个键：和 App 同域（/admin/ 与 / 共用 localStorage），不能互相覆盖。
const TOKEN_KEY = 'fitapp_admin_token'

export const getToken = () => {
  try {
    return localStorage.getItem(TOKEN_KEY) || ''
  } catch {
    return ''
  }
}

export const setToken = (t: string) => {
  try {
    if (t) localStorage.setItem(TOKEN_KEY, t)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // 存不了就只在本页有效
  }
}

export class ApiError extends Error {
  constructor(
    public code: number,
    message: string
  ) {
    super(message)
  }
}

type Query = Record<string, string | number | boolean | null | undefined>

async function send<T>(method: string, url: string, body?: unknown, query?: Query): Promise<T> {
  const qs = query
    ? Object.entries(query)
        .filter(([, v]) => v !== undefined && v !== null && v !== '')
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
        .join('&')
    : ''
  const token = getToken()
  let res: Response
  try {
    res = await fetch(`/api${url}${qs ? `?${qs}` : ''}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: body === undefined ? undefined : JSON.stringify(body)
    })
  } catch {
    ElMessage.error('网络异常，请稍后重试')
    throw new ApiError(-1, 'network')
  }
  let result: Result<T>
  try {
    result = (await res.json()) as Result<T>
  } catch {
    ElMessage.error(res.ok ? '服务器返回了看不懂的内容' : `请求失败（HTTP ${res.status}）`)
    throw new ApiError(-1, 'bad response')
  }
  if (result.code === 0) return result.data
  if (result.code === 401 || result.code === 403) {
    // 登录过期，或这个账号不是管理员：一律回登录页（ADR 0048）
    setToken('')
    if (router.currentRoute.value.name !== 'login') {
      ElMessage.error(result.code === 403 ? '这个账号没有管理权限' : '登录已过期，请重新登录')
      void router.replace({ name: 'login' })
    }
    throw new ApiError(result.code, result.message)
  }
  ElMessage.error(result.message || '操作失败')
  throw new ApiError(result.code, result.message)
}

/** multipart 上传（食物图片）：不能带 JSON 的 Content-Type，由浏览器自己写边界。 */
async function sendForm<T>(url: string, form: FormData): Promise<T> {
  const token = getToken()
  let res: Response
  try {
    res = await fetch(`/api${url}`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form
    })
  } catch {
    ElMessage.error('网络异常，请稍后重试')
    throw new ApiError(-1, 'network')
  }
  let result: Result<T>
  try {
    result = (await res.json()) as Result<T>
  } catch {
    ElMessage.error(res.ok ? '服务器返回了看不懂的内容' : `请求失败（HTTP ${res.status}）`)
    throw new ApiError(-1, 'bad response')
  }
  if (result.code === 0) return result.data
  ElMessage.error(result.message || '上传失败')
  throw new ApiError(result.code, result.message)
}

/** 取一张要登录才给的图，返回 object URL（调用方用完自己 revoke）；没有图返回 null。 */
async function blobUrl(url: string): Promise<string | null> {
  const token = getToken()
  try {
    const res = await fetch(`/api${url}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
    if (!res.ok) return null
    return URL.createObjectURL(await res.blob())
  } catch {
    return null
  }
}

export const api = {
  get: <T>(url: string, query?: Query) => send<T>('GET', url, undefined, query),
  post: <T>(url: string, body?: unknown) => send<T>('POST', url, body),
  put: <T>(url: string, body?: unknown) => send<T>('PUT', url, body),
  del: <T>(url: string) => send<T>('DELETE', url),
  postForm: sendForm,
  blobUrl
}
