<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useTrainingStore } from '@/stores/training'
import { createWorkout, getLastRecord } from '@/api/workout'
import { getPlan } from '@/api/plan'
import { formatClock, formatThousands, muscleLabel } from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import {
  clearSession,
  createSession,
  isPaused,
  loadSession,
  netElapsedSec,
  pause as pauseSession,
  resume as resumeSession,
  saveSession,
  setDurationSec,
  type Session,
  type SessionBlock,
  type SessionSet
} from '@/utils/session'

// ── 训练会话（计划多动作：按目标预填 → 点勾完成 → 全组完成自动切下一个未完成动作）──
// 会话存本机（ADR 0020）：返回＝最小化、退到后台与杀进程都能接续；计时走墙钟净时长（ADR 0022）。
const training = useTrainingStore()

const session = ref<Session | null>(null)
/**
 * 本会话是否已结束（提交成功、或 0 组丢弃）。
 * ⚠️ 必须是「单向闩锁」：`clearSession()` 只是删本机那份，`session.value` 仍在内存里，
 * 页面的兜底保存（变更落盘 / 离页落盘 / `onUnmounted`）会把它原样写回去——
 * 首页 `onShow` 一读就成了「训练进行中」。凡清会话处必须先立这个闩，见下方保存点。
 */
let ended = false
/** 只用于驱动「计时」重绘的当前时刻——计时本身由时间戳现算，不靠定时器累加。 */
const nowMs = ref(Date.now())

const curBlock = computed<SessionBlock | undefined>(() => {
  const s = session.value
  return s ? s.blocks[s.current] : undefined
})
/** 模板里用的两个非空代理：块列表与当前下标。 */
const blocks = computed<SessionBlock[]>(() => session.value?.blocks ?? [])
const curIdx = computed(() => session.value?.current ?? 0)
const paused = computed(() => !!session.value && isPaused(session.value))
const clock = computed(() => formatClock(session.value ? netElapsedSec(session.value, nowMs.value) : 0))

/** 按目标铺初始组行：设了目标组数就铺 N 行并预填重量/次数；未设 = 1 行空值。 */
function buildSets(
  t: Pick<SessionBlock, 'targetSets' | 'targetReps' | 'targetWeight'>
): SessionSet[] {
  const n = t.targetSets && t.targetSets > 0 ? t.targetSets : 1
  const w = t.targetWeight != null ? String(t.targetWeight) : ''
  const r = t.targetReps != null ? String(t.targetReps) : ''
  return Array.from({ length: n }, () => ({ w, r, done: false, doneAt: null, dur: null }))
}

function goalText(e: {
  targetSets?: number | null
  targetReps?: number | null
  targetWeight?: number | null
}): string {
  const parts: string[] = []
  if (e.targetSets != null || e.targetReps != null) {
    parts.push(`目标 ${e.targetSets ?? '–'} 组 × ${e.targetReps ?? '–'} 次`)
  }
  if (e.targetWeight != null) parts.push(`${e.targetWeight} kg`)
  return parts.join(' · ')
}

/** 「上次数据」：有 exerciseId 时从真接口取（自动/手动切动作时都要重拉）。 */
async function loadLastForBlock(i: number) {
  const b = session.value?.blocks[i]
  if (!b || !b.exerciseId) return
  try {
    const rec = await getLastRecord(b.exerciseId)
    b.last = rec ? `${rec.weight} kg × ${rec.reps}` : '暂无记录'
  } catch {
    // 拉取失败保留本地兜底文案
  }
}

/** 自由训练：单块占位（动作选择落地前沿用设计稿演示数据），1 行空值起步。 */
function initFreeBlock() {
  const s = createSession(null, '自由训练')
  s.blocks = [
    {
      exerciseId: 1,
      name: '杠铃卧推',
      muscle: '胸',
      last: '暂无记录',
      goal: '',
      targetSets: null,
      targetReps: null,
      targetWeight: null,
      sets: buildSets({ targetSets: null, targetReps: null, targetWeight: null })
    }
  ]
  session.value = s
  void loadLastForBlock(0)
}

/** 从「开始训练」带计划进来：拉计划详情，全部动作进场（各自按目标预填组行）。 */
async function loadPlan(pid: number) {
  try {
    const plan = await getPlan(pid)
    if (plan.exercises.length === 0) {
      initFreeBlock()
      return
    }
    const s = createSession(plan.id, plan.name)
    s.blocks = plan.exercises.map((e) => {
      const targetSets = e.targetSets ?? null
      const targetReps = e.targetReps ?? null
      const targetWeight = e.targetWeight ?? null
      return {
        exerciseId: e.exerciseId,
        name: e.name,
        muscle: muscleLabel(e.muscle),
        last: '暂无记录',
        goal: goalText({ targetSets, targetReps, targetWeight }),
        targetSets,
        targetReps,
        targetWeight,
        sets: buildSets({ targetSets, targetReps, targetWeight })
      }
    })
    session.value = s
    void loadLastForBlock(0)
  } catch {
    // request 层已统一提示；退化为自由训练块，不白屏
    initFreeBlock()
  }
}

/**
 * 进场分两路（ADR 0020）：
 * - 带 `fresh=1`（首页点「开始训练」选了计划/自由训练）＝一定是新会话；
 * - 否则（首页横幅点「继续」、或直接进本页）＝有本机会话就接续，没有才新建。
 */
async function bootstrap(q: Record<string, string | undefined>) {
  const existing = q.fresh === '1' ? null : loadSession()
  if (existing && existing.blocks.length > 0) {
    session.value = existing
    await loadLastForBlock(existing.current)
    return
  }
  clearSession()
  const pid = Number(q.plan)
  if (pid) await loadPlan(pid)
  else initFreeBlock()
}

onLoad((query) => {
  if (!ensureLogin()) return
  void bootstrap((query || {}) as Record<string, string | undefined>)
})

/** 任何变更都落本机一次：返回首页、退到后台、进程被杀都不丢（会话已结束就不再落）。 */
watch(
  session,
  (s) => {
    if (s && !ended) saveSession(s)
  },
  { deep: true }
)

// 计时靠时间戳现算，这个定时器只负责让页面上的数字往前走（500ms 一跳，秒数看起来更准）
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  timer = setInterval(() => {
    nowMs.value = Date.now()
  }, 500)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (session.value && !ended) saveSession(session.value)
})

/** 暂停 / 恢复：暂停时计时冻结、录入区锁住（ADR 0022）。 */
function togglePause() {
  const s = session.value
  if (!s) return
  if (isPaused(s)) resumeSession(s)
  else pauseSession(s)
}

/** 活动行 = 本动作第一个未完成的组；全部完成时无活动行。 */
function isActive(i: number) {
  return (curBlock.value?.sets.findIndex((s) => !s.done) ?? -1) === i
}

/** 本动作是否全部完成。 */
function isBlockDone(i: number): boolean {
  const b = session.value?.blocks[i]
  return !!b && b.sets.length > 0 && b.sets.every((s) => s.done)
}

/** 本动作已完成组数（切换条上的 x/y）。 */
function doneInBlock(i: number): number {
  return session.value?.blocks[i]?.sets.filter((s) => s.done).length ?? 0
}

/** 自由切换：点切换条任意动作即跳过去（不丢已勾的组；暂停中也能看别的动作）。 */
function switchTo(i: number) {
  const s = session.value
  if (!s || i === s.current || !s.blocks[i]) return
  s.current = i
  void loadLastForBlock(i)
}

/** 从 from 起环形找下一个「还有未完成组」的动作；都完成返回 -1。 */
function nextUnfinished(from: number): number {
  const list = session.value?.blocks ?? []
  const n = list.length
  for (let k = 1; k < n; k++) {
    const idx = (from + k) % n
    if (!isBlockDone(idx)) return idx
  }
  return -1
}

/** 长按后浏览器／WebView 还会补发一次 click（日志页踩过这个坑），给个抑制窗口。 */
let clickGuardUntil = 0

/**
 * 点勾 = 标记完成 ＋ 记下这一组的净用时（ADR 0022）；
 * 组内顺序推进（ADR 0021）：前面还有没完成的组就**静默无反应**，不弹提示。
 * 本动作全部完成 → 自动切到下一个未完成动作。
 */
function completeSet(i: number) {
  const s = session.value
  const b = curBlock.value
  if (!s || !b || paused.value) return
  if (Date.now() < clickGuardUntil) return
  const row = b.sets[i]
  if (!row || row.done) return
  if (b.sets.findIndex((x) => !x.done) !== i) return
  if (!row.w || !row.r) {
    uni.showToast({ title: '请填写重量和次数', icon: 'none' })
    return
  }
  const now = Date.now()
  // ⚠️ 先算用时、再落完成态：setDurationSec 找的是「上一组完成时刻」，本组一旦先写上
  // doneAt 就会被当成起点，净用时恒为 0（所以同时把 row 传进去排除本组）。
  const dur = setDurationSec(s, now, row)
  row.done = true
  row.doneAt = now
  row.dur = dur
  if (isBlockDone(s.current)) {
    const next = nextUnfinished(s.current)
    if (next >= 0) {
      s.current = next
      void loadLastForBlock(next)
      uni.showToast({ title: `进入下一动作：${s.blocks[next]?.name ?? ''}`, icon: 'none' })
    } else {
      uni.showToast({ title: '全部动作已完成，可点「结束训练」', icon: 'none' })
    }
  }
}

/** 加一组（独立按钮）：沿用上一行的值预填。 */
function addSet() {
  const b = curBlock.value
  if (!b || paused.value) return
  const last = b.sets[b.sets.length - 1]
  b.sets.push({ w: last?.w ?? '', r: last?.r ?? '', done: false, doneAt: null, dur: null })
}

/** 长按组行弹操作菜单（ADR 0021）：已勾的行多一项「取消完成」。 */
function onLongPress(i: number) {
  const b = curBlock.value
  const row = b?.sets[i]
  if (!b || !row || paused.value) return
  clickGuardUntil = Date.now() + 600
  const items = row.done ? ['取消完成', '删除这一组'] : ['删除这一组']
  uni.showActionSheet({
    itemList: items,
    success: ({ tapIndex }) => {
      if (items[tapIndex] === '取消完成') askUndoSet(i)
      else removeSet(i)
    }
  })
}

/** 删一组：每个动作至少留一组（全删空等于顺手把动作也删了）。 */
function removeSet(i: number) {
  const b = curBlock.value
  if (!b) return
  if (b.sets.length <= 1) {
    uni.showToast({ title: '每个动作至少留一组', icon: 'none' })
    return
  }
  b.sets.splice(i, 1)
}

/**
 * 取消某一组的完成：二次确认，并**级联取消该动作内后面已勾的组**（ADR 0021）——
 * 保住「已勾的组永远是连续的一段」，免得出现「第 2 组空着、第 3 组却打着勾」的矛盾态。
 */
function askUndoSet(i: number) {
  const b = curBlock.value
  const row = b?.sets[i]
  if (!b || !row || !row.done) return
  const cascaded = b.sets.slice(i).filter((x) => x.done).length
  uni.showModal({
    title: '取消这一组的完成？',
    content:
      cascaded > 1
        ? `第 ${i + 1} 组，以及它后面 ${cascaded - 1} 个已完成的组，都会回到未完成。`
        : `第 ${i + 1} 组会回到未完成，可以重新改重量和次数。`,
    confirmText: '取消完成',
    cancelText: '保留',
    success: ({ confirm }) => {
      if (!confirm) return
      for (let k = i; k < b.sets.length; k++) {
        if (b.sets[k].done) {
          b.sets[k].done = false
          b.sets[k].doneAt = null
          b.sets[k].dur = null
        }
      }
    }
  })
}

const doneCount = computed(() =>
  (session.value?.blocks ?? []).reduce((sum, b) => sum + b.sets.filter((s) => s.done).length, 0)
)
const totalVolume = computed(() =>
  (session.value?.blocks ?? []).reduce(
    (sum, b) =>
      sum + b.sets.filter((s) => s.done).reduce((v, s) => v + Number(s.w || 0) * Number(s.r || 0), 0),
    0
  )
)
const volumeText = computed(() => formatThousands(totalVolume.value))

/** 离开会话页：会话已在变更时落本机，这里再存一次兜底（本页不做离页确认，见 ADR 0020）。
 *  已结束的会话不落——否则刚 `clearSession()` 的会在这一步被原样写回。 */
function leaveSession() {
  if (session.value && !ended) saveSession(session.value)
  uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/home/index' }) })
}

async function finish() {
  const s = session.value
  if (!s) return
  const doneBlocks = s.blocks
    .map((b) => ({ b, sets: b.sets.filter((x) => x.done) }))
    .filter((x) => x.sets.length > 0)
  // 一组都没勾：确认后直接丢弃这条会话（不发请求、不留记录）
  if (doneBlocks.length === 0) {
    uni.showModal({
      title: '结束本次训练？',
      content: '还没有完成任何一组，本次不会有记录。确定结束吗？',
      confirmText: '结束',
      cancelText: '继续',
      success: ({ confirm }) => {
        if (confirm) {
          ended = true
          clearSession()
          leaveSession()
        }
      }
    })
    return
  }
  // 有任意动作的任意组未完成 → 先确认是否提前结束
  const pending = s.blocks.reduce((sum, b) => sum + b.sets.filter((x) => !x.done).length, 0)
  if (pending > 0) {
    uni.showModal({
      title: '提前结束训练？',
      content: `还有 ${pending} 组未完成，确定现在结束吗？未完成的组不会入库。`,
      confirmText: '结束',
      cancelText: '继续',
      success: ({ confirm }) => {
        if (confirm) void submit(doneBlocks)
      }
    })
    return
  }
  void submit(doneBlocks)
}

/** 提交：时长与组用时都取净时间（ADR 0022），提交成功后清掉本机会话。 */
async function submit(doneBlocks: { b: SessionBlock; sets: SessionSet[] }[]) {
  const s = session.value
  if (!s) return
  try {
    const detail = await createWorkout({
      planId: s.planId,
      title: s.title,
      durationSec: netElapsedSec(s, Date.now()),
      blocks: doneBlocks.map((x) => ({
        exerciseId: x.b.exerciseId,
        name: x.b.name,
        sets: x.sets.map((y) => ({
          weight: Number(y.w),
          reps: Number(y.r),
          durationSec: y.dur ?? undefined
        }))
      }))
    })
    ended = true
    clearSession()
    training.setCurrent(detail)
    // 用 redirectTo 顶掉本页：会话已结束，安卓物理返回不该再回到它（否则会重复提交一份）
    uni.redirectTo({
      url: '/pages/summary/index',
      fail: () => uni.reLaunch({ url: '/pages/summary/index' })
    })
  } catch {
    // request 层已统一提示；留在本页可重试（会话还在，数据不丢）
  }
}

function comingSoon(tip: string) {
  uni.showToast({ title: tip, icon: 'none' })
}
</script>

<template>
  <view class="session">
    <view class="session__body" :class="{ 'session__body--paused': paused }">
      <!-- 1. 顶部状态行：整块状态区可点＝暂停 / 恢复 -->
      <view class="session__head">
        <view class="session__status" @click="togglePause">
          <view class="session__dot" :class="{ 'session__dot--paused': paused }" />
          <text class="session__status-text">{{ paused ? '已暂停' : '训练中' }}</text>
          <text class="session__status-mid">·</text>
          <text class="session__clock ft-num">{{ clock }}</text>
          <view class="session__pause">
            <view v-if="paused" class="session__play" />
            <template v-else>
              <view class="session__pause-bar" />
              <view class="session__pause-bar" />
            </template>
          </view>
        </view>
        <text class="session__end" @click="finish">结束训练</text>
      </view>

      <template v-if="curBlock">
        <!-- 动作切换条：自由切换（可做完 A 一组去做 B，再回来做 A 下一组） -->
        <scroll-view scroll-x class="switcher">
          <view class="switcher__inner">
            <view
              v-for="(b, i) in blocks"
              :key="i"
              class="switcher__chip"
              :class="{
                'switcher__chip--active': i === curIdx,
                'switcher__chip--done': i !== curIdx && isBlockDone(i)
              }"
              @click="switchTo(i)"
            >
              <text class="switcher__name">{{ b.name }}</text>
              <view class="switcher__meta">
                <text v-if="i !== curIdx && isBlockDone(i)" class="switcher__tick">✓</text>
                <text class="switcher__prog ft-num">{{ doneInBlock(i) }}/{{ b.sets.length }}</text>
              </view>
            </view>
          </view>
        </scroll-view>

        <!-- 2. 录入卡（暂停时整块锁住并压暗） -->
        <view class="card" :class="{ 'card--locked': paused }">
          <view class="card__head">
            <view class="card__title-row">
              <text class="card__title">{{ curBlock.name }}</text>
              <text class="card__tag">{{ curBlock.muscle }}</text>
            </view>
            <view class="card__last">
              <text v-if="curBlock.last === '暂无记录'">暂无记录</text>
              <text v-else>上次 <text class="ft-num">{{ curBlock.last }}</text></text>
              <text v-if="curBlock.goal" class="card__goal ft-num">{{ curBlock.goal }}</text>
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
            v-for="(s, i) in curBlock.sets"
            :key="i"
            class="setrow"
            :class="{ 'setrow--active': isActive(i) }"
            @longpress="onLongPress(i)"
          >
            <view v-if="isActive(i)" class="setrow__bar" />

            <text class="setrow__idx" :class="{ 'setrow__idx--strong': isActive(i) }">
              第 {{ i + 1 }} 组
            </text>

            <view class="setrow__right">
              <!-- 完成态：只读值 ＋ 这一组的净用时 ＋ 实心勾 -->
              <template v-if="s.done">
                <text class="setrow__value ft-num">{{ s.w }} kg × {{ s.r }}</text>
                <text v-if="s.dur != null" class="setrow__dur ft-num">{{ formatClock(s.dur) }}</text>
                <view class="setrow__checkbox">
                  <wd-icon name="check" size="14px" />
                </view>
              </template>

              <!-- 未完成：值可改；点勾完成（活动行黑框高亮，其余浅框） -->
              <template v-else>
                <view class="setrow__inputs">
                  <view class="setrow__input" :class="{ 'setrow__input--soft': !isActive(i) }">
                    <input
                      v-model="s.w"
                      type="digit"
                      class="setrow__input-field ft-num"
                      :disabled="paused"
                    />
                    <text class="setrow__unit">kg</text>
                  </view>
                  <text class="setrow__x">×</text>
                  <view class="setrow__input" :class="{ 'setrow__input--soft': !isActive(i) }">
                    <input
                      v-model="s.r"
                      type="number"
                      class="setrow__input-field ft-num"
                      :disabled="paused"
                    />
                    <text class="setrow__unit">次</text>
                  </view>
                </view>
                <view
                  class="setrow__check"
                  :class="{ 'setrow__check--pending': !isActive(i) }"
                  @click="completeSet(i)"
                >
                  <wd-icon name="check" size="14px" :color="isActive(i) ? '#ffffff' : ''" />
                </view>
              </template>
            </view>
          </view>
        </view>

        <!-- 3. 加一组 -->
        <view class="add-set" :class="{ 'add-set--locked': paused }" @click="addSet">
          <text class="add-set__plus">+</text>
          <text>加一组</text>
        </view>

        <!-- 4. 添加动作（临场增删动作见待办，暂为占位） -->
        <view class="add-act" @click="comingSoon('动作选择建设中')">
          <text class="add-set__plus">+</text>
          <text>添加动作</text>
        </view>

        <text class="session__tip">长按组次可删除这一组，或取消完成</text>
      </template>

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

.session__dot--paused {
  background: $app-surface;
  border: 2rpx solid $app-ink-subtle;
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

/* 暂停 / 恢复按钮：运行中显示两条竖线，暂停中显示三角 */
.session__pause {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  width: 44rpx;
  height: 44rpx;
  border: 2rpx solid $app-ink;
  border-radius: 8rpx;
}

.session__pause-bar {
  width: 4rpx;
  height: 20rpx;
  background: $app-ink;
}

.session__play {
  width: 0;
  height: 0;
  border-top: 10rpx solid transparent;
  border-bottom: 10rpx solid transparent;
  border-left: 16rpx solid $app-ink;
  margin-left: 4rpx;
}

.session__end {
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink-muted;
}

/* 暂停中：录入区整体压暗（点击另有代码层拦截，这里只是让「已暂停」看得见） */
.session__body--paused .card,
.session__body--paused .add-set {
  opacity: 0.45;
}

/* ── 动作切换条 ── */
.switcher {
  white-space: nowrap;
  margin-bottom: 24rpx;
}

.switcher__inner {
  display: inline-flex;
  gap: 16rpx;
  padding-bottom: 4rpx;
}

.switcher__chip {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  min-width: 168rpx;
  padding: 16rpx 24rpx;
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-md;
}

.switcher__chip--active {
  background: $app-ink;
  border-color: $app-ink;
}

.switcher__chip--done {
  background: $app-canvas;
}

.switcher__name {
  font-size: $text-sm;
  font-weight: 600;
  color: $app-ink;
  max-width: 240rpx;
  overflow: hidden;
  text-overflow: ellipsis;
}

.switcher__chip--active .switcher__name {
  color: #ffffff;
}

.switcher__meta {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.switcher__tick {
  font-size: 20rpx;
  line-height: 1;
  color: $app-ink;
}

.switcher__prog {
  font-size: 20rpx;
  color: $app-ink-muted;
}

.switcher__chip--active .switcher__prog {
  color: rgba(255, 255, 255, 0.7);
}

/* ── 录入卡 ── */
.card {
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-lg;
  overflow: hidden;
  margin-bottom: 24rpx;
}

.card--locked {
  pointer-events: none;
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
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.card__goal {
  color: $app-ink;
  font-weight: 500;
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
  gap: 20rpx;
}

.setrow__value {
  font-size: $text-md;
  font-weight: 500;
  color: $app-ink;
}

/* 这一组的净用时（与上一组完成的间隔，不含暂停） */
.setrow__dur {
  font-size: 22rpx;
  color: $app-ink-muted;
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

/* 非活动行（预填待做）：浅框，值可改 */
.setrow__input--soft {
  border-color: $app-line;
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

/* 非活动行（预填待做）的勾：线框空心 */
.setrow__check--pending {
  background: $app-surface;
  border: 2rpx solid $app-ink;
  color: $app-ink;
}

/* ── + 加一组 ── */
.add-set {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 8rpx;
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink-muted;
}

.add-set--locked {
  pointer-events: none;
}

.add-set__plus {
  font-size: $text-md;
}

/* ── 添加动作（占位） ── */
.add-act {
  display: flex;
  align-items: center;
  gap: 8rpx;
  border-top: 1rpx solid $app-line;
  margin-top: 24rpx;
  padding: 28rpx 8rpx 0;
  font-size: $text-sm;
  font-weight: 500;
  color: $app-ink-muted;
}

/* 长按入口不可见，得说一句 */
.session__tip {
  display: block;
  padding: 20rpx 8rpx 0;
  font-size: 22rpx;
  color: $app-ink-subtle;
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
