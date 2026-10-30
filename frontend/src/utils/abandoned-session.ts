// ── 「开新的」丢掉的训练（ADR 0047）──
//    会话只在本机（ADR 0020），回收站在服务器，所以丢掉时要传上去。断网也不该拦住开新训练：
//    先存进本机队列，有网再补传。服务端按开始时刻去重，传两次也只留一条。
//    ⚠️ 补传失败要静默：这不是主人当下在做的事，不能冒出「网络异常」。

import { API_BASE } from '@/api/base'
import { clearSession, loadSession, saveSession, type Session } from './session'

const PENDING_KEY = 'fitapp_abandoned_pending'

interface Abandoned {
  abandonedAt: number
  session: Session
}

function readPending(): Abandoned[] {
  try {
    const raw = uni.getStorageSync(PENDING_KEY) as string
    return raw ? (JSON.parse(raw) as Abandoned[]) : []
  } catch {
    return []
  }
}

function writePending(list: Abandoned[]): void {
  try {
    if (list.length) uni.setStorageSync(PENDING_KEY, JSON.stringify(list))
    else uni.removeStorageSync(PENDING_KEY)
  } catch {
    // 存储满或被禁用：最坏只是这一场进不了回收站，不能因此拦住开新训练
  }
}

/** 把本机这场训练移到回收站，并清掉本机会话。 */
export function abandonCurrentSession(): void {
  const s = loadSession()
  if (s && s.blocks.length > 0) {
    writePending([...readPending(), { abandonedAt: Date.now(), session: s }])
  }
  clearSession()
  void flushAbandoned()
}

let flushing: Promise<void> | null = null

/** 补传本机队列；已经在传就等那一次。回收站页进场时要等它，列表才看得到刚丢掉的那场。 */
export function flushAbandoned(): Promise<void> {
  if (!flushing) flushing = doFlush().finally(() => (flushing = null))
  return flushing
}

async function doFlush(): Promise<void> {
  const token = (uni.getStorageSync('fitapp_token') as string) || ''
  if (!token) return
  for (const item of readPending()) {
    let body: { code?: number } | null = null
    try {
      const res = await fetch(`${API_BASE}/trash/sessions`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify(item)
      })
      body = await res.json()
    } catch {
      return // 还是没网：留着下次再传
    }
    // 服务端不可用或登录失效：留着下次再传。其余业务错误重试也没用，别堵住后面的
    if (body?.code === 401 || body?.code === 503) return
    if (body?.code !== 0) console.warn('[abandoned] 丢掉的训练没传上去', body)
    writePending(readPending().filter((x) => x.session.startedAt !== item.session.startedAt))
  }
}

/** 恢复时写回本机：丢掉到恢复这段算作暂停，否则净时长会多出几天。 */
export function restoreAbandoned(item: Abandoned): void {
  const s = item.session
  const last = s.pauses[s.pauses.length - 1]
  // 丢掉时本来就停在暂停里：那段暂停还开着，不用再补
  if (!last || last.to !== null) s.pauses.push({ from: item.abandonedAt, to: Date.now() })
  saveSession(s)
}
