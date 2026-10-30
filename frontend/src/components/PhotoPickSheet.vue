<script setup lang="ts">
import { computed, ref } from 'vue'
import { listAlbum } from '@/api/nutrition'
import { listDiaryAlbum } from '@/api/diary'
import { fetchDiaryPhoto, fetchMealPhoto } from '@/utils/photo'
import type { PhotoSource } from '@/utils/markdown-lite'
import FtButton from '@/components/FtButton.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtSheet from '@/components/FtSheet.vue'

// ── 写日记时从相册选图（ADR 0046）：餐照和日记两栏都能选，可多选 ──
//    图片地址归 utils/photo-cache.ts 的 LRU 所有，这里不 revoke。

const emit = defineEmits<{ (e: 'close'): void; (e: 'pick', markdown: string): void }>()

const PAGE_SIZE = 60
const TABS: { key: PhotoSource; label: string }[] = [
  { key: 'meal', label: '餐照' },
  { key: 'diary', label: '日记' }
]

const tab = ref<PhotoSource>('meal')
const ids = ref<number[]>([])
const thumbs = ref<Record<string, string>>({})
const loading = ref(false)
const finished = ref(false)
/** 选中的图，按点选先后排，插入正文时也按这个顺序。 */
const picked = ref<string[]>([])

const keyOf = (id: number) => `${tab.value}:${id}`
const pickedCount = computed(() => picked.value.length)

async function loadPage(offset: number) {
  const current = tab.value
  loading.value = true
  try {
    const list = await (current === 'meal' ? listAlbum(offset, PAGE_SIZE) : listDiaryAlbum(offset, PAGE_SIZE))
    if (current !== tab.value) return
    const more = list.map((p) => p.id)
    ids.value = offset === 0 ? more : ids.value.concat(more)
    finished.value = more.length < PAGE_SIZE
    for (const id of more) void loadThumb(current, id)
  } catch {
    // request 层已统一提示
  } finally {
    if (current === tab.value) loading.value = false
  }
}

async function loadThumb(source: PhotoSource, id: number) {
  const r = await (source === 'meal' ? fetchMealPhoto(id, true) : fetchDiaryPhoto(id, true))
  if (r.url) thumbs.value[`${source}:${id}`] = r.url
}

function switchTab(t: PhotoSource) {
  if (tab.value === t) return
  tab.value = t
  ids.value = []
  finished.value = false
  void loadPage(0)
}

function loadMore() {
  if (!loading.value && !finished.value) void loadPage(ids.value.length)
}

function toggle(id: number) {
  const k = keyOf(id)
  picked.value = picked.value.includes(k) ? picked.value.filter((x) => x !== k) : [...picked.value, k]
}

function confirm() {
  const lines = picked.value.map((k) => {
    const [source, id] = k.split(':')
    return `![图片](${source}-photo:${id})`
  })
  emit('pick', lines.join('\n\n'))
}

void loadPage(0)
</script>

<template>
  <FtSheet title="从相册选图" @close="emit('close')">
    <view class="ps">
      <view class="seg" role="tablist">
        <view
          v-for="t in TABS"
          :key="t.key"
          class="seg__item"
          :class="{ 'seg__item--on': tab === t.key }"
          role="tab"
          :aria-selected="tab === t.key"
          @click="switchTab(t.key)"
        >
          {{ t.label }}
        </view>
      </view>

      <scroll-view scroll-y class="ps__list" @scrolltolower="loadMore">
        <view v-if="ids.length" class="ps__grid">
          <view
            v-for="id in ids"
            :key="id"
            class="ps__cell"
            :class="{ 'ps__cell--on': picked.includes(keyOf(id)) }"
            role="checkbox"
            :aria-checked="picked.includes(keyOf(id))"
            @click="toggle(id)"
          >
            <image v-if="thumbs[keyOf(id)]" class="ps__img" :src="thumbs[keyOf(id)]" mode="aspectFill" />
            <view v-if="picked.includes(keyOf(id))" class="ps__check">
              <FtIcon name="check" :size="13" :stroke="2.4" tone="inverse" />
            </view>
          </view>
        </view>
        <view v-else-if="loading" class="ps__hint">加载中…</view>
        <FtEmpty v-else title="这一栏还没有图片" />
      </scroll-view>

      <FtButton variant="primary" block :disabled="pickedCount === 0" @click="confirm">
        {{ pickedCount ? `插入 ${pickedCount} 张` : '插入' }}
      </FtButton>
    </view>
  </FtSheet>
</template>

<style lang="scss" scoped>
.ps {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.seg {
  @include seg-track;
  display: flex;
}

.seg__item {
  @include seg-item;
  flex: 1;
  text-align: center;
}

.seg__item--on {
  @include seg-item-on;
}

.ps__list {
  height: 56vh;
}

.ps__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8rpx;
}

.ps__cell {
  position: relative;
  aspect-ratio: 1;
  border-radius: $radius-sm;
  overflow: hidden;
  background: $app-surface-2;
}

.ps__cell--on::after {
  content: '';
  position: absolute;
  inset: 0;
  border: 4rpx solid $accent;
  border-radius: $radius-sm;
}

.ps__img {
  display: block;
  width: 100%;
  height: 100%;
}

.ps__check {
  position: absolute;
  top: 10rpx;
  right: 10rpx;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: $accent;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ps__hint {
  padding: 48rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}
</style>
