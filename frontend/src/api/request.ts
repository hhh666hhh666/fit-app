import type { Result } from '@/types'

// 分平台 base 地址。
// 本轮只跑 H5：与后端同源（dev 由 vite 代理 /api，上线走同域），所以用相对路径。
// ⚠️ 将来若接微信小程序，必须改成绝对地址（如 https://<域名>/api），届时在此切换。
const BASE_URL = '/api'

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

function buildUrl(url: string, params?: Record<string, unknown>): string {
  if (!params) return BASE_URL + url
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join('&')
  return BASE_URL + url + (qs ? `?${qs}` : '')
}

// 把 uni.request 套成 { data: Result<T> } 的形状，好让 unwrap 与后端信封逐字对应
function send<T>(
  method: Method,
  url: string,
  data?: unknown,
  params?: Record<string, unknown>
): Promise<{ data: Result<T> }> {
  return new Promise((resolve, reject) => {
    const token = (uni.getStorageSync('fitapp_token') as string) || ''
    uni.request({
      url: buildUrl(url, params),
      method,
      data: data as Record<string, unknown> | undefined,
      timeout: 30000,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => resolve({ data: res.data as Result<T> }),
      fail: () => {
        uni.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
        reject(new Error('network error'))
      }
    })
  })
}

const request = {
  get: <T>(url: string, params?: Record<string, unknown>) => send<T>('GET', url, undefined, params),
  post: <T>(url: string, data?: unknown, params?: Record<string, unknown>) =>
    send<T>('POST', url, data, params),
  put: <T>(url: string, data?: unknown, params?: Record<string, unknown>) =>
    send<T>('PUT', url, data, params),
  delete: <T>(url: string) => send<T>('DELETE', url)
}

/**
 * 后端是「HTTP 200 + 业务码」封装：成功码 0，401 也在 body 里。
 * 401 时清 token 并回登录页；其余非 0 码 toast 后 reject。
 */
export async function unwrap<T>(promise: Promise<{ data: Result<T> }>): Promise<T> {
  const response = await promise
  const body = response.data
  if (body.code === 0) {
    return body.data
  }
  if (body.code === 401) {
    uni.removeStorageSync('fitapp_token')
    uni.reLaunch({ url: '/pages/login/index' })
  }
  const message = body.message || '请求失败'
  uni.showToast({ title: message, icon: 'none' })
  return Promise.reject(new Error(message))
}

export default request
