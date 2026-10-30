<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { batchDeleteDiary, listDiary, type DiaryListItem } from '@/api/diary'
import { clearAll } from '@/api/cache'
import { formatTimeLabel } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import { TRASH_HINT } from '@/utils/confirm'
import { plainPreview } from '@/utils/markdown-lite'
import { loadDraft } from '@/utils/diary-draft'
import FtButton from '@/components/FtButton.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import FtSheet from '@/components/FtSheet.vue'

// ── 日记（ADR 0044）：按月翻看的时间线；有搜索词时搜全部日记 ──
//    形态照抄训练日志页：翻月、触底分页、长按多选删除。

const PAGE_SIZE = 20

const now = new Date()
const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
const month = ref(currentMonth)
const keyword = ref('')
const items = ref<DiaryListItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const hasMore = ref(false)
const hasDraft = ref(false)

const monthLabel = computed(() => {
  const [y, m] = month.value.split('-')
  return `${y}年${Number(m)}月`
})
const canNext = computed(() => month.value < currentMonth)
const searching = computed(() => keyword.value.trim().length > 0)

const emptyText = computed(() => {
  if (searching.value) return '没有搜到相关日记'
  return month.value === currentMonth ? '这个月还没有写日记' : `${monthLabel.value}没有日记`
})

// 换了条件就作废正在路上的旧请求：先发的搜索词可能后回来
let requestSeq = 0

async function load(reset = false) {
  const seq = ++requestSeq
  if (reset) loading.value = items.value.length === 0
  else loadingMore.value = true
  try {
    const list = await listDiary(month.value, keyword.value.trim(), PAGE_SIZE, reset ? 0 : items.value.length)
    if (seq !== requestSeq) return
    items.value = reset ? list : [...items.value, ...list]
    hasMore.value = list.length === PAGE_SIZE
  } catch {
    // request 层已统一提示
  } finally {
    if (seq === requestSeq) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

onShow(() => {
  if (!ensureLogin()) return
  hasDraft.value = !!loadDraft(null)
  void load(true)
})

onPullDownRefresh(async () => {
  clearAll()
  await load(true)
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (!hasMore.value || loading.value || loadingMore.value) return
  void load()
})

let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(keyword, () => {
  clearTimeout(searchTimer)
  exitSelect()
  searchTimer = setTimeout(() => {
    items.value = []
    void load(true)
  }, 250)
})

function shiftMonth(delta: number) {
  const [y, m] = month.value.split('-').map(Number)
  const d = new Date(y, m - 1 + delta, 1)
  const next = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  if (next > currentMonth) return
  month.value = next
  exitSelect()
  items.value = []
  void load(true)
}

/** 按日期分组（列表已是时间倒序，Map 保持插入顺序）。 */
const groups = computed(() => {
  const map = new Map<string, DiaryListItem[]>()
  for (const item of items.value) {
    const day = item.writtenAt.slice(0, 10)
    const arr = map.get(day)
    if (arr) arr.push(item)
    else map.set(day, [item])
  }
  return [...map.entries()].map(([date, rows]) => ({ date, rows }))
})

function dateText(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  const week = '日一二三四五六'[new Date(y, m - 1, d).getDay()]
  const head = y === now.getFullYear() ? '' : `${y}年`
  return `${head}${m}月${d}日 周${week}`
}

function previewOf(item: DiaryListItem): string {
  return plainPreview(item.preview)
}

function write() {
  uni.navigateTo({ url: '/pages/diary-entry/index' })
}

// ── 多选删除：长按进入多选态；多选态下点击行＝切换勾选 ──

const selectMode = ref(false)
const selected = ref<Set<number>>(new Set())
const allSelected = computed(() => items.value.length > 0 && selected.value.size === items.value.length)

/** H5 上长按后浏览器还会补发一次 click，不挡就会把刚选中的那条又取消掉。 */
let clickLockUntil = 0

function toggleSelect(id: number) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

function onRowClick(item: DiaryListItem) {
  if (Date.now() < clickLockUntil) return
  if (selectMode.value) toggleSelect(item.id)
  else uni.navigateTo({ url: `/pages/diary-entry/index?id=${item.id}` })
}

function onRowLongPress(item: DiaryListItem) {
  if (selectMode.value) return
  clickLockUntil = Date.now() + 400
  selectMode.value = true
  selected.value = new Set([item.id])
}

function exitSelect() {
  selectMode.value = false
  selected.value = new Set()
}

function toggleAll() {
  selected.value = allSelected.value ? new Set() : new Set(items.value.map((i) => i.id))
}

// 图默认留在相册（ADR 0046），要连图一起删得自己勾；系统确认框放不了勾选，所以用弹层
const deleteSheet = ref(false)
const withPhotos = ref(false)
const deleting = ref(false)

function doDelete() {
  if (selected.value.size === 0) return
  withPhotos.value = false
  deleteSheet.value = true
}

async function confirmDelete() {
  const ids = [...selected.value]
  deleting.value = true
  try {
    await batchDeleteDiary(ids, withPhotos.value)
    deleteSheet.value = false
    exitSelect()
    await load(true)
    uni.showToast({ title: `已删除 ${ids.length} 篇`, icon: 'none' })
  } catch {
    // request 层已统一提示
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <view class="diary">
    <view class="diary__body">
      <FtPageHeader title="日记" back>
        <template #right>
          <view v-if="!searching" class="monthbar">
            <view class="monthbar__arrow" role="button" aria-label="上个月" @click="shiftMonth(-1)">
              <FtIcon name="chevron-left" :size="18" :stroke="2" />
            </view>
            <text class="monthbar__label ft-num">{{ monthLabel }}</text>
            <view
              class="monthbar__arrow"
              :class="{ 'monthbar__arrow--off': !canNext }"
              role="button"
              aria-label="下个月"
              @click="shiftMonth(1)"
            >
              <FtIcon name="chevron-right" :size="18" :stroke="2" />
            </view>
          </view>
        </template>
      </FtPageHeader>

      <view class="search">
        <FtIcon name="search" :size="18" tone="subtle" />
        <input
          v-model="keyword"
          class="search__input"
          placeholder="搜索全部日记"
          placeholder-class="search__ph"
          confirm-type="search"
        />
        <view v-if="keyword" class="search__clear" role="button" aria-label="清空" @click="keyword = ''">
          <FtIcon name="close" :size="12" :stroke="2.4" />
        </view>
      </view>

      <view v-if="loading" class="diary__hint">加载中…</view>
      <view v-else-if="items.length === 0" class="glass">
        <FtEmpty :title="emptyText" />
      </view>
      <template v-else>
        <view v-for="g in groups" :key="g.date" class="grp">
          <text class="grp__date ft-num">{{ dateText(g.date) }}</text>
          <view class="glass grp__card">
            <view
              v-for="item in g.rows"
              :key="item.id"
              class="row"
              :class="{ 'row--sel': selectMode && selected.has(item.id) }"
              @click="onRowClick(item)"
              @longpress="onRowLongPress(item)"
            >
              <view v-if="selectMode" class="row__check" :class="{ 'row__check--on': selected.has(item.id) }">
                <FtIcon v-if="selected.has(item.id)" name="check" :size="13" :stroke="2.4" tone="inverse" />
              </view>
              <text class="row__time ft-num">{{ formatTimeLabel(item.writtenAt) }}</text>
              <view class="row__main">
                <text v-if="item.title" class="row__title">{{ item.title }}</text>
                <text v-if="previewOf(item)" class="row__preview">{{ previewOf(item) }}</text>
              </view>
            </view>
          </view>
        </view>
      </template>
      <view v-if="loadingMore" class="diary__hint">加载中…</view>
    </view>

    <view v-if="selectMode" class="selbar">
      <text class="selbar__count ft-num">已选 {{ selected.size }} 篇</text>
      <text class="selbar__all" @click="toggleAll">{{ allSelected ? '取消全选' : '全选' }}</text>
      <text class="selbar__all" @click="exitSelect">取消</text>
      <view class="selbar__del" role="button" @click="doDelete">删除</view>
    </view>
    <view v-else class="diary__write">
      <FtButton variant="primary" block @click="write">{{ hasDraft ? '继续写' : '写一篇' }}</FtButton>
    </view>

    <Transition name="ft-sheet">
      <FtSheet v-if="deleteSheet" title="删除日记" :sub="`选中的 ${selected.size} 篇`" @close="deleteSheet = false">
        <view class="dl">
          <view
            class="dl__opt"
            role="checkbox"
            :aria-checked="withPhotos"
            @click="withPhotos = !withPhotos"
          >
            <view class="row__check" :class="{ 'row__check--on': withPhotos }">
              <FtIcon v-if="withPhotos" name="check" :size="13" :stroke="2.4" tone="inverse" />
            </view>
            <view class="dl__text">
              <text class="dl__title">同时删除日记里的图片</text>
              <text class="dl__sub">只删只属于这些日记的图。不勾的话，图片留在相册。</text>
            </view>
          </view>
          <text class="dl__sub">{{ TRASH_HINT }}</text>
          <FtButton variant="danger" block :disabled="deleting" @click="confirmDelete">删除</FtButton>
        </view>
      </FtSheet>
    </Transition>
  </view>
</template>

<style lang="scss" scoped>
.diary__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(200rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

.diary__hint {
  padding: 16rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.diary__write {
  position: fixed;
  left: 40rpx;
  right: 40rpx;
  bottom: calc(32rpx + env(safe-area-inset-bottom));
}

.monthbar {
  @include glass;
  display: flex;
  align-items: center;
  flex-shrink: 0;
  margin-bottom: 8rpx;
  padding: 4rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: $shadow-sm;
}

.monthbar__arrow {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $app-ink-2;
  @include press(fill);
}

.monthbar__arrow--off {
  opacity: 0.25;
}

.monthbar__label {
  padding: 0 4rpx;
  font-size: $text-base;
  color: $app-ink;
}

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

.grp {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.grp__date {
  padding: 0 8rpx;
  font-size: $text-base;
  color: $app-ink-muted;
}

.grp__card {
  padding: 8rpx 0;
}

.row {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding: 24rpx 32rpx;
  @include press(dim);
}

.row + .row {
  border-top: 1rpx solid $app-surface-2;
}

.row--sel {
  background: $accent-row;
}

.row__check {
  flex-shrink: 0;
  width: 36rpx;
  height: 36rpx;
  margin-top: 4rpx;
  border-radius: 50%;
  border: 2rpx solid $app-ink-subtle;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
}

.row__check--on {
  border-color: $accent;
  background: $accent;
}

.row__time {
  flex-shrink: 0;
  padding-top: 2rpx;
  font-size: $text-base;
  color: $app-ink-muted;
}

.row__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.row__title {
  font-size: $text-md;
  font-weight: $weight-bold;
  line-height: 1.4;
  color: $app-ink;
  word-break: break-word;
}

.row__preview {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  font-size: $text-base;
  line-height: 1.5;
  color: $app-ink-2;
  word-break: break-word;
}

.selbar {
  @include selbar;
}

.dl {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.dl__opt {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  @include press(dim);
}

.dl__text {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  min-width: 0;
}

.dl__title {
  font-size: $text-base;
  color: $app-ink;
}

.dl__sub {
  font-size: $text-sm;
  line-height: 1.5;
  color: $app-ink-muted;
}
</style>
