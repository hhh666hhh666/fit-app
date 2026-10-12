<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  deleteFood,
  exportFoods,
  importFoods,
  listFoods,
  type FoodItem,
  type ImportResult
} from '@/api/nutrition'
import { ensureLogin } from '@/utils/guard'

// ── 食物库：列表 / 搜索 / 增改删（编辑跳独立页）/ JSON 导入导出 ──

const all = ref<FoodItem[]>([])
const loading = ref(false)
const keyword = ref('')

onShow(() => {
  if (!ensureLogin()) return
  void load()
})

async function load() {
  loading.value = true
  try {
    all.value = await listFoods()
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

const filtered = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) return all.value
  return all.value.filter((f) => f.name.includes(kw))
})

function goBack() {
  uni.navigateBack()
}

function goNew() {
  uni.navigateTo({ url: '/pages/food-edit/index' })
}

function onTap(f: FoodItem) {
  uni.showActionSheet({
    itemList: ['编辑', '删除'],
    success: ({ tapIndex }) => {
      if (tapIndex === 0) {
        uni.navigateTo({ url: `/pages/food-edit/index?id=${f.id}` })
      } else if (tapIndex === 1) {
        void remove(f)
      }
    }
  })
}

function remove(f: FoodItem) {
  uni.showModal({
    title: '删除食物',
    content: `删除「${f.name}」？历史饮食记录不受影响（营养按记录时的快照）。`,
    confirmText: '删除',
    confirmColor: '#C4453C',
    success: async ({ confirm }) => {
      if (!confirm) return
      try {
        await deleteFood(f.id)
        all.value = all.value.filter((x) => x.id !== f.id)
        uni.showToast({ title: '已删除', icon: 'none' })
      } catch {
        // request 层已统一提示
      }
    }
  })
}

function perText(f: FoodItem): string {
  return `每100${f.unit === 'ml' ? 'ml' : 'g'} · ${Math.round(f.kcal)} 大卡 · 蛋白 ${f.protein}g`
}

// ── 导入 ──

const importSheet = ref({ show: false, text: '', result: null as ImportResult | null, busy: false })

function openImport() {
  importSheet.value = { show: true, text: importSheet.value.text, result: null, busy: false }
}

async function doImport() {
  const s = importSheet.value
  if (!s.text.trim()) {
    uni.showToast({ title: '先粘贴 JSON', icon: 'none' })
    return
  }
  s.busy = true
  try {
    s.result = await importFoods(s.text)
    await load()
  } catch {
    // request 层已统一提示
  } finally {
    s.busy = false
  }
}

// ── 导出 ──

const exportSheet = ref({ show: false, text: '' })

async function doExport() {
  try {
    const data = await exportFoods()
    if (data.length === 0) {
      uni.showToast({ title: '食物库还是空的', icon: 'none' })
      return
    }
    const text = JSON.stringify(data, null, 2)
    exportSheet.value = { show: true, text }
    uni.setClipboardData({ data: text })
  } catch {
    // request 层已统一提示
  }
}

function copyExport() {
  uni.setClipboardData({ data: exportSheet.value.text })
}
</script>

<template>
  <view class="foods">
    <view class="foods__head">
      <text class="foods__back" @click="goBack">‹</text>
      <text class="foods__title">食物库</text>
      <text class="foods__new" @click="goNew">+ 新增</text>
    </view>

    <view class="foods__body">
      <view class="search">
        <wd-icon name="search" size="16px" color="#8a8f98" />
        <input
          v-model="keyword"
          class="search__input"
          placeholder="搜索食物"
          placeholder-class="search__ph"
          confirm-type="search"
        />
        <text v-if="keyword" class="search__clear" @click="keyword = ''">×</text>
      </view>

      <view class="ops">
        <text class="ops__btn" @click="openImport">导入 JSON</text>
        <text class="ops__btn" @click="doExport">导出 JSON</text>
        <text class="ops__meta ft-num">{{ all.length }} 项</text>
      </view>

      <view class="flist">
        <view v-for="f in filtered" :key="f.id" class="frow" @click="onTap(f)">
          <view class="frow__mid">
            <text class="frow__name">{{ f.name }}</text>
            <text class="frow__sub ft-num">{{ perText(f) }}</text>
          </view>
          <text class="frow__go">›</text>
        </view>
        <view v-if="!loading && filtered.length === 0" class="flist__empty">
          {{ all.length === 0 ? '还没有食物——点右上「+ 新增」，或导入 JSON' : '没有匹配的食物' }}
        </view>
      </view>
    </view>

    <!-- 导入弹层 -->
    <view v-if="importSheet.show" class="fs-mask" @click="importSheet.show = false" />
    <view v-if="importSheet.show" class="fs">
      <view class="fs__handle" />
      <text class="fs__title">导入 JSON</text>
      <text class="fs__hint">粘贴食物数组：数值按「每 100g/100ml」，也可给 kj 自动换算；同名会跳过</text>
      <textarea
        v-model="importSheet.text"
        class="fs__area"
        placeholder='[{"name":"鸡蛋","kcal":139,"protein":13.1,"fat":8.6,"carb":2.4,"sodium":131}]'
        placeholder-class="fs__ph"
        maxlength="-1"
      />
      <template v-if="importSheet.result">
        <view class="fs__result">
          <text class="fs__line ft-num">
            成功 {{ importSheet.result.imported }} · 跳过 {{ importSheet.result.skipped.length }} ·
            失败 {{ importSheet.result.failed.length }}
          </text>
          <text v-for="(s, i) in importSheet.result.skipped" :key="'s' + i" class="fs__skip">
            跳过（同名）：{{ s }}
          </text>
          <text v-for="(fl, i) in importSheet.result.failed" :key="'f' + i" class="fs__fail">
            失败：{{ fl.name }} —— {{ fl.reason }}
          </text>
        </view>
      </template>
      <view
        class="ft-btn ft-btn--primary ft-btn--block fs__btn"
        :class="{ 'ft-btn--disabled': importSheet.busy }"
        @click="doImport"
      >
        导入
      </view>
    </view>

    <!-- 导出弹层 -->
    <view v-if="exportSheet.show" class="fs-mask" @click="exportSheet.show = false" />
    <view v-if="exportSheet.show" class="fs">
      <view class="fs__handle" />
      <text class="fs__title">导出 JSON（已复制到剪贴板）</text>
      <textarea :value="exportSheet.text" class="fs__area" maxlength="-1" />
      <view class="ft-btn ft-btn--primary ft-btn--block fs__btn" @click="copyExport">
        再复制一次
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.foods {
  min-height: 100vh;
}

.foods__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 12rpx;
}

.foods__back {
  font-size: 56rpx;
  line-height: 1;
  color: $app-ink;
  width: 64rpx;
}

.foods__title {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.foods__new {
  font-size: $text-base;
  color: $app-ink;
  width: 120rpx;
  text-align: right;
}

.foods__body {
  padding: 12rpx 32rpx 40rpx;
}

.search {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-lg;
  padding: 16rpx 22rpx;
}

.search__input {
  flex: 1;
  font-size: $text-base;
  color: $app-ink;
}

.search__ph {
  color: $app-ink-subtle;
}

.search__clear {
  font-size: $text-md;
  color: $app-ink-subtle;
  padding: 0 6rpx;
}

.ops {
  display: flex;
  align-items: center;
  gap: 32rpx;
  padding: 22rpx 8rpx 16rpx;
}

.ops__btn {
  font-size: $text-sm;
  color: $app-ink-muted;
  border-bottom: 1rpx dashed $app-line;
  padding-bottom: 2rpx;
}

.ops__meta {
  margin-left: auto;
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.flist {
  background: $app-surface;
  border: 1rpx solid $app-line-soft;
  border-radius: $radius-lg;
  overflow: hidden;
}

.frow {
  display: flex;
  align-items: center;
  padding: 26rpx 28rpx;
}

.frow + .frow {
  border-top: 1rpx solid $app-line-soft;
}

.frow__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.frow__name {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.frow__sub {
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.frow__go {
  font-size: 40rpx;
  color: $app-ink-subtle;
  line-height: 1;
  margin-left: 16rpx;
}

.flist__empty {
  padding: 60rpx 24rpx;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

/* ── 弹层 ── */
.fs-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(23, 25, 29, 0.4);
  z-index: 90;
}

.fs {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: $app-surface;
  border-radius: 40rpx 40rpx 0 0;
  padding: 20rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
  z-index: 91;
  max-height: 88vh;
  overflow-y: auto;
  box-sizing: border-box;
}

.fs__handle {
  width: 72rpx;
  height: 8rpx;
  border-radius: 4rpx;
  background: $app-line;
  margin: 0 auto 24rpx;
}

.fs__title {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  color: $app-ink;
}

.fs__hint {
  display: block;
  font-size: $text-xs;
  color: $app-ink-subtle;
  padding: 10rpx 0 18rpx;
}

.fs__area {
  width: 100%;
  height: 380rpx;
  background: $app-canvas;
  border: 1rpx solid $app-line;
  border-radius: $radius-lg;
  padding: 20rpx;
  font-size: $text-sm;
  color: $app-ink;
  box-sizing: border-box;
}

.fs__ph {
  color: $app-ink-subtle;
}

.fs__result {
  padding: 20rpx 4rpx 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.fs__line {
  font-size: $text-sm;
  color: $app-ink;
}

.fs__skip {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.fs__fail {
  font-size: $text-xs;
  color: #c4453c;
}

.fs__btn {
  margin-top: 28rpx;
}
</style>
