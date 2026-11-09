<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getRecognitionLog, type RecognitionLogDetail } from '@/api/nutrition'
import { formatBytes, formatThousands } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import FtPageHeader from '@/components/FtPageHeader.vue'

// ── 识别详情（ADR 0053）：一条识别流水能显示的全部信息——结果、模型与参数、token、耗时与图片、
//    服务商回执、模型原始输出。照片不留（ADR 0018），所以这里没有图。
//    V40 之前的老记录只有 token / 模型 / 耗时 / 成败，其余显示「未记录」。

const rec = ref<RecognitionLogDetail | null>(null)
const loading = ref(true)

onLoad((query) => {
  if (!ensureLogin()) return
  const id = Number((query || {}).id)
  if (!id) {
    loading.value = false
    return
  }
  void load(id)
})

async function load(id: number) {
  try {
    rec.value = await getRecognitionLog(id)
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

/** 老记录：V40 之前写的，kind 一定为空。 */
const legacy = computed(() => rec.value != null && !rec.value.kind)
const MISSING = '未记录'

function or(v: string | number | null | undefined, empty = MISSING): string {
  return v == null || v === '' ? empty : String(v)
}

const kindText = computed(() => {
  const k = rec.value?.kind
  if (k === 'label') return '营养成分表入库（食物库「拍照识别」）'
  if (k === 'meal') return '记一餐 AI 识别'
  return MISSING
})

/** 精确到秒：2026-11-09 20:10:37 */
const timeText = computed(() => {
  if (!rec.value) return ''
  const d = new Date(rec.value.createdAt)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
})

const hostText = computed(() => {
  const e = rec.value?.endpoint
  if (!e) return MISSING
  try {
    return new URL(e).host
  } catch {
    return e
  }
})

const thinkingText = computed(() => {
  if (legacy.value) return MISSING
  const m = rec.value?.thinkingMode
  if (m === 'disabled') return '关'
  if (m === 'enabled') return '开'
  if (m === 'auto') return '不传，按服务商默认'
  return or(m)
})

const effortText = computed(() => (legacy.value ? MISSING : or(rec.value?.reasoningEffort, '没传')))

const tokens = computed(() => {
  const r = rec.value
  if (!r) return []
  const rows: { label: string; value: string; sub?: boolean }[] = [
    { label: '输入', value: formatThousands(r.tokensIn) },
    { label: '其中命中缓存', value: formatThousands(r.cachedTokens), sub: true },
    { label: '其中未命中', value: formatThousands(Math.max(0, r.tokensIn - r.cachedTokens)), sub: true },
    { label: '输出', value: formatThousands(r.tokensOut) },
    {
      label: '其中思考',
      value: r.reasoningTokens == null ? (legacy.value ? MISSING : '服务商没给') : formatThousands(r.reasoningTokens),
      sub: true
    },
    { label: '合计', value: formatThousands(r.tokensIn + r.tokensOut) }
  ]
  return rows
})

/** 元：一分钱以下给 6 位小数，否则 4 位；去掉末尾的 0。 */
function yuan(v: number | null | undefined): string {
  const n = Number(v ?? 0)
  if (n === 0) return '0 元'
  const s = n < 0.01 ? n.toFixed(6) : n.toFixed(4)
  return `${s.replace(/\.?0+$/, '')} 元`
}

const price = computed(() => rec.value?.price ?? null)

/** 合计的「约多少分」：几厘钱的数直接读元不直观。 */
const centText = computed(() => {
  const t = Number(price.value?.total ?? 0)
  if (t <= 0 || t >= 1) return ''
  return `约 ${Number((t * 100).toFixed(3))} 分`
})

const durationText = computed(() => {
  const ms = rec.value?.durationMs ?? 0
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)} 秒（${formatThousands(ms)} ms）` : `${ms} ms`
})

const imageText = computed(() => {
  const b = rec.value?.imageBytes
  if (b == null) return MISSING
  return `${formatBytes(b)}（${formatThousands(b)} 字节）`
})

const FINISH: Record<string, string> = {
  stop: '正常结束',
  length: '输出被截断（到了长度上限）',
  content_filter: '被服务商的内容审核拦下',
  tool_calls: '模型要求调用工具',
  insufficient_system_resource: '服务商资源不足，中途停止'
}

const finishText = computed(() => {
  const f = rec.value?.finishReason
  if (!f) return MISSING
  return FINISH[f] ? `${FINISH[f]}（${f}）` : f
})

/** 是 JSON 就排版一下（剥掉 ```json 围栏、保留「HTTP 401」这种状态行），不是就原样。 */
function pretty(raw: string | null | undefined): string {
  if (!raw) return ''
  let head = ''
  let body = raw.trim()
  const m = body.match(/^(HTTP \d{3})\n([\s\S]*)$/)
  if (m) {
    head = m[1] + '\n'
    body = m[2].trim()
  }
  body = body.replace(/^```[a-zA-Z]*\s*/, '').replace(/```\s*$/, '').trim()
  try {
    return head + JSON.stringify(JSON.parse(body), null, 2)
  } catch {
    return raw
  }
}

const rawText = computed(() => pretty(rec.value?.rawOutput))
const requestText = computed(() => pretty(rec.value?.requestJson))
const responseText = computed(() => pretty(rec.value?.responseJson))

/** 两段 JSON 很长（请求里有整段 prompt），默认收起，点开再看。 */
const open = ref({ request: false, response: false })

function copy(text: string | null | undefined, what: string) {
  if (!text) return
  uni.setClipboardData({
    data: text,
    success: () => uni.showToast({ title: `已复制${what}`, icon: 'none' })
  })
}
</script>

<template>
  <view class="rd">
    <view class="rd__body">
      <FtPageHeader title="识别详情" back />

      <view v-if="loading" class="rd__hint">加载中…</view>
      <view v-else-if="!rec" class="rd__hint">没找到这条识别记录</view>

      <template v-else>
        <!-- 结果 -->
        <view class="glass card">
          <view class="hero">
            <text class="badge" :class="rec.success ? 'badge--ok' : 'badge--fail'">{{ rec.success ? '成功' : '失败' }}</text>
            <text class="hero__time ft-num">{{ timeText }}</text>
          </view>
          <text v-if="rec.success" class="hero__title">{{ or(rec.resultName, legacy ? '（旧记录没存识别结果）' : '（没认出名称）') }}</text>
          <text v-else class="hero__title hero__title--fail">{{ or(rec.errorMessage, legacy ? '（旧记录没存失败原因）' : '（没有失败原因）') }}</text>
          <view class="kv">
            <text class="kv__k">入口</text>
            <text class="kv__v">{{ kindText }}</text>
          </view>
          <text v-if="legacy" class="card__note">这是 11 月 9 日更新前的记录，当时只存了 token、模型、耗时和成败，其余显示「未记录」。</text>
        </view>

        <!-- 模型与参数 -->
        <view class="glass card">
          <text class="card__title">模型与参数</text>
          <view class="kv">
            <text class="kv__k">模型</text>
            <text class="kv__v">{{ rec.model }}</text>
          </view>
          <view class="kv">
            <text class="kv__k">服务商</text>
            <text class="kv__v">{{ hostText }}</text>
          </view>
          <view v-if="rec.endpoint" class="kv kv--stack">
            <text class="kv__k">接口地址</text>
            <text class="kv__v kv__v--mono">{{ rec.endpoint }}</text>
          </view>
          <view class="kv">
            <text class="kv__k">思考开关</text>
            <text class="kv__v">{{ thinkingText }}</text>
          </view>
          <view class="kv">
            <text class="kv__k">思考等级</text>
            <text class="kv__v">{{ effortText }}</text>
          </view>
        </view>

        <!-- Token -->
        <view class="glass card">
          <text class="card__title">Token</text>
          <view v-for="t in tokens" :key="t.label" class="kv" :class="{ 'kv--sub': t.sub }">
            <text class="kv__k">{{ t.label }}</text>
            <text class="kv__v ft-num">{{ t.value }}</text>
          </view>
        </view>

        <!-- 价格（ADR 0054）：只算 DeepSeek，按调用时刻分高峰 / 空闲 -->
        <view class="glass card">
          <text class="card__title">价格</text>
          <template v-if="price && price.supported">
            <view class="price">
              <text class="price__total ft-num">{{ yuan(price.total) }}</text>
              <text v-if="centText" class="price__cent ft-num">{{ centText }}</text>
              <text class="badge" :class="price.peak ? 'badge--fail' : 'badge--ok'">{{ price.peak ? '高峰价' : '空闲价（半价）' }}</text>
            </view>
            <view v-for="it in price.items" :key="it.label" class="kv kv--stack">
              <view class="kv__row">
                <text class="kv__k">{{ it.label }}</text>
                <text class="kv__v ft-num">{{ yuan(it.cost) }}</text>
              </view>
              <text class="kv__calc ft-num">{{ formatThousands(it.tokens) }} token × {{ it.unitPrice }} 元 / 百万</text>
            </view>
            <view class="kv kv--stack">
              <text class="kv__k">时段</text>
              <text class="kv__v">{{ price.periodReason }}</text>
              <text class="kv__calc ft-num">按请求发出的时刻判断（北京时间 {{ (price.callTime || '').replace('T', ' ').slice(0, 19) }}）</text>
            </view>
            <view class="kv kv--stack">
              <text class="kv__k">价目</text>
              <text class="kv__v">{{ price.modelLabel }}</text>
            </view>
            <text class="card__note">
              按 {{ price.source }} 估算，以 DeepSeek 账单为准。高峰：北京时间周一至周五 9:00–12:00、14:00–18:00（法定节假日除外），其余时段半价。输入已含图片，输出已含思考。{{ price.holidayCovered ? '' : '这一年的放假安排还没录入，节假日只按周末判断。' }}
            </text>
          </template>
          <text v-else class="card__note">{{ price?.note || '没法算这条的价格。' }}</text>
        </view>

        <!-- 耗时与图片 -->
        <view class="glass card">
          <text class="card__title">耗时与图片</text>
          <view class="kv">
            <text class="kv__k">耗时</text>
            <text class="kv__v ft-num">{{ durationText }}</text>
          </view>
          <view class="kv">
            <text class="kv__k">发给模型的图片</text>
            <text class="kv__v ft-num">{{ imageText }}</text>
          </view>
          <view class="kv">
            <text class="kv__k">图片格式</text>
            <text class="kv__v">{{ or(rec.imageMime) }}</text>
          </view>
          <text class="card__note">照片本身不保存（只用来识别）。</text>
        </view>

        <!-- 服务商回执 -->
        <view class="glass card">
          <text class="card__title">服务商回执</text>
          <view class="kv">
            <text class="kv__k">结束原因</text>
            <text class="kv__v">{{ finishText }}</text>
          </view>
          <view class="kv kv--stack" role="button" @click="copy(rec.upstreamId, '请求 ID')">
            <text class="kv__k">请求 ID{{ rec.upstreamId ? '（点一下复制）' : '' }}</text>
            <text class="kv__v kv__v--mono">{{ or(rec.upstreamId) }}</text>
          </view>
        </view>

        <!-- 模型原始输出 -->
        <view class="glass card">
          <view class="card__head">
            <text class="card__title">{{ rec.success ? '模型原始输出' : '模型或服务商返回的原文' }}</text>
            <text v-if="rawText" class="card__act" role="button" @click="copy(rec.rawOutput, '原文')">复制</text>
          </view>
          <text v-if="rawText" class="raw" selectable :user-select="true">{{ rawText }}</text>
          <text v-else class="card__note">{{ legacy ? '旧记录没存原文。' : rec.success ? '这次没有拿到原文。' : '这次模型没有给出回答，服务商回了什么见下面「收到的响应」。' }}</text>
        </view>

        <!-- 发送的请求 / 收到的响应（JSON 全文） -->
        <view class="glass card">
          <view class="card__head">
            <text class="card__title">发送的请求</text>
            <view class="card__acts">
              <text v-if="requestText" class="card__act" role="button" @click="copy(rec.requestJson, '请求')">复制</text>
              <text v-if="requestText" class="card__act" role="button" @click="open.request = !open.request">{{ open.request ? '收起' : '展开' }}</text>
            </view>
          </view>
          <text v-if="requestText" class="card__note">发给服务商的完整请求体，只把图片的 base64 换成了「已省略」占位；API Key 在请求头里，不在这里。</text>
          <text v-if="requestText && open.request" class="raw" selectable :user-select="true">{{ requestText }}</text>
          <text v-else-if="!requestText" class="card__note">这条记录没存请求（更新前的记录，或照片没发出去）。</text>
        </view>

        <view class="glass card">
          <view class="card__head">
            <text class="card__title">收到的响应</text>
            <view class="card__acts">
              <text v-if="responseText" class="card__act" role="button" @click="copy(rec.responseJson, '响应')">复制</text>
              <text v-if="responseText" class="card__act" role="button" @click="open.response = !open.response">{{ open.response ? '收起' : '展开' }}</text>
            </view>
          </view>
          <text v-if="responseText" class="card__note">服务商返回的完整内容{{ rec.success ? '' : '（失败时是 HTTP 状态和报错正文）' }}。</text>
          <text v-if="responseText && open.response" class="raw" selectable :user-select="true">{{ responseText }}</text>
          <text v-else-if="!responseText" class="card__note">这条记录没存响应（更新前的记录，或请求没发出去）。</text>
        </view>

        <text class="rd__id ft-num">记录编号 #{{ rec.id }}</text>
      </template>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.rd__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(64rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

.rd__hint {
  padding: 48rpx 0;
  text-align: center;
  font-size: $text-base;
  color: $app-ink-muted;
}

.card {
  padding-top: 28rpx;
  padding-bottom: 20rpx;
}

.card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.card__title {
  display: block;
  padding-bottom: 8rpx;
  font-size: $text-title;
  font-weight: $weight-bold;
  color: $app-ink;
}

.card__acts {
  display: flex;
  gap: 28rpx;
}

.card__act {
  font-size: $text-sm;
  color: $app-ink-2;
  @include press(dim);
}

.card__note {
  display: block;
  padding-top: 12rpx;
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

.hero {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.hero__time {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.hero__title {
  display: block;
  padding: 16rpx 0 12rpx;
  font-size: $text-lg;
  font-weight: $weight-bold;
  line-height: 1.5;
  color: $app-ink;
  word-break: break-all;
}

.hero__title--fail {
  color: $app-danger;
}

.badge {
  @include badge(null, null);
}

.badge--ok {
  background: $app-surface-2;
  color: $app-ink-2;
}

/* 失败：与列表、编辑食物页「待核对」同一枚角标 */
.badge--fail {
  background: $accent-soft;
  color: $app-danger;
}

.kv {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 24rpx;
  padding: 18rpx 0;
  border-top: 1rpx solid $app-line-soft;
}

.kv--stack {
  flex-direction: column;
  align-items: flex-start;
  gap: 8rpx;
}

.kv--sub .kv__k {
  padding-left: 28rpx;
  font-size: $text-sm;
}

.kv--sub .kv__v {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.kv__row {
  align-self: stretch;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 24rpx;
}

.kv__calc {
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.price {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 16rpx;
  padding: 8rpx 0 20rpx;
}

.price__total {
  font-size: $text-xl;
  font-weight: $weight-heavy;
  color: $app-ink;
}

.price__cent {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.kv__k {
  flex-shrink: 0;
  font-size: $text-base;
  color: $app-ink-muted;
}

.kv__v {
  min-width: 0;
  text-align: right;
  font-size: $text-base;
  color: $app-ink;
  word-break: break-all;
}

.kv--stack .kv__v {
  text-align: left;
}

.kv__v--mono,
.raw {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
}

.kv__v--mono {
  font-size: $text-sm;
}

.raw {
  display: block;
  margin-top: 8rpx;
  padding: 20rpx 24rpx;
  border-radius: $radius-sm;
  background: $app-surface-2;
  font-size: $text-xs;
  line-height: 1.6;
  color: $app-ink-2;
  white-space: pre-wrap;
  word-break: break-all;
}

.rd__id {
  text-align: center;
  font-size: $text-xs;
  color: $app-ink-subtle;
}
</style>
