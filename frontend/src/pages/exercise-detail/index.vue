<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getExercise, type ExerciseDetail } from '@/api/exercise'
import { muscleLabel } from '@/utils/format'
import { fetchExerciseMedia } from '@/utils/photo'
import { ensureLogin } from '@/utils/guard'
import FtEmpty from '@/components/FtEmpty.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'

// ── 动作详情（ADR 0055）：动图 ＋ 练到哪里 ＋ 动作要领，只读。动作库点动作进入。 ──
//    资料来自开源动作数据集，只有导入过的预设才有；自定义动作只显示名字和肌群。
//    动图版权归 Gym visual：接口要登录才给，页面上带署名。
//    PR、趋势、历史这些复盘内容还没做，见 docs/todo/2026-11-09-TODO-动作详情页.md。

const id = ref<number | null>(null)
const ex = ref<ExerciseDetail | null>(null)
const loading = ref(true)
const gif = ref<string | null>(null)
const gifState = ref<'none' | 'loading' | 'ok' | 'failed'>('none')

onLoad((query) => {
  const v = Number((query as Record<string, string | undefined> | undefined)?.id)
  if (v) id.value = v
})

onShow(() => {
  if (!ensureLogin()) return
  void load()
})

async function load() {
  if (id.value == null) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    ex.value = await getExercise(id.value)
    if (ex.value.media && !gif.value) void loadGif(ex.value.id)
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

async function loadGif(exerciseId: number) {
  gifState.value = 'loading'
  // ⚠️ 地址归 blob 缓存所有，不许 revoke（同餐照，ADR 0035）
  gif.value = await fetchExerciseMedia(exerciseId)
  gifState.value = gif.value ? 'ok' : 'failed'
}

const preset = computed(() => ex.value?.preset === true)
const archived = computed(() => preset.value && ex.value?.archived === true)

/** 「练到哪里」：只列有值的行。 */
const infoRows = computed(() => {
  const e = ex.value
  if (!e) return []
  return [
    { label: '肌群', value: muscleLabel(e.muscle) },
    { label: '器械', value: e.equipment },
    { label: '主要目标肌', value: e.target },
    { label: '辅助肌群', value: e.secondary.length ? e.secondary.join('、') : null }
  ].filter((r): r is { label: string; value: string } => !!r.value)
})

const hasData = computed(() => !!ex.value && (ex.value.media || ex.value.steps.length > 0 || !!ex.value.target))
</script>

<template>
  <view class="xd">
    <view class="xd__body">
      <FtPageHeader title="动作详情" back />

      <template v-if="ex">
        <!-- 动图：数据集的动图白底 180×180，放在白色方框里居中 -->
        <view v-if="ex.media" class="glass xd__media">
          <view class="xd__frame">
            <image v-if="gif" class="xd__gif" :src="gif" mode="aspectFit" />
            <text v-else-if="gifState === 'failed'" class="xd__frame-tip">动图没能加载，下拉回来再试一次</text>
            <text v-else class="xd__frame-tip">动图加载中…</text>
          </view>
          <text class="xd__credit">动图 © Gym visual</text>
        </view>

        <!-- 名称与来源 -->
        <view class="glass">
          <view class="xd__name-row">
            <text class="xd__name">{{ ex.name }}</text>
            <text v-if="!preset" class="xd__badge">自定义</text>
            <text v-if="archived" class="xd__badge">已下架</text>
          </view>
          <text v-if="ex.nameEn" class="xd__en">{{ ex.nameEn }}</text>
          <text v-if="ex.metric === 'floors'" class="xd__meta">按层数记录</text>
        </view>

        <!-- 练到哪里 -->
        <view class="glass block">
          <text class="block__title">练到哪里</text>
          <view v-for="r in infoRows" :key="r.label" class="xd__row">
            <text class="xd__label">{{ r.label }}</text>
            <text class="xd__value">{{ r.value }}</text>
          </view>
        </view>

        <!-- 动作要领 -->
        <view v-if="ex.steps.length" class="glass block">
          <text class="block__title">动作要领</text>
          <view v-for="(s, i) in ex.steps" :key="i" class="xd__step">
            <text class="xd__step-no ft-num">{{ i + 1 }}</text>
            <text class="xd__step-text">{{ s }}</text>
          </view>
          <text class="xd__meta xd__meta--foot">要领译自开源动作数据集，仅供参考</text>
        </view>

        <text v-if="!hasData" class="xd__note">
          {{ preset ? '这个动作还没有动图和要领。' : '自定义动作没有动图和要领。' }}
        </text>
      </template>
      <view v-else-if="!loading" class="glass">
        <FtEmpty title="动作不存在或已删除" sub="回到动作库重新找一下。" />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.xd__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(64rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

.xd__media {
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* 动图自带白底：框也用不透明白面，免得毛玻璃底色透出一圈色差 */
.xd__frame {
  width: 440rpx;
  height: 440rpx;
  max-width: 100%;
  border-radius: $radius-md;
  background: $app-surface;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.xd__gif {
  width: 100%;
  height: 100%;
}

.xd__frame-tip {
  padding: 0 32rpx;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-subtle;
}

.xd__credit {
  padding-top: 12rpx;
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.xd__name-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  min-width: 0;
}

.xd__name {
  flex: 0 1 auto;
  min-width: 0;
  font-size: $text-xl;
  font-weight: $weight-bold;
  line-height: 1.35;
  color: $app-ink;
}

.xd__badge {
  flex: none;
  padding: 2rpx 14rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-xs;
  color: $app-ink-muted;
}

.xd__en {
  display: block;
  padding-top: 6rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.xd__meta {
  display: block;
  padding-top: 8rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.xd__meta--foot {
  padding: 16rpx 0 8rpx;
  font-size: $text-xs;
  color: $app-ink-subtle;
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

.xd__row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
  padding: 20rpx 0;
}

.xd__row + .xd__row {
  border-top: 1rpx solid $app-line-soft;
}

.xd__label {
  flex-shrink: 0;
  font-size: $text-base;
  color: $app-ink-muted;
}

.xd__value {
  min-width: 0;
  text-align: right;
  font-size: $text-md;
  color: $app-ink;
}

.xd__step {
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
  padding: 16rpx 0;
}

.xd__step + .xd__step {
  border-top: 1rpx solid $app-line-soft;
}

.xd__step-no {
  flex: none;
  width: 40rpx;
  height: 40rpx;
  margin-top: 2rpx;
  border-radius: 50%;
  background: $app-surface-2;
  text-align: center;
  line-height: 40rpx;
  font-size: $text-xs;
  font-weight: $weight-bold;
  color: $app-ink-2;
}

.xd__step-text {
  flex: 1;
  min-width: 0;
  font-size: $text-md;
  line-height: 1.6;
  color: $app-ink;
}

.xd__note {
  display: block;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}
</style>
