import request, { unwrap } from './request'
import { API_BASE } from './base'

// ── 外部 agent（ADR 0043）：令牌管理与接口文档 ──
//    都不进 api/cache.ts：令牌状态要反映 agent 刚才用没用过，缓存着就看不出来了。

export interface AgentTokenStatus {
  exists: boolean
  createdAt?: string | null
  lastUsedAt?: string | null
}

export interface AgentTokenCreated {
  /** 令牌明文：只有这个响应里有，之后再也取不到。 */
  token: string
  createdAt: string
}

export function getAgentTokenStatus() {
  return unwrap(request.get<AgentTokenStatus>('/agent/token'))
}

/** 生成新令牌；已有的那把立即失效。 */
export function regenerateAgentToken() {
  return unwrap(request.post<AgentTokenCreated>('/agent/token'))
}

export function revokeAgentToken() {
  return unwrap(request.delete<void>('/agent/token'))
}

/** 接口文档是 Markdown 原文，不走 Result 信封，所以不经 request 封装。 */
export function getAgentDocs(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${API_BASE}/agent/docs`,
      method: 'GET',
      dataType: 'text',
      timeout: 30000,
      success: (res) => {
        if (res.statusCode === 200 && typeof res.data === 'string') {
          resolve(res.data)
        } else {
          uni.showToast({ title: '文档加载失败', icon: 'none' })
          reject(new Error('docs failed'))
        }
      },
      fail: () => {
        uni.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
        reject(new Error('network error'))
      }
    })
  })
}

/** 接口文档里的路径都带 `/api`，这里给去掉 `/api` 的那段；同源部署时取当前站点。 */
export function agentApiOrigin(): string {
  const origin = API_BASE.startsWith('http') ? API_BASE : `${window.location.origin}${API_BASE}`
  return origin.replace(/\/api\/?$/, '')
}
