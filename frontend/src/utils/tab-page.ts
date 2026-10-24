// 主 tab 页的切页接线（ADR 0038 / 0040）。页面被保活、不再重建，入场只能每次 onShow 重放；
// 以前四个页面各抄一遍 tabDir／tabSeq／takeTabEnter／playTabEnter，现在收在这里。
//
// 用法：
//   const tab = useTabPage('log')
//   onShow(() => { if (!ensureLogin()) return; tab.enter(); ... })
//   <FtTabPage :page="tab" class="log"> <view class="log__body">…</view> </FtTabPage>
//
// enter() 由页面自己调、不在这里注册 onShow：登录守卫必须排在最前，顺序要看得见。

import { reactive } from 'vue'
import { playTabEnter, takeTabEnter } from '@/utils/nav'
import { TABS, type TabKey } from '@/utils/tabs'

export interface TabPage {
  key: TabKey
  /** 本次入场方向；底栏的指示条据此从上一格滑过来 */
  dir: 'left' | 'right' | null
  /** 每次 enter() 自增：底栏据此重放指示条、重读进行中的会话 */
  seq: number
  enter: () => void
}

export function useTabPage(key: TabKey): TabPage {
  const body = TABS.find((t) => t.key === key)!.body
  const page: TabPage = reactive({
    key,
    dir: null,
    seq: 0,
    enter: () => {
      const dir = takeTabEnter(key)
      page.dir = dir
      page.seq++
      playTabEnter(body, dir)
    }
  })
  return page
}
