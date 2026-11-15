// 食物搜索结果里「为什么搜出了它」（ADR 0056）：服务端按名字或别名命中，
// 名字本身对不上、靠别名命中时，列表行上小字标出是哪个别名。

/** 关键词里的字按顺序出现在 text 里（中间可以隔别的字），空白不算字；和服务端的筛选同一口径。 */
export function matchesInOrder(text: string, keyword: string): boolean {
  const chars = Array.from(keyword).filter((c) => c.trim() !== '')
  if (chars.length === 0) return false
  let i = 0
  for (const c of Array.from(text)) {
    if (c === chars[i]) i++
    if (i === chars.length) return true
  }
  return false
}

/** 靠别名命中时返回那个别名；名字本身就对得上、没有关键词或没有别名时返回 null。 */
export function matchedAlias(food: { name: string; aliases?: string[] | null }, keyword: string): string | null {
  const kw = keyword.trim()
  if (!kw || !food.aliases?.length || matchesInOrder(food.name, kw)) return null
  // 优先整个相等的，其次连着包含的，最后按字顺序的
  return (
    food.aliases.find((a) => a === kw) ??
    food.aliases.find((a) => a.includes(kw)) ??
    food.aliases.find((a) => matchesInOrder(a, kw)) ??
    null
  )
}
