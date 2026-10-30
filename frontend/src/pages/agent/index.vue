<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  agentApiOrigin,
  getAgentDocs,
  getAgentTokenStatus,
  regenerateAgentToken,
  revokeAgentToken,
  type AgentTokenStatus
} from '@/api/agent'
import { formatDateTimeLabel } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import { parseMarkdown } from '@/utils/markdown-lite'
import FtButton from '@/components/FtButton.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'

// ── 外部 agent（ADR 0043）：令牌管理 ＋ 接口文档 ──
// 令牌明文只在生成时拿到一次，只放在这个页面的内存里，离开就没了。

const status = ref<AgentTokenStatus | null>(null)
const freshToken = ref('')
const busy = ref(false)
const docs = ref('')

const origin = agentApiOrigin()
const blocks = computed(() => parseMarkdown(docs.value))

onShow(() => {
  if (!ensureLogin()) return
  void loadStatus()
  if (!docs.value) void loadDocs()
})

async function loadStatus() {
  try {
    status.value = await getAgentTokenStatus()
  } catch {
    // unwrap 已提示
  }
}

async function loadDocs() {
  try {
    docs.value = await getAgentDocs()
  } catch {
    // getAgentDocs 已提示
  }
}

const statusText = computed(() => {
  const s = status.value
  if (!s) return '—'
  if (!s.exists) return '还没有令牌'
  return s.lastUsedAt ? `最后使用：${formatDateTimeLabel(s.lastUsedAt)}` : '已生成，尚未使用'
})

const createdText = computed(() =>
  status.value?.createdAt ? `生成于 ${formatDateTimeLabel(status.value.createdAt)}` : ''
)

function confirm(title: string, content: string, confirmText: string): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      confirmText,
      success: (res) => resolve(res.confirm),
      fail: () => resolve(false)
    })
  })
}

async function generate() {
  if (busy.value) return
  if (status.value?.exists) {
    const ok = await confirm('重新生成令牌', '旧令牌会立即失效，正在用它的 agent 要换成新令牌。', '重新生成')
    if (!ok) return
  }
  busy.value = true
  try {
    const created = await regenerateAgentToken()
    freshToken.value = created.token
    await loadStatus()
  } catch {
    // unwrap 已提示
  } finally {
    busy.value = false
  }
}

async function revoke() {
  if (busy.value) return
  const ok = await confirm('吊销令牌', '吊销后 agent 立即无法再拉取数据。', '吊销')
  if (!ok) return
  busy.value = true
  try {
    await revokeAgentToken()
    freshToken.value = ''
    await loadStatus()
    uni.showToast({ title: '已吊销', icon: 'none' })
  } catch {
    // unwrap 已提示
  } finally {
    busy.value = false
  }
}

function copy(text: string) {
  uni.setClipboardData({ data: text })
}

/** 文档里的路径不含域名，复制给 agent 时把基址一起带上（令牌不带，要单独给）。 */
function copyDocs() {
  copy(`接口基址：${origin}\n\n${docs.value}`)
}
</script>

<template>
  <view class="agent">
    <view class="agent__body">
      <FtPageHeader title="外部 agent" back />

      <text class="note">让自己运行的 agent 只读拉取训练、饮食和身体数据做分析；它不能修改任何数据</text>

      <view class="glass card">
        <text class="card-title">令牌</text>
        <text class="card__status ft-num">{{ statusText }}</text>
        <text v-if="createdText" class="card__sub ft-num">{{ createdText }}</text>

        <view v-if="freshToken" class="fresh">
          <text class="fresh__warn">只显示这一次，离开页面后就再也看不到了</text>
          <text class="fresh__token" selectable>{{ freshToken }}</text>
          <FtButton variant="primary" size="sm" block @click="copy(freshToken)">复制令牌</FtButton>
        </view>

        <view class="card__actions">
          <FtButton :variant="status?.exists ? 'secondary' : 'primary'" size="sm" :disabled="busy" @click="generate">
            {{ status?.exists ? '重新生成' : '生成令牌' }}
          </FtButton>
          <FtButton v-if="status?.exists" variant="danger" size="sm" :disabled="busy" @click="revoke">吊销</FtButton>
        </view>
      </view>

      <view class="glass card">
        <view class="card__head">
          <text class="card-title">接口基址</text>
          <text class="card__link" role="button" @click="copy(origin)">复制</text>
        </view>
        <text class="card__code" selectable>{{ origin }}</text>
      </view>

      <view class="glass card">
        <view class="card__head">
          <text class="card-title">接口文档</text>
          <text v-if="docs" class="card__link" role="button" @click="copyDocs">复制全文</text>
        </view>
        <text v-if="!docs" class="card__sub">加载中…</text>
        <view class="doc">
          <template v-for="(b, i) in blocks" :key="i">
            <view v-if="b.type === 'heading'" :class="['doc__h', `doc__h--${b.level}`]">
              <text v-for="(s, j) in b.inlines" :key="j" :class="`doc__${s.kind}`">{{ s.text }}</text>
            </view>
            <view v-else-if="b.type === 'paragraph'" class="doc__p">
              <text v-for="(s, j) in b.inlines" :key="j" :class="`doc__${s.kind}`" selectable>{{ s.text }}</text>
            </view>
            <view v-else-if="b.type === 'list'" class="doc__list">
              <view v-for="(item, k) in b.items" :key="k" class="doc__li">
                <text class="doc__dot">·</text>
                <view class="doc__li-body">
                  <text v-for="(s, j) in item" :key="j" :class="`doc__${s.kind}`" selectable>{{ s.text }}</text>
                </view>
              </view>
            </view>
            <text v-else-if="b.type === 'code'" class="doc__pre" selectable>{{ b.text }}</text>
          </template>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.agent__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(64rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

.card-title {
  @include card-title;
}

.note {
  padding: 0 8rpx;
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

.card {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 28rpx;
  min-width: 0;
}

.card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16rpx;
}

.card__status {
  font-size: $text-md;
  color: $app-ink;
}

.card__sub {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.card__link {
  font-size: $text-sm;
  color: $app-ink-2;
  @include press(dim);
}

.card__code {
  font-size: $text-sm;
  color: $app-ink;
  word-break: break-all;
}

.card__actions {
  display: flex;
  gap: 16rpx;
  margin-top: 8rpx;
}

/* 明文令牌：一次性信息，底色与普通内容分开 */
.fresh {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 8rpx;
  padding: 24rpx;
  border-radius: $radius-md;
  background: $accent-soft;
}

.fresh__warn {
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-danger;
}

.fresh__token {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: $text-sm;
  line-height: 1.5;
  color: $app-ink;
  word-break: break-all;
}

/* ── 文档排版 ── */
.doc {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  min-width: 0;
  font-size: $text-sm;
  line-height: 1.7;
  color: $app-ink-2;
}

.doc__h {
  color: $app-ink;
  font-weight: 600;
  line-height: 1.4;
}

.doc__h--1 {
  font-size: $text-lg;
}

.doc__h--2 {
  margin-top: 20rpx;
  font-size: $text-md;
}

.doc__h--3 {
  margin-top: 12rpx;
  font-size: $text-sm;
  word-break: break-all;
}

.doc__list {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.doc__li {
  display: flex;
  gap: 12rpx;
  min-width: 0;
}

.doc__dot {
  flex: none;
  color: $app-ink-muted;
}

.doc__li-body {
  flex: 1;
  min-width: 0;
}

.doc__bold {
  font-weight: 600;
  color: $app-ink;
}

.doc__code,
.doc__pre {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  word-break: break-all;
}

.doc__code {
  padding: 0 6rpx;
  border-radius: 6rpx;
  background: $app-surface-2;
  color: $app-ink;
}

/* 代码块换行而不是横滑：页面根不许有横向溢出 */
.doc__pre {
  display: block;
  padding: 20rpx;
  border-radius: $radius-md;
  background: $app-surface-2;
  color: $app-ink;
  line-height: 1.5;
  white-space: pre-wrap;
}
</style>
