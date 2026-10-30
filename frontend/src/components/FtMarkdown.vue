<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { parseMarkdown, type PhotoSource } from '@/utils/markdown-lite'
import { resolveDiaryRefs, type DiaryRef } from '@/api/diary'
import { refSub, refTitle, refUrl } from '@/utils/diary-ref'
import { fetchDiaryPhoto, fetchMealPhoto } from '@/utils/photo'
import FtIcon from '@/components/FtIcon.vue'
import FtMdInline from '@/components/FtMdInline.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'

// ── 日记正文的渲染（ADR 0044）：文字、图片、记录引用卡片 ──
//    图片地址归 utils/photo-cache.ts 的 LRU 所有，这里不 revoke。
//    卡片每次都读最新数据；refresh 变了就重读（从训练详情改完回来时）。

const props = defineProps<{ source: string; refresh?: number }>()

type PhotoState = { status: 'loading' } | { status: 'ok'; url: string } | { status: 'deleted' } | { status: 'failed' }

const blocks = computed(() => parseMarkdown(props.source, { rich: true }))
const photos = ref<Record<string, PhotoState>>({})
const refs = ref<Record<string, DiaryRef>>({})
const refsFailed = ref(false)
const viewer = ref({ show: false, index: 0 })

/** 日记图片和餐照的 id 各算各的，所以用「来源:id」当键。 */
function photoKeyOf(b: { source: PhotoSource; id: number }): string {
  return `${b.source}:${b.id}`
}

const images = computed(() => {
  const out = new Map<string, { source: PhotoSource; id: number }>()
  for (const b of blocks.value) {
    if (b.type === 'image') out.set(photoKeyOf(b), { source: b.source, id: b.id })
  }
  return out
})
const photoKeys = computed(() => [...images.value.keys()])

const refKeys = computed(() => {
  const keys: string[] = []
  for (const b of blocks.value) {
    if (b.type === 'ref' && !keys.includes(b.key)) keys.push(b.key)
  }
  return keys
})

async function loadPhoto(key: string) {
  const img = images.value.get(key)
  if (!img) return
  photos.value[key] = { status: 'loading' }
  const r = await (img.source === 'meal' ? fetchMealPhoto(img.id, false) : fetchDiaryPhoto(img.id, false))
  if (r.url) photos.value[key] = { status: 'ok', url: r.url }
  else photos.value[key] = { status: r.deleted ? 'deleted' : 'failed' }
}

watch(
  photoKeys,
  (keys) => {
    for (const key of keys) {
      if (!photos.value[key]) void loadPhoto(key)
    }
  },
  { immediate: true }
)

async function loadRefs() {
  const keys = refKeys.value
  if (keys.length === 0) return
  try {
    const list = await resolveDiaryRefs(keys)
    const map: Record<string, DiaryRef> = {}
    for (const r of list) map[r.key] = r
    refs.value = map
    refsFailed.value = false
  } catch {
    refsFailed.value = true
  }
}

watch([refKeys, () => props.refresh], () => void loadRefs(), { immediate: true })

function urlOf(key: string): string | null {
  const s = photos.value[key]
  return s && s.status === 'ok' ? s.url : null
}

/** 翻页只翻看得到的图：已删除的那张不占一页。 */
const viewerKeys = computed(() => photoKeys.value.filter((key) => urlOf(key)))
const viewerUrls = computed(() => viewerKeys.value.map(urlOf))

function onPhotoClick(key: string) {
  const s = photos.value[key]
  if (!s) return
  if (s.status === 'failed') {
    void loadPhoto(key)
  } else if (s.status === 'ok') {
    viewer.value = { show: true, index: viewerKeys.value.indexOf(key) }
  }
}

function refSubText(key: string): string {
  const r = refs.value[key]
  if (r) return r.exists ? refSub(r) : '这条记录已删除'
  return refsFailed.value ? '暂时读不到，稍后再看' : '加载中…'
}

function onRefClick(key: string) {
  const r = refs.value[key]
  const url = r && refUrl(r)
  if (url) uni.navigateTo({ url })
}
</script>

<template>
  <view class="md">
    <template v-for="(b, i) in blocks" :key="i">
      <view v-if="b.type === 'heading'" class="md__h" :class="'md__h--' + b.level">
        <FtMdInline :inlines="b.inlines" />
      </view>
      <view v-else-if="b.type === 'paragraph'" class="md__p"><FtMdInline :inlines="b.inlines" /></view>
      <view v-else-if="b.type === 'quote'" class="md__quote"><FtMdInline :inlines="b.inlines" /></view>
      <view v-else-if="b.type === 'list'" class="md__list">
        <view v-for="(item, k) in b.items" :key="k" class="md__li">
          <text class="md__mark">•</text>
          <view class="md__li-body"><FtMdInline :inlines="item" /></view>
        </view>
      </view>
      <view v-else-if="b.type === 'ordered'" class="md__list">
        <view v-for="(item, k) in b.items" :key="k" class="md__li">
          <text class="md__mark md__mark--num ft-num">{{ b.start + k }}.</text>
          <view class="md__li-body"><FtMdInline :inlines="item" /></view>
        </view>
      </view>
      <view v-else-if="b.type === 'hr'" class="md__hr" />
      <text v-else-if="b.type === 'code'" class="md__pre" selectable>{{ b.text }}</text>
      <view v-else-if="b.type === 'image'" class="md__photo" role="button" @click="onPhotoClick(photoKeyOf(b))">
        <image v-if="urlOf(photoKeyOf(b))" class="md__img" :src="urlOf(photoKeyOf(b)) || ''" mode="widthFix" />
        <text v-else class="md__photo-hint">
          {{
            photos[photoKeyOf(b)]?.status === 'deleted'
              ? '图片已删除'
              : photos[photoKeyOf(b)]?.status === 'failed'
                ? '图片没加载出来，点一下重试'
                : '图片加载中…'
          }}
        </text>
      </view>
      <view
        v-else-if="b.type === 'ref'"
        class="md__ref"
        :class="{ 'md__ref--gone': refs[b.key] && !refs[b.key].exists }"
        role="button"
        @click="onRefClick(b.key)"
      >
        <view class="md__ref-body">
          <text class="md__ref-title">{{ refs[b.key]?.exists ? refTitle(refs[b.key]) : b.label }}</text>
          <text class="md__ref-sub ft-num">{{ refSubText(b.key) }}</text>
        </view>
        <FtIcon v-if="refs[b.key]?.exists" name="chevron-right" :size="16" tone="subtle" />
      </view>
    </template>

    <!-- 挂到 body：正文在玻璃卡片里，backdrop-filter 会让 fixed 只相对卡片定位，铺不满全屏 -->
    <Teleport to="body">
      <Transition name="ft-sheet">
        <PhotoViewer v-if="viewer.show" :urls="viewerUrls" :index="viewer.index" @close="viewer.show = false" />
      </Transition>
    </Teleport>
  </view>
</template>

<style lang="scss" scoped>
.md {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  font-size: $text-lg;
  line-height: 1.7;
  color: $app-ink;
}

.md__h {
  font-weight: $weight-bold;
  line-height: 1.35;
}

.md__h--1 {
  font-size: $text-xl;
}

.md__h--2,
.md__h--3,
.md__h--4,
.md__h--5,
.md__h--6 {
  font-size: $text-lg;
}

.md__quote {
  padding-left: 24rpx;
  border-left: 6rpx solid $app-surface-2;
  color: $app-ink-muted;
}

.md__list {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.md__li {
  display: flex;
  gap: 16rpx;
}

.md__mark {
  flex-shrink: 0;
  color: $app-ink-muted;
}

.md__li-body {
  flex: 1;
  min-width: 0;
}

.md__hr {
  height: 2rpx;
  background: $app-surface-2;
}

.md__pre {
  display: block;
  padding: 20rpx 24rpx;
  border-radius: $radius-sm;
  background: $app-surface-2;
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: $text-base;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.md__photo {
  overflow: hidden;
  border-radius: $radius-sm;
  background: $app-surface-2;
}

.md__img {
  display: block;
  width: 100%;
}

.md__photo-hint {
  display: block;
  padding: 48rpx 24rpx;
  text-align: center;
  font-size: $text-base;
  color: $app-ink-muted;
}

.md__ref {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 28rpx;
  border-radius: $radius-md;
  background: $app-surface;
  box-shadow: $shadow-sm;
  @include press(dim);
}

.md__ref--gone {
  background: $app-surface-2;
  box-shadow: none;
}

.md__ref-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.md__ref-title {
  font-size: $text-md;
  font-weight: $weight-bold;
  line-height: 1.4;
}

.md__ref-sub {
  font-size: $text-sm;
  line-height: 1.4;
  color: $app-ink-muted;
}
</style>
