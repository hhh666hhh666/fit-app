<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useTrainingStore } from '@/stores/training'
import { createWorkout, getLastRecord } from '@/api/workout'
import { getPlan } from '@/api/plan'
import { formatClock, muscleLabel } from '@/utils/format'
import { metricKindOf, metricText } from '@/utils/metric'
import { formatSetTargets, targetModeOf } from '@/utils/plan-target'
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

/**
 * 本次训练手填的消耗（大卡，ADR 0029）。做了读写代理是为了直接 v-model 到本机会话上——
 * 这样它跟其他字段一样「改一次就落盘一次」，杀进程也不丢。
 */
const calories = computed({
  get: () => session.value?.calories ?? '',
  set: (v: string) => {
    if (session.value) session.value.calories = v
  }
})

/** 计划里的一组目标（后端 `PlanSetTarget` 的形状）。 */
interface PlannedSet {
  reps?: number | null
  weight?: number | null
  /** 该组目标层数：仅爬楼（`metric = floors`）（ADR 0031）。 */
  floors?: number | null
}

/**
 * 按逐组目标铺初始组行（ADR 0027／0031）：**第 i 行取第 i 组的目标**；
 * 计划没给组目标（空数组）＝不设目标，1 行空值起步（与旧行为一致）。
 * 爬楼组的层数同样预填进「层数」栏——目标只是默认值，训练中可改（ADR 0007）。
 */
function buildSets(
  targets: PlannedSet[] | null | undefined,
  fallbackReps: number | null,
  fallbackWeight: number | null
): SessionSet[] {
  const list = targets ?? []
  if (list.length === 0) {
    return [{ w: '', r: '', f: '', done: false, doneAt: null, dur: null }]
  }
  return list.map((t) => ({
    w: (t.weight ?? fallbackWeight) != null ? String(t.weight ?? fallbackWeight) : '',
    r: (t.reps ?? fallbackReps) != null ? String(t.reps ?? fallbackReps) : '',
    f: t.floors != null ? String(t.floors) : '',
    done: false,
    doneAt: null,
    dur: null
  }))
}

function goalText(e: {
  sets?: PlannedSet[] | null
  metric?: string | null
}): string {
  const list = (e.sets ?? []).map((s) => ({
    reps: s.reps ?? null,
    weight: s.weight ?? null,
    floors: s.floors ?? null
  }))
  return formatSetTargets(list, targetModeOf(e.metric))
}

/** 「上次数据」：有 exerciseId 时从真接口取（自动/手动切动作时都要重拉）。 */
async function loadLastForBlock(i: number) {
  const b = session.value?.blocks[i]
  if (!b || !b.exerciseId) return
  try {
    const rec = await getLastRecord(b.exerciseId)
    if (!rec) {
      b.last = '暂无记录'
    } else {
      // 层数口径（爬楼）的「上次」是层数，不是重量×次数
      b.last = rec.floors != null ? `${rec.floors} 层` : `${rec.weight} kg × ${rec.reps}`
    }
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
      metric: '',
      last: '暂无记录',
      goal: '',
      targetSets: null,
      targetReps: null,
      targetWeight: null,
      circuitNo: null,
      sets: buildSets([], null, null)
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
      const planned = e.sets ?? []
      const targetReps = e.targetReps ?? null
      const targetWeight = e.targetWeight ?? null
      return {
        exerciseId: e.exerciseId,
        name: e.name,
        muscle: muscleLabel(e.muscle),
        metric: e.metric ?? '',
        last: '暂无记录',
        goal: goalText({ sets: planned, metric: e.metric }),
        targetSets: planned.length > 0 ? planned.length : (e.targetSets ?? null),
        targetReps,
        targetWeight,
        circuitNo: e.circuitNo ?? null,
        sets: buildSets(planned, targetReps, targetWeight)
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

// ── 循环组（ADR 0028）──

/** 循环组号 → 显示字母。 */
const CIRCUIT_LABELS = ['A', 'B', 'C', 'D']

function circuitLabel(no: number | null | undefined): string {
  if (no == null) return ''
  return CIRCUIT_LABELS[no - 1] ?? String(no)
}

/**
 * 某个循环组展开成一条序列：轮次 × 组内动作。
 * 轮数 ＝ 组内最大组数，某动作在超出自己组数的轮次直接跳过（顺序如 A1 B1 C1 A2 …）。
 * 每次现算而不缓存：会话页能临场加减组，轮数必须跟着变。
 */
function circuitSequenceOf(no: number): { b: number; s: number }[] {
  const list = session.value?.blocks ?? []
  const idxs: number[] = []
  list.forEach((b, i) => {
    if (b.circuitNo === no) idxs.push(i)
  })
  if (idxs.length === 0) return []
  const rounds = Math.max(...idxs.map((i) => list[i].sets.length))
  const seq: { b: number; s: number }[] = []
  for (let round = 0; round < rounds; round++) {
    for (const i of idxs) {
      if (round < list[i].sets.length) seq.push({ b: i, s: round })
    }
  }
  return seq
}

/** 某个位置的下一个序列位置；走完返回 null。 */
function nextInCircuit(blockIdx: number, setIdx: number): { b: number; s: number } | null {
  const no = session.value?.blocks[blockIdx]?.circuitNo
  if (no == null) return null
  const seq = circuitSequenceOf(no)
  const pos = seq.findIndex((p) => p.b === blockIdx && p.s === setIdx)
  if (pos < 0) return null
  return seq[pos + 1] ?? null
}

/** 这一组现在能不能勾：循环组内要等序列里前面的组都完成；普通动作仍按动作内顺序（ADR 0021）。 */
function canComplete(blockIdx: number, setIdx: number): boolean {
  const s = session.value
  const b = s?.blocks[blockIdx]
  if (!s || !b) return false
  if (b.circuitNo == null) return b.sets.findIndex((x) => !x.done) === setIdx
  const seq = circuitSequenceOf(b.circuitNo)
  const pos = seq.findIndex((p) => p.b === blockIdx && p.s === setIdx)
  if (pos < 0) return false
  for (let k = 0; k < pos; k++) {
    const p = seq[k]
    if (!s.blocks[p.b]?.sets[p.s]?.done) return false
  }
  return true
}

/** 顶部提示行：只在循环组里出现。 */
const circuitHint = computed(() => {
  const s = session.value
  const b = curBlock.value
  if (!s || !b || b.circuitNo == null) return ''
  const list = s.blocks
  const idxs: number[] = []
  list.forEach((x, i) => {
    if (x.circuitNo === b.circuitNo) idxs.push(i)
  })
  if (idxs.length === 0) return ''
  const rounds = Math.max(...idxs.map((i) => list[i].sets.length))
  const curSet = b.sets.findIndex((x) => !x.done)
  const round = curSet >= 0 ? curSet + 1 : b.sets.length
  const next = curSet >= 0 ? nextInCircuit(s.current, curSet) : null
  const tail = next
    ? `下一组 ${list[next.b]?.name ?? ''} 第 ${next.s + 1} 组`
    : '循环已走完'
  return `循环 ${circuitLabel(b.circuitNo)} · 第 ${round}/${rounds} 轮 · ${tail}`
})

/**
 * 点勾 = 标记完成 ＋ 记下这一组的净用时（ADR 0022）；
 * 顺序推进（ADR 0021／0028）：普通动作按动作内顺序、循环组按序列顺序；
 * 前面的还没完成就**静默无反应**，不弹提示。
 * 循环组内完成一组 → 自动跳到序列的下一组；动作全部完成 → 自动切到下一个未完成动作。
 */
function completeSet(i: number) {
  const s = session.value
  const b = curBlock.value
  if (!s || !b || paused.value) return
  if (Date.now() < clickGuardUntil) return
  const row = b.sets[i]
  if (!row || row.done) return
  if (!canComplete(s.current, i)) return
  if (isFloors.value ? !row.f : !row.w || !row.r) {
    uni.showToast({ title: isFloors.value ? '请填写层数' : '请填写重量和次数', icon: 'none' })
    return
  }
  const now = Date.now()
  // ⚠️ 先算用时、再落完成态：setDurationSec 找的是「上一组完成时刻」，本组一旦先写上
  // doneAt 就会被当成起点，净用时恒为 0（所以同时把 row 传进去排除本组）。
  const dur = setDurationSec(s, now, row)
  row.done = true
  row.doneAt = now
  row.dur = dur

  // 循环组：立刻跳到序列的下一组（可能是别的动作），这是「循环」的全部意义
  if (b.circuitNo != null) {
    const hop = nextInCircuit(s.current, i)
    if (hop) {
      s.current = hop.b
      void loadLastForBlock(hop.b)
      uni.showToast({
        title: `轮到 ${s.blocks[hop.b]?.name ?? ''} 第 ${hop.s + 1} 组`,
        icon: 'none'
      })
      return
    }
  }

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
  b.sets.push({
    w: last?.w ?? '',
    r: last?.r ?? '',
    f: last?.f ?? '',
    done: false,
    doneAt: null,
    dur: null
  })
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
/** 有氧动作不参与容量（ADR 0023）；会话块里存的肌群是中文名（`muscleLabel` 的产物），按名比对。 */
const CARDIO_LABEL = muscleLabel('cardio')

const hasLift = computed(() =>
  (session.value?.blocks ?? []).some((b) => b.muscle !== CARDIO_LABEL)
)
const totalVolume = computed(() =>
  (session.value?.blocks ?? [])
    .filter((b) => b.muscle !== CARDIO_LABEL)
    .reduce(
      (sum, b) =>
        sum + b.sets.filter((s) => s.done).reduce((v, s) => v + Number(s.w || 0) * Number(s.r || 0), 0),
      0
    )
)
/** 层数口径（爬楼）的已完成组合计——有氧块不做容量，它的成绩是层数（ADR 0025）。 */
const totalFloors = computed(() =>
  (session.value?.blocks ?? [])
    .filter((b) => b.metric === 'floors')
    .reduce(
      (sum, b) => sum + b.sets.filter((s) => s.done).reduce((v, s) => v + (Number(s.f) || 0), 0),
      0
    )
)
/** 当前动作是不是「按层数录」——组行与图例据此换形态。 */
const isFloors = computed(() => curBlock.value?.metric === 'floors')
const metricKind = computed(() => metricKindOf(hasLift.value, totalFloors.value))
/**
 * 底部标签：这一场只有层数口径的动作（爬楼）时叫「总层数」，其余仍说「总重」（会话页的轻说法）。
 * ⚠️ 判据是**有没有层数口径的动作**，不是已完成的层数之和——一组都没勾时也该叫「总层数」。
 */
const volumeLabel = computed(() =>
  !hasLift.value && blocks.value.some((b) => b.metric === 'floors') ? '总层数' : '总重'
)
/** 整场既没力量也没层数时显示「—」，不显示 0 kg。 */
const volumeText = computed(() =>
  metricText(metricKind.value, totalVolume.value, totalFloors.value)
)

/** 组次只读文案：层数口径显示层数，其余显示「重量 × 次数」。 */
function setValueText(x: SessionSet): string {
  return isFloors.value ? `${x.f} 层` : `${x.w} kg × ${x.r}`
}

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
      content: '还没有完成任何一组，本次不会有记录，填的消耗也不会计入。确定结束吗？',
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
  const kcalText = (s.calories ?? '').trim()
  const kcal = kcalText === '' ? undefined : Number(kcalText)
  try {
    const detail = await createWorkout({
      planId: s.planId,
      title: s.title,
      durationSec: netElapsedSec(s, Date.now()),
      // 未填就不传（＝null，不计入当日消耗）；与「填 0」是两件事
      calories: kcal != null && Number.isFinite(kcal) ? Math.max(0, Math.round(kcal)) : undefined,
      blocks: doneBlocks.map((x) => ({
        exerciseId: x.b.exerciseId,
        name: x.b.name,
        sets: x.sets.map((y) => {
          const floors = x.b.metric === 'floors'
          return {
            // floors 口径（爬楼）：重量／次数只是占位，服务端也只信 floors（ADR 0025）
            weight: floors ? 0 : Number(y.w),
            reps: floors ? 1 : Number(y.r),
            ...(floors ? { floors: Number(y.f) || 0 } : {}),
            durationSec: y.dur ?? undefined
          }
        })
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

      <!-- 本次消耗：手填，计入当日总消耗（ADR 0029）。放这里是因为它跟时长同级，属于「整场」 -->
      <view class="burn">
        <text class="burn__label">本次消耗</text>
        <view class="burn__field">
          <input
            v-model="calories"
            type="number"
            class="burn__input ft-num"
            placeholder="–"
            :disabled="paused"
          />
          <text class="burn__unit">大卡</text>
        </view>
        <text class="burn__hint">手填，算进今日消耗</text>
      </view>

      <template v-if="curBlock">
        <!-- 动作切换条：自由切换（可做完 A 一组去做 B，再回来做 A 下一组） -->
        <scroll-view scroll-x class="switcher" :scroll-into-view="'chip-' + curIdx" scroll-with-animation>
          <view class="switcher__inner">
            <view
              v-for="(b, i) in blocks"
              :id="'chip-' + i"
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

        <!-- 循环组状态行（ADR 0028）：只在该动作参与循环时出现 -->
        <view v-if="circuitHint" class="circhint">
          <text class="circhint__dot" />
          <text class="circhint__text ft-num">{{ circuitHint }}</text>
        </view>

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
              <text class="ft-num">{{ isFloors ? '层数' : '负荷 / 次数' }}</text>
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
                <text class="setrow__value ft-num">{{ setValueText(s) }}</text>
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
                      v-if="isFloors"
                      v-model="s.f"
                      type="number"
                      class="setrow__input-field ft-num"
                      :disabled="paused"
                    />
                    <input
                      v-else
                      v-model="s.w"
                      type="digit"
                      class="setrow__input-field ft-num"
                      :disabled="paused"
                    />
                    <text class="setrow__unit">{{ isFloors ? '层' : 'kg' }}</text>
                  </view>
                  <template v-if="!isFloors">
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
                  </template>
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
        <text> 组 · {{ volumeLabel }} </text>
        <text class="ft-num session__summary-num">{{ volumeText }}</text>
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

/* ── 本次消耗（ADR 0029） ── */
.burn {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 14rpx 20rpx;
  margin: -12rpx 0 26rpx;
  background: $app-surface;
  border: 1rpx solid $app-line-soft;
  border-radius: $radius-md;
}

.burn__label {
  font-size: $text-xs;
  color: $app-ink-muted;
  flex-shrink: 0;
}

.burn__field {
  display: flex;
  align-items: baseline;
  gap: 6rpx;
  flex: 1;
}

.burn__input {
  min-width: 0;
  width: 140rpx;
  font-size: $text-md;
  font-weight: 600;
  color: $app-ink;
  border-bottom: 2rpx solid $app-line;
  padding-bottom: 2rpx;
}

.burn__unit {
  font-size: 20rpx;
  color: $app-ink-subtle;
}

.burn__hint {
  font-size: 18rpx;
  color: $app-ink-subtle;
  flex-shrink: 0;
}

/* ── 循环组状态行 ── */
.circhint {
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 0 4rpx 18rpx;
}

.circhint__dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: $app-ink;
}

.circhint__text {
  font-size: $text-xs;
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
