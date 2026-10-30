// ── 日记草稿（ADR 0044）：每次改动存本机，点「保存」才上传 ──
//    App 被系统杀掉、断网、误退都不丢。每个编辑对象一份：新写一篇共用一份，改已有的按 id 各一份。
//    键带用户名：换账号看不到别人的草稿；登出不清，下次登录还能接着写。

export interface DiaryDraft {
  date: string
  time: string
  title: string
  content: string
}

function keyOf(id: number | null): string {
  const user = (uni.getStorageSync('fitapp_username') as string) || ''
  return `fitapp_diary_draft:${user}:${id ?? 'new'}`
}

export function loadDraft(id: number | null): DiaryDraft | null {
  try {
    const raw = uni.getStorageSync(keyOf(id))
    return raw ? (JSON.parse(raw as string) as DiaryDraft) : null
  } catch {
    return null
  }
}

export function saveDraft(id: number | null, draft: DiaryDraft): void {
  try {
    uni.setStorageSync(keyOf(id), JSON.stringify(draft))
  } catch {
    // 存储满了只是少一层保险，不该打断写作
  }
}

export function clearDraft(id: number | null): void {
  try {
    uni.removeStorageSync(keyOf(id))
  } catch {
    // 同上
  }
}
