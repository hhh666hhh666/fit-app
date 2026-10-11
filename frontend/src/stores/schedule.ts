import { defineStore } from 'pinia'

// ── 计划与周安排（本地 mock，后端接通后替换）──

export interface PlanBrief {
  id: number
  name: string
  exercises: string[]
}

/** 周安排某一格：计划 id ｜ 'rest'（休息）｜ null（未安排） */
export type DaySlot = number | 'rest' | null

const MOCK_PLANS: PlanBrief[] = [
  { id: 1, name: '胸 & 三头', exercises: ['杠铃卧推', '上斜哑铃卧推', '坐姿肩推', '绳索下压'] },
  { id: 2, name: '背 & 二头', exercises: ['引体向上', '杠铃划船', '面拉', '哑铃弯举'] },
  { id: 3, name: '腿 & 核心', exercises: ['深蹲', '硬拉', '腿举', '平板支撑'] }
]

// 0=周一 … 6=周日
const MOCK_WEEK: DaySlot[] = [1, 2, 3, 'rest', 1, 2, 'rest']

export const useScheduleStore = defineStore('schedule', {
  state: () => ({
    plans: MOCK_PLANS as PlanBrief[],
    week: [...MOCK_WEEK] as DaySlot[]
  }),
  getters: {
    /** 今天在周表里的下标（0=周一） */
    todayIndex(): number {
      return (new Date().getDay() + 6) % 7
    },
    todaySlot(): DaySlot {
      return this.week[this.todayIndex] ?? null
    },
    todayPlan(): PlanBrief | null {
      const slot = this.todaySlot
      return typeof slot === 'number' ? this.plans.find((p) => p.id === slot) ?? null : null
    },
    isRest(): boolean {
      return this.todaySlot === 'rest'
    },
    planOf() {
      return (id: number | null | undefined): PlanBrief | null =>
        typeof id === 'number' ? this.plans.find((p) => p.id === id) ?? null : null
    }
  },
  actions: {
    /** 设置某天：计划 id ｜ 'rest' ｜ null */
    setDay(idx: number, slot: DaySlot) {
      this.week[idx] = slot
    }
  }
})
