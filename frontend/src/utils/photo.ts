// ── 餐照：选择 / 压缩（四档）/ 上传 / 取图（H5 原生通道；<img> 带不了鉴权头，一律 fetch→blob）──

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

/** 选一张照片（相册/相机），返回临时路径（H5 下是 blob URL）；取消返回 null。 */
export function choosePhoto(): Promise<string | null> {
  return new Promise((resolve) => {
    uni.chooseImage({
      count: 1,
      sizeType: ['original'],
      sourceType: ['album', 'camera'],
      success: (res) => resolve((res.tempFilePaths && res.tempFilePaths[0]) || null),
      fail: () => resolve(null)
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

/** 长边 ≤1600 等比缩放 + JPEG 重编码。 */
async function compress(blob: Blob, quality: number): Promise<Blob> {
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
  const path = await choosePhoto()
  if (!path) return false
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
      `/api/diet/photos?date=${encodeURIComponent(date)}&meal=${encodeURIComponent(meal)}`,
      { method: 'POST', headers: authHeader(), body: fd }
    )
    let body: { code?: number; message?: string } | null = null
    try {
      body = await res.json()
    } catch {
      /* 非 JSON（如 nginx 错误页） */
    }
    if (res.ok && body && body.code === 0) return true
    uni.showToast({ title: (body && body.message) || '上传失败', icon: 'none' })
    return false
  } catch {
    uni.showToast({ title: '照片处理失败', icon: 'none' })
    return false
  }
}

/** 取某餐照片的临时地址（fetch → objectURL）；无照片返回 null。调用方在替换/卸载时 revoke。 */
export async function fetchPhotoUrl(date: string, meal: string): Promise<string | null> {
  try {
    const res = await fetch(
      `/api/diet/photos/${encodeURIComponent(date)}/${encodeURIComponent(meal)}`,
      { headers: authHeader() }
    )
    if (!res.ok) return null
    const blob = await res.blob()
    return blob.size ? URL.createObjectURL(blob) : null
  } catch {
    return null
  }
}

/** 用完即弃地释放 objectURL。 */
export function releasePhotoUrl(url: string | null) {
  if (url && url.startsWith('blob:')) {
    URL.revokeObjectURL(url)
  }
}
