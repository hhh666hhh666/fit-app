// 首页「训练进行中」横幅的边框动效（设置页可切）。
// 只影响本机观感，所以和体重单位一样落本地存储，不进服务器设置。

export type ResumeFx = 'meteor' | 'glow' | 'off'

const STORAGE_KEY = 'fitapp_resume_fx'

export const RESUME_FX_OPTIONS: { key: ResumeFx; label: string; desc: string }[] = [
  { key: 'meteor', label: '流星', desc: '两颗流星沿边框匀速对跑（默认）' },
  { key: 'glow', label: '流光', desc: '四层旋转光带（旧版）' },
  { key: 'off', label: '关闭', desc: '静态细边，最省电' }
]

export function getResumeFx(): ResumeFx {
  try {
    const v = uni.getStorageSync(STORAGE_KEY)
    return v === 'glow' || v === 'off' ? v : 'meteor'
  } catch {
    return 'meteor'
  }
}

export function setResumeFx(fx: ResumeFx): void {
  uni.setStorageSync(STORAGE_KEY, fx)
}
