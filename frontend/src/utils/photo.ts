// ── 餐照：选择 / 压缩（四档）/ 上传 / 取图（H5 原生通道；<img> 带不了鉴权头，一律 fetch→blob）──
//    取图结果进 blob 缓存（ADR 0035）：页面卸载不释放，重复打开同一张不发请求。

import { API_BASE } from '@/api/base'
import { invalidate } from '@/api/cache'
import { cachePhoto, invalidatePhoto, isPhotoEmpty, markPhotoEmpty, peekPhoto, photoKey, thumbKey } from './photo-cache'

export type PhotoQuality = 'lossless' | 'high' | 'medium' | 'low'

/** 压缩长边上限（px）；无损档例外——原图直传。 */
const MAX_EDGE = 1600

/** 有损档的 JPEG 质量。 */
const QUALITY_MAP: Record<Exclude<PhotoQuality, 'lossless'>, number> = {
  high: 0.85,
  medium: 0.7,
  low: 0.5
}

const TOKEN_KEY = 'fitapp_token'
const MAX_BYTES = 10 * 1024 * 1024

function authHeader(): Record<string, string> {
  const token = (uni.getStorageSync(TOKEN_KEY) as string) || ''
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/** 选图结果：path＝选到的临时路径；cancelled＝用户主动取消；error＝真失败的原因。 */
export interface ChoosePhotoResult {
  path: string | null
  cancelled: boolean
  error?: string
}

/**
 * 选一张照片（相册/相机）。
 *
 * ⚠️ 必须把「用户取消」与「真失败（权限被拒／浏览器拦截／环境不支持）」区分开：
 * 早先的实现把两者一并 resolve(null)，于是任何异常都表现为「点了没反应」，
 * 用户看不到任何提示、也无从排查（真机尤其容易踩到）。
 */
export function choosePhoto(): Promise<ChoosePhotoResult> {
  return new Promise((resolve) => {
    uni.chooseImage({
      count: 1,
      sizeType: ['original'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const path = (res.tempFilePaths && res.tempFilePaths[0]) || null
        // 空结果也算取消：部分环境用户取消时不给 cancel 事件、只回空数组，
        // 若按「失败」处理会平白弹一次错误提示。
        resolve({ path, cancelled: !path })
      },
      fail: (err) => {
        const msg = String((err as { errMsg?: string } | undefined)?.errMsg || '')
        // uni 约定：用户取消 → "chooseImage:fail cancel"；其余均为真失败
        resolve({ path: null, cancelled: msg.includes('cancel'), error: msg || 'chooseImage:fail' })
      }
    })
  })
}

async function loadImage(blob: Blob): Promise<HTMLImageElement> {
  const url = URL.createObjectURL(blob)
  try {
    return await new Promise<HTMLImageElement>((resolve, reject) => {
      const img = new Image()
      img.onload = () => resolve(img)
      img.onerror = () => reject(new Error('图片解码失败'))
      img.src = url
    })
  } finally {
    URL.revokeObjectURL(url)
  }
}

/** 长边 ≤1600 等比缩放 + JPEG 重编码（导出供识别链路复用——标签文字小，别压糊）。 */
export async function compress(blob: Blob, quality: number): Promise<Blob> {
  const img = await loadImage(blob)
  const scale = Math.min(1, MAX_EDGE / Math.max(img.naturalWidth, img.naturalHeight))
  const w = Math.max(1, Math.round(img.naturalWidth * scale))
  const h = Math.max(1, Math.round(img.naturalHeight * scale))
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('canvas 不可用')
  ctx.drawImage(img, 0, 0, w, h)
  return await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('压缩失败'))), 'image/jpeg', quality)
  })
}

/**
 * 选图 →（按档压缩）→ 上传替换该餐照片。
 * 返回 true=已上传；false=取消选择或失败（失败已 toast）。
 */
export async function pickAndUploadPhoto(
  date: string,
  meal: string,
  quality: PhotoQuality
): Promise<boolean> {
  const picked = await choosePhoto()
  if (picked.cancelled) return false
  if (!picked.path) {
    // 真失败：让用户看见原因，别再静默（否则表现就是「点了没反应」）
    console.warn('[photo] 打开相机/相册失败：', picked.error)
    uni.showToast({ title: '没能打开相机或相册，请检查权限设置', icon: 'none' })
    return false
  }
  const path = picked.path
  try {
    const original = await (await fetch(path)).blob()
    const blob = quality === 'lossless' ? original : await compress(original, QUALITY_MAP[quality])
    if (blob.size > MAX_BYTES) {
      uni.showToast({ title: '照片超过 10MB，请换更低的压缩档', icon: 'none' })
      return false
    }
    const fd = new FormData()
    fd.append('file', blob, blob.type === 'image/png' ? 'photo.png' : 'photo.jpg')
    const res = await fetch(
      `${API_BASE}/diet/photos?date=${encodeURIComponent(date)}&meal=${encodeURIComponent(meal)}`,
      { method: 'POST', headers: authHeader(), body: fd }
    )
    let body: { code?: number; message?: string } | null = null
    try {
      body = await res.json()
    } catch {
      /* 非 JSON（如 nginx 错误页） */
    }
    if (res.ok && body && body.code === 0) {
      // 换过图：这张的缓存立刻作废，存储占用也跟着变（ADR 0035）
      invalidatePhoto(date, meal)
      invalidate('diet')
      return true
    }
    uni.showToast({ title: (body && body.message) || '上传失败', icon: 'none' })
    return false
  } catch {
    uni.showToast({ title: '照片处理失败', icon: 'none' })
    return false
  }
}

/** 取一张图：命中缓存直接给地址（零请求），否则 fetch 后放进缓存。 */
async function fetchCached(url: string, key: string): Promise<string | null> {
  const hit = peekPhoto(key)
  if (hit) return hit
  // 已知这餐没照片：不再为它跑一次 404（大多数餐次都没拍照）
  if (isPhotoEmpty(key)) return null
  try {
    const res = await fetch(url, { headers: authHeader() })
    if (res.status === 404) {
      markPhotoEmpty(key)
      return null
    }
    if (!res.ok) return null
    const blob = await res.blob()
    return blob.size ? cachePhoto(key, blob) : null
  } catch {
    return null
  }
}

/**
 * 取某餐照片的临时地址（缓存里的 objectURL）；无照片返回 null。
 * ⚠️ 调用方**不许 revoke**——地址归缓存所有（ADR 0035）；换图／删图走 `invalidatePhoto`，
 * 缓存自己按 LRU 淘汰。撤销一个正在 `<img>` 上显示的地址会当场变裂图。
 */
export function fetchPhotoUrl(date: string, meal: string): Promise<string | null> {
  return fetchCached(
    `${API_BASE}/diet/photos/${encodeURIComponent(date)}/${encodeURIComponent(meal)}`,
    photoKey(date, meal)
  )
}

/** 取某餐照片的**缩略图**地址（相册与列表用）；无照片返回 null。老数据无缩略图时后端回退原图。 */
export function fetchPhotoThumbUrl(date: string, meal: string): Promise<string | null> {
  return fetchCached(
    `${API_BASE}/diet/photos/${encodeURIComponent(date)}/${encodeURIComponent(meal)}/thumb`,
    thumbKey(date, meal)
  )
}

/** 删除某餐照片（不可恢复）。成功返回 true；失败已 toast。 */
export async function deletePhoto(date: string, meal: string): Promise<boolean> {
  try {
    const res = await fetch(
      `${API_BASE}/diet/photos/${encodeURIComponent(date)}/${encodeURIComponent(meal)}`,
      { method: 'DELETE', headers: authHeader() }
    )
    let body: { code?: number; message?: string } | null = null
    try {
      body = await res.json()
    } catch {
      /* 非 JSON（如 nginx 错误页） */
    }
    if (res.ok && body && body.code === 0) {
      invalidatePhoto(date, meal)
      invalidate('diet')
      return true
    }
    uni.showToast({ title: (body && body.message) || '删除失败', icon: 'none' })
    return false
  } catch {
    uni.showToast({ title: '删除失败', icon: 'none' })
    return false
  }
}
