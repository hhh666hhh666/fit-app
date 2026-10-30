// ── 极简 Markdown 解析：不引渲染库，输出结构化块，不走 v-html，所以没有注入 HTML 的风险 ──
//    默认只认接口文档用到的语法（标题／段落／无序列表／代码块／行内代码与粗体）。
//    `rich` 给日记用（ADR 0044）：多认斜体、链接、有序列表、引用块、分割线、图片与记录引用，
//    单个回车即换行；表格、嵌套列表、内嵌 HTML 按原文显示。

export type InlineKind = 'text' | 'code' | 'bold' | 'italic' | 'link'

export interface Inline {
  kind: InlineKind
  text: string
  /** 只有 link 有。 */
  href?: string
}

/** 日记正文里的图来自哪：日记图片或餐照（ADR 0046）。 */
export type PhotoSource = 'diary' | 'meal'

export type Block =
  | { type: 'heading'; level: number; inlines: Inline[] }
  | { type: 'paragraph'; inlines: Inline[] }
  | { type: 'list'; items: Inline[][] }
  | { type: 'code'; text: string }
  | { type: 'ordered'; start: number; items: Inline[][] }
  | { type: 'quote'; inlines: Inline[] }
  | { type: 'hr' }
  | { type: 'image'; source: PhotoSource; id: number }
  | { type: 'ref'; key: string; label: string }

export interface ParseOptions {
  rich?: boolean
}

const PLAIN_INLINE = /`([^`]+)`|\*\*(.+?)\*\*/g
// 链接只认这三种协议：别的写法（javascript: 之类）按原文显示
const RICH_INLINE = /`([^`]+)`|\*\*(.+?)\*\*|\*([^*\s](?:[^*\n]*[^*\s])?)\*|\[([^\]\n]+)\]\(((?:https?:\/\/|mailto:)[^)\s]+)\)/g

// ⚠️ 下面两种语法写进了主人的正文，是长期格式，改它们要迁移数据（ADR 0044）
const IMAGE_LINE = /^!\[[^\]]*\]\((diary|meal)-photo:(\d+)\)\s*$/
const REF_LINE = /^\[([^\]]*)\]\(fit-ref:([^)\s]+)\)\s*$/
const HR_LINE = /^(-{3,}|\*{3,})\s*$/
const UL_LINE = /^[-*] /
const OL_LINE = /^(\d+)\. /
const QUOTE_LINE = /^> ?/

export function parseInline(text: string, options: ParseOptions = {}): Inline[] {
  const out: Inline[] = []
  let last = 0
  for (const m of text.matchAll(options.rich ? RICH_INLINE : PLAIN_INLINE)) {
    if (m.index > last) out.push({ kind: 'text', text: text.slice(last, m.index) })
    if (m[1] !== undefined) {
      out.push({ kind: 'code', text: m[1] })
    } else if (m[2] !== undefined) {
      // 粗体里常夹行内代码，这里压平成纯粗体，只去掉反引号
      out.push({ kind: 'bold', text: m[2].replace(/`/g, '') })
    } else if (m[3] !== undefined) {
      out.push({ kind: 'italic', text: m[3] })
    } else {
      out.push({ kind: 'link', text: m[4], href: m[5] })
    }
    last = m.index + m[0].length
  }
  if (last < text.length) out.push({ kind: 'text', text: text.slice(last) })
  return out
}

/** 这一行是不是另一个块的开头（段落读到这里就该停）。 */
function startsBlock(line: string, rich: boolean): boolean {
  if (/^(#{1,6} |- |```)/.test(line)) return true
  if (!rich) return false
  return (
    UL_LINE.test(line) ||
    OL_LINE.test(line) ||
    QUOTE_LINE.test(line) ||
    HR_LINE.test(line) ||
    IMAGE_LINE.test(line) ||
    REF_LINE.test(line)
  )
}

export function parseMarkdown(source: string, options: ParseOptions = {}): Block[] {
  const rich = !!options.rich
  const blocks: Block[] = []
  const lines = source.replace(/\r\n/g, '\n').split('\n')
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    let m: RegExpExecArray | null
    if (!line.trim()) {
      i++
    } else if (line.startsWith('```')) {
      const code: string[] = []
      i++
      while (i < lines.length && !lines[i].startsWith('```')) code.push(lines[i++])
      i++
      blocks.push({ type: 'code', text: code.join('\n') })
    } else if (/^#{1,6} /.test(line)) {
      const level = line.indexOf(' ')
      blocks.push({ type: 'heading', level, inlines: parseInline(line.slice(level + 1), options) })
      i++
    } else if (rich && (m = IMAGE_LINE.exec(line))) {
      blocks.push({ type: 'image', source: m[1] as PhotoSource, id: Number(m[2]) })
      i++
    } else if (rich && (m = REF_LINE.exec(line))) {
      blocks.push({ type: 'ref', key: m[2], label: m[1] })
      i++
    } else if (rich && HR_LINE.test(line)) {
      blocks.push({ type: 'hr' })
      i++
    } else if (rich && QUOTE_LINE.test(line)) {
      const quote: string[] = []
      while (i < lines.length && QUOTE_LINE.test(lines[i])) quote.push(lines[i++].replace(QUOTE_LINE, ''))
      blocks.push({ type: 'quote', inlines: parseInline(quote.join('\n'), options) })
    } else if (rich && (m = OL_LINE.exec(line))) {
      const start = Number(m[1])
      const items: Inline[][] = []
      while (i < lines.length && OL_LINE.test(lines[i])) items.push(parseInline(lines[i++].replace(OL_LINE, ''), options))
      blocks.push({ type: 'ordered', start, items })
    } else if (rich ? UL_LINE.test(line) : line.startsWith('- ')) {
      const items: Inline[][] = []
      const isItem = (l: string) => (rich ? UL_LINE.test(l) : l.startsWith('- '))
      while (i < lines.length && isItem(lines[i])) items.push(parseInline(lines[i++].slice(2), options))
      blocks.push({ type: 'list', items })
    } else {
      const para: string[] = []
      while (i < lines.length && lines[i].trim() && !startsBlock(lines[i], rich)) para.push(lines[i++])
      if (para.length === 0) {
        // 走到这里说明这一行被认成块开头、却没有对应的分支接住：按普通段落收下，不能死循环
        para.push(lines[i++])
      }
      blocks.push({ type: 'paragraph', inlines: parseInline(para.join(rich ? '\n' : ''), options) })
    }
  }
  return blocks
}

/** 日记列表里的一行摘要：去掉语法，图片写成「[图片]」，记录引用只留说明。 */
export function plainPreview(source: string): string {
  const flat = (inlines: Inline[]) => inlines.map((s) => s.text).join('')
  return parseMarkdown(source, { rich: true })
    .map((b) => {
      switch (b.type) {
        case 'heading':
        case 'paragraph':
        case 'quote':
          return flat(b.inlines)
        case 'list':
        case 'ordered':
          return b.items.map(flat).join(' ')
        case 'code':
          return b.text
        case 'image':
          return '[图片]'
        case 'ref':
          return b.label
        default:
          return ''
      }
    })
    .filter(Boolean)
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim()
}
