<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useScheduleStore } from '@/stores/schedule'
import { useTrainingStore } from '@/stores/training'
import { createWorkout, getLastRecord } from '@/api/workout'
import { formatThousands } from '@/utils/format'

// ── 计划与周安排仍走本地 mock（第二期落库）；训练记录已接真接口 ──
const schedule = useScheduleStore()
const training = useTrainingStore()

const planTitle = ref('自由训练')

const DEMO_LAST = '60 kg × 8'

// id=1 是预设「杠铃卧推」；计划来的动作暂无 id（计划表第二期落库）
const exercise = ref({ id: 1 as number | null, name: '杠铃卧推', muscle: '胸', last: DEMO_LAST })
const nextExercise = ref('上斜哑铃卧推')

type SetRow = { w: string; r: string; done: boolean }
// 演示动作带已完成两组；其它动作从第一组空输入开始
const sets = ref<SetRow[]>([
  { w: '60', r: '8', done: true },
  { w: '60', r: '8', done: true },
  { w: '60', r: '8', done: false }
])

// 「上次数据」：有 exerciseId 时从真接口取
async function loadLastRecord() {
  const id = exercise.value.id
  if (!id) return
  try {
    const rec = await getLastRecord(id)
    exercise.value.last = rec ? `${rec.weight} kg × ${rec.reps}` : '暂无记录'
  } catch {
    // 拉取失败保留本地兜底文案
  }
}

// 从「开始训练」带计划进来：用计划里的动作铺当前动作（从第一组空输入开始）
onLoad((query) => {
  const q = (query || {}) as Record<string, string | undefined>
  const plan = schedule.planOf(Number(q.plan))
  if (plan && plan.exercises.length > 0) {
    planTitle.value = plan.name
    const first = plan.exercises[0]
    exercise.value = {
      id: null,
      name: first,
      muscle: plan.name.split(/[&＆]/)[0].trim(),
      last: '暂无记录'
    }
    nextExercise.value = plan.exercises[1] ?? '—'
    sets.value = [{ w: '', r: '', done: false }]
  }
  loadLastRecord()
})

// 输入态 = 最后一行；勾完最后一行会自动补一行新的
function isActive(i: number) {
  return i === sets.value.length - 1
}

function completeSet(i: number) {
  if (sets.value[i].done) return
  const cur = sets.value[i]
  if (!cur.w || !cur.r) {
    uni.showToast({ title: '请填写重量和次数', icon: 'none' })
    return
  }
  cur.done = true
  sets.value.push({ w: cur.w, r: cur.r, done: false })
}

function addSet() {
  const last = sets.value[sets.value.length - 1]
  sets.value.push({ w: last.w, r: last.r, done: false })
}

const doneCount = computed(() => sets.value.filter((s) => s.done).length)
const totalVolume = computed(() =>
  sets.value.filter((s) => s.done).reduce((sum, s) => sum + Number(s.w || 0) * Number(s.r || 0), 0)
)
const volumeText = computed(() => formatThousands(totalVolume.value))

// 训练计时（进页面开始走秒；真实接数据后从训练开始时间起算）
const seconds = ref(0)
let timer: ReturnType<typeof setInterval> | undefined
const clock = computed(() => {
  const m = String(Math.floor(seconds.value / 60)).padStart(2, '0')
  const s = String(seconds.value % 60).padStart(2, '0')
  return `${m}:${s}`
})
onMounted(() => {
  timer = setInterval(() => {
    seconds.value++
  }, 1000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function finish() {
  const done = sets.value.filter((s) => s.done)
  if (done.length === 0) {
    uni.showToast({ title: '还没有完成的组', icon: 'none' })
    return
  }
  try {
    const detail = await createWorkout({
      title: planTitle.value,
      durationSec: seconds.value,
      blocks: [
        {
          exerciseId: exercise.value.id,
          name: exercise.value.name,
          sets: done.map((s) => ({ weight: Number(s.w), reps: Number(s.r) }))
        }
      ]
    })
    training.setCurrent(detail)
    uni.navigateTo({ url: '/pages/summary/index' })
  } catch {
    // request 层已统一提示；留在本页可重试
  }
}
function comingSoon(tip: string) {
  uni.showToast({ title: tip, icon: 'none' })
}
</script>

<template>
  <view class="session">
    <view class="session__body">
      <!-- 1. 顶部状态行 -->
      <view class="session__head">
        <view class="session__status">
          <view class="session__dot" />
          <text class="session__status-text">训练中</text>
          <text class="session__status-mid">·</text>
          <text class="session__clock ft-num">{{ clock }}</text>
        </view>
        <text class="session__end" @click="finish">结束训练</text>
      </view>

      <!-- 2. 录入卡 -->
      <view class="card">
        <view class="card__head">
          <view class="card__title-row">
            <text class="card__title">{{ exercise.name }}</text>
            <text class="card__tag">{{ exercise.muscle }}</text>
          </view>
          <view class="card__last">
            <text v-if="exercise.last === '暂无记录'">暂无记录</text>
            <text v-else>上次 <text class="ft-num">{{ exercise.last }}</text></text>
          </view>
        </view>

        <view class="card__legend">
          <text class="legend__c1">组次</text>
          <view class="legend__right">
            <text class="ft-num">负荷 / 次数</text>
            <text class="legend__state">状态</text>
          </view>
        </view>

        <view
          v-for="(s, i) in sets"
          :key="i"
          class="setrow"
          :class="{ 'setrow--active': isActive(i) }"
        >
          <view v-if="isActive(i)" class="setrow__bar" />

          <text class="setrow__idx" :class="{ 'setrow__idx--strong': isActive(i) }">
            第 {{ i + 1 }} 组
          </text>

          <view class="setrow__right">
            <template v-if="!isActive(i)">
              <text class="setrow__value ft-num">{{ s.w }} kg × {{ s.r }}</text>
              <view class="setrow__checkbox">
                <wd-icon name="check" size="14px" />
              </view>
            </template>

            <template v-else>
              <view class="setrow__inputs">
                <view class="setrow__input">
                  <input v-model="s.w" type="digit" class="setrow__input-field ft-num" />
                  <text class="setrow__unit">kg</text>
                </view>
                <text class="setrow__x">×</text>
                <view class="setrow__input">
                  <input v-model="s.r" type="number" class="setrow__input-field ft-num" />
                  <text class="setrow__unit">次</text>
                </view>
              </view>
              <view class="setrow__check" @click="completeSet(i)">
                <wd-icon name="check" size="14px" color="#ffffff" />
              </view>
            </template>
          </view>
        </view>
      </view>

      <!-- 3. 加一组 -->
      <view class="add-set" @click="addSet">
        <text class="add-set__plus">+</text>
        <text>加一组</text>
      </view>

      <!-- 4. 下一动作 -->
      <view class="next">
        <view class="next__row">
          <view class="next__label-wrap">
            <text class="next__label">下一个动作：</text>
            <text class="next__name">{{ nextExercise }}</text>
          </view>
          <text class="next__swap" @click="comingSoon('动作切换建设中')">更换</text>
        </view>
        <view class="next__add" @click="comingSoon('动作选择建设中')">
          <text class="add-set__plus">+</text>
          <text>添加动作</text>
        </view>
      </view>

      <view class="session__pad" />
    </view>

    <!-- 5. 底部固定区 -->
    <view class="session__bottom">
      <view class="session__summary">
        <text>本次 </text>
        <text class="ft-num session__summary-num">{{ doneCount }}</text>
        <text> 组 · 总重 </text>
        <text class="ft-num session__summary-num">{{ volumeText }} kg</text>
      </view>
      <view class="ft-btn ft-btn--primary ft-btn--block session__finish" @click="finish">
        结束训练
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.session__body {
  padding: 48rpx 32rpx 0;
}

/* ── 顶部状态行 ── */
.session__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
  margin-bottom: 32rpx;
}

.session__status {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.session__dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: $app-ink;
}

.session__status-text {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.session__status-mid {
  color: $app-ink-muted;
}

.session__clock {
  font-size: $text-lg;
  font-weight: 600;
  color: $app-ink;
}

.session__end {
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink-muted;
}

/* ── 录入卡 ── */
.card {
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-lg;
  overflow: hidden;
  margin-bottom: 24rpx;
}

.card__head {
  padding: 32rpx 32rpx 24rpx;
  border-bottom: 1rpx solid $app-line;
}

.card__title-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 6rpx;
}

.card__title {
  font-size: 40rpx;
  font-weight: 700;
  color: $app-ink;
}

.card__tag {
  font-size: 22rpx;
  font-weight: 500;
  color: $app-ink-muted;
  background: $app-canvas;
  border: 1rpx solid $app-line;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.card__last {
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* 表头 */
.card__legend {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 32rpx;
  background: #fbfbfa;
  border-bottom: 1rpx solid $app-line;
  font-size: 22rpx;
  font-weight: 500;
  color: $app-ink-muted;
}

.legend__c1 {
  width: 128rpx;
}

.legend__right {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.legend__state {
  width: 56rpx;
  text-align: right;
}

/* 组行 */
.setrow {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx;
  background: $app-surface;
}

.setrow + .setrow {
  border-top: 1rpx solid $app-line;
}

.setrow--active {
  padding: 20rpx 32rpx;
}

/* 活动行的左缘荧光条（本屏唯一的高亮用法） */
.setrow__bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 8rpx;
  background: $app-highlight;
}

.setrow__idx {
  width: 128rpx;
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink-muted;
  flex-shrink: 0;
}

.setrow__idx--strong {
  color: $app-ink;
  font-weight: 600;
}

.setrow__right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 32rpx;
}

.setrow__value {
  font-size: $text-md;
  font-weight: 500;
  color: $app-ink;
}

.setrow__checkbox {
  width: 40rpx;
  height: 40rpx;
  border-radius: 8rpx;
  background: $app-canvas;
  border: 1rpx solid $app-line;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $app-ink;
  flex-shrink: 0;
}

/* 输入态 */
.setrow__inputs {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.setrow__input {
  display: flex;
  align-items: center;
  border: 2rpx solid $app-ink;
  border-radius: 8rpx;
  background: $app-surface;
  padding: 8rpx 16rpx;
}

.setrow__input-field {
  width: 64rpx;
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
  text-align: left;
}

.setrow__unit {
  font-size: 22rpx;
  color: $app-ink-muted;
  margin-left: 8rpx;
}

.setrow__x {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.setrow__check {
  width: 48rpx;
  height: 48rpx;
  border-radius: 8rpx;
  background: $app-ink;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* ── + 加一组 ── */
.add-set {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 8rpx;
  margin-bottom: 48rpx;
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink-muted;
}

.add-set__plus {
  font-size: $text-md;
}

/* ── 下一动作 ── */
.next {
  border-top: 1rpx solid $app-line;
  padding: 32rpx 8rpx 0;
}

.next__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
  font-size: $text-sm;
}

.next__label-wrap {
  display: flex;
  align-items: center;
}

.next__label {
  color: $app-ink-muted;
}

.next__name {
  color: $app-ink;
  font-weight: 500;
}

.next__swap {
  color: $app-ink-muted;
}

.next__add {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 0;
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink-muted;
}

/* ── 底部固定区 ── */
.session__pad {
  height: 320rpx;
}

.session__bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: $app-canvas;
  border-top: 1rpx solid $app-line;
  padding: 24rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
}

.session__summary {
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
  margin-bottom: 24rpx;
}

.session__summary-num {
  color: $app-ink;
  font-weight: 500;
}

.session__finish {
  height: 104rpx;
  font-size: $text-md;
}
</style>
