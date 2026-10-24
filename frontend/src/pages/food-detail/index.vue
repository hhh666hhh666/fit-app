<script setup lang="ts">
import { PALETTE } from '@/utils/palette'
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { setFoodFavorite, deleteFood, getFood, incrementFoodView, type FoodItem } from '@/api/nutrition'
import { MAIN_FIELDS, OPTIONAL_GROUPS } from '@/utils/food-fields'
import { ensureLogin } from '@/utils/guard'
import FtIcon from '@/components/FtIcon.vue'
import FtButton from '@/components/FtButton.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'

// ── 食物详情（只读）：预置＝「复制为自定义」；自有＝编辑/删除。食物库列表点条目进入。 ──
//    只展示「有值」的营养字段（空字段不占版面）；主组固定显示（未测显示 —）。

const id = ref<number | null>(null)
const food = ref<FoodItem | null>(null)
const loading = ref(true)
const deleting = ref(false)

onLoad((query) => {
  const q = (query || {}) as Record<string, string | undefined>
  const v = Number(q.id)
  if (v) id.value = v
})

onShow(() => {
  if (!ensureLogin()) return
  // onShow 重拉：编辑保存后返回本页要看到最新数据
  void load()
})

// 浏览次数只在首次载入时 +1（onShow 会多次触发——编辑保存返回等，不应重复计数）
let viewCounted = false

async function load() {
  if (id.value == null) return
  loading.value = true
  try {
    food.value = await getFood(id.value)
    if (!viewCounted) {
      viewCounted = true
      // 失败静默：计数失败不该影响查看
      void incrementFoodView(id.value).catch(() => {})
    }
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

const preset = computed(() => food.value?.preset === true)
const starred = computed(() => (food.value?.name ?? '').endsWith('*'))
const favorited = computed(() => food.value?.favorite === true)

// ── 收藏（星标）：收藏＝食物库列表置顶 ──

const favBusy = ref(false)

async function toggleFavorite() {
  const f = food.value
  if (!f || favBusy.value) return
  favBusy.value = true
  const next = !favorited.value
  try {
    await setFoodFavorite(f.id, next)
    f.favorite = next
    uni.showToast({ title: next ? '已收藏（置顶）' : '已取消收藏', icon: 'none' })
  } catch {
    // request 层已统一提示
  } finally {
    favBusy.value = false
  }
}

function numText(v: unknown): string | null {
  if (v == null || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? String(n) : null
}

/** 主组：固定 5 行，未测显示 —。 */
const mainRows = computed(() =>
  MAIN_FIELDS.map((f) => ({
    label: f.label,
    value:
      numText((food.value as unknown as Record<string, unknown> | null)?.[f.key]) ?? '—'
  }))
)

const giText = computed(() => numText(food.value?.gi))

const portionText = computed(() => {
  const f = food.value
  if (!f?.portionLabel || f.portionGrams == null) return null
  return `${f.portionLabel} ＝ ${numText(f.portionGrams)}${f.unit === 'ml' ? 'ml' : 'g'}`
})

/** 可选分组：只保留有值的行；整组无值则不显示该组。 */
const groups = computed(() => {
  const f = food.value as unknown as Record<string, unknown> | null
  if (!f) return []
  return OPTIONAL_GROUPS.map((g) => ({
    title: g.title,
    rows: g.fields
      .map((fd) => ({ key: fd.key, label: fd.label, value: numText(f[fd.key]) }))
      .filter((r) => r.value != null)
  })).filter((g) => g.rows.length > 0)
})

function doCopy() {
  if (id.value == null) return
  uni.navigateTo({ url: `/pages/food-edit/index?copyFrom=${id.value}` })
}

function doEdit() {
  if (id.value == null) return
  uni.navigateTo({ url: `/pages/food-edit/index?id=${id.value}` })
}

function doDelete() {
  const f = food.value
  if (!f || deleting.value) return
  uni.showModal({
    title: '删除食物',
    content: `删除「${f.name}」？历史饮食记录不受影响（营养按记录时的快照）。`,
    confirmText: '删除',
    confirmColor: PALETTE.danger,
    success: async ({ confirm }) => {
      if (!confirm) return
      deleting.value = true
      try {
        await deleteFood(f.id)
        uni.showToast({ title: '已删除', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 300)
      } catch {
        // request 层已统一提示
      } finally {
        deleting.value = false
      }
    }
  })
}
</script>

<template>
  <view class="fd">
    <view class="fd__body" :class="{ 'fd__body--with-bar': food && !preset }">
      <FtPageHeader title="食物详情" back>
        <template #right>
          <!-- 星标＝收藏（收藏后在食物库置顶）；预置与自有都可收藏 -->
          <view
            v-if="food"
            class="fd__fav"
            role="button"
            :aria-label="favorited ? '取消收藏' : '收藏'"
            @click="toggleFavorite"
          >
            <FtIcon :name="favorited ? 'star-fill' : 'star'" :size="22" :tone="favorited ? 'gold' : 'muted'" />
          </view>
        </template>
      </FtPageHeader>

      <template v-if="food">
        <!-- 名称与来源 -->
        <view class="glass">
          <view class="fd__name-row">
            <text class="fd__name">{{ food.name }}</text>
            <text v-if="preset" class="fd__badge">预设</text>
          </view>
          <text v-if="food.category" class="fd__meta">{{ food.category }}</text>
          <text class="fd__meta">以下数值均为每 100{{ food.unit === 'ml' ? 'ml' : 'g' }}</text>
          <text v-if="starred" class="fd__meta">营养数据来自公开资料补全，供参考</text>
        </view>

        <!-- 主要营养（4+1，固定显示） -->
        <view class="glass block">
          <text class="block__title">主要营养</text>
          <view v-for="r in mainRows" :key="r.label" class="fd__row">
            <text class="fd__label">{{ r.label }}</text>
            <text class="fd__value ft-num">{{ r.value }}</text>
          </view>
        </view>

        <!-- 升糖指数（有值才显示） -->
        <view v-if="giText" class="glass block">
          <text class="block__title">升糖指数（GI）</text>
          <view class="fd__row">
            <text class="fd__label">葡萄糖＝100 基准</text>
            <text class="fd__value ft-num">{{ giText }}</text>
          </view>
        </view>

        <!-- 其余分组：只显示有值的字段 -->
        <view v-for="g in groups" :key="g.title" class="glass block">
          <text class="block__title">{{ g.title }}</text>
          <view v-for="r in g.rows" :key="r.key" class="fd__row">
            <text class="fd__label">{{ r.label }}</text>
            <text class="fd__value ft-num">{{ r.value }}</text>
          </view>
        </view>

        <!-- 常用份 -->
        <view v-if="portionText" class="glass block">
          <text class="block__title">常用份</text>
          <text class="fd__portion ft-num">{{ portionText }}</text>
        </view>

        <!-- （预置）复制为自定义：沉到页面最底，纯文本、非按钮样式——需滑到底才看到 -->
        <text v-if="preset" class="fd__copy-text" @click="doCopy">复制为自定义</text>
      </template>
      <view v-else-if="!loading" class="glass">
        <FtEmpty title="食物不存在或已删除" sub="回到食物库重新找一下。" />
      </view>
    </view>

    <!-- 底部操作（仅自建食物：编辑 / 删除）：和会话页汇总条同一款悬浮胶囊 -->
    <view v-if="food && !preset" class="fd__bar">
      <view class="fd__del" role="button" @click="doDelete">删除</view>
      <FtButton variant="primary" class="fd__edit" @click="doEdit">编辑</FtButton>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.fd__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(64rpx + env(safe-area-inset-bottom));
}

/* 自建食物有底部悬浮条（编辑/删除），预留出它的高度 */
.fd__body--with-bar {
  padding-bottom: calc(200rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

/* 右上角收藏星标 */
.fd__fav {
  @include glass($glass-bg-strong);
  width: 80rpx;
  height: 80rpx;
  flex-shrink: 0;
  border-radius: 50%;
  border: 1rpx solid $glass-edge;
  box-shadow: $shadow-sm;
  display: flex;
  align-items: center;
  justify-content: center;
}

.fd__name-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  min-width: 0;
}

.fd__name {
  flex: 0 1 auto;
  min-width: 0;
  font-size: $text-xl;
  font-weight: $weight-bold;
  line-height: 1.35;
  color: $app-ink;
}

.fd__badge {
  flex: none;
  padding: 2rpx 14rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.fd__meta {
  display: block;
  padding-top: 8rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.block {
  padding-top: 28rpx;
  padding-bottom: 12rpx;
}

.block__title {
  display: block;
  padding-bottom: 8rpx;
  font-size: $text-title;
  font-weight: $weight-bold;
  color: $app-ink;
}

.fd__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0;
}

.fd__row + .fd__row {
  border-top: 1rpx solid $app-line-soft;
}

.fd__label {
  flex-shrink: 0;
  font-size: $text-base;
  color: $app-ink-muted;
}

.fd__value {
  margin-left: 20rpx;
  text-align: right;
  font-size: $text-md;
  color: $app-ink;
}

.fd__portion {
  display: block;
  padding: 16rpx 0;
  font-size: $text-md;
  color: $app-ink;
}

/* 页脚「复制为自定义」：纯文本、非按钮样式——沉在内容最底，需滑到底才看到 */
.fd__copy-text {
  display: block;
  margin-top: 24rpx;
  text-align: center;
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

/* ── 底部悬浮条 ── */
.fd__bar {
  @include glass($glass-bg-strong);
  position: fixed;
  left: $tabbar-side;
  right: $tabbar-side;
  bottom: calc(#{$tabbar-gap} + env(safe-area-inset-bottom));
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx;
  border: 1rpx solid $glass-edge;
  border-radius: $radius-pill;
  box-shadow: inset 0 2rpx 0 $app-surface, 0 0 0 1rpx $tabbar-outline, $glass-shadow-float;
}

.fd__del {
  height: 92rpx;
  padding: 0 44rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  display: flex;
  align-items: center;
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-danger;
}

.fd__del:active {
  opacity: 0.7;
}

.fd__bar .fd__edit {
  flex: 1;
}
</style>
