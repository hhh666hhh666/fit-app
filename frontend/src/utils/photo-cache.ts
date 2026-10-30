// ── 照片的 blob 缓存（ADR 0035／0036）：<img> 带不了鉴权头，照片一律 fetch → blob → objectURL ──
//    两层：
//      ① 内存（下面这个 Map）——当前进程的 objectURL，按**字节** LRU，64MB；
//      ② 磁盘（IndexedDB）——跨进程保留，128MB，按最近访问淘汰（ADR 0036）。
//    内存层是同步的（首屏不该等异步）；磁盘层异步，只在内存未命中时按需读一张。
//    ⚠️ 调用方不许 revoke：页面卸载不再释放，只由 LRU 挤掉 / 换图删图失效 / 登出清空。
//       撤销一个正在 <img> 上显示的 objectURL 会当场变裂图。

/** 内存预算：中低端安卓 WebView 给到 64MB 图片缓存是安全区间。 */
const MAX_BYTES = 64 * 1024 * 1024
/** 内存条数上限：缩略图很小，单靠字节数会让 Map 条目过多。 */
const MAX_ENTRIES = 240

/** 磁盘预算（ADR 0036）：按单张原图 1~4MB 算约 40~120 张，够覆盖几周三餐。 */
const DISK_MAX_BYTES = 128 * 1024 * 1024

interface Entry {
  url: string
  bytes: number
}

/** Map 的插入顺序即 LRU 顺序：命中时重插到末尾。 */
const store = new Map<string, Entry>()
let totalBytes = 0

/**
 * 「这一餐没有照片」的负缓存。
 * 大多数餐次本来就没拍照，不记下来就会每次打开弹层都白跑一次 404——正是要消掉的「点进去重新加载一下」。
 */
const emptyKeys = new Set<string>()

/** 原图与缩略图各一把键。 */
export function photoKey(date: string, meal: string): string {
  return `full:${date}|${meal}`
}

export function thumbKey(date: string, meal: string): string {
  return `thumb:${date}|${meal}`
}

/** 取一个已缓存的内存地址（命中即刷新 LRU 位次）；没有返回 null。 */
export function peekPhoto(key: string): string | null {
  const hit = store.get(key)
  if (!hit) return null
  store.delete(key)
  store.set(key, hit)
  return hit.url
}

/** 把 blob 放进内存缓存并返回 objectURL；超预算时从最久未用的开始挤。 */
export function cachePhoto(key: string, blob: Blob): string {
  const old = store.get(key)
  if (old) {
    store.delete(key)
    totalBytes -= old.bytes
    URL.revokeObjectURL(old.url)
  }
  const url = URL.createObjectURL(blob)
  store.set(key, { url, bytes: blob.size })
  totalBytes += blob.size
  evict()
  return url
}

function evict(): void {
  // 至少留一条：宁可超预算，也不把刚放进去的那张挤掉
  while ((totalBytes > MAX_BYTES || store.size > MAX_ENTRIES) && store.size > 1) {
    const oldest = store.keys().next()
    if (oldest.done) return
    const entry = store.get(oldest.value)
    if (entry) {
      totalBytes -= entry.bytes
      URL.revokeObjectURL(entry.url)
    }
    store.delete(oldest.value)
  }
}

/** 日记图片（ADR 0044）的原图与缩略图各一把键。 */
export function diaryPhotoKey(id: number, thumb: boolean): string {
  return `${thumb ? 'diary-thumb' : 'diary-full'}:${id}`
}

/** 换图 / 删图后精确作废某餐（原图与缩略图一起，内存与磁盘都清，含「没有照片」的负缓存）。 */
export function invalidatePhoto(date: string, meal: string): void {
  dropKeys([photoKey(date, meal), thumbKey(date, meal)])
}

/** 删掉一张日记图片后作废它（下次取会得到 404，正文显示「图片已删除」）。 */
export function invalidateDiaryPhoto(id: number): void {
  dropKeys([diaryPhotoKey(id, false), diaryPhotoKey(id, true)])
}

function dropKeys(keys: string[]): void {
  for (const key of keys) {
    emptyKeys.delete(key)
    const entry = store.get(key)
    if (entry) {
      totalBytes -= entry.bytes
      URL.revokeObjectURL(entry.url)
      store.delete(key)
    }
  }
  void dropFromDisk(keys)
}

/** 记下「这餐确实没有照片」（接口回 404 时）；换图/删图会连带清掉。 */
export function markPhotoEmpty(key: string): void {
  emptyKeys.add(key)
}

/** 是否已知「没有照片」。 */
export function isPhotoEmpty(key: string): boolean {
  return emptyKeys.has(key)
}

/** 整份清空（登出 / 401 / 换账号）：内存与磁盘一起清。 */
export function clearPhotoCache(): void {
  for (const entry of store.values()) URL.revokeObjectURL(entry.url)
  store.clear()
  emptyKeys.clear()
  totalBytes = 0
  void clearDisk()
}

// ── 磁盘层（IndexedDB，ADR 0036）──────────────────────────
//    结构：photos = { key, blob, bytes, ts }（ts 建索引，淘汰按它升序取最久未用的）
//          meta   = { key: 'totalBytes', value }

const DB_NAME = 'fitapp-photos'
const DB_VERSION = 1
const STORE_PHOTOS = 'photos'
const STORE_META = 'meta'
const META_TOTAL = 'totalBytes'

interface DiskRecord {
  key: string
  blob: Blob
  bytes: number
  ts: number
}

let dbPromise: Promise<IDBDatabase | null> | null = null
/** 磁盘已用字节（首次打开时从 meta 读回）。 */
let diskBytes = -1

function openDb(): Promise<IDBDatabase | null> {
  if (dbPromise) return dbPromise
  dbPromise = new Promise((resolve) => {
    if (typeof indexedDB === 'undefined') {
      resolve(null)
      return
    }
    try {
      const req = indexedDB.open(DB_NAME, DB_VERSION)
      req.onupgradeneeded = () => {
        const db = req.result
        if (!db.objectStoreNames.contains(STORE_PHOTOS)) {
          const s = db.createObjectStore(STORE_PHOTOS, { keyPath: 'key' })
          s.createIndex('ts', 'ts')
        }
        if (!db.objectStoreNames.contains(STORE_META)) {
          db.createObjectStore(STORE_META, { keyPath: 'key' })
        }
      }
      req.onsuccess = () => resolve(req.result)
      // 打不开（隐私模式 / 存储被禁）就静默降级成「只有内存缓存」
      req.onerror = () => resolve(null)
      req.onblocked = () => resolve(null)
    } catch {
      resolve(null)
    }
  })
  return dbPromise
}

function readTotal(db: IDBDatabase): Promise<number> {
  return new Promise((resolve) => {
    try {
      const req = db.transaction([STORE_META], 'readonly').objectStore(STORE_META).get(META_TOTAL)
      req.onsuccess = () => resolve(Number((req.result as { value?: number } | undefined)?.value) || 0)
      req.onerror = () => resolve(0)
    } catch {
      resolve(0)
    }
  })
}

function writeTotal(db: IDBDatabase, value: number): void {
  try {
    const tx = db.transaction([STORE_META], 'readwrite')
    tx.objectStore(STORE_META).put({ key: META_TOTAL, value })
  } catch {
    /* ignore */
  }
}

async function ensureDisk(db: IDBDatabase): Promise<number> {
  if (diskBytes < 0) diskBytes = await readTotal(db)
  return diskBytes
}

/**
 * 从磁盘取一张（命中后把 ts 推到最新，等于刷 LRU 位次）。
 * 返回的 blob 交给调用方放进内存层；打不开库就返回 null，调用方自然回落到网络。
 */
export async function readPhotoFromDisk(key: string): Promise<Blob | null> {
  const db = await openDb()
  if (!db) return null
  try {
    const record = await new Promise<DiskRecord | null>((resolve) => {
      const req = db.transaction([STORE_PHOTOS], 'readonly').objectStore(STORE_PHOTOS).get(key)
      req.onsuccess = () => resolve((req.result as DiskRecord | undefined) ?? null)
      req.onerror = () => resolve(null)
    })
    if (!record || !record.blob) return null
    // 刷新访问时间（异步，失败不影响返回）
    try {
      const tx = db.transaction([STORE_PHOTOS], 'readwrite')
      tx.objectStore(STORE_PHOTOS).put({ ...record, ts: Date.now() })
    } catch {
      /* ignore */
    }
    return record.blob
  } catch {
    return null
  }
}

/** 把一张写进磁盘（fire-and-forget）；超预算按 ts 升序淘汰。 */
export function savePhotoToDisk(key: string, blob: Blob): void {
  void (async () => {
    const db = await openDb()
    if (!db) return
    try {
      const used = await ensureDisk(db)
      const record: DiskRecord = { key, blob, bytes: blob.size, ts: Date.now() }
      await new Promise<void>((resolve) => {
        const tx = db.transaction([STORE_PHOTOS], 'readwrite')
        tx.objectStore(STORE_PHOTOS).put(record)
        tx.oncomplete = () => resolve()
        tx.onerror = () => resolve()
      })
      diskBytes = used + blob.size
      await evictDisk(db, key)
      writeTotal(db, diskBytes)
    } catch {
      /* 存不进去就当没有磁盘层 */
    }
  })()
}

/** 淘汰到预算以内；`keep` 是刚写进来的那张，不许删。 */
async function evictDisk(db: IDBDatabase, keep: string): Promise<void> {
  if (diskBytes <= DISK_MAX_BYTES) return
  await new Promise<void>((resolve) => {
    try {
      const req = db
        .transaction([STORE_PHOTOS], 'readwrite')
        .objectStore(STORE_PHOTOS)
        .index('ts')
        .openCursor()
      req.onsuccess = () => {
        const cursor = req.result
        if (!cursor || diskBytes <= DISK_MAX_BYTES) {
          resolve()
          return
        }
        const record = cursor.value as DiskRecord
        if (record.key === keep) {
          cursor.continue()
          return
        }
        diskBytes -= record.bytes
        cursor.delete()
        cursor.continue()
      }
      req.onerror = () => resolve()
    } catch {
      resolve()
    }
  })
}

/** 从磁盘删掉若干键（换图 / 删图）。 */
async function dropFromDisk(keys: string[]): Promise<void> {
  const db = await openDb()
  if (!db) return
  try {
    await new Promise<void>((resolve) => {
      const tx = db.transaction([STORE_PHOTOS], 'readwrite')
      const os = tx.objectStore(STORE_PHOTOS)
      for (const key of keys) {
        const req = os.get(key)
        req.onsuccess = () => {
          const record = req.result as DiskRecord | undefined
          if (!record) return
          if (diskBytes > 0) diskBytes -= record.bytes
          os.delete(key)
        }
      }
      tx.oncomplete = () => resolve()
      tx.onerror = () => resolve()
    })
    writeTotal(db, Math.max(0, diskBytes))
  } catch {
    /* ignore */
  }
}

/** 整份清空磁盘（登出 / 401 / 换账号）。 */
async function clearDisk(): Promise<void> {
  const db = await openDb()
  if (!db) return
  try {
    await new Promise<void>((resolve) => {
      const tx = db.transaction([STORE_PHOTOS], 'readwrite')
      tx.objectStore(STORE_PHOTOS).clear()
      tx.oncomplete = () => resolve()
      tx.onerror = () => resolve()
    })
    diskBytes = 0
    writeTotal(db, 0)
  } catch {
    /* ignore */
  }
}
