// ── 照片的 blob 缓存（ADR 0035）：<img> 带不了鉴权头，照片一律 fetch → blob → objectURL ──
//    缓存单位是 blob，按**字节**做 LRU（单张原图上限 10MB，按张数算会估不准内存）。
//    ⚠️ 调用方不许 revoke：页面卸载不再释放，只由 LRU 挤掉 / 换图删图失效 / 登出清空。
//       撤销一个正在 <img> 上显示的 objectURL 会当场变裂图。

/** 总预算：中低端安卓 WebView 给到 64MB 图片缓存是安全区间。 */
const MAX_BYTES = 64 * 1024 * 1024
/** 条数上限：缩略图很小，单靠字节数会让 Map 条目过多。 */
const MAX_ENTRIES = 240

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

/** 取一个已缓存的地址（命中即刷新 LRU 位次）；没有返回 null。 */
export function peekPhoto(key: string): string | null {
  const hit = store.get(key)
  if (!hit) return null
  store.delete(key)
  store.set(key, hit)
  return hit.url
}

/** 把 blob 放进缓存并返回 objectURL；超预算时从最久未用的开始挤。 */
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

/** 换图 / 删图后精确作废某餐（原图与缩略图一起，含「没有照片」的负缓存）。 */
export function invalidatePhoto(date: string, meal: string): void {
  for (const key of [photoKey(date, meal), thumbKey(date, meal)]) {
    emptyKeys.delete(key)
    const entry = store.get(key)
    if (!entry) continue
    totalBytes -= entry.bytes
    URL.revokeObjectURL(entry.url)
    store.delete(key)
  }
}

/** 记下「这餐确实没有照片」（接口回 404 时）；换图/删图会连带清掉。 */
export function markPhotoEmpty(key: string): void {
  emptyKeys.add(key)
}

/** 是否已知「没有照片」。 */
export function isPhotoEmpty(key: string): boolean {
  return emptyKeys.has(key)
}

/** 整份清空（登出 / 401 / 换账号）。 */
export function clearPhotoCache(): void {
  for (const entry of store.values()) URL.revokeObjectURL(entry.url)
  store.clear()
  emptyKeys.clear()
  totalBytes = 0
}
