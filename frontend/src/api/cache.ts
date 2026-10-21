// ── 客户端响应缓存（ADR 0035）：写后失效、按域作废、不设 TTL（跨自然日自动落空）──
//    键＝URL＋参数；每条挂一个「域」，写操作成功后按域整组作废。
//    ⚠️ 新增写接口必须用 mutating() 包一层，否则症状是「改了不生效」，且只在切页后才看得出来。

import request, { onAuthFail, unwrap } from './request'
import { clearPhotoCache } from '@/utils/photo-cache'

/** 域只有五个：域内任一处改动 → 域内全部重取（粗，但漏不掉）。 */
export type CacheDomain = 'workout' | 'plan' | 'diet' | 'body' | 'food'

interface Entry {
  /** 存 Promise：同一 URL 的并发调用自动合并成一次请求。 */
  promise: Promise<unknown>
  domain: CacheDomain
  /** 写入当天的本机日期（YYYY-MM-DD）；跨零点即视为过期。 */
  day: string
}

/** 条目上限：只为防无界增长（正常用量远低于它——分页列表只缓存首屏）。 */
const MAX_ENTRIES = 120

const store = new Map<string, Entry>()

/** 本机当天；跨自然日分区用它，别用 UTC（否则早上 8 点前会被当成前一天）。 */
function today(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function keyOf(url: string, params?: Record<string, unknown>): string {
  if (!params) return url
  const qs = Object.keys(params)
    .sort()
    .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
    .map((k) => `${k}=${String(params[k])}`)
    .join('&')
  return qs ? `${url}?${qs}` : url
}

/**
 * 读一个 GET 接口：命中（且同日）直接给上一次那个 Promise，否则发请求并记账。
 * 存的是 `unwrap` 之后的**对象本身**——页面重复赋值拿到的是同一个引用，Vue 不会重渲染。
 *
 * `cached = false` 用于「不该缓存」的读：分页续页（offset > 0）、带搜索词的列表、导出等。
 */
export function read<T>(
  url: string,
  params: Record<string, unknown> | undefined,
  domain: CacheDomain,
  cached = true
): Promise<T> {
  const key = keyOf(url, params)
  if (cached) {
    const hit = store.get(key)
    if (hit && hit.day === today()) return hit.promise as Promise<T>
  }
  const promise = unwrap<T>(request.get<T>(url, params))
  if (!cached) return promise
  const entry: Entry = { promise: promise as Promise<unknown>, domain, day: today() }
  store.set(key, entry)
  // 失败不占坑：业务码非 0 或网络异常时清掉，下次调用重新请求
  promise.catch(() => {
    if (store.get(key) === entry) store.delete(key)
  })
  if (store.size > MAX_ENTRIES) {
    const oldest = store.keys().next()
    if (!oldest.done) store.delete(oldest.value)
  }
  return promise
}

/** 作废若干域的全部缓存。 */
export function invalidate(...domains: CacheDomain[]): void {
  for (const [key, entry] of store) {
    if (domains.includes(entry.domain)) store.delete(key)
  }
}

/** 清空全部数据缓存（不动照片——照片按 LRU 管，且换图/删图另有精确失效）。 */
export function clearAll(): void {
  store.clear()
}

/** 数据缓存 ＋ 照片缓存一起清：登出、401、换账号时用。 */
export function resetCaches(): void {
  store.clear()
  clearPhotoCache()
}

/**
 * 包一个写操作：成功后才作废对应域（失败时保留既有缓存，不白白重取）。
 *
 * ```ts
 * export function createFood(payload: FoodPayload) {
 *   return mutating('food', () => unwrap(request.post<FoodItem>('/foods', payload)))
 * }
 * ```
 */
export async function mutating<T>(
  domain: CacheDomain | CacheDomain[],
  run: () => Promise<T>
): Promise<T> {
  const result = await run()
  invalidate(...(Array.isArray(domain) ? domain : [domain]))
  return result
}

// 401（登录态失效）：缓存层自己挂上去，request.ts 不必反向依赖 cache
onAuthFail(resetCaches)
