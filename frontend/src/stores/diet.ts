import { defineStore } from 'pinia'
import { getSettings, updateSettings, type DietSetting } from '@/api/nutrition'
import { invalidate } from '@/api/cache'

// ── 饮食设置（图片压缩等级 / 饮水步进 / 目标）：读取处多、改动处少，跨页共享 ──
export const useDietStore = defineStore('diet', {
  state: () => ({
    settings: null as DietSetting | null,
    loading: false
  }),
  actions: {
    /**
     * 默认走缓存（ADR 0035）：重复调不发请求，`getSettings` 命中时返回同一对象。
     * `force = true` 先作废「饮食」域。失败保留旧值不抛出。
     */
    async load(force = false) {
      if (this.loading) return
      if (force) invalidate('diet')
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
