import { defineStore } from 'pinia'
import { getSchedule, listPlans, setScheduleDay, type PlanListItem } from '@/api/plan'
import { invalidate } from '@/api/cache'

// ── 计划与周安排（真接口；ADR 0004 周安排驱动「开始训练」）──

/** 周安排某一格：计划 id ｜ 'rest'（休息）｜ null（未安排） */
export type DaySlot = number | 'rest' | null

const EMPTY_WEEK: DaySlot[] = [null, null, null, null, null, null, null]

export const useScheduleStore = defineStore('schedule', {
  state: () => ({
    plans: [] as PlanListItem[],
    /** 0=周一 … 6=周日 */
    week: [...EMPTY_WEEK] as DaySlot[],
    loading: false
  }),
  getters: {
    /** 今天在周表里的下标（0=周一） */
    todayIndex(): number {
      return (new Date().getDay() + 6) % 7
    },
    todaySlot(): DaySlot {
      return this.week[this.todayIndex] ?? null
    },
    todayPlan(): PlanListItem | null {
      const slot = this.todaySlot
      return typeof slot === 'number' ? this.plans.find((p) => p.id === slot) ?? null : null
    },
    isRest(): boolean {
      return this.todaySlot === 'rest'
    },
    planOf() {
      return (id: number | null | undefined): PlanListItem | null =>
        typeof id === 'number' ? this.plans.find((p) => p.id === id) ?? null : null
    }
  },
  actions: {
    /**
     * 拉取计划列表 + 周表。页面 `onShow` 调用即可——**重复调不会发请求**：
     * 取数走 `api/cache.ts` 的按域缓存（ADR 0035），命中时返回同一份数据，赋值也不触发重渲染。
     * `force = true` 先作废「计划」域（下拉刷新那种「就要最新」的场合）。
     */
    async load(force = false) {
      if (this.loading) return
      if (force) invalidate('plan')
      this.loading = true
      try {
        const [plans, days] = await Promise.all([listPlans(), getSchedule()])
        this.plans = plans
        // 归一化：后端 Jackson 省略 null 字段（planId 可能 undefined）
        this.week = days.map((d) => (d.isRest ? 'rest' : d.planId ?? null)) as DaySlot[]
      } catch {
        // request 层已统一提示；保留旧数据
      } finally {
        this.loading = false
      }
    },

    /** 设置某天并落库；失败回滚本地状态（request 层已统一 toast）。 */
    async setDay(idx: number, slot: DaySlot) {
      const prev = this.week[idx]
      this.week[idx] = slot
      try {
        await setScheduleDay(
          idx,
          slot === 'rest' ? { isRest: true } : slot === null ? {} : { planId: slot }
        )
      } catch (e) {
        this.week[idx] = prev
        throw e
      }
    }
  }
})
