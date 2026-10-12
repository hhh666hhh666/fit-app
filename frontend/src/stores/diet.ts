import { defineStore } from 'pinia'
import { getSettings, updateSettings, type DietSetting } from '@/api/nutrition'

// ── 饮食设置（图片压缩等级 / 饮水步进 / 目标）：读取处多、改动处少，跨页共享 ──
export const useDietStore = defineStore('diet', {
  state: () => ({
    settings: null as DietSetting | null,
    loading: false
  }),
  actions: {
    /** 默认强刷（数据轻，保正确）；失败保留旧值不抛出。 */
    async load(force = true) {
      if (this.loading) return
      if (this.settings && !force) return
      this.loading = true
      try {
        this.settings = await getSettings()
      } catch {
        // request 层已统一提示
      } finally {
        this.loading = false
      }
    },
    async save(payload: DietSetting) {
      this.settings = await updateSettings(payload)
    }
  }
})
