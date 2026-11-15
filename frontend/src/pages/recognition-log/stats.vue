<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getRecognitionStats, type RecognitionStats, type RecognitionStatsGroup, type RecognitionStatsRange } from '@/api/nutrition'
import { formatThousands, formatYuan } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import FtEmpty from '@/components/FtEmpty.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import FtTrendChart from '@/components/FtTrendChart.vue'

// ── 用量统计（ADR 0054 后续注记）：一个时间范围内的估算金额、token 构成、平均每次、按日趋势、按模型／入口拆分。
//    金额由后端按 DeepSeek 价目逐条估算后合计，前端不存价目；非 DeepSeek 的记录不计价，单独注明。

const RANGES: { key: RecognitionStatsRange; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'month', label: '本月' },
  { key: 'week', label: '近 7 天' },
]

type Metric = 'cost' | 'tokens' | 'requests'
const METRICS: { key: Metric; label: string }[] = [
  { key: 'cost', label: '金额' },
  { key: 'tokens', label: 'token' },
  { key: 'requests', label: '次数' },
]

const range = ref<RecognitionStatsRange>('all')
const metric = ref<Metric>('cost')
const stats = ref<RecognitionStats | null>(null)
const loading = ref(true)

onShow(() => {
  if (!ensureLogin()) return
  void load()
})

async function load() {
  loading.value = true
  const want = range.value
  try {
    const s = await getRecognitionStats(want)
    if (range.value === want) stats.value = s
  } catch {
    // unwrap 已提示
  } finally {
    loading.value = false
  }
}

function pickRange(r: RecognitionStatsRange) {
  if (range.value === r) return
  range.value = r
  void load()
}

const empty = computed(() => !loading.value && (stats.value?.requests ?? 0) === 0)
const priced = computed(() => (stats.value?.pricedRequests ?? 0) > 0)

const missTokens = computed(() => Math.max(0, (stats.value?.tokensIn ?? 0) - (stats.value?.cachedTokens ?? 0)))

function pct(v: number | null | undefined): string {
  if (v == null) return '—'
  return `${(v * 100).toFixed(1)}%`
}

function durationText(ms: number | null | undefined): string {
  if (ms == null) return '—'
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)} 秒` : `${Math.round(ms)} ms`
}

/** 万以上写「12.3 万」，省宽度。 */
function compact(n: number): string {
  return n >= 10000 ? `${(n / 10000).toFixed(1)} 万` : formatThousands(Math.round(n))
}

const points = computed(() =>
  (stats.value?.daily ?? []).map((d) => ({
    date: d.date,
    value: metric.value === 'cost' ? Number(d.cost) : metric.value === 'tokens' ? d.tokens : d.requests,
  }))
)

/** 气泡读数：金额用统一写法，token 千分位，次数整数。 */
const formatPoint = computed(() =>
  metric.value === 'cost'
    ? (v: number) => formatYuan(v)
    : (v: number) => `${formatThousands(Math.round(v))}${metric.value === 'tokens' ? ' token' : ' 次'}`
)

/** y 轴刻度：金额几厘钱，取整会全是 0；刻度区窄，写得紧凑些。 */
const formatTick = computed(() =>
  metric.value === 'cost'
    ? (v: number) => (v === 0 ? '0' : v >= 1 ? v.toFixed(1) : v >= 0.01 ? v.toFixed(2) : v.toFixed(3))
    : (v: number) => (v >= 10000 ? `${Math.round(v / 10000)}万` : String(Math.round(v)))
)

function kindLabel(k: string | null): string {
  if (k === 'label') return '营养成分表入库'
  if (k === 'meal') return '记一餐 AI 识别'
  return '未记录入口（老记录）'
}

/** 一组的金额：全部未计价写「未计价」；部分未计价在后面注明几次。 */
function groupCost(g: RecognitionStatsGroup): string {
  if (g.unpricedRequests >= g.requests) return '未计价'
  return formatYuan(g.cost)
}

const holidayNote = computed(() => {
  const ys = stats.value?.uncoveredHolidayYears ?? []
  return ys.length ? `${ys.join('、')} 年的放假安排还没录入，这些年份的节假日只按周末判断。` : ''
})
</script>

<template>
  <view class="rstat">
    <view class="rstat__body">
      <FtPageHeader title="用量统计" back />

      <view class="seg seg--wide" role="tablist">
        <view
          v-for="r in RANGES"
          :key="r.key"
          class="seg__item"
          :class="{ 'seg__item--on': range === r.key }"
          role="tab"
          :aria-selected="range === r.key"
          @click="pickRange(r.key)"
        >
          {{ r.label }}
        </view>
      </view>

      <view v-if="empty" class="glass">
        <FtEmpty title="这段时间没有识别记录" sub="换个时间范围看看，或者去拍照识别一次。" />
      </view>

      <template v-else-if="stats">
        <!-- 合计 -->
        <view class="glass card">
          <text class="card__k">估算金额</text>
          <text class="hero ft-num">{{ priced ? formatYuan(stats.cost) : '未计价' }}</text>
          <text v-if="priced" class="card__sub ft-num">高峰 {{ formatYuan(stats.peakCost) }} · 空闲 {{ formatYuan(stats.offPeakCost) }}</text>
          <view class="tiles">
            <view class="tile">
              <text class="tile__v ft-num">{{ formatThousands(stats.totalTokens) }}</text>
              <text class="tile__k">总 token</text>
            </view>
            <view class="tile">
              <text class="tile__v ft-num">{{ formatThousands(stats.requests) }}</text>
              <text class="tile__k ft-num">次 · 成功 {{ stats.successes }} · 失败 {{ stats.failures }}</text>
            </view>
          </view>
          <text v-if="stats.unpricedRequests > 0" class="card__note ft-num">
            另有 {{ stats.unpricedRequests }} 次（{{ formatThousands(stats.unpricedTokens) }} token）用的不是 DeepSeek 模型，未计价。
          </text>
        </view>

        <!-- token 构成 -->
        <view class="glass card">
          <text class="card-title">token 构成</text>
          <view class="kv">
            <text class="kv__k">输入 · 未命中缓存</text>
            <text class="kv__v ft-num">{{ formatThousands(missTokens) }}</text>
          </view>
          <view class="kv">
            <text class="kv__k">输入 · 缓存命中</text>
            <text class="kv__v ft-num">{{ formatThousands(stats.cachedTokens) }}</text>
          </view>
          <view class="kv">
            <text class="kv__k">输出</text>
            <text class="kv__v ft-num">{{ formatThousands(stats.tokensOut) }}</text>
          </view>
          <view class="kv kv--sub">
            <text class="kv__k">其中思考</text>
            <text class="kv__v ft-num">{{ formatThousands(stats.reasoningTokens) }}</text>
          </view>
          <view class="kv">
            <text class="kv__k">缓存命中率</text>
            <text class="kv__v ft-num">{{ pct(stats.cacheHitRate) }}</text>
          </view>
        </view>

        <!-- 平均每次 -->
        <view class="glass card">
          <text class="card-title">平均每次</text>
          <view class="kv">
            <text class="kv__k">token</text>
            <text class="kv__v ft-num">{{ stats.avgTokens == null ? '—' : formatThousands(Math.round(stats.avgTokens)) }}</text>
          </view>
          <view class="kv">
            <text class="kv__k">金额</text>
            <text class="kv__v ft-num">{{ stats.avgCost == null ? '未计价' : formatYuan(stats.avgCost) }}</text>
          </view>
          <view class="kv">
            <text class="kv__k">耗时</text>
            <text class="kv__v ft-num">{{ durationText(stats.avgDurationMs) }}</text>
          </view>
        </view>

        <!-- 按日趋势 -->
        <view class="glass card">
          <view class="card__head">
            <text class="card-title">按日趋势</text>
            <view class="seg seg--sm" role="tablist">
              <view
                v-for="m in METRICS"
                :key="m.key"
                class="seg__item"
                :class="{ 'seg__item--on': metric === m.key }"
                role="tab"
                :aria-selected="metric === m.key"
                @click="metric = m.key"
              >
                {{ m.label }}
              </view>
            </view>
          </view>
          <FtTrendChart
            v-if="points.length >= 2"
            :key="`${range}-${metric}`"
            :points="points"
            :height="180"
            :zero-based="true"
            :format-value="formatPoint"
            :format-tick="formatTick"
          />
          <text v-else class="card__note">只有一天的数据，画不出趋势。</text>
          <text v-if="points.length >= 2" class="chart__tip">单指按住看数值 · 双指捏合缩放 · 双指拖动平移</text>
        </view>

        <!-- 按模型 -->
        <view class="glass card">
          <text class="card-title">按模型</text>
          <view v-for="g in stats.byModel" :key="g.key ?? '-'" class="grow">
            <view class="grow__line">
              <text class="grow__name">{{ g.key || '未记录模型' }}</text>
              <text class="grow__cost ft-num" :class="{ 'grow__cost--none': g.unpricedRequests >= g.requests }">{{ groupCost(g) }}</text>
            </view>
            <text class="grow__meta ft-num">
              {{ g.requests }} 次 · {{ compact(g.tokens) }} token<template v-if="g.unpricedRequests > 0 && g.unpricedRequests < g.requests"> · 其中 {{ g.unpricedRequests }} 次未计价</template>
            </text>
          </view>
        </view>

        <!-- 按入口 -->
        <view class="glass card">
          <text class="card-title">按入口</text>
          <view v-for="g in stats.byKind" :key="g.key ?? '-'" class="grow">
            <view class="grow__line">
              <text class="grow__name">{{ kindLabel(g.key) }}</text>
              <text class="grow__cost ft-num" :class="{ 'grow__cost--none': g.unpricedRequests >= g.requests }">{{ groupCost(g) }}</text>
            </view>
            <text class="grow__meta ft-num">
              {{ g.requests }} 次 · {{ compact(g.tokens) }} token<template v-if="g.unpricedRequests > 0 && g.unpricedRequests < g.requests"> · 其中 {{ g.unpricedRequests }} 次未计价</template>
            </text>
          </view>
        </view>

        <text class="note">
          金额按 DeepSeek 官方价目逐条估算后合计，以 DeepSeek 账单为准；目前只计算 DeepSeek 的模型。失败的调用也算——token 真实花了。时间按请求发出的时刻（北京时间）划分。{{ holidayNote }}
        </text>
      </template>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.rstat__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(64rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

.card {
  display: flex;
  flex-direction: column;
  padding: 28rpx;
}

.card-title {
  @include card-title;
  padding-bottom: 12rpx;
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding-bottom: 16rpx;
}

.card__head .card-title {
  padding-bottom: 0;
}

.card__k {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.hero {
  padding: 4rpx 0;
  font-size: $text-3xl;
  font-weight: $weight-heavy;
  color: $app-ink;
}

.card__sub {
  font-size: $text-sm;
  color: $app-ink-2;
}

.card__note {
  padding-top: 16rpx;
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

.tiles {
  display: flex;
  gap: 24rpx;
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid $app-line-soft;
}

.tile {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  min-width: 0;
}

.tile__v {
  font-size: $text-xl;
  font-weight: $weight-heavy;
  color: $app-ink;
}

.tile__k {
  font-size: $text-xs;
  color: $app-ink-muted;
}

.kv {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16rpx;
  padding: 14rpx 0;
}

.kv + .kv {
  border-top: 1rpx solid $app-line-soft;
}

.kv--sub .kv__k {
  padding-left: 24rpx;
  color: $app-ink-subtle;
}

.kv__k {
  font-size: $text-base;
  color: $app-ink-muted;
}

.kv__v {
  font-size: $text-md;
  color: $app-ink;
}

.grow {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  padding: 18rpx 0;
}

.grow + .grow {
  border-top: 1rpx solid $app-line-soft;
}

.grow__line {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  min-width: 0;
}

.grow__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-md;
  color: $app-ink;
}

.grow__cost {
  flex-shrink: 0;
  font-size: $text-md;
  color: $app-ink;
}

.grow__cost--none {
  color: $app-ink-muted;
}

.grow__meta {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.chart__tip {
  display: block;
  padding: 12rpx 8rpx 0;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.note {
  padding: 0 8rpx;
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

.seg {
  @include seg-track;
  display: flex;
}

.seg__item {
  @include seg-item;
  text-align: center;
}

.seg__item--on {
  @include seg-item-on;
}

.seg--wide .seg__item {
  flex: 1;
  padding-left: 0;
  padding-right: 0;
}

.seg--sm .seg__item {
  padding: 8rpx 20rpx;
  font-size: $text-sm;
}
</style>
