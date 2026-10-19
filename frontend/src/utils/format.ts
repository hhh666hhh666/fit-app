/** 千分位：1234 → "1,234" */
export function formatThousands(n: number): string {
  return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** 秒 → "mm:ss" */
export function formatClock(totalSec: number): string {
  const m = String(Math.floor(totalSec / 60)).padStart(2, '0')
  const s = String(totalSec % 60).padStart(2, '0')
  return `${m}:${s}`
}

/** 秒 → "2 小时 45 分"（不足 1 小时为 "45 分钟"） */
export function formatDuration(totalSec: number): string {
  const h = Math.floor(totalSec / 3600)
  const m = Math.round((totalSec % 3600) / 60)
  return h > 0 ? `${h} 小时 ${m} 分` : `${m} 分钟`
}

/** ISO 时间 → "10月11日" */
export function formatDateLabel(iso: string): string {
  const d = new Date(iso)
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

/** ISO 时间 → "23:51"（只取时分，与日期分两行显示时用） */
export function formatTimeLabel(iso: string): string {
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** ISO 时间 → "10月11日 23:51"（精确到分，用来区分同一天的多条记录） */
export function formatDateTimeLabel(iso: string): string {
  const d = new Date(iso)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${d.getMonth() + 1}月${d.getDate()}日 ${hh}:${mm}`
}

/**
 * 本地时间 → 后端要的 ISO 串：`2026-10-13T23:51:00`。
 * ⚠️ 后端只认带 T 的这一种（空格分隔会 400）——见 `application.yml` 里 jackson 那条注释。
 */
export function toIsoMinute(date: string, time: string): string {
  return `${date}T${time}:00`
}

/** 取今天的 `YYYY-MM-DD` */
export function todayKey(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 取当前时刻的 `HH:mm` */
export function nowTime(): string {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** 肌群码 → 中文显示（码表见 CONTEXT.md「肌群」词条） */
const MUSCLE_LABELS: Record<string, string> = {
  chest: '胸',
  back: '背',
  leg: '腿',
  shoulder: '肩',
  arm: '手臂',
  core: '核心',
  cardio: '有氧'
}

export function muscleLabel(code?: string | null): string {
  if (!code) return ''
  return MUSCLE_LABELS[code] ?? code
}
