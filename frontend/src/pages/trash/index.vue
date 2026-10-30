<script setup lang="ts">
import { computed, ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import { clearTrash, listTrash, purgeTrash, restoreTrash, type TrashItem, type TrashType } from '@/api/trash'
import { flushAbandoned, restoreAbandoned } from '@/utils/abandoned-session'
import { ask } from '@/utils/confirm'
import { ensureLogin } from '@/utils/guard'
import { fetchTrashThumb } from '@/utils/photo'
import { loadSession } from '@/utils/session'
import FtEmpty from '@/components/FtEmpty.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'

// ── 回收站（ADR 0047）：混合列表，按删除时间倒序，可按类型筛选 ──
//    不做详情页：每种类型都要一套渲染，想看完整内容就先恢复。
//    多选交互和训练日志页一致：长按进入多选态，多选态下点击行＝切换勾选。

const PAGE_SIZE = 30

const TYPE_LABEL: Record<TrashType, string> = {
  workout: '训练',
  session: '没结束的训练',
  diary: '日记',
  meal_photo: '餐照',
  diary_photo: '日记图片',
  body: '身体档案',
  plan: '计划',
  exercise: '动作',
  food: '食物'
}

const FILTERS: { key: string; label: string; types: TrashType[] }[] = [
  { key: 'all', label: '全部', types: [] },
  { key: 'workout', label: '训练', types: ['workout', 'session'] },
  { key: 'diary', label: '日记', types: ['diary'] },
  { key: 'photo', label: '照片', types: ['meal_photo', 'diary_photo'] },
  { key: 'body', label: '身体', types: ['body'] },
  { key: 'plan', label: '计划', types: ['plan'] },
  { key: 'exercise', label: '动作', types: ['exercise'] },
  { key: 'food', label: '食物', types: ['food'] }
]

const filter = ref('all')
const items = ref<TrashItem[]>([])
const thumbs = ref<Record<number, string>>({})
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)
const busy = ref(false)

const typesOf = (key: string) => FILTERS.find((f) => f.key === key)?.types ?? []

onShow(async () => {
  if (!ensureLogin()) return
  // 刚丢掉的训练可能还在路上：等它传完，列表才看得到
  await flushAbandoned()
  void reload()
})

async function reload() {
  const current = filter.value
  loading.value = true
  finished.value = false
  try {
    const list = await listTrash(typesOf(current), 0, PAGE_SIZE)
    if (current !== filter.value) return
    items.value = list
    finished.value = list.length < PAGE_SIZE
    loadThumbs(list)
  } catch {
    // request 层已统一提示
  } finally {
    if (current === filter.value) loading.value = false
  }
}

onReachBottom(async () => {
  if (loading.value || loadingMore.value || finished.value) return
  const current = filter.value
  loadingMore.value = true
  try {
    const more = await listTrash(typesOf(current), items.value.length, PAGE_SIZE)
    if (current !== filter.value) return
    items.value = items.value.concat(more)
    finished.value = more.length < PAGE_SIZE
    loadThumbs(more)
  } catch {
    // request 层已统一提示
  } finally {
    loadingMore.value = false
  }
})

function loadThumbs(list: TrashItem[]) {
  for (const it of list) {
    if (!it.hasThumb || thumbs.value[it.id]) continue
    void fetchTrashThumb(it.id).then((u) => {
      if (u) thumbs.value[it.id] = u
    })
  }
}

function switchFilter(key: string) {
  if (filter.value === key) return
  filter.value = key
  exitSelect()
  items.value = []
  void reload()
}

// ── 多选 ──

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

function onRowClick(it: TrashItem) {
  if (Date.now() < clickLockUntil) return
  if (selectMode.value) {
    toggleSelect(it.id)
    return
  }
  uni.showActionSheet({
    itemList: ['恢复', '彻底删除'],
    success: ({ tapIndex }) => void (tapIndex === 0 ? restore([it.id]) : purge([it.id]))
  })
}

function onRowLongPress(it: TrashItem) {
  if (selectMode.value) return
  clickLockUntil = Date.now() + 400
  selectMode.value = true
  selected.value = new Set([it.id])
}

function exitSelect() {
  selectMode.value = false
  selected.value = new Set()
}

function toggleAll() {
  selected.value = allSelected.value ? new Set() : new Set(items.value.map((i) => i.id))
}

// ── 恢复与彻底删除 ──

/**
 * 没结束的训练恢复后是本机唯一的进行中会话，所以有进行中的就不恢复，一次也只恢复一场：
 * 这是「恢复一定成功」的例外，因为不能把主人正在练的换走（ADR 0047）。
 */
function restorableIds(ids: number[]): number[] {
  const sessions = items.value.filter((i) => i.type === 'session' && ids.includes(i.id)).map((i) => i.id)
  if (sessions.length === 0) return ids
  if (loadSession()) {
    uni.showToast({ title: '先结束当前训练，再恢复没结束的那场', icon: 'none' })
    return ids.filter((id) => !sessions.includes(id))
  }
  if (sessions.length > 1) {
    uni.showToast({ title: '没结束的训练一次只能恢复一场', icon: 'none' })
    return ids.filter((id) => id === sessions[0] || !sessions.includes(id))
  }
  return ids
}

async function restore(ids: number[]) {
  const target = restorableIds(ids)
  if (target.length === 0 || busy.value) return
  busy.value = true
  try {
    const done = await restoreTrash(target)
    for (const r of done) {
      if (r.session) restoreAbandoned(r.session)
    }
    exitSelect()
    await reload()
    const note = done.find((r) => r.note)?.note
    const resumed = done.some((r) => r.session)
    uni.showToast({
      title: note ?? (resumed ? '已恢复，点底部圆钮接着练' : `已恢复 ${done.length} 条`),
      icon: 'none'
    })
  } catch {
    // request 层已统一提示
  } finally {
    busy.value = false
  }
}

async function purge(ids: number[]) {
  if (ids.length === 0 || busy.value) return
  const ok = await ask({
    title: '彻底删除？',
    content: `选中的 ${ids.length} 条会被彻底删除，不能恢复。`,
    confirmText: '彻底删除',
    danger: true
  })
  if (!ok) return
  busy.value = true
  try {
    await purgeTrash(ids)
    exitSelect()
    await reload()
    uni.showToast({ title: '已彻底删除', icon: 'none' })
  } catch {
    // request 层已统一提示
  } finally {
    busy.value = false
  }
}

async function clearAllItems() {
  if (busy.value) return
  const ok = await ask({
    title: '清空回收站？',
    content: '里面所有内容都会被彻底删除，不能恢复。',
    confirmText: '清空',
    danger: true
  })
  if (!ok) return
  busy.value = true
  try {
    await clearTrash()
    exitSelect()
    await reload()
    uni.showToast({ title: '已清空', icon: 'none' })
  } catch {
    // request 层已统一提示
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="trash">
    <view class="trash__body">
      <FtPageHeader title="回收站" back>
        <template #right>
          <text v-if="items.length && !selectMode" class="trash__clear" role="button" @click="clearAllItems">清空</text>
        </template>
      </FtPageHeader>

      <text class="trash__note">删除的内容在这里保留 30 天，到期自动清除。长按可以多选。</text>

      <scroll-view scroll-x class="chips">
        <view class="chips__inner">
          <view
            v-for="f in FILTERS"
            :key="f.key"
            class="chip"
            :class="{ 'chip--on': filter === f.key }"
            role="button"
            @click="switchFilter(f.key)"
          >
            {{ f.label }}
          </view>
        </view>
      </scroll-view>

      <view v-if="loading && items.length === 0" class="trash__hint">加载中…</view>
      <view v-else-if="items.length === 0" class="glass">
        <FtEmpty title="回收站是空的" sub="删除的内容会先到这里。" />
      </view>
      <view v-else class="glass list">
        <view
          v-for="it in items"
          :key="it.id"
          class="row"
          :class="{ 'row--sel': selectMode && selected.has(it.id) }"
          @click="onRowClick(it)"
          @longpress="onRowLongPress(it)"
        >
          <view v-if="selectMode" class="row__check" :class="{ 'row__check--on': selected.has(it.id) }">
            <FtIcon v-if="selected.has(it.id)" name="check" :size="13" :stroke="2.4" tone="inverse" />
          </view>
          <view v-if="it.hasThumb" class="row__thumb">
            <image v-if="thumbs[it.id]" class="row__img" :src="thumbs[it.id]" mode="aspectFill" />
          </view>
          <view class="row__main">
            <text class="row__title">{{ it.title }}</text>
            <text class="row__sub">{{ TYPE_LABEL[it.type] }}{{ it.summary ? ` · ${it.summary}` : '' }}</text>
          </view>
          <text class="row__days ft-num">剩 {{ it.remainingDays }} 天</text>
        </view>
      </view>
      <view v-if="loadingMore" class="trash__hint">加载中…</view>
    </view>

    <view v-if="selectMode" class="selbar">
      <text class="selbar__count ft-num">已选 {{ selected.size }}</text>
      <text class="selbar__all" @click="toggleAll">{{ allSelected ? '取消全选' : '全选' }}</text>
      <text class="selbar__all" @click="exitSelect">取消</text>
      <text class="selbar__all selbar__purge" role="button" @click="purge([...selected])">彻底删除</text>
      <view class="selbar__del selbar__restore" role="button" @click="restore([...selected])">恢复</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.trash__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(180rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

.trash__clear {
  font-size: $text-sm;
  color: $app-danger;
  @include press(dim);
}

.trash__note {
  padding: 0 8rpx;
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

.trash__hint {
  padding: 48rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.chips {
  white-space: nowrap;
}

.chips__inner {
  display: inline-flex;
  gap: 12rpx;
}

.chip {
  padding: 10rpx 26rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-sm;
  color: $app-ink-2;
  @include press(dim);
}

.chip--on {
  background: $app-ink;
  color: $app-surface;
}

.list {
  padding: 8rpx 0;
}

.row {
  display: flex;
  align-items: center;
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

.row__thumb {
  flex-shrink: 0;
  width: 88rpx;
  height: 88rpx;
  border-radius: $radius-sm;
  overflow: hidden;
  background: $app-surface-2;
}

.row__img {
  display: block;
  width: 100%;
  height: 100%;
}

.row__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.row__title {
  font-size: $text-base;
  color: $app-ink;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.row__sub {
  font-size: $text-sm;
  color: $app-ink-muted;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.row__days {
  flex-shrink: 0;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.selbar {
  @include selbar;
}

/* 恢复是这一栏的主动作，彻底删除退成红字，免得手滑 */
.selbar__purge {
  color: $app-danger;
}

.selbar__restore {
  background: $accent;
}
</style>
