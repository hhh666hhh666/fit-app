// ── 客户端响应缓存（ADR 0035）：写后失效、按域作废、不设 TTL（跨自然日自动落空）──
//    键＝URL＋参数；每条挂一个「域」，写操作成功后按域整组作废。
//    ⚠️ 新增写接口必须用 mutating() 包一层，否则症状是「改了不生效」，且只在切页后才看得出来。
//
// ── 跨进程持久化（ADR 0036）：白名单小接口的快照落 localStorage，冷启动同步灌回 ──
//    不落 `/diet/trend`、`/body` 这类随记录线性增长的大对象——同步写盘会掉帧。

import request, { onAuthFail, unwrap } from './request'
import { clearPhotoCache } from '@/utils/photo-cache'

/** 域只有五个：域内任一处改动 → 域内全部重取（粗，但漏不掉）。 */
export type CacheDomain = 'workout' | 'plan' | 'diet' | 'body' | 'food'

interface Entry {
  /** 存 Promise：同一 URL 的并发调用自动合并成一次请求。 */
  promise: Promise<unknown>
  /** 解析后的值。落盘要用——Promise 里的值取不出来，只能另记一份。 */
  value?: unknown
  domain: CacheDomain
  /** 写入当天的本机日期（YYYY-MM-DD）；跨零点即视为过期。 */
  day: string
  /** 去掉查询参数的路径，白名单判定用。 */
  path: string
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
  const entry: Entry = { promise: promise as Promise<unknown>, domain, day: today(), path: url }
  store.set(key, entry)
  promise.then(
    (value) => {
      entry.value = value
      scheduleSave()
    },
    // 失败不占坑：业务码非 0 或网络异常时清掉，下次调用重新请求
    () => {
      if (store.get(key) === entry) store.delete(key)
    }
  )
  if (store.size > MAX_ENTRIES) {
    const oldest = store.keys().next()
    if (!oldest.done) store.delete(oldest.value)
  }
  return promise
}

/** 作废若干域的全部缓存。 */
export function invalidate(...domains: CacheDomain[]): void {
  let touched = false
  for (const [key, entry] of store) {
    if (domains.includes(entry.domain)) {
      store.delete(key)
      touched = true
    }
  }
  // 失效必须立刻落盘：否则重启后又从快照里读到刚被作废的旧值
  if (touched) saveSnapshot()
}

/** 清空全部数据缓存（不动照片——照片按 LRU 管，且换图/删图另有精确失效）。 */
export function clearAll(): void {
  store.clear()
  saveSnapshot()
}

/** 数据缓存 ＋ 照片缓存一起清：登出、401、换账号时用。 */
export function resetCaches(): void {
  store.clear()
  try {
    uni.removeStorageSync(storageKey())
  } catch {
    /* storage 不可用就只清内存 */
  }
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

// ── 快照（ADR 0036）────────────────────────────────────────

const SNAPSHOT_VERSION = 1
const SNAPSHOT_PREFIX = 'fitapp_cache_v1'
/** 单条落盘上限：超过就当它「不是小对象」，不写。 */
const MAX_VALUE_BYTES = 64 * 1024
/** 整份上限：超了宁可少缓存，也不在写入时卡主线程。 */
const MAX_SNAPSHOT_BYTES = 512 * 1024
const SAVE_DEBOUNCE_MS = 300

/** 构建期注入（`vite.config.ts` 的 define）；没注入时 typeof 兜底，不会抛 ReferenceError。 */
const APP_VERSION = typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : 'dev'

/**
 * **白名单**：只落盘首屏（四个主 tab）真正用得到的小对象接口。
 * 刻意不收：`/diet/trend`（全量历史趋势）、`/body`（含全部序列）、`/foods`、`/combos`、
 * `/workouts/{id}`——它们随数据量增长，或只有进子页才用得到。
 */
const PERSIST_PATHS = new Set([
  '/workouts',
  '/workouts/month-summary',
  '/workouts/totals',
  '/stats',
  '/diet',
  '/settings',
  '/plans',
  '/schedule',
  '/exercises',
  '/foods/count',
  '/diet/storage',
  '/food-recognition/usage'
])

interface SnapshotEntry {
  d: CacheDomain
  day: string
  val: unknown
}

interface Snapshot {
  v: number
  /** 构建版本：与当前不一致就整份丢弃（后端 VO 可能已加字段） */
  app: string
  entries: Record<string, SnapshotEntry>
}

/** 快照按用户分开存：换人读不到上一个人的数据。 */
function storageKey(): string {
  const user = (uni.getStorageSync('fitapp_username') as string) || 'anon'
  return `${SNAPSHOT_PREFIX}:${user}`
}

function pathOf(key: string): string {
  const i = key.indexOf('?')
  return i < 0 ? key : key.slice(0, i)
}

let saveTimer: ReturnType<typeof setTimeout> | undefined

function scheduleSave(): void {
  if (saveTimer) return
  saveTimer = setTimeout(() => {
    saveTimer = undefined
    saveSnapshot()
  }, SAVE_DEBOUNCE_MS)
}

/** 把当前可落盘的条目写成快照；坏数据一律吞掉，宁可没有缓存也不能白屏。 */
function saveSnapshot(): void {
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = undefined
  }
  const entries: Record<string, SnapshotEntry> = {}
  for (const [key, entry] of store) {
    if (!PERSIST_PATHS.has(entry.path)) continue
    if (entry.day !== today()) continue // 跨零点的条目直接剪掉
    if (entry.value === undefined) continue
    let size = 0
    try {
      size = JSON.stringify(entry.value)?.length ?? 0
    } catch {
      continue // 不可序列化的值（不该出现，出现就跳过）
    }
    if (size > MAX_VALUE_BYTES) continue
    entries[key] = { d: entry.domain, day: entry.day, val: entry.value }
  }
  const snapshot: Snapshot = { v: SNAPSHOT_VERSION, app: APP_VERSION, entries }
  try {
    const raw = JSON.stringify(snapshot)
    if (raw.length > MAX_SNAPSHOT_BYTES) {
      console.warn('[cache] 快照超过上限，本次不落盘：', raw.length)
      return
    }
    uni.setStorageSync(storageKey(), raw)
  } catch {
    // 存储写不进去（配额满 / 被禁）：退化为只在内存里缓存，不影响功能
  }
}

/**
 * 启动时同步灌回内存（`App.vue` 的 `onLaunch` 调）。之所以同步，是为了让页面
 * `onShow` 里的取数直接命中，不用 await 任何「就绪」信号。
 */
export function hydrateCache(): void {
  let raw = ''
  try {
    raw = (uni.getStorageSync(storageKey()) as string) || ''
  } catch {
    return
  }
  if (!raw) return
  try {
    const snapshot = JSON.parse(raw) as Snapshot
    if (snapshot.v !== SNAPSHOT_VERSION || snapshot.app !== APP_VERSION) {
      // 结构或构建版本变了：整份丢弃，别把旧 DTO 塞给新代码
      uni.removeStorageSync(storageKey())
      return
    }
    for (const [key, item] of Object.entries(snapshot.entries || {})) {
      if (!item || typeof item.day !== 'string' || item.val === undefined) continue
      if (item.day !== today()) continue
      store.set(key, {
        promise: Promise.resolve(item.val),
        value: item.val,
        domain: item.d,
        day: item.day,
        path: pathOf(key)
      })
    }
  } catch {
    // 坏数据：当没有缓存
    try {
      uni.removeStorageSync(storageKey())
    } catch {
      /* ignore */
    }
  }
}

// 401（登录态失效）：缓存层自己挂上去，request.ts 不必反向依赖 cache
onAuthFail(resetCaches)
