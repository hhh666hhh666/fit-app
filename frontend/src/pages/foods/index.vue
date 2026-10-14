<script setup lang="ts">
import { ref, watch } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import {
  deleteCombo,
  exportFoods,
  getFoodCount,
  importFoods,
  listCombos,
  listFoods,
  recognizeFood,
  type FoodComboVO,
  type FoodItem,
  type ImportResult
} from '@/api/nutrition'
import { choosePhoto, compress } from '@/utils/photo'
import { useRecognitionStore } from '@/stores/recognition'
import { ensureLogin } from '@/utils/guard'
import ComboEditSheet from '@/components/ComboEditSheet.vue'

// ── 食物库：列表（预置＋自有，按浏览次数降序）/ 服务端搜索 / 滚动分页（每页 100）/ 点条目进详情页 / JSON 导入导出 ──
//    预置 1800+ 条：lite 轻量字段 ＋ 服务端搜索 ＋ 每页 100 条触底加载（前端不再全量拉取）。

const PAGE_SIZE = 100

/** 页签：食物库 / 食物组合（ADR 0012）。 */
const tab = ref<'food' | 'combo'>('food')

const list = ref<FoodItem[]>([])
const total = ref(0)
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)
const keyword = ref('')

onShow(() => {
  if (!ensureLogin()) return
  void reload()
  void loadCombos()
})

/** 重新加载第一页（进入页面 / 换关键词时）：并取回总数用于「到底」判断与展示。 */
async function reload() {
  loading.value = true
  finished.value = false
  try {
    const kw = keyword.value.trim() || undefined
    const [items, cnt] = await Promise.all([
      listFoods(kw, { lite: true, limit: PAGE_SIZE, offset: 0 }),
      getFoodCount(kw)
    ])
    list.value = items
    total.value = cnt.total
    finished.value = items.length >= cnt.total
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

/** 触底加载下一页。 */
async function loadMore() {
  if (loading.value || loadingMore.value || finished.value) return
  loadingMore.value = true
  try {
    const kw = keyword.value.trim() || undefined
    const items = await listFoods(kw, { lite: true, limit: PAGE_SIZE, offset: list.value.length })
    list.value = list.value.concat(items)
    finished.value = items.length === 0 || list.value.length >= total.value
  } catch {
    // request 层已统一提示
  } finally {
    loadingMore.value = false
  }
}

onReachBottom(() => void loadMore())

// ── 食物组合（一串食物 ＋ 固定克数）──

const combos = ref<FoodComboVO[]>([])
const comboSheet = ref({ show: false, editing: null as FoodComboVO | null })

async function loadCombos() {
  try {
    combos.value = await listCombos()
  } catch {
    // request 层已统一提示
  }
}

/** 点组合 → 编辑 / 删除。 */
function onComboTap(c: FoodComboVO) {
  uni.showActionSheet({
    itemList: ['编辑', '删除'],
    success: ({ tapIndex }) => {
      if (tapIndex === 0) {
        comboSheet.value = { show: true, editing: c }
      } else if (tapIndex === 1) {
        removeCombo(c)
      }
    }
  })
}

/** 删除组合：连带明细一起删；已记录的历史是快照，不受影响。 */
function removeCombo(c: FoodComboVO) {
  uni.showModal({
    title: '删除组合？',
    content: `「${c.name}」将被删除。已记录的历史不受影响。`,
    confirmText: '删除',
    cancelText: '取消',
    success: async ({ confirm }) => {
      if (!confirm) return
      try {
        await deleteCombo(c.id)
        combos.value = combos.value.filter((x) => x.id !== c.id)
        uni.showToast({ title: '已删除', icon: 'none' })
      } catch {
        // request 层已统一提示
      }
    }
  })
}

function onComboSaved() {
  comboSheet.value = { show: false, editing: null }
  void loadCombos()
}

// 关键词防抖（服务端搜索）
let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => void reload(), 250)
})

function goBack() {
  uni.navigateBack()
}

function goNew() {
  if (tab.value === 'combo') {
    comboSheet.value = { show: true, editing: null }
    return
  }
  uni.navigateTo({ url: '/pages/food-edit/index' })
}

function onTap(f: FoodItem) {
  // 点条目 → 食物详情页（预置＝复制为自定义；自有＝编辑/删除，均在详情页内）
  uni.navigateTo({ url: `/pages/food-detail/index?id=${f.id}` })
}

function perText(f: FoodItem): string {
  return `每100${f.unit === 'ml' ? 'ml' : 'g'} · ${Math.round(f.kcal)} 大卡 · 蛋白 ${f.protein}g`
}

// ── 拍照识别（ADR 0018）：选图 → 压缩（长边 1600，与餐照同口径——标签字小别压糊）→ 识别 → 草稿进编辑页 ──

const recognition = useRecognitionStore()
const recognizing = ref(false)

async function startRecognize() {
  if (recognizing.value) return
  const picked = await choosePhoto()
  if (picked.cancelled || !picked.path) {
    // 真失败才提示；用户取消静默（与 photo.ts 的口径一致）
    if (picked.error) {
      uni.showToast({ title: '没能打开相机或相册，请检查权限设置', icon: 'none' })
    }
    return
  }
  recognizing.value = true
  uni.showLoading({ title: '识别中…', mask: true })
  try {
    const original = await (await fetch(picked.path)).blob()
    const blob = await compress(original, 0.85)
    const draft = await recognizeFood(blob)
    recognition.set(draft)
    uni.navigateTo({ url: '/pages/food-edit/index?recognized=1' })
  } catch (e) {
    // 后端降级原因（如未配置 Key）原样透出
    uni.showToast({ title: (e as Error)?.message || '识别失败', icon: 'none' })
  } finally {
    recognizing.value = false
    uni.hideLoading()
  }
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
    await reload()
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
      <text class="foods__title">{{ tab === 'combo' ? '食物组合' : '食物库' }}</text>
      <text class="foods__new" @click="goNew">{{ tab === 'combo' ? '+ 新建' : '+ 新增' }}</text>
    </view>

    <view class="tabs">
      <text class="tabs__item" :class="{ 'tabs__item--on': tab === 'food' }" @click="tab = 'food'">
        食物
      </text>
      <text
        class="tabs__item"
        :class="{ 'tabs__item--on': tab === 'combo' }"
        @click="tab = 'combo'"
      >
        组合
      </text>
    </view>

    <view v-if="tab === 'food'" class="foods__body">
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
        <text class="ops__btn" @click="startRecognize">拍照识别</text>
        <text class="ops__meta ft-num">{{
          keyword.trim() ? `找到 ${total} 项` : `共 ${total} 种`
        }}</text>
      </view>

      <text class="ops__note">「预设」＝系统内置只读数据（可复制为自定义）；名称带 * 的营养数据来自公开资料补全，供参考</text>

      <view class="flist">
        <view v-for="f in list" :key="f.id" class="frow" @click="onTap(f)">
          <view class="frow__mid">
            <view class="frow__line">
              <text class="frow__name">{{ f.name }}</text>
              <text v-if="f.preset" class="frow__badge">预设</text>
            </view>
            <text class="frow__sub ft-num">{{ perText(f) }}</text>
          </view>
          <text class="frow__go">›</text>
        </view>
        <view v-if="!loading && list.length === 0" class="flist__empty">
          {{ keyword.trim() ? '没有匹配的食物' : '还没有食物——点右上「+ 新增」' }}
        </view>
        <view v-else-if="loadingMore" class="flist__more">加载中…</view>
        <view v-else-if="finished && list.length > 0" class="flist__more">
          共 {{ total }} 种食物
        </view>
      </view>
    </view>

    <!-- 组合页签：列表（点条目 → 编辑 / 删除） -->
    <view v-if="tab === 'combo'" class="foods__body">
      <text class="ops__note">
        组合＝一串「食物 ＋ 固定克数」。记录一餐时在记录弹层里点一下即可整份加入；组合的改动不影响已记录的历史
      </text>
      <view class="clist">
        <view v-for="c in combos" :key="c.id" class="crow" @click="onComboTap(c)">
          <view class="crow__mid">
            <text class="crow__name">{{ c.name }}</text>
            <text class="crow__sub ft-num">
              {{ c.items.length }} 样 · {{ Math.round(c.totalKcal) }} 大卡
            </text>
          </view>
          <text class="frow__go">›</text>
        </view>
        <view v-if="combos.length === 0" class="flist__empty">
          还没有组合——点右上「+ 新建」，或在记录一餐时用「存为组合」
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

    <!-- 组合编辑弹层（新建 / 编辑共用） -->
    <ComboEditSheet
      v-if="comboSheet.show"
      :combo="comboSheet.editing"
      @close="comboSheet.show = false"
      @saved="onComboSaved"
    />
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

/* ── 页签 ── */
.tabs {
  display: flex;
  gap: 44rpx;
  padding: 6rpx 32rpx 0;
}

.tabs__item {
  font-size: $text-base;
  color: $app-ink-muted;
  padding: 10rpx 0;
  border-bottom: 4rpx solid transparent;
}

.tabs__item--on {
  color: $app-ink;
  font-weight: 600;
  border-bottom-color: $app-ink;
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

.ops__note {
  display: block;
  padding: 0 8rpx 16rpx;
  font-size: $text-xs;
  line-height: 1.5;
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

.frow__line {
  display: flex;
  align-items: center;
  gap: 12rpx;
  min-width: 0;
}

.frow__name {
  flex: 0 1 auto;
  min-width: 0;
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.frow__badge {
  flex: none;
  padding: 5rpx 10rpx;
  border-radius: 8rpx;
  border: 1rpx solid $app-line;
  background: $app-canvas;
  font-size: 20rpx;
  line-height: 1;
  color: $app-ink-muted;
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

.flist__more {
  padding: 20rpx 24rpx;
  text-align: center;
  font-size: $text-xs;
  color: $app-ink-subtle;
  border-top: 1rpx solid $app-line-soft;
}

/* ── 组合列表 ── */
.clist {
  background: $app-surface;
  border: 1rpx solid $app-line-soft;
  border-radius: $radius-lg;
  overflow: hidden;
}

.crow {
  display: flex;
  align-items: center;
  padding: 26rpx 28rpx;
}

.crow + .crow {
  border-top: 1rpx solid $app-line-soft;
}

.crow__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.crow__name {
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.crow__sub {
  font-size: $text-xs;
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
