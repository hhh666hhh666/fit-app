<script setup lang="ts">
import { computed, ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import { listAlbum, type AlbumPhoto, type MealKey } from '@/api/nutrition'
import { deletePhoto, fetchPhotoThumbUrl, fetchPhotoUrl } from '@/utils/photo'
import { invalidatePhoto } from '@/utils/photo-cache'
import PhotoViewer from '@/components/PhotoViewer.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import { ensureLogin } from '@/utils/guard'
import { ask } from '@/utils/confirm'

// ── 相册（ADR 0014）：全部餐照按日期倒序的网格（每行 3 张、日期分组、触底加载）──
//    列表只加载**缩略图**；点开看大图时才按需拉原图（走 PhotoViewer，可左右翻、可就地删）。
//    ⚠️ 图片地址归 utils/photo-cache.ts 的 LRU 所有（ADR 0035）：这里不 revoke，换/删图走 invalidatePhoto。

const PAGE_SIZE = 60

const MEAL_LABEL: Record<MealKey, string> = {
  breakfast: '早餐',
  lunch: '午餐',
  dinner: '晚餐',
  snack: '加餐'
}

const rows = ref<AlbumPhoto[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)

/** 缩略图地址：key = `${date}|${meal}`；'' ＝ 取不到（不反复重试）。 */
const thumbs = ref<Record<string, string>>({})

/** 看大图：urls 与 rows 一一对应，未加载的位置为 null。 */
const viewer = ref({ show: false, index: 0, urls: [] as (string | null)[] })

onShow(() => {
  if (!ensureLogin()) return
  void reload()
})

function keyOf(r: AlbumPhoto) {
  return `${r.logDate}|${r.meal}`
}

/** 只清本地索引，不释放地址——地址仍归照片缓存，下次进页还能直接命中。 */
function clearThumbs() {
  thumbs.value = {}
}

async function reload() {
  loading.value = true
  finished.value = false
  clearThumbs()
  try {
    rows.value = await listAlbum(0, PAGE_SIZE)
    finished.value = rows.value.length < PAGE_SIZE
    loadThumbs()
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loading.value || loadingMore.value || finished.value) return
  loadingMore.value = true
  try {
    const more = await listAlbum(rows.value.length, PAGE_SIZE)
    rows.value = rows.value.concat(more)
    finished.value = more.length < PAGE_SIZE
    loadThumbs()
  } catch {
    // request 层已统一提示
  } finally {
    loadingMore.value = false
  }
}

onReachBottom(() => void loadMore())

/** 补齐还没取过的缩略图（用一个空串占位，避免同一张被并发取两次）。 */
function loadThumbs() {
  for (const r of rows.value) {
    const k = keyOf(r)
    if (thumbs.value[k] !== undefined) continue
    thumbs.value[k] = ''
    void fetchPhotoThumbUrl(r.logDate, r.meal).then((u) => {
      thumbs.value[k] = u ?? ''
    })
  }
}

/** 按日期分组（Map 保持插入顺序＝日期倒序）。 */
const groups = computed(() => {
  const map = new Map<string, AlbumPhoto[]>()
  for (const r of rows.value) {
    const arr = map.get(r.logDate)
    if (arr) arr.push(r)
    else map.set(r.logDate, [r])
  }
  return [...map.entries()].map(([date, items]) => ({ date, items }))
})

// ── 看大图 ──

function openViewer(r: AlbumPhoto) {
  const i = rows.value.findIndex((x) => x.id === r.id)
  if (i < 0) return
  viewer.value = { show: true, index: i, urls: rows.value.map(() => null) }
  void loadOriginal(i)
}

/** 按需拉当前这张的原图（列表态的 url 为空，取到后填进对应位置）。 */
async function loadOriginal(i: number) {
  const r = rows.value[i]
  if (!r || !viewer.value.show || viewer.value.urls[i]) return
  const url = await fetchPhotoUrl(r.logDate, r.meal)
  if (viewer.value.show) viewer.value.urls[i] = url
}

function onViewerChange(i: number) {
  void loadOriginal(i)
}

function closeViewer() {
  viewer.value.show = false
  viewer.value.urls = []
}

const viewerCaption = computed(() => {
  const r = rows.value[viewer.value.index]
  return r ? `${r.logDate} · ${MEAL_LABEL[r.meal]}` : ''
})

/** 就地删除当前这张（二次确认，不可恢复；饮食条目不受影响）。 */
function removeCurrent() {
  const r = rows.value[viewer.value.index]
  if (!r) return
  void ask({
    title: '删除这张照片？',
    content: '删除后不可恢复；这一餐的饮食条目不受影响。',
    confirmText: '删除',
    danger: true
  }).then(async (confirm) => {
    if (!confirm) return
    const ok = await deletePhoto(r.logDate, r.meal)
    if (!ok) return
    // 照片已删：这张的原图与缩略图一起失效（缓存自己 revoke），本地索引同步去掉
    invalidatePhoto(r.logDate, r.meal)
    const k = keyOf(r)
    delete thumbs.value[k]
    rows.value = rows.value.filter((x) => x.id !== r.id)
    closeViewer()
    uni.showToast({ title: '已删除', icon: 'none' })
  })
}

/** 分组标题：今年的只写「10月21日 周二」，往年的带上年份。 */
function dateText(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  const week = '日一二三四五六'[new Date(y, m - 1, d).getDay()]
  const head = y === new Date().getFullYear() ? '' : `${y}年`
  return `${head}${m}月${d}日 周${week}`
}
</script>

<template>
  <view class="album">
    <view class="album__body">
      <FtPageHeader title="照片" back>
        <template #right>
          <text v-if="rows.length" class="album__count ft-num">{{ rows.length }} 张</text>
        </template>
      </FtPageHeader>

      <view v-if="loading" class="album__hint">加载中…</view>

      <template v-else>
        <view v-for="g in groups" :key="g.date" class="glass grp">
          <text class="grp__date ft-num">{{ dateText(g.date) }}</text>
          <view class="grid">
            <view v-for="r in g.items" :key="r.id" class="cell" role="button" @click="openViewer(r)">
              <image v-if="thumbs[keyOf(r)]" class="cell__img" :src="thumbs[keyOf(r)]" mode="aspectFill" />
              <text class="cell__tag" :class="'cell__tag--' + r.meal">{{ MEAL_LABEL[r.meal] }}</text>
            </view>
          </view>
        </view>

        <view v-if="rows.length === 0" class="glass">
          <FtEmpty title="还没有照片" sub="记录一餐时拍一张，就会出现在这里。" />
        </view>
        <view v-else-if="loadingMore" class="album__hint">加载中…</view>
        <view v-else-if="finished" class="album__hint ft-num">共 {{ rows.length }} 张</view>
      </template>
    </view>

    <PhotoViewer
      v-if="viewer.show"
      :urls="viewer.urls"
      :index="viewer.index"
      :caption="viewerCaption"
      @close="closeViewer"
      @change="onViewerChange"
    >
      <view class="vw-del" role="button" @click="removeCurrent">删除</view>
    </PhotoViewer>
  </view>
</template>

<style lang="scss" scoped>
.album__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(64rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

.album__count {
  flex-shrink: 0;
  padding-bottom: 12rpx;
  font-size: $text-base;
  color: $app-ink-muted;
}

.album__hint {
  padding: 16rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* ── 日期分组：一天一张玻璃卡 ── */
.grp {
  padding: 24rpx;
}

.grp__date {
  display: block;
  padding: 0 8rpx 16rpx;
  font-size: $text-md;
  color: $app-ink;
}

.grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.cell {
  position: relative;
  width: calc((100% - 24rpx) / 3);
  aspect-ratio: 1;
  border-radius: $radius-sm;
  overflow: hidden;
  background: $app-surface-2;
}

.cell:active {
  opacity: 0.8;
}

.cell__img {
  width: 100%;
  height: 100%;
}

/* 角标：餐次，用各餐自己的颜色（与首页、饮食页的餐次图标同一套） */
.cell__tag {
  position: absolute;
  left: 8rpx;
  bottom: 8rpx;
  padding: 2rpx 12rpx;
  border-radius: $radius-pill;
  font-size: $text-xs;
  line-height: 1.6;
}

.cell__tag--breakfast {
  background: $meal-breakfast-bg;
  color: $meal-breakfast-fg;
}

.cell__tag--lunch {
  background: $meal-lunch-bg;
  color: $meal-lunch-fg;
}

.cell__tag--dinner {
  background: $meal-dinner-bg;
  color: $meal-dinner-fg;
}

.cell__tag--snack {
  background: $meal-snack-bg;
  color: $meal-snack-fg;
}

/* 看图浮层里的「删除」：深底胶囊，和说明胶囊并排 */
.vw-del {
  flex-shrink: 0;
  height: 80rpx;
  padding: 0 32rpx;
  display: flex;
  align-items: center;
  border-radius: $radius-pill;
  background: $viewer-chip;
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $viewer-ink;
}

.vw-del:active {
  opacity: 0.6;
}
</style>
