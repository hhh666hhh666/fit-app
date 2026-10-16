/**
 * 训练会话的本机持久化与计时口径（[ADR 0020]、[ADR 0022]）。
 *
 * ⚠️ 计时**不用**「每秒加一」：退到后台后安卓 WebView 会把页面的定时器限速甚至冻结，
 * 累加值必然少走。这里只记时间戳，显示时现算——净时长 = 现在 − 开始时刻 − 暂停合计，
 * 所以进程被杀、退到后台多久都不会算错。
 *
 * 口径统一到「净时间」：训练净时长与组次净用时都剔除暂停段，两者可比、不会互相打架。
 */

const STORAGE_KEY = 'fitapp_session'

/** 会话里的一个组次。重量与次数留成字符串：输入框的中间态（如 "6." ）不该被改写。 */
export interface SessionSet {
  w: string
  r: string
  done: boolean
  /** 本组完成的时刻（毫秒时间戳）；未完成、或撤回后为 null。 */
  doneAt: number | null
  /** 本组净用时（秒，剔除暂停段）；勾选完成时一次算好并冻结。 */
  dur: number | null
}

/** 会话里的一个动作（对应计划的 plan_exercise 或自由训练的占位块）。 */
export interface SessionBlock {
  exerciseId: number | null
  name: string
  muscle: string
  /** 「上次数据」文案（展示用，不参与计算）。 */
  last: string
  /** 计划目标提示文案。 */
  goal: string
  targetSets: number | null
  targetReps: number | null
  targetWeight: number | null
  sets: SessionSet[]
}

/** 一段暂停；`to` 为 null 表示此刻仍停在暂停里。 */
export interface PauseInterval {
  from: number
  to: number | null
}

export interface Session {
  /** 结构版本：将来改字段时用它判断要不要丢弃旧数据。 */
  v: 1
  /** 训练开始时刻（毫秒时间戳）。 */
  startedAt: number
  /** 来源计划（自由训练为 null）。 */
  planId: number | null
  title: string
  blocks: SessionBlock[]
  /** 当前查看的动作下标。 */
  current: number
  pauses: PauseInterval[]
}

/** 新建一条会话（还没铺动作；动作由调用方填）。 */
export function createSession(planId: number | null, title: string): Session {
  return { v: 1, startedAt: Date.now(), planId, title, blocks: [], current: 0, pauses: [] }
}

/** 是否正停在暂停里。 */
export function isPaused(s: Session): boolean {
  const last = s.pauses[s.pauses.length - 1]
  return !!last && last.to === null
}

/** 暂停合计（毫秒）：还没收尾的那段算到 `now`。 */
export function pausedMs(s: Session, now = Date.now()): number {
  return s.pauses.reduce((sum, p) => sum + Math.max(0, (p.to ?? now) - p.from), 0)
}

/** 区间 [a, b] 里有多少毫秒落在暂停段内。 */
export function pausedMsBetween(s: Session, a: number, b: number): number {
  let sum = 0
  for (const p of s.pauses) {
    const from = Math.max(a, p.from)
    const to = Math.min(b, p.to ?? b)
    if (to > from) sum += to - from
  }
  return sum
}

/** 训练净时长（秒）＝真实时间 − 暂停合计。 */
export function netElapsedSec(s: Session, now = Date.now()): number {
  return Math.max(0, Math.floor((now - s.startedAt - pausedMs(s, now)) / 1000))
}

/** 开始暂停。已经在暂停里就什么都不做。 */
export function pause(s: Session, now = Date.now()): void {
  if (isPaused(s)) return
  s.pauses.push({ from: now, to: null })
}

/** 结束暂停。不在暂停里就什么都不做。 */
export function resume(s: Session, now = Date.now()): void {
  const last = s.pauses[s.pauses.length - 1]
  if (last && last.to === null) last.to = now
}

/**
 * 全局「上一组完成」的时刻（跨动作按实际勾选先后），没有则为 null。
 * 撤回某一组的完成状态后，这里自然回退到剩下最后一个已完成的那组，不需要额外字段。
 *
 * `exclude` 跳过某一组：算某组用时时，**本组必须排除**——否则会把本组刚写上的 doneAt
 * 当成「上一组完成」，净用时恒为 0（见 `setDurationSec`）。
 */
export function lastDoneAt(s: Session, exclude?: SessionSet | null): number | null {
  let max: number | null = null
  for (const b of s.blocks) {
    for (const x of b.sets) {
      if (x === exclude) continue
      if (x.done && x.doneAt != null && (max === null || x.doneAt > max)) max = x.doneAt
    }
  }
  return max
}

/**
 * 某组的净用时（秒）：与上一组完成的间隔，剔除其中的暂停段；
 * 本动作第一组（或全局第一组）从开始训练算起。
 *
 * ⚠️ `self` 是要计算的那一组，**必须传**。调用方常常先把本组标成完成（写 done/doneAt）、
 * 再回头算用时，此时若不过滤本组，`lastDoneAt` 会返回本组的时刻，结果恒为 0。
 */
export function setDurationSec(s: Session, now = Date.now(), self?: SessionSet | null): number {
  const from = lastDoneAt(s, self) ?? s.startedAt
  const ms = Math.max(0, now - from - pausedMsBetween(s, from, now))
  return Math.round(ms / 1000)
}

/** 读本机会话；没有、结构版本不符或解析失败都返回 null。 */
export function loadSession(): Session | null {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY)
    if (!raw) return null
    const parsed = typeof raw === 'string' ? (JSON.parse(raw) as Session) : (raw as Session)
    if (!parsed || parsed.v !== 1 || !Array.isArray(parsed.blocks)) return null
    if (!Array.isArray(parsed.pauses)) parsed.pauses = []
    return parsed
  } catch {
    return null
  }
}

/** 写本机会话（每次变更都调一次，异常静默——存不下也不该打断训练）。 */
export function saveSession(s: Session): void {
  try {
    uni.setStorageSync(STORAGE_KEY, JSON.stringify(s))
  } catch {
    // 存储满或被禁用：不打断训练，最坏只是这一场不跨进程续
  }
}

/** 清掉本机会话（提交成功、或 0 组结束训练时调）。 */
export function clearSession(): void {
  try {
    uni.removeStorageSync(STORAGE_KEY)
  } catch {
    // 忽略
  }
}
