// ── 餐照：选择 / 压缩（四档）/ 上传 / 取图（H5 原生通道；<img> 带不了鉴权头，一律 fetch→blob）──
//    取图结果进 blob 缓存（ADR 0035）：页面卸载不释放，重复打开同一张不发请求。

import { API_BASE } from '@/api/base'
import { invalidate } from '@/api/cache'
import {
  cachePhoto,
  diaryPhotoKey,
  invalidateDiaryPhoto,
  invalidatePhoto,
  isPhotoEmpty,
  markPhotoEmpty,
  peekPhoto,
  photoKey,
  readPhotoFromDisk,
  savePhotoToDisk,
  thumbKey
} from './photo-cache'

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
/** 一次最多选几张。 */
const MAX_PICK = 9

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

/** 选多张照片；取消或失败返回空数组（失败已 toast）。 */
function choosePhotos(count: number): Promise<string[]> {
  return new Promise((resolve) => {
    uni.chooseImage({
      count,
      sizeType: ['original'],
      sourceType: ['album', 'camera'],
      success: (res) => resolve((res.tempFilePaths as string[] | undefined) ?? []),
      fail: (err) => {
        const msg = String((err as { errMsg?: string } | undefined)?.errMsg || '')
        if (!msg.includes('cancel')) {
          console.warn('[photo] 打开相机/相册失败：', msg)
          uni.showToast({ title: '没能打开相机或相册，请检查权限设置', icon: 'none' })
        }
        resolve([])
      }
    })
  })
}

/**
 * 选图 →（按档压缩）→ 逐张上传到这一餐（一餐可以有多张，ADR 0045）。
 * 返回传成功的张数；取消或全部失败时为 0（失败已 toast）。
 */
export async function pickAndUploadPhotos(date: string, meal: string, quality: PhotoQuality): Promise<number> {
  const paths = await choosePhotos(MAX_PICK)
  if (paths.length === 0) return 0
  let uploaded = 0
  uni.showLoading({ title: '上传中…' })
  try {
    for (const path of paths) {
      const original = await (await fetch(path)).blob()
      const blob = quality === 'lossless' ? original : await compress(original, QUALITY_MAP[quality])
      if (blob.size > MAX_BYTES) {
        uni.hideLoading()
        uni.showToast({ title: '照片超过 10MB，请换更低的压缩档', icon: 'none' })
        return uploaded
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
      if (!res.ok || !body || body.code !== 0) {
        uni.hideLoading()
        uni.showToast({ title: (body && body.message) || '上传失败', icon: 'none' })
        return uploaded
      }
      uploaded++
    }
    uni.hideLoading()
    return uploaded
  } catch {
    uni.hideLoading()
    uni.showToast({ title: '照片处理失败', icon: 'none' })
    return uploaded
  } finally {
    // 哪怕只传成功一部分：这一餐的照片和存储占用都变了（ADR 0035）
    if (uploaded > 0) invalidate('diet')
  }
}

/** 取一张图：内存 → 磁盘 → 网络，命中前两层都零请求（ADR 0036）。 */
async function fetchCached(url: string, key: string): Promise<string | null> {
  const hit = peekPhoto(key)
  if (hit) return hit
  // 已知这张没有了：不再为它跑一次 404
  if (isPhotoEmpty(key)) return null
  // 磁盘里可能有上个进程留下的那张
  const fromDisk = await readPhotoFromDisk(key)
  if (fromDisk && fromDisk.size) return cachePhoto(key, fromDisk)
  try {
    const res = await fetch(url, { headers: authHeader() })
    if (res.status === 404) {
      markPhotoEmpty(key)
      return null
    }
    if (!res.ok) return null
    const blob = await res.blob()
    if (!blob.size) return null
    savePhotoToDisk(key, blob)
    return cachePhoto(key, blob)
  } catch {
    return null
  }
}

/**
 * 取一张餐照。deleted＝接口明确说没有这张，和网络失败区分开。
 * ⚠️ 调用方**不许 revoke** 拿到的地址——它归缓存所有（ADR 0035）；删图走 `invalidatePhoto`，
 * 缓存自己按 LRU 淘汰。撤销一个正在 `<img>` 上显示的地址会当场变裂图。
 */
export async function fetchMealPhoto(id: number, thumb: boolean): Promise<{ url: string | null; deleted: boolean }> {
  const key = thumb ? thumbKey(id) : photoKey(id)
  const url = await fetchCached(`${API_BASE}/diet/photos/${id}${thumb ? '/thumb' : ''}`, key)
  return { url, deleted: !url && isPhotoEmpty(key) }
}

/** 回收站列表里的缩略图；取不到返回 null。 */
export function fetchTrashThumb(id: number): Promise<string | null> {
  return fetchCached(`${API_BASE}/trash/${id}/thumb`, `trash-thumb:${id}`)
}

// ── 日记图片（ADR 0044）：共用压缩、配额和 blob 缓存，按 id 取 ──

/** 选图 →（按档压缩）→ 上传，返回图片 id；取消或失败返回 null（失败已 toast）。 */
export async function pickAndUploadDiaryPhoto(quality: PhotoQuality): Promise<number | null> {
  const picked = await choosePhoto()
  if (picked.cancelled) return null
  if (!picked.path) {
    console.warn('[photo] 打开相机/相册失败：', picked.error)
    uni.showToast({ title: '没能打开相机或相册，请检查权限设置', icon: 'none' })
    return null
  }
  uni.showLoading({ title: '上传中…' })
  try {
    const original = await (await fetch(picked.path)).blob()
    const blob = quality === 'lossless' ? original : await compress(original, QUALITY_MAP[quality])
    if (blob.size > MAX_BYTES) {
      uni.showToast({ title: '照片超过 10MB，请换更低的压缩档', icon: 'none' })
      return null
    }
    const fd = new FormData()
    fd.append('file', blob, blob.type === 'image/png' ? 'photo.png' : 'photo.jpg')
    const res = await fetch(`${API_BASE}/diary/photos`, { method: 'POST', headers: authHeader(), body: fd })
    let body: { code?: number; message?: string; data?: number } | null = null
    try {
      body = await res.json()
    } catch {
      /* 非 JSON（如 nginx 错误页） */
    }
    uni.hideLoading()
    if (res.ok && body && body.code === 0 && body.data != null) {
      // 存储占用变了，相册也多了一张
      invalidate('diet', 'diary')
      return body.data
    }
    uni.showToast({ title: (body && body.message) || '上传失败', icon: 'none' })
    return null
  } catch {
    uni.hideLoading()
    // 断网时插不了图，但文字还能接着写（ADR 0044）
    uni.showToast({ title: '图片没传上去，文字可以先写', icon: 'none' })
    return null
  }
}

/** 取一张日记图片。deleted＝接口明确说没有这张（要显示「图片已删除」），和网络失败区分开。 */
export async function fetchDiaryPhoto(id: number, thumb: boolean): Promise<{ url: string | null; deleted: boolean }> {
  const key = diaryPhotoKey(id, thumb)
  const url = await fetchCached(`${API_BASE}/diary/photos/${id}${thumb ? '/thumb' : ''}`, key)
  return { url, deleted: !url && isPhotoEmpty(key) }
}

/** 把一张日记图片移到回收站。成功返回 true；失败已 toast。 */
export async function deleteDiaryPhoto(id: number): Promise<boolean> {
  try {
    const res = await fetch(`${API_BASE}/diary/photos/${id}`, { method: 'DELETE', headers: authHeader() })
    let body: { code?: number; message?: string } | null = null
    try {
      body = await res.json()
    } catch {
      /* 非 JSON（如 nginx 错误页） */
    }
    if (res.ok && body && body.code === 0) {
      invalidateDiaryPhoto(id)
      invalidate('diet', 'diary')
      return true
    }
    uni.showToast({ title: (body && body.message) || '删除失败', icon: 'none' })
    return false
  } catch {
    uni.showToast({ title: '删除失败', icon: 'none' })
    return false
  }
}

/** 把一张餐照移到回收站。成功返回 true；失败已 toast。 */
export async function deletePhoto(id: number): Promise<boolean> {
  try {
    const res = await fetch(`${API_BASE}/diet/photos/${id}`, { method: 'DELETE', headers: authHeader() })
    let body: { code?: number; message?: string } | null = null
    try {
      body = await res.json()
    } catch {
      /* 非 JSON（如 nginx 错误页） */
    }
    if (res.ok && body && body.code === 0) {
      invalidatePhoto(id)
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
