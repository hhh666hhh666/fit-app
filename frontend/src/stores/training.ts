import { defineStore } from 'pinia'
import type { WorkoutDetail } from '@/api/workout'

// ── 刚结束的那次训练（汇总页读它；数据来自 POST /api/workouts 的响应）──
export const useTrainingStore = defineStore('training', {
  state: () => ({
    current: null as WorkoutDetail | null
  }),
  actions: {
    setCurrent(detail: WorkoutDetail) {
      this.current = detail
    }
  }
})
