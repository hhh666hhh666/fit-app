// ── 系统确认框的统一口径 ──
// 全站的 uni.showModal 都走这里：确认钮默认深字，会删数据 / 丢数据的传 danger 走危险红，
// 取消钮次要灰（uni 默认是纯黑取消 ＋ iOS 蓝确认，和晴窗不搭；框本身的皮在 App.vue 里覆盖）。
//
// ⚠️ 取消位（左边）只放「不做」：安卓返回键会替用户点它（utils/backStack.ts）。
//    要让人在两个动作之间选，用 FtSheet 放两个按钮，别塞进确认框的两个位子。
import { PALETTE } from './palette'

/** 删除确认框里的统一说法（ADR 0047）。 */
export const TRASH_HINT = '会移到回收站，30 天内可恢复。'

export interface AskOptions {
  title: string
  content?: string
  confirmText?: string
  cancelText?: string
  /** 删除、放弃、丢数据：确认钮标危险红 */
  danger?: boolean
}

/** 点确认得 true，取消 / 返回键 / 调用失败都得 false */
export function ask(o: AskOptions): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: o.title,
      content: o.content ?? '',
      confirmText: o.confirmText ?? '确定',
      cancelText: o.cancelText ?? '取消',
      confirmColor: o.danger ? PALETTE.danger : PALETTE.ink,
      cancelColor: PALETTE.inkMuted,
      success: ({ confirm }) => resolve(!!confirm),
      fail: () => resolve(false)
    })
  })
}
