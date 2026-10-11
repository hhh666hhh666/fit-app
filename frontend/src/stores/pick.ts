import { defineStore } from 'pinia'

export interface PickedExercise {
  id: number
  name: string
  muscle: string
}

/**
 * 动作选择的一次性回调通道：动作库（点选模式）→ 计划编辑页。
 * 双方约定：选页 set，用页「取走即清」（take），避免 select 状态残留。
 */
export const usePickStore = defineStore('pick', {
  state: () => ({
    picked: null as PickedExercise | null
  }),
  actions: {
    pick(item: PickedExercise) {
      this.picked = item
    },
    /** 取走并清空；没选中返回 null。 */
    take(): PickedExercise | null {
      const p = this.picked
      this.picked = null
      return p
    }
  }
})
