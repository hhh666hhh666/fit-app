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

/** ISO 时间 → "10月11日" */
export function formatDateLabel(iso: string): string {
  const d = new Date(iso)
  return `${d.getMonth() + 1}月${d.getDate()}日`
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
