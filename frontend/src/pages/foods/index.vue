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
import FtIcon from '@/components/FtIcon.vue'
import FtButton from '@/components/FtButton.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import FtSheet from '@/components/FtSheet.vue'

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
    <view class="foods__body">
      <FtPageHeader :title="tab === 'combo' ? '食物组合' : '食物库'" back>
        <template #right>
          <view class="pill" role="button" @click="goNew">
            <FtIcon name="plus" :size="16" :stroke="2" />
            <text>{{ tab === 'combo' ? '新建' : '新增' }}</text>
          </view>
        </template>
      </FtPageHeader>

      <view class="seg" role="tablist">
        <view
          class="seg__item"
          :class="{ 'seg__item--on': tab === 'food' }"
          role="tab"
          :aria-selected="tab === 'food'"
          @click="tab = 'food'"
        >
          食物
        </view>
        <view
          class="seg__item"
          :class="{ 'seg__item--on': tab === 'combo' }"
          role="tab"
          :aria-selected="tab === 'combo'"
          @click="tab = 'combo'"
        >
          组合
        </view>
      </view>

      <template v-if="tab === 'food'">
        <view class="search">
          <FtIcon name="search" :size="18" tone="subtle" />
          <input
            v-model="keyword"
            class="search__input"
            placeholder="搜索食物"
            placeholder-class="search__ph"
            confirm-type="search"
          />
          <view v-if="keyword" class="search__clear" role="button" aria-label="清空" @click="keyword = ''">
            <FtIcon name="close" :size="14" :stroke="2" />
          </view>
        </view>

        <view class="ops">
          <view class="ops__btn" role="button" @click="startRecognize">拍照识别</view>
          <view class="ops__btn" role="button" @click="openImport">导入 JSON</view>
          <view class="ops__btn" role="button" @click="doExport">导出 JSON</view>
        </view>

        <view class="glass flist">
          <view class="flist__head">
            <text class="flist__count ft-num">{{ keyword.trim() ? `找到 ${total} 项` : `共 ${total} 种` }}</text>
          </view>
          <view v-for="f in list" :key="f.id" class="frow" @click="onTap(f)">
            <view class="frow__mid">
              <view class="frow__line">
                <text class="frow__name">{{ f.name }}</text>
                <text v-if="f.preset" class="frow__badge">预设</text>
              </view>
              <text class="frow__sub ft-num">{{ perText(f) }}</text>
            </view>
            <FtIcon name="chevron-right" :size="18" tone="subtle" />
          </view>
          <FtEmpty
            v-if="!loading && list.length === 0"
            :title="keyword.trim() ? '没有匹配的食物' : '还没有食物'"
            :sub="keyword.trim() ? '换个关键词，或点右上角新增一种。' : '点右上角「新增」，或拍一张包装营养成分表识别。'"
          />
          <view v-else-if="loadingMore" class="flist__more">加载中…</view>
          <view v-else-if="finished && list.length > 0" class="flist__more ft-num">共 {{ total }} 种食物</view>
        </view>

        <text class="note">「预设」＝系统内置只读数据（可复制为自定义）；名称带 * 的营养数据来自公开资料补全，供参考</text>
      </template>

      <!-- 组合页签：列表（点条目 → 编辑 / 删除） -->
      <template v-else>
        <view class="glass flist">
          <view v-for="c in combos" :key="c.id" class="frow" @click="onComboTap(c)">
            <view class="frow__mid">
              <text class="frow__name">{{ c.name }}</text>
              <text class="frow__sub ft-num">{{ c.items.length }} 样 · {{ Math.round(c.totalKcal) }} 大卡</text>
            </view>
            <FtIcon name="chevron-right" :size="18" tone="subtle" />
          </view>
          <FtEmpty
            v-if="combos.length === 0"
            title="还没有组合"
            sub="点右上角「新建」，或在记录一餐时用「把这一餐存为组合」。"
          />
        </view>
        <text class="note">
          组合＝一串「食物 ＋ 固定克数」。记录一餐时在记录弹层里点一下即可整份加入；组合的改动不影响已记录的历史
        </text>
      </template>
    </view>

    <!-- 导入弹层 -->
    <FtSheet
      v-if="importSheet.show"
      title="导入 JSON"
      sub="粘贴食物数组：数值按「每 100g/100ml」，也可给 kj 自动换算；同名会跳过"
      @close="importSheet.show = false"
    >
      <scroll-view scroll-y class="fs__scroll">
        <textarea
          v-model="importSheet.text"
          class="fs__area"
          placeholder='[{"name":"鸡蛋","kcal":139,"protein":13.1,"fat":8.6,"carb":2.4,"sodium":131}]'
          placeholder-class="fs__ph"
          maxlength="-1"
        />
        <view v-if="importSheet.result" class="fs__result">
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
      </scroll-view>
      <FtButton variant="primary" block :disabled="importSheet.busy" class="fs__btn" @click="doImport">
        导入
      </FtButton>
    </FtSheet>

    <!-- 导出弹层 -->
    <FtSheet v-if="exportSheet.show" title="导出 JSON" sub="已复制到剪贴板" @close="exportSheet.show = false">
      <textarea :value="exportSheet.text" class="fs__area" maxlength="-1" />
      <FtButton block class="fs__btn" @click="copyExport">再复制一次</FtButton>
    </FtSheet>

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
.foods__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(64rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-lg;
  box-shadow: $glass-shadow;
  padding: 32rpx 36rpx;
}

/* 页头右侧的胶囊按钮 */
.pill {
  @include glass($glass-bg-strong);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 16rpx 28rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $app-ink-2;
}

.pill:active {
  opacity: 0.7;
}

/* ── 食物／组合 分段 ── */
.seg {
  @include glass;
  display: flex;
  padding: 6rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
}

.seg__item {
  flex: 1;
  padding: 14rpx 0;
  border-radius: $radius-pill;
  text-align: center;
  font-size: $text-base;
  color: $app-ink-muted;
  transition: background $dur-fast $ease-out, color $dur-fast $ease-out;
}

.seg__item--on {
  background: $app-surface-2;
  color: $app-ink;
  font-weight: $weight-bold;
}

/* ── 搜索 ── */
.search {
  @include glass($glass-bg-strong);
  display: flex;
  align-items: center;
  gap: 12rpx;
  height: 88rpx;
  padding: 0 28rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
}

.search__input {
  flex: 1;
  font-size: $text-md;
  color: $app-ink;
}

.search__ph {
  color: $app-ink-subtle;
}

.search__clear {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: $app-surface-2;
  color: $app-ink-muted;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ── 操作：拍照识别 ／ 导入 ／ 导出 ── */
.ops {
  display: flex;
  gap: 16rpx;
}

.ops__btn {
  flex: 1;
  height: 72rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $text-base;
  color: $app-ink-2;
}

.ops__btn:active {
  opacity: 0.7;
}

/* ── 列表 ── */
.flist {
  padding: 8rpx 28rpx;
}

.flist__head {
  padding: 16rpx 0 8rpx;
}

.flist__count {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.frow {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 0;
}

.frow + .frow,
.flist__head + .frow {
  border-top: 1rpx solid $app-line-soft;
}

.frow:active {
  opacity: 0.6;
}

.frow__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.frow__badge {
  flex: none;
  padding: 2rpx 12rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.frow__sub {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.flist__more {
  padding: 24rpx 0;
  border-top: 1rpx solid $app-line-soft;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.note {
  padding: 0 8rpx;
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

/* ── 导入／导出弹层（壳是 FtSheet） ── */
.fs__scroll {
  max-height: 56vh;
}

.fs__area {
  width: 100%;
  height: 380rpx;
  box-sizing: border-box;
  padding: 24rpx;
  background: $app-surface;
  border-radius: $radius-lg;
  font-family: $font-num;
  font-size: $text-sm;
  color: $app-ink;
}

.fs__ph {
  color: $app-ink-subtle;
}

.fs__result {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 24rpx 8rpx 0;
}

.fs__line {
  font-size: $text-base;
  color: $app-ink;
}

.fs__skip {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.fs__fail {
  font-size: $text-sm;
  color: $app-danger;
}

.foods .fs__btn {
  flex-shrink: 0;
  margin-top: 28rpx;
}

@media (prefers-reduced-motion: reduce) {
  .seg__item {
    transition: none;
  }
}
</style>
