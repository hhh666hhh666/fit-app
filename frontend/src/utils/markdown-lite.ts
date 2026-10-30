// ── 极简 Markdown 解析：只认接口文档用到的语法（标题／段落／无序列表／代码块／行内代码与粗体）──
//    这一页不值得为此引入渲染库；文档要用新语法时，这里跟着加。

export type InlineKind = 'text' | 'code' | 'bold'

export interface Inline {
  kind: InlineKind
  text: string
}

export type Block =
  | { type: 'heading'; level: number; inlines: Inline[] }
  | { type: 'paragraph'; inlines: Inline[] }
  | { type: 'list'; items: Inline[][] }
  | { type: 'code'; text: string }

export function parseInline(text: string): Inline[] {
  const out: Inline[] = []
  const pattern = /`([^`]+)`|\*\*(.+?)\*\*/g
  let last = 0
  for (const m of text.matchAll(pattern)) {
    if (m.index > last) out.push({ kind: 'text', text: text.slice(last, m.index) })
    if (m[1] !== undefined) {
      out.push({ kind: 'code', text: m[1] })
    } else {
      // 粗体里常夹行内代码，这里压平成纯粗体，只去掉反引号
      out.push({ kind: 'bold', text: m[2].replace(/`/g, '') })
    }
    last = m.index + m[0].length
  }
  if (last < text.length) out.push({ kind: 'text', text: text.slice(last) })
  return out
}

export function parseMarkdown(source: string): Block[] {
  const blocks: Block[] = []
  const lines = source.replace(/\r\n/g, '\n').split('\n')
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
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
      blocks.push({ type: 'heading', level, inlines: parseInline(line.slice(level + 1)) })
      i++
    } else if (line.startsWith('- ')) {
      const items: Inline[][] = []
      while (i < lines.length && lines[i].startsWith('- ')) items.push(parseInline(lines[i++].slice(2)))
      blocks.push({ type: 'list', items })
    } else {
      const para: string[] = []
      while (i < lines.length && lines[i].trim() && !/^(#{1,6} |- |```)/.test(lines[i])) para.push(lines[i++])
      blocks.push({ type: 'paragraph', inlines: parseInline(para.join('')) })
    }
  }
  return blocks
}
