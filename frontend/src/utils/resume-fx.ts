// 「训练进行中」的动效（设置页可切）：作用在底栏中间圆钮的外圈（ADR 0041；以前是首页横幅的边框）。
// 只影响本机观感，所以和体重单位一样落本地存储，不进服务器设置。

export type ResumeFx = 'meteor' | 'glow' | 'off'

const STORAGE_KEY = 'fitapp_resume_fx'

export const RESUME_FX_OPTIONS: { key: ResumeFx; label: string; desc: string }[] = [
  { key: 'meteor', label: '流星', desc: '一颗流星绕着圆钮匀速跑（默认）' },
  { key: 'glow', label: '流光', desc: '两段柔光绕着圆钮流动' },
  { key: 'off', label: '关闭', desc: '只换成「继续」图标，最省电' }
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
