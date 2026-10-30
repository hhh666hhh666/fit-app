<script setup lang="ts">
import { computed, getCurrentInstance, onBeforeUnmount, ref, watch } from 'vue'
import { onHide, onLoad, onShow } from '@dcloudio/uni-app'
import { createDiary, getDiary, updateDiary } from '@/api/diary'
import { useDietStore } from '@/stores/diet'
import { formatDateLabel, nowTime, todayKey, toIsoMinute } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import { ask } from '@/utils/confirm'
import { clearDraft, loadDraft, saveDraft, type DiaryDraft } from '@/utils/diary-draft'
import { pickAndUploadDiaryPhoto } from '@/utils/photo'
import DiaryRefSheet from '@/components/DiaryRefSheet.vue'
import PhotoPickSheet from '@/components/PhotoPickSheet.vue'
import FtButton from '@/components/FtButton.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtMarkdown from '@/components/FtMarkdown.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'

// ── 写和看一篇日记（ADR 0044）：写的是 Markdown 原文，可切到预览；打开已写的默认看渲染结果 ──
//    每次改动存本机草稿，点「保存」才上传。

const CONTENT_MAX = 20000
/** 手机时钟可能比服务器快一点，和后端的容忍一致。 */
const FUTURE_TOLERANCE_MS = 5 * 60 * 1000

const entryId = ref<number | null>(null)
const mode = ref<'edit' | 'preview'>('edit')
const loading = ref(false)
const saving = ref(false)
const fromDraft = ref(false)
const refSheet = ref(false)
const showTick = ref(0)

const form = ref<DiaryDraft>({ date: todayKey(), time: nowTime(), title: '', content: '' })
/** 最后一次和服务器一致的内容；新写的一篇是空白。 */
const saved = ref<DiaryDraft>({ ...form.value })

const dirty = computed(() => {
  const a = form.value
  const b = saved.value
  return a.date !== b.date || a.time !== b.time || a.title !== b.title || a.content !== b.content
})
const isEmpty = computed(() => !form.value.title.trim() && !form.value.content.trim())
const showSave = computed(() => (entryId.value == null ? !isEmpty.value : dirty.value))

const headerTitle = computed(() => (entryId.value == null ? '写日记' : formatDateLabel(`${form.value.date}T00:00:00`)))

onLoad((query) => {
  if (!ensureLogin()) return
  const id = Number(query?.id)
  if (id > 0) {
    entryId.value = id
    mode.value = 'preview'
    void loadEntry(id)
  } else {
    restoreDraft()
  }
})

onShow(() => {
  // 从训练详情改完回来时，卡片要重读最新数据
  showTick.value++
})

function restoreDraft() {
  const draft = loadDraft(entryId.value)
  if (!draft) return
  form.value = draft
  fromDraft.value = true
  mode.value = 'edit'
}

async function loadEntry(id: number) {
  loading.value = true
  try {
    const e = await getDiary(id)
    const state: DiaryDraft = {
      date: e.writtenAt.slice(0, 10),
      time: e.writtenAt.slice(11, 16),
      title: e.title,
      content: e.content
    }
    saved.value = state
    form.value = { ...state }
    restoreDraft()
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

function setMode(next: 'edit' | 'preview') {
  pullText()
  mode.value = next
}

// ── 草稿 ──

let draftTimer: ReturnType<typeof setTimeout> | undefined

function flushDraft() {
  clearTimeout(draftTimer)
  draftTimer = undefined
  if (loading.value) return
  pullText()
  const hasContent = entryId.value == null ? !isEmpty.value : dirty.value
  if (hasContent) saveDraft(entryId.value, form.value)
  else clearDraft(entryId.value)
}

watch(
  form,
  () => {
    clearTimeout(draftTimer)
    draftTimer = setTimeout(flushDraft, 300)
  },
  { deep: true }
)

// 被切到后台后随时可能被系统杀掉，不能等定时器
onHide(flushDraft)
onBeforeUnmount(flushDraft)

async function discardDraft() {
  const ok = await ask({
    title: '放弃没保存的内容？',
    content: '放弃后不可恢复。',
    confirmText: '放弃',
    danger: true
  })
  if (!ok) return
  clearTimeout(draftTimer)
  clearDraft(entryId.value)
  form.value = { ...saved.value }
  const el = nativeTextarea()
  if (el) {
    el.value = saved.value.content
    el.dispatchEvent(new Event('input', { bubbles: true }))
  }
  fromDraft.value = false
  if (entryId.value != null) mode.value = 'preview'
}

// ── 保存 ──

async function save() {
  if (saving.value) return
  pullText()
  if (isEmpty.value) {
    uni.showToast({ title: '写点什么再保存', icon: 'none' })
    return
  }
  const writtenAt = toIsoMinute(form.value.date, form.value.time)
  if (new Date(writtenAt).getTime() > Date.now() + FUTURE_TOLERANCE_MS) {
    uni.showToast({ title: '时刻不能选未来', icon: 'none' })
    return
  }
  saving.value = true
  try {
    const payload = { writtenAt, title: form.value.title, content: form.value.content }
    const oldId = entryId.value
    const e = oldId == null ? await createDiary(payload) : await updateDiary(oldId, payload)
    clearTimeout(draftTimer)
    clearDraft(oldId)
    entryId.value = e.id
    form.value = { ...form.value, title: e.title }
    saved.value = { ...form.value }
    fromDraft.value = false
    mode.value = 'preview'
    uni.showToast({ title: '已保存', icon: 'none' })
  } catch {
    // request 层已统一提示；内容还在草稿里
  } finally {
    saving.value = false
  }
}

// ── 时刻：不能选未来 ──

const today = todayKey()

function onDateChange(e: { detail: { value: string } }) {
  form.value.date = e.detail.value
}

function onTimeChange(e: { detail: { value: string } }) {
  form.value.time = e.detail.value
}

// ── 快捷按钮：都作用在文本框的光标或选区上 ──

const instance = getCurrentInstance()

function nativeTextarea(): HTMLTextAreaElement | null {
  const root = instance?.proxy?.$el as HTMLElement | undefined
  return root?.querySelector?.('.entry__text textarea') ?? null
}

/**
 * ⚠️ 文本框的内容以原生元素为准：uni 的 textarea 往外报 v-model 有 100ms 节流、往里写值有 100ms 防抖，
 * 按 form.content 算位置会拿到旧值，写 form.content 再定位光标会被它的延迟写回冲掉。
 */
function currentText(): string {
  return nativeTextarea()?.value ?? form.value.content
}

/** 把原生元素里最新的内容收回表单：保存、存草稿、切预览之前都要先收。 */
function pullText() {
  const el = nativeTextarea()
  if (el && el.value !== form.value.content) form.value.content = el.value
}

function selectionRange(): [number, number] {
  const el = nativeTextarea()
  const end = currentText().length
  return el ? [el.selectionStart ?? end, el.selectionEnd ?? end] : [end, end]
}

/** 替换 [start, end)，然后把选区放到 [selStart, selEnd]。走一遍 input 事件，和手打一样通知 uni。 */
function replaceRange(start: number, end: number, text: string, selStart: number, selEnd: number) {
  const el = nativeTextarea()
  const c = currentText()
  const next = c.slice(0, start) + text + c.slice(end)
  if (!el) {
    form.value.content = next
    return
  }
  el.value = next
  el.focus()
  el.setSelectionRange(selStart, selEnd)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}

/** 包住选区；没选中就放一段占位文字并选中它，接着打字就替换掉了。 */
function wrap(before: string, after: string, placeholder: string) {
  const [s, e] = selectionRange()
  const inner = currentText().slice(s, e) || placeholder
  const text = before + inner + after
  replaceRange(s, e, text, s + before.length, s + before.length + inner.length)
}

/** 给选区涉及的每一行加前缀。 */
function prefixLines(prefixOf: (i: number) => string) {
  const [s, e] = selectionRange()
  const c = currentText()
  const start = c.lastIndexOf('\n', s - 1) + 1
  const text = c
    .slice(start, e)
    .split('\n')
    .map((line, i) => prefixOf(i) + line)
    .join('\n')
  const caret = start + text.length
  replaceRange(start, e, text, caret, caret)
}

/** 在光标处插一个独占一行的块（图片、记录引用、分割线都要求单独一行才会被认出来）。 */
function insertBlockAt(pos: number, block: string) {
  const c = currentText()
  const head = pos > 0 && c[pos - 1] !== '\n' ? '\n' : ''
  const tail = pos < c.length && c[pos] !== '\n' ? '\n' : ''
  const text = `${head}${block}\n${tail}`
  const caret = pos + head.length + block.length + 1
  replaceRange(pos, pos, text, caret, caret)
}

function insertCode() {
  const [s, e] = selectionRange()
  if (currentText().slice(s, e).includes('\n')) wrap('```\n', '\n```', '')
  else wrap('`', '`', '代码')
}

const diet = useDietStore()
const uploading = ref(false)

// 插图：传一张新的，或从相册选已有的（ADR 0046）
const photoSheet = ref(false)
let photoPos = 0

function insertPhoto() {
  if (uploading.value) return
  // 选图期间文本框会失焦，先记下位置
  const [pos] = selectionRange()
  uni.showActionSheet({
    itemList: ['上传新图', '从相册选'],
    success: ({ tapIndex }) => {
      if (tapIndex === 0) {
        void uploadPhoto(pos)
      } else {
        photoPos = pos
        photoSheet.value = true
      }
    }
  })
}

function onPickPhotos(markdown: string) {
  photoSheet.value = false
  insertBlockAt(photoPos, markdown)
}

async function uploadPhoto(pos: number) {
  uploading.value = true
  try {
    await diet.load(false)
    const id = await pickAndUploadDiaryPhoto(diet.settings?.photoQuality ?? 'medium')
    if (id != null) insertBlockAt(pos, `![图片](diary-photo:${id})`)
  } finally {
    uploading.value = false
  }
}

let refPos = 0

function openRefSheet() {
  refPos = selectionRange()[0]
  refSheet.value = true
}

function onPickRef(markdown: string) {
  refSheet.value = false
  insertBlockAt(refPos, markdown)
}

const TOOLS: { key: string; label: string; run: () => void }[] = [
  { key: 'heading', label: '标题', run: () => prefixLines(() => '## ') },
  { key: 'bold', label: '粗体', run: () => wrap('**', '**', '粗体') },
  { key: 'italic', label: '斜体', run: () => wrap('*', '*', '斜体') },
  { key: 'ul', label: '列表', run: () => prefixLines(() => '- ') },
  { key: 'ol', label: '编号', run: () => prefixLines((i) => `${i + 1}. `) },
  { key: 'quote', label: '引用块', run: () => prefixLines(() => '> ') },
  { key: 'hr', label: '分割线', run: () => insertBlockAt(selectionRange()[0], '---') },
  { key: 'code', label: '代码', run: insertCode },
  { key: 'link', label: '链接', run: () => wrap('[', '](https://)', '文字') }
]
</script>

<template>
  <view class="entry">
    <view class="entry__body">
      <FtPageHeader :title="headerTitle" back>
        <template #right>
          <view class="seg" role="tablist">
            <view
              class="seg__item"
              :class="{ 'seg__item--on': mode === 'edit' }"
              role="tab"
              :aria-selected="mode === 'edit'"
              @click="setMode('edit')"
            >
              编辑
            </view>
            <view
              class="seg__item"
              :class="{ 'seg__item--on': mode === 'preview' }"
              role="tab"
              :aria-selected="mode === 'preview'"
              @click="setMode('preview')"
            >
              预览
            </view>
          </view>
        </template>
      </FtPageHeader>

      <view v-if="loading" class="entry__hint">加载中…</view>

      <template v-else>
        <!-- 钉在顶部：键盘弹起、正文很长时也够得着 -->
        <view class="dock" :class="{ 'dock--plain': mode === 'preview' && !showSave }">
          <view class="dock__meta">
            <template v-if="mode === 'edit'">
              <picker mode="date" :value="form.date" :end="today" @change="onDateChange">
                <view class="chip ft-num">{{ form.date }}<FtIcon name="chevron-right" :size="14" tone="subtle" /></view>
              </picker>
              <picker mode="time" :value="form.time" @change="onTimeChange">
                <view class="chip ft-num">{{ form.time }}<FtIcon name="chevron-right" :size="14" tone="subtle" /></view>
              </picker>
            </template>
            <text v-else class="dock__time ft-num">{{ form.date }} {{ form.time }}</text>
            <view class="dock__save">
              <FtButton v-if="showSave" variant="primary" size="sm" :disabled="saving" @click="save">
                {{ saving ? '保存中…' : '保存' }}
              </FtButton>
            </view>
          </view>

          <scroll-view v-if="mode === 'edit'" scroll-x class="tools" :show-scrollbar="false">
            <view class="tools__row">
              <!-- mousedown 不抢焦点：点按钮时键盘不收、选区还在 -->
              <view
                v-for="t in TOOLS"
                :key="t.key"
                class="tool"
                role="button"
                @mousedown.prevent
                @click="t.run"
              >
                {{ t.label }}
              </view>
              <view class="tool" :class="{ 'tool--off': uploading }" role="button" @mousedown.prevent @click="insertPhoto">
                图片
              </view>
              <view class="tool" role="button" @mousedown.prevent @click="openRefSheet">引用记录</view>
            </view>
          </scroll-view>
        </view>

        <view v-if="fromDraft" class="draft">
          <text class="draft__text">这是上次没保存的内容</text>
          <text class="draft__action" role="button" @click="discardDraft">放弃</text>
        </view>

        <view v-if="mode === 'edit'" class="glass paper">
          <input
            v-model="form.title"
            class="entry__title"
            :maxlength="50"
            placeholder="标题（可不填）"
            placeholder-class="entry__ph"
          />
          <textarea
            v-model="form.content"
            class="entry__text"
            :maxlength="CONTENT_MAX"
            auto-height
            :show-confirm-bar="false"
            placeholder="写点什么，支持 Markdown"
            placeholder-class="entry__ph"
          />
          <text v-if="form.content.length > CONTENT_MAX - 1000" class="entry__count ft-num">
            {{ form.content.length }} / {{ CONTENT_MAX }}
          </text>
        </view>

        <view v-else class="glass paper">
          <text v-if="form.title" class="paper__title">{{ form.title }}</text>
          <FtMarkdown v-if="form.content.trim()" :source="form.content" :refresh="showTick" />
          <text v-else-if="!form.title" class="entry__hint">还没有内容</text>
        </view>
      </template>
    </view>

    <Transition name="ft-sheet">
      <DiaryRefSheet v-if="refSheet" :date="form.date" @close="refSheet = false" @pick="onPickRef" />
    </Transition>
    <Transition name="ft-sheet">
      <PhotoPickSheet v-if="photoSheet" @close="photoSheet = false" @pick="onPickPhotos" />
    </Transition>
  </view>
</template>

<style lang="scss" scoped>
.entry__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(64rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

.entry__hint {
  padding: 16rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.seg {
  @include seg-track;
  display: flex;
  flex-shrink: 0;
  margin-bottom: 8rpx;
}

.seg__item {
  @include seg-item;
  text-align: center;
}

.seg__item--on {
  @include seg-item-on;
}

.dock {
  @include glass($glass-bg-strong);
  position: sticky;
  top: calc(12rpx + env(safe-area-inset-top));
  z-index: 5;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding: 16rpx 20rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}

/* 只是看的时候没有要够得着的东西，不占一块玻璃、不钉在顶上挡正文 */
.dock--plain {
  position: static;
  padding: 0 8rpx;
  border: 0;
  background: none;
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.dock__meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  min-height: 64rpx;
}

.dock__time {
  padding-left: 8rpx;
  font-size: $text-base;
  color: $app-ink-muted;
}

.dock__save {
  margin-left: auto;
  flex-shrink: 0;
}

.chip {
  display: flex;
  align-items: center;
  gap: 4rpx;
  padding: 10rpx 12rpx 10rpx 20rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-base;
  color: $app-ink-2;
  white-space: nowrap;
}

.tools {
  white-space: nowrap;
}

.tools__row {
  display: inline-flex;
  gap: 8rpx;
}

.tool {
  flex-shrink: 0;
  padding: 12rpx 22rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-base;
  color: $app-ink-2;
  @include press(dim);
}

.tool--off {
  opacity: 0.4;
}

.draft {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 0 8rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.draft__action {
  padding: 8rpx 16rpx;
  color: $app-ink-2;
  font-weight: $weight-bold;
  @include press(dim);
}

.paper {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  padding: 36rpx;
}

.paper__title {
  font-size: $text-2xl;
  font-weight: $weight-bold;
  line-height: 1.35;
  color: $app-ink;
  word-break: break-word;
}

.entry__title {
  height: 72rpx;
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $app-ink;
}

.entry__text {
  width: 100%;
  min-height: 560rpx;
  font-size: $text-lg;
  line-height: 1.7;
  color: $app-ink;
}

.entry__ph {
  color: $app-ink-subtle;
  font-weight: $weight-regular;
}

.entry__count {
  align-self: flex-end;
  font-size: $text-xs;
  color: $app-ink-muted;
}
</style>
