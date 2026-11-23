/**
 * App 内更新日志（ADR 0068）：唯一内容源是仓根 CHANGELOG.md，构建时注入成 `__CHANGELOG__`。
 *
 * - 按「## [版本] - 日期」切版本，「### Added / Changed / Fixed」切三类，「- 」开头是一条；「未发布」一节不显示；
 * - 开发字眼（括号里的 ADR 编号、「迁移 Vxx」、代码片段）普通账号一律去掉，管理员默认显示、可关；
 *   只在显示时去，原文不改；
 * - 自动更新后第一次打开弹一次「这次更新了什么」，跨了几个版本就合起来；记住看过的版本号。
 */

export type ChangeKind = 'added' | 'changed' | 'fixed' | 'other'

export interface ChangeSection {
  kind: ChangeKind
  label: string
  items: string[]
}

export interface ChangeVersion {
  version: string
  /** YYYY-MM-DD */
  date: string
  sections: ChangeSection[]
  /** 这一版一共几条。 */
  count: number
}

const KIND: Record<string, { kind: ChangeKind; label: string }> = {
  added: { kind: 'added', label: '新增' },
  changed: { kind: 'changed', label: '改动' },
  fixed: { kind: 'fixed', label: '修复' },
  removed: { kind: 'other', label: '移除' },
  deprecated: { kind: 'other', label: '弃用' },
  security: { kind: 'other', label: '安全' }
}

const VERSION_LINE = /^## \[([^\]]+)\](?:\s*-\s*(\d{4}-\d{2}-\d{2}))?/
const SECTION_LINE = /^### (.+)$/
const ITEM_LINE = /^- (.*)$/
const SUB_ITEM_LINE = /^\s+[-*] (.*)$/

/** 解析整份 CHANGELOG；「未发布」与没有版本号的节跳过。 */
export function parseChangelog(src: string): ChangeVersion[] {
  const out: ChangeVersion[] = []
  let ver: ChangeVersion | null = null
  let sec: ChangeSection | null = null
  for (const raw of src.split(/\r?\n/)) {
    const v = VERSION_LINE.exec(raw)
    if (v) {
      ver = /^\d+\.\d+\.\d+/.test(v[1]) ? { version: v[1], date: v[2] ?? '', sections: [], count: 0 } : null
      if (ver) out.push(ver)
      sec = null
      continue
    }
    if (!ver) continue
    const h = SECTION_LINE.exec(raw)
    if (h) {
      const k = KIND[h[1].trim().toLowerCase()] ?? { kind: 'other' as const, label: h[1].trim() }
      sec = { kind: k.kind, label: k.label, items: [] }
      ver.sections.push(sec)
      continue
    }
    if (!sec) continue
    const it = ITEM_LINE.exec(raw)
    if (it) {
      sec.items.push(it[1].trim())
      ver.count++
      continue
    }
    const last = sec.items.length - 1
    if (last < 0 || !raw.trim()) continue
    const sub = SUB_ITEM_LINE.exec(raw)
    // 续行接在上一条后面；子条目用分号接上
    sec.items[last] += sub ? `；${sub[1].trim()}` : raw.trim()
  }
  return out.filter((x) => x.count > 0)
}

const DEV_WORDS = /ADR|迁移|V\d+|scope|#\d+|`/

/** 给普通账号看：去掉括号里的开发字眼、零散的「ADR 00xx」「迁移 Vxx」，代码片段只留字。 */
export function stripDev(text: string): string {
  return text
    .replace(/[（(]([^（）()]*)[）)]/g, (m, inner: string) => (DEV_WORDS.test(inner) ? '' : m))
    .replace(/[，、,]?\s*(?:见\s*)?ADR\s*\d{4}(?:\s*[、,，]\s*\d{4})*/g, '')
    .replace(/[，、,]?\s*迁移\s*V\d+/g, '')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/\s{2,}/g, ' ')
    .trim()
}

/** 语义化版本比较：a 新于 b 为正。 */
export function compareVersion(a: string, b: string): number {
  const pa = a.split(/[.-]/).map((x) => Number.parseInt(x, 10) || 0)
  const pb = b.split(/[.-]/).map((x) => Number.parseInt(x, 10) || 0)
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0)
    if (d) return d
  }
  return 0
}

export const APP_VERSION = typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : 'dev'
const SOURCE = typeof __CHANGELOG__ === 'string' ? __CHANGELOG__ : ''

let cached: ChangeVersion[] | null = null
/** 全部版本，新的在前。 */
export function allVersions(): ChangeVersion[] {
  if (!cached) cached = parseChangelog(SOURCE)
  return cached
}

const SEEN_KEY = 'ft.changelog.seen'
const DEV_KEY = 'ft.changelog.dev'

function readKey(k: string): string {
  try {
    return String(uni.getStorageSync(k) || '')
  } catch {
    return ''
  }
}
function writeKey(k: string, v: string) {
  try {
    uni.setStorageSync(k, v)
  } catch {
    // 存不下：下次再弹一次而已
  }
}

/**
 * 这次升级还没看过的版本（新的在前）：看过的版本号之后、到当前版本为止。
 * 没记过看过的版本（刚装上，或者从没有更新日志的老版本升上来）就只给当前这一版。开发版不弹。
 */
export function unseenVersions(): ChangeVersion[] {
  if (APP_VERSION === 'dev') return []
  const list = allVersions().filter((v) => compareVersion(v.version, APP_VERSION) <= 0)
  const seen = readKey(SEEN_KEY)
  if (!seen) return list.filter((v) => v.version === APP_VERSION)
  if (compareVersion(seen, APP_VERSION) >= 0) return []
  return list.filter((v) => compareVersion(v.version, seen) > 0)
}

export function markSeen() {
  if (APP_VERSION !== 'dev') writeKey(SEEN_KEY, APP_VERSION)
}

/** 管理员看不看开发细节：默认看，可以关（只记在这台手机上）。 */
export function devDetailsOn(): boolean {
  return readKey(DEV_KEY) !== 'off'
}
export function setDevDetails(on: boolean) {
  writeKey(DEV_KEY, on ? 'on' : 'off')
}
