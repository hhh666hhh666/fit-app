import { defineStore } from 'pinia'
import type { FoodDraft } from '@/api/nutrition'

/**
 * 标签识别草稿的一次性回传通道（仿 pick store；ADR 0018）：
 * 食物库页识别成功 → set；「编辑食物」页 onLoad → take（取走即清，刷新不残留）。
 * 草稿有 45 个字段，不走 URL——大对象经 store 传，路由只带一个 recognized=1 标记。
 */
export const useRecognitionStore = defineStore('recognition', {
  state: () => ({
    draft: null as FoodDraft | null
  }),
  actions: {
    set(draft: FoodDraft) {
      this.draft = draft
    },
    /** 取走并清空；没有返回 null。 */
    take(): FoodDraft | null {
      const d = this.draft
      this.draft = null
      return d
    }
  }
})
