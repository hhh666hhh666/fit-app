<script setup lang="ts">
import FtButton from '@/components/FtButton.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import FtIcon from '@/components/FtIcon.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import FtSheet from '@/components/FtSheet.vue'
import { computed, ref } from 'vue'
import { onLoad, onReachBottom, onShow } from '@dcloudio/uni-app'
import {
  batchDeleteBodyRecords,
  createBodyRecord,
  getBodyOverview,
  getBodyRecords,
  updateBodyRecord
} from '@/api/body'
import type { BodyOverview, BodyRecordRow } from '@/api/body'
import { getProfile, saveProfile } from '@/api/profile'
import type { ActivityLevel, ProfileInfo, ProfileSex } from '@/api/profile'
import { ACTIVITY_OPTIONS, activityLabel, sexLabel, SEX_OPTIONS } from '@/utils/energy'
import FtTrendChart from '@/components/FtTrendChart.vue'
import {
  formatDateLabel,
  formatDateTimeLabel,
  nowTime,
  toIsoMinute,
  todayKey
} from '@/utils/format'
import { ensureLogin } from '@/utils/guard'
import {
  formatWeight,
  getWeightUnit,
  kgToDisplay,
  parseWeightInput,
  setWeightUnit,
  weightPrecision,
  weightUnitLabel,
  weightUnitShort,
  type WeightUnit
} from '@/utils/weight'
import { ask } from '@/utils/confirm'

// ── 身体档案：一条记录＝一次测定，体重与四围同一行（ADR 0017）──

const PAGE_SIZE = 20

const overview = ref<BodyOverview | null>(null)
const records = ref<BodyRecordRow[]>([])
/** 个人档案（身高／性别／生日／活动档）＋ 今日消耗拆解（ADR 0029／0030）。 */
const profile = ref<ProfileInfo | null>(null)
const loading = ref(true)
const hasMore = ref(false)
const loadingMore = ref(false)
const weightUnit = ref<WeightUnit>('jin')

async function load() {
  loading.value = true
  try {
    const [ov, rows, pf] = await Promise.all([
      getBodyOverview(),
      getBodyRecords(PAGE_SIZE, 0),
      // 档案拿不到不该让整页空着——身体数据才是主角
      getProfile().catch(() => null)
    ])
    overview.value = ov
    records.value = rows
    profile.value = pf
    hasMore.value = rows.length >= PAGE_SIZE
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const rows = await getBodyRecords(PAGE_SIZE, records.value.length)
    records.value = [...records.value, ...rows]
    hasMore.value = rows.length >= PAGE_SIZE
  } catch {
    // request 层已统一提示
  } finally {
    loadingMore.value = false
  }
}

onReachBottom(loadMore)

/** 从饮食卡「记体重」带参进入（?record=1）：加载后自动打开录入弹层。 */
const pendingRecord = ref(false)

onLoad((query) => {
  const q = (query || {}) as Record<string, string | undefined>
  pendingRecord.value = q.record === '1'
})

onShow(() => {
  if (!ensureLogin()) return
  weightUnit.value = getWeightUnit()
  load()
  if (pendingRecord.value) {
    pendingRecord.value = false
    openSheet()
  }
})

// ── 体重／体脂图（序列已由后端按日聚合、取当天最小）──
// 同一张图、点标题上的「体重 / 体脂」切换序列（ADR 0030）：不做双线，量纲不同会读出假相关。
const weightPoints = computed(() =>
  (overview.value?.weightSeries ?? []).map((p) => ({
    date: p.date,
    value: kgToDisplay(Number(p.weight), weightUnit.value)
  }))
)
/** 体脂率是百分数，不参与斤／千克换算。 */
const bodyFatPoints = computed(() =>
  (overview.value?.bodyFatSeries ?? []).map((p) => ({
    date: p.date,
    value: Number(p.bodyFat)
  }))
)

const chartMetric = ref<'weight' | 'fat'>('weight')

const chartPoints = computed(() =>
  chartMetric.value === 'weight' ? weightPoints.value : bodyFatPoints.value
)

/** 区间文案：两个序列的值都已经是「展示单位」（体重已换算成斤／千克），按单位精度取位即可。 */
const chartRange = computed(() => {
  const pts = chartPoints.value
  if (pts.length < 2) return ''
  const head = pts[0].value
  const tail = pts[pts.length - 1].value
  if (chartMetric.value === 'fat') {
    return `${head.toFixed(1)} → ${tail.toFixed(1)} %`
  }
  const p = weightPrecision(weightUnit.value)
  return `${head.toFixed(p)} → ${tail.toFixed(p)} ${weightUnitShort(weightUnit.value)}`
})

/**
 * 准星气泡的数值格式（ADR 0030 的唯一精度改动点）：斤 1 位／千克 2 位，与卡片、列表一致。
 * y 轴刻度仍取整——刻度是视觉参考，气泡是读数。
 */
const chartFormat = computed(() => {
  if (chartMetric.value === 'fat') return (v: number) => `${v.toFixed(1)}`
  const p = weightPrecision(weightUnit.value)
  return (v: number) => v.toFixed(p)
})

const chartEmptyText = computed(() =>
  chartMetric.value === 'fat' ? '再记一次体脂，就能看到趋势线' : '再记一次体重，就能看到趋势线'
)

// ── 围度图：一张图 + 部位分段条切换（四个部位数值跨度大，共用 y 轴会压扁）──
type GirthKey = 'chest' | 'waist' | 'hip' | 'arm'
const GIRTH_PARTS: { key: GirthKey; label: string }[] = [
  { key: 'chest', label: '胸围' },
  { key: 'waist', label: '腰围' },
  { key: 'hip', label: '臀围' },
  { key: 'arm', label: '臂围' }
]
const girthPart = ref<GirthKey>('chest')

const girthPoints = computed(() => {
  const key = girthPart.value
  return (overview.value?.girthSeries ?? [])
    .filter((p) => p[key] != null)
    .map((p) => ({ date: p.date, value: Number(p[key]) }))
})
const girthRange = computed(() => {
  const pts = girthPoints.value
  if (pts.length < 2) return ''
  return `${pts[0].value} → ${pts[pts.length - 1].value} cm`
})

// ── 多选删除：长按进入多选态；多选态下点行＝切换勾选（不再进编辑）──
const selectMode = ref(false)
const selected = ref<Set<number>>(new Set())
const selectedCount = computed(() => selected.value.size)
const allSelected = computed(
  () => records.value.length > 0 && selected.value.size === records.value.length
)

/**
 * H5 上长按触发后浏览器还会补发一次 click——不挡的话刚被长按选中的那条会被
 * 这次 click 又取消掉（选数 1→0，表现为「长按没反应」）。长按期间挂短窗口吞掉 click。
 */
let clickLockUntil = 0

function toggleSelect(id: number) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

function onRowClick(r: BodyRecordRow) {
  if (Date.now() < clickLockUntil) return
  if (selectMode.value) toggleSelect(r.id)
  else openEdit(r)
}

function onRowLongPress(r: BodyRecordRow) {
  if (selectMode.value) return
  clickLockUntil = Date.now() + 400
  selectMode.value = true
  selected.value = new Set([r.id])
}

function exitSelect() {
  selectMode.value = false
  selected.value = new Set()
}

function toggleAll() {
  selected.value = allSelected.value ? new Set() : new Set(records.value.map((r) => r.id))
}

function doDelete() {
  const ids = [...selected.value]
  if (ids.length === 0) return
  void ask({
    title: '删除身体档案记录',
    content:
      ids.length === 1
        ? '删除这条记录？删除后不可恢复。'
        : `删除选中的 ${ids.length} 条记录？删除后不可恢复。`,
    confirmText: '删除',
    danger: true
  }).then(async (confirm) => {
    if (!confirm) return
    try {
      await batchDeleteBodyRecords(ids)
      exitSelect()
      uni.showToast({ title: `已删除 ${ids.length} 条`, icon: 'none' })
      await load() // 卡片与两张图都要按新数据重算
    } catch {
      // request 层已统一提示
    }
  })
}

// ── 录入 / 编辑弹层（同一个，靠 editId 区分）──
const sheet = ref({
  show: false,
  editId: null as number | null,
  date: todayKey(),
  time: nowTime(),
  weight: '',
  /** 体脂率 %：只记录、不参与计算（ADR 0030）。 */
  bodyFat: '',
  chest: '',
  waist: '',
  hip: '',
  arm: '',
  note: '',
  saving: false,
  /** 新增时预填来源的测定时刻（用于「上次 ×月×日」提示）；编辑时为 null。 */
  prefilledFrom: null as string | null
})

function numOrBlank(v: number | null | undefined): string {
  return v == null ? '' : formatWeight(v, weightUnit.value)
}

/**
 * 体脂率输入解析：空＝null；不合规返回 NaN（调用方据此拦下）。
 * 1 位小数是体脂秤的精度，多留一位是假精度（ADR 0030）。
 */
function parseBodyFat(input: string): number | null {
  const raw = input.trim()
  if (raw === '') return null
  if (!/^\d+(?:\.\d+)?$/.test(raw)) return Number.NaN
  if ((raw.split('.')[1]?.length ?? 0) > 1) return Number.NaN
  const v = Number(raw)
  if (!Number.isFinite(v) || v < 3 || v > 75) return Number.NaN
  return v
}

function displayWeight(v: number | null | undefined): string {
  return formatWeight(v, weightUnit.value)
}

function unitChange(unit: WeightUnit) {
  if (unit === weightUnit.value) return
  const current = sheet.value.weight.trim()
  if (current) {
    const kg = parseWeightInput(current, weightUnit.value)
    sheet.value.weight = Number.isFinite(kg) ? formatWeight(kg, unit) : ''
  }
  weightUnit.value = unit
  setWeightUnit(unit)
}

function weightInputStep(): string {
  return weightPrecision(weightUnit.value) === 1 ? '0.1' : '0.01'
}

/** 新增：日期时间取「现在」，体重与四围预填上次数值（省敲键盘）。 */
function openSheet() {
  const last = records.value.find((r) => r.weight != null) ?? null
  sheet.value = {
    show: true,
    editId: null,
    date: todayKey(),
    time: nowTime(),
    weight: numOrBlank(last?.weight),
    bodyFat: last?.bodyFat != null ? String(last.bodyFat) : '',
    chest: numOrBlank(last?.chest),
    waist: numOrBlank(last?.waist),
    hip: numOrBlank(last?.hip),
    arm: numOrBlank(last?.arm),
    note: '',
    saving: false,
    prefilledFrom: last?.date ?? null
  }
}

/** 编辑：点明细行进来，值全部预填（含时刻），弹层里不放删除——删除统一走长按。 */
function openEdit(r: BodyRecordRow) {
  const [date, time] = splitIso(r.date)
  sheet.value = {
    show: true,
    editId: r.id,
    date,
    time,
    weight: numOrBlank(r.weight),
    bodyFat: r.bodyFat != null ? String(r.bodyFat) : '',
    chest: numOrBlank(r.chest),
    waist: numOrBlank(r.waist),
    hip: numOrBlank(r.hip),
    arm: numOrBlank(r.arm),
    note: r.note ?? '',
    saving: false,
    prefilledFrom: null
  }
}

/** `2026-10-13T23:51:00` → ['2026-10-13', '23:51'] */
function splitIso(iso: string): [string, string] {
  const [d, t = '00:00:00'] = iso.split('T')
  return [d, t.slice(0, 5)]
}

/** 预填值的来源提示：不标的话，只想记体重时容易把上个月的围度当成今天的存进去。 */
const lastHint = computed(() => {
  const d = sheet.value.prefilledFrom
  return d ? `上次 ${formatDateLabel(d)}` : ''
})

function onDateChange(e: { detail: { value: string } }) {
  sheet.value.date = e.detail.value
}

function onTimeChange(e: { detail: { value: string } }) {
  sheet.value.time = e.detail.value
}

async function save() {
  const f = sheet.value
  const parsedWeight = parseWeightInput(f.weight, weightUnit.value)
  if (Number.isNaN(parsedWeight)) {
    uni.showToast({ title: `${weightUnitLabel(weightUnit.value)}最多保留${weightPrecision(weightUnit.value)}位小数`, icon: 'none' })
    return
  }
  // 体脂率不合规要被拦住（否则后端 400，用户看到的是通用报错）
  const bodyFat = parseBodyFat(f.bodyFat)
  if (Number.isNaN(bodyFat)) {
    uni.showToast({ title: '体脂率 3–75，最多 1 位小数', icon: 'none' })
    return
  }
  const payload = {
    measuredAt: toIsoMinute(f.date, f.time),
    weight: parsedWeight,
    bodyFat,
    chest: f.chest ? Number(f.chest) : null,
    waist: f.waist ? Number(f.waist) : null,
    hip: f.hip ? Number(f.hip) : null,
    arm: f.arm ? Number(f.arm) : null,
    note: f.note.trim()
  }
  if (
    payload.weight == null &&
    payload.bodyFat == null &&
    payload.chest == null &&
    payload.waist == null &&
    payload.hip == null &&
    payload.arm == null
  ) {
    uni.showToast({ title: '体重、体脂和围度至少填一项', icon: 'none' })
    return
  }
  f.saving = true
  try {
    if (f.editId != null) await updateBodyRecord(f.editId, payload)
    else await createBodyRecord(payload)
    sheet.value.show = false
    uni.showToast({ title: '已保存', icon: 'none' })
    await load()
  } catch {
    // request 层已统一提示
  } finally {
    f.saving = false
  }
}

// ── 个人档案弹层（ADR 0030）：四项一起提交，缺一项就算不出消耗 ──
const LEVEL_OPTIONS = ACTIVITY_OPTIONS

const profileSheet = ref({
  show: false,
  sex: 'male' as ProfileSex,
  birthDate: '',
  height: '',
  activityLevel: 'light' as ActivityLevel,
  saving: false
})

function openProfileSheet() {
  const x = profile.value
  profileSheet.value = {
    show: true,
    sex: x?.sex ?? 'male',
    birthDate: x?.birthDate ?? '',
    height: x?.heightCm != null ? String(x.heightCm) : '',
    activityLevel: x?.activityLevel ?? 'light',
    saving: false
  }
}

function onBirthChange(e: { detail: { value: string } }) {
  profileSheet.value.birthDate = e.detail.value
}

async function saveProfileSheet() {
  const f = profileSheet.value
  if (!f.birthDate) {
    uni.showToast({ title: '请选择出生日期', icon: 'none' })
    return
  }
  const height = Number(f.height)
  if (!Number.isFinite(height) || height < 100 || height > 250) {
    uni.showToast({ title: '身高填 100–250 cm', icon: 'none' })
    return
  }
  f.saving = true
  try {
    profile.value = await saveProfile({
      sex: f.sex,
      birthDate: f.birthDate,
      heightCm: Math.round(height * 10) / 10,
      activityLevel: f.activityLevel
    })
    f.show = false
    uni.showToast({ title: '已保存', icon: 'none' })
  } catch {
    // request 层已统一提示
  } finally {
    f.saving = false
  }
}

/** 「怎么算的」弹层：算式与代入值全部来自服务端，前端只排版。 */
const energySheet = ref({ show: false })

/** 标签统一取 `utils/energy`（与饮食页同一份念法）。 */
const levelLabel = activityLabel

const energy = computed(() => profile.value?.energy ?? null)

/** 「怎么算的」里的体重日期提示（空＝没有记录）。 */
const weightDateHint = computed(() => {
  const at = energy.value?.weightAt
  return at ? `（${formatDateLabel(String(at).slice(0, 10))}）` : ''
})

// ── 展示用小工具 ──
function signed(v: number | null | undefined, unit: WeightUnit = 'kg'): string {
  if (v == null) return ''
  const n = kgToDisplay(Number(v), unit)
  return `${n > 0 ? '+' : ''}${n.toFixed(weightPrecision(unit))}`
}

function girthValue(v: number | null | undefined): string {
  return v == null ? '–' : String(v)
}

/** 明细行第二行：把量过的围度挤成一行小字，没量的不出现；体脂排在最前。 */
function girthLine(r: BodyRecordRow): string {
  const parts: string[] = []
  if (r.bodyFat != null) parts.push(`体脂 ${Number(r.bodyFat).toFixed(1)}%`)
  if (r.chest != null) parts.push(`胸 ${r.chest}`)
  if (r.waist != null) parts.push(`腰 ${r.waist}`)
  if (r.hip != null) parts.push(`臀 ${r.hip}`)
  if (r.arm != null) parts.push(`臂 ${r.arm}`)
  return parts.join(' · ')
}

/** 体脂率文案：固定 1 位小数（ADR 0030）。 */
function fatText(v: number | null | undefined): string {
  return v == null ? '–' : `${Number(v).toFixed(1)}%`
}
</script>

<template>
  <view class="bodypg">
    <view class="bodypg__body">
      <FtPageHeader title="身体档案" back>
        <template v-if="selectMode" #right>
          <view class="pill" role="button" @click="exitSelect">取消</view>
        </template>
      </FtPageHeader>

      <view v-if="loading" class="hint">加载中…</view>

      <template v-else>
        <!-- 个人档案卡：四项 ＋ 今日消耗 ＋ 「怎么算的」（ADR 0029／0030） -->
        <view class="glass">
          <view class="card-head">
            <text class="card-title">个人档案</text>
            <view v-if="profile?.complete" class="chip-btn" role="button" @click="openProfileSheet">编辑</view>
          </view>

          <template v-if="profile?.complete">
            <view class="quad">
              <view class="quad__cell">
                <text class="quad__k">性别</text>
                <text class="quad__v">{{ sexLabel(profile.sex) }}</text>
              </view>
              <view class="quad__cell">
                <text class="quad__k">年龄</text>
                <text class="quad__v ft-num">{{ profile.age ?? '–' }}</text>
              </view>
              <view class="quad__cell">
                <text class="quad__k">身高</text>
                <text class="quad__v ft-num">{{ profile.heightCm ?? '–' }}<text class="unit">cm</text></text>
              </view>
              <view class="quad__cell">
                <text class="quad__k">活动量</text>
                <text class="quad__v">{{ levelLabel(profile.activityLevel) }}</text>
              </view>
            </view>

            <view v-if="energy?.complete" class="energy">
              <view class="energy__num-row">
                <text class="energy__num ft-num">{{ energy.totalKcal }}</text>
                <text class="energy__unit">大卡 · 今日消耗</text>
              </view>
              <view class="chip-btn" role="button" @click="energySheet.show = true">怎么算的</view>
            </view>
            <view v-else class="link-row" role="button" @click="openSheet">
              <text>还没有体重记录，补一条才能算消耗</text>
              <FtIcon name="chevron-right" :size="18" tone="subtle" />
            </view>
          </template>

          <FtEmpty v-else title="还没填个人档案" sub="填一下性别、生日、身高和活动量，就能算出每天消耗。">
            <FtButton size="sm" class="bodypg__fill" @click="openProfileSheet">去填写</FtButton>
          </FtEmpty>
        </view>

        <!-- 当前体重 -->
        <view class="glass">
          <view class="card-head">
            <text class="card-title">当前体重</text>
            <text v-if="overview?.isLowest" class="pr">阶段最低 · PR</text>
          </view>
          <view v-if="overview?.latest" class="weight">
            <text class="weight__num ft-num">{{ displayWeight(overview.latest.weight) }}</text>
            <text class="weight__unit">{{ weightUnitShort(weightUnit) }}</text>
          </view>
          <text v-else class="muted-line">还没有体重记录</text>
          <view v-if="overview?.latest" class="weight__foot">
            <text class="ft-num">{{ formatDateLabel(overview.latest.date) }} · 最近一次</text>
            <text v-if="overview.weekDiff != null" class="weight__diff ft-num">
              较上周 {{ signed(overview.weekDiff, weightUnit) }} {{ weightUnitShort(weightUnit) }}
            </text>
          </view>
          <!-- 体脂：与体重并列，只记录不参与计算（ADR 0030） -->
          <view v-if="overview?.bodyFat" class="weight__fat">
            <text class="weight__fat-k">体脂</text>
            <text class="weight__fat-v ft-num">{{ fatText(overview.bodyFat.value) }}</text>
            <text class="weight__fat-d ft-num">{{ formatDateLabel(overview.bodyFat.date) }}</text>
          </view>
        </view>

        <!-- 基础围度（最新一次） -->
        <view class="glass">
          <view class="card-head">
            <text class="card-title">基础围度</text>
            <text v-if="overview?.girth" class="card-meta ft-num">{{ formatDateLabel(overview.girth.date) }} 测定</text>
          </view>
          <view v-if="overview?.girth" class="quad">
            <view v-for="p in GIRTH_PARTS" :key="p.key" class="quad__cell">
              <text class="quad__k">{{ p.label }}</text>
              <text class="quad__v quad__v--num ft-num">{{ girthValue(overview.girth[p.key]) }}<text class="unit">cm</text></text>
            </view>
          </view>
          <text v-else class="muted-line">还没有围度记录，「记录今天的数据」里可以一起量</text>
        </view>

        <!-- 体重 / 体脂趋势（分段切换序列，ADR 0030：不做双线） -->
        <view class="glass">
          <view class="card-head">
            <view class="seg seg--sm" role="tablist">
              <view
                class="seg__item"
                :class="{ 'seg__item--on': chartMetric === 'weight' }"
                role="tab"
                :aria-selected="chartMetric === 'weight'"
                @click="chartMetric = 'weight'"
              >
                体重
              </view>
              <view
                class="seg__item"
                :class="{ 'seg__item--on': chartMetric === 'fat' }"
                role="tab"
                :aria-selected="chartMetric === 'fat'"
                @click="chartMetric = 'fat'"
              >
                体脂
              </view>
            </view>
            <text v-if="chartRange" class="card-meta ft-num">{{ chartRange }}</text>
          </view>
          <FtTrendChart
            v-if="chartPoints.length >= 2"
            :key="chartMetric"
            :points="chartPoints"
            :height="180"
            :zero-based="false"
            :format-value="chartFormat"
          />
          <text v-else class="muted-line">{{ chartEmptyText }}</text>
          <text v-if="chartPoints.length >= 2" class="tip">同一天记多次取最小值 · 单指按住看数值 · 双指捏合缩放</text>
        </view>

        <!-- 围度趋势（单独一张图，部位分段切换） -->
        <view class="glass">
          <view class="card-head">
            <text class="card-title">围度趋势</text>
            <text v-if="girthRange" class="card-meta ft-num">{{ girthRange }}</text>
          </view>
          <view class="seg seg--full" role="tablist">
            <view
              v-for="p in GIRTH_PARTS"
              :key="p.key"
              class="seg__item"
              :class="{ 'seg__item--on': girthPart === p.key }"
              role="tab"
              :aria-selected="girthPart === p.key"
              @click="girthPart = p.key"
            >
              {{ p.label }}
            </view>
          </view>
          <FtTrendChart v-if="girthPoints.length >= 2" :points="girthPoints" :height="180" :zero-based="false" />
          <text v-else class="muted-line">这个部位再量一次，就能看到趋势线</text>
          <text v-if="girthPoints.length >= 2" class="tip">同一天量多次取最小值 · 单指按住看数值 · 双指捏合缩放</text>
        </view>

        <!-- 全部记录（体重与围度同一条时间线；点行编辑，长按多选删除） -->
        <view class="glass mlist">
          <view class="card-head mlist__head">
            <text class="card-title">全部记录</text>
            <text class="card-meta">{{ selectMode ? '点选要删的记录' : '点一条编辑 · 长按批量删除' }}</text>
          </view>
          <FtEmpty v-if="records.length === 0" title="还没有记录" sub="点下面「记录今天的数据」记第一条。" />
          <view
            v-for="r in records"
            :key="r.id"
            class="mrow"
            @click="onRowClick(r)"
            @longpress="onRowLongPress(r)"
          >
            <view v-if="selectMode" class="check" :class="{ 'check--on': selected.has(r.id) }">
              <FtIcon v-if="selected.has(r.id)" name="check" :size="13" :stroke="2.4" tone="inverse" />
            </view>
            <view class="mrow__main">
              <view class="mrow__line">
                <text class="mrow__date ft-num">{{ formatDateTimeLabel(r.date) }}</text>
                <text v-if="r.note" class="mrow__note">{{ r.note }}</text>
                <text v-if="r.weight != null" class="mrow__kg ft-num">
                  {{ displayWeight(r.weight) }}<text class="unit">{{ weightUnitShort(weightUnit) }}</text>
                </text>
              </view>
              <view v-if="girthLine(r) || r.diff != null" class="mrow__line">
                <text class="mrow__girth ft-num">{{ girthLine(r) }}</text>
                <text v-if="r.diff != null" class="mrow__diff ft-num">{{ signed(r.diff, weightUnit) }}</text>
              </view>
            </view>
          </view>
          <view v-if="loadingMore" class="mlist__more">加载中…</view>
          <view v-else-if="!hasMore && records.length > 0" class="mlist__more ft-num">共 {{ records.length }} 条</view>
        </view>
      </template>
    </view>

    <!-- 底部：常规＝记录今天（悬浮主按钮）；多选＝操作条（与动作库同一个） -->
    <view v-if="!selectMode" class="bodypg__bottom">
      <FtButton variant="primary" block class="bodypg__record" @click="openSheet">
        <FtIcon name="plus" :size="18" :stroke="2.2" />
        <text>记录今天的数据</text>
      </FtButton>
    </view>
    <view v-else class="selbar">
      <text class="selbar__count ft-num">已选 {{ selectedCount }} 项</text>
      <view class="selbar__all" role="button" @click="toggleAll">{{ allSelected ? '取消全选' : '全选' }}</view>
      <view class="selbar__del" :class="{ 'is-off': selectedCount === 0 }" role="button" @click="doDelete">删除</view>
    </view>

    <!-- 录入 / 编辑弹层 -->
    <FtSheet
      v-if="sheet.show"
      :title="sheet.editId == null ? '记录身体数据' : '编辑记录'"
      :sub="lastHint ? `${lastHint}的数据已预填，没量的项清空即可` : undefined"
      @close="sheet.show = false"
    >
      <scroll-view scroll-y class="sh__scroll" :class="{ 'is-bare': !lastHint }">
        <view class="group">
          <view class="row">
            <text class="row__k">日期</text>
            <picker mode="date" :value="sheet.date" @change="onDateChange">
              <view class="row__picker ft-num">{{ sheet.date }}<FtIcon name="chevron-right" :size="16" tone="subtle" /></view>
            </picker>
          </view>
          <view class="row">
            <text class="row__k">时刻</text>
            <picker mode="time" :value="sheet.time" @change="onTimeChange">
              <view class="row__picker ft-num">{{ sheet.time }}<FtIcon name="chevron-right" :size="16" tone="subtle" /></view>
            </picker>
          </view>
        </view>

        <view class="group">
          <view class="row">
            <text class="row__k">体重</text>
            <view class="units" role="radiogroup" aria-label="体重单位">
              <view
                class="units__item"
                :class="{ 'units__item--on': weightUnit === 'jin' }"
                role="radio"
                :aria-checked="weightUnit === 'jin'"
                @click="unitChange('jin')"
              >
                斤
              </view>
              <view
                class="units__item"
                :class="{ 'units__item--on': weightUnit === 'kg' }"
                role="radio"
                :aria-checked="weightUnit === 'kg'"
                @click="unitChange('kg')"
              >
                千克
              </view>
            </view>
            <input
              v-model="sheet.weight"
              class="row__input ft-num"
              type="digit"
              :step="weightInputStep()"
              :placeholder="weightUnit === 'jin' ? '如 145.0' : '如 72.50'"
              placeholder-class="ph"
            />
          </view>
          <!-- 体脂率：跟体重并列的一项，只记录不参与任何计算（ADR 0030） -->
          <view class="row">
            <view class="row__k-col">
              <text class="row__k">体脂率（%）</text>
              <text class="row__aside">只做记录，不参与计算</text>
            </view>
            <input v-model="sheet.bodyFat" class="row__input ft-num" type="digit" step="0.1" placeholder="如 18.4" placeholder-class="ph" />
          </view>
        </view>

        <view class="group">
          <text class="group__label">围度（cm）</text>
          <view class="girths">
            <view v-for="p in GIRTH_PARTS" :key="p.key" class="girths__cell">
              <text class="girths__k">{{ p.label }}</text>
              <input v-model="sheet[p.key]" class="girths__input ft-num" type="digit" placeholder="–" placeholder-class="ph" />
            </view>
          </view>
        </view>

        <view class="group">
          <view class="row">
            <text class="row__k">备注</text>
            <input
              v-model="sheet.note"
              class="row__input row__input--text"
              placeholder="晨起空腹 / 练后称重…"
              placeholder-class="ph"
              maxlength="64"
            />
          </view>
        </view>
      </scroll-view>
      <FtButton variant="primary" block :disabled="sheet.saving" class="sh__btn" @click="save">保存</FtButton>
    </FtSheet>

    <!-- 个人档案弹层（ADR 0030） -->
    <FtSheet
      v-if="profileSheet.show"
      title="个人档案"
      sub="填一次就行；体重始终取「身体档案」里最新的一条"
      @close="profileSheet.show = false"
    >
      <scroll-view scroll-y class="sh__scroll">
        <view class="group">
          <view class="row">
            <text class="row__k">性别</text>
            <view class="units" role="radiogroup" aria-label="性别">
              <view
                v-for="o in SEX_OPTIONS"
                :key="o.key"
                class="units__item"
                :class="{ 'units__item--on': profileSheet.sex === o.key }"
                role="radio"
                :aria-checked="profileSheet.sex === o.key"
                @click="profileSheet.sex = o.key"
              >
                {{ o.label }}
              </view>
            </view>
          </view>
          <view class="row">
            <text class="row__k">出生日期</text>
            <picker mode="date" :value="profileSheet.birthDate" @change="onBirthChange">
              <view class="row__picker ft-num" :class="{ 'is-empty': !profileSheet.birthDate }">
                {{ profileSheet.birthDate || '选择日期' }}<FtIcon name="chevron-right" :size="16" tone="subtle" />
              </view>
            </picker>
          </view>
          <view class="row">
            <text class="row__k">身高（cm）</text>
            <input v-model="profileSheet.height" class="row__input ft-num" type="digit" step="0.1" placeholder="如 175" placeholder-class="ph" />
          </view>
        </view>

        <view class="group">
          <view class="group__head">
            <text class="group__label">平时活动量</text>
            <text class="row__aside">只算日常，训练另记</text>
          </view>
          <view
            v-for="o in LEVEL_OPTIONS"
            :key="o.key"
            class="opt"
            role="radio"
            :aria-checked="profileSheet.activityLevel === o.key"
            @click="profileSheet.activityLevel = o.key"
          >
            <view class="opt__mid">
              <text class="opt__label">{{ o.label }}</text>
              <text class="opt__desc">{{ o.desc }}</text>
            </view>
            <view class="check" :class="{ 'check--on': profileSheet.activityLevel === o.key }">
              <FtIcon v-if="profileSheet.activityLevel === o.key" name="check" :size="13" :stroke="2.4" tone="inverse" />
            </view>
          </view>
        </view>
      </scroll-view>
      <FtButton variant="primary" block :disabled="profileSheet.saving" class="sh__btn" @click="saveProfileSheet">
        保存
      </FtButton>
    </FtSheet>

    <!-- 「怎么算的」弹层：算式与代入值全部来自服务端（ADR 0029）；与饮食页同一套排版 -->
    <FtSheet v-if="energySheet.show" title="今日消耗怎么算的" @close="energySheet.show = false">
      <scroll-view scroll-y class="sh__scroll is-bare">
        <template v-if="energy?.complete">
          <view class="eq">
            <view class="eq__row">
              <text class="eq__k">基础代谢</text>
              <text class="eq__v ft-num">{{ energy.bmr }} 大卡</text>
            </view>
            <text class="eq__note">
              按 Mifflin-St Jeor：体重 {{ energy.weightKg }} kg · 身高 {{ energy.heightCm }} cm · 年龄
              {{ energy.age }} · {{ sexLabel(energy.sex) }}
            </text>
            <view class="eq__row">
              <text class="eq__k">× 生活活动系数</text>
              <text class="eq__v ft-num">{{ energy.activityFactor }}（{{ levelLabel(energy.activityLevel) }}）</text>
            </view>
            <view class="eq__row">
              <text class="eq__k">= 基础消耗</text>
              <text class="eq__v ft-num">{{ energy.baseKcal }} 大卡</text>
            </view>
            <view class="eq__row">
              <text class="eq__k">+ 今日训练消耗</text>
              <text class="eq__v ft-num">{{ energy.trainingKcal }} 大卡</text>
            </view>
            <view class="eq__row eq__row--sum">
              <text class="eq__k">= 今日总消耗</text>
              <text class="eq__v ft-num">{{ energy.totalKcal }} 大卡</text>
            </view>
          </view>
          <text class="eq__hint">
            生活活动系数只算日常活动（工作、走动），专门训练不在里面——所以训练消耗是单独加上去的，不会重复计算。
          </text>
          <text class="eq__hint">体重取身体档案里最新一条{{ weightDateHint }}，换体重后数字会跟着变。</text>
        </template>
      </scroll-view>
      <FtButton block class="sh__btn" @click="energySheet.show = false">知道了</FtButton>
    </FtSheet>
  </view>
</template>

<style lang="scss" scoped>
.bodypg__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  /* 底下常驻一颗悬浮按钮（或多选操作条），正文末尾让出它的高度 */
  padding: 28rpx 40rpx calc(200rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

.hint {
  padding: 80rpx 0;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.ph {
  color: $app-ink-subtle;
  font-weight: $weight-regular;
}

.unit {
  /* ⚠️ 不靠文本里的空格留间距：模板里的前导空白会被折叠，数字与单位会贴在一起 */
  margin-left: 6rpx;
  font-size: $text-sm;
  font-weight: $weight-bold;
  color: $app-ink-muted;
}

/* 页头右侧的胶囊按钮 */
.pill {
  @include glass-pill;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.pill:active {
  opacity: 0.7;
}

/* ── 卡头：标题 ＋ 右侧小字或小胶囊 ── */
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding-bottom: 20rpx;
}

.card-title {
  @include card-title;
}

.card-meta {
  min-width: 0;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.chip-btn {
  flex-shrink: 0;
  padding: 10rpx 24rpx;
  border-radius: $radius-pill;
  background: $app-surface-2;
  font-size: $text-sm;
  color: $app-ink-2;
}

.chip-btn:active {
  opacity: 0.6;
}

.muted-line {
  display: block;
  font-size: $text-base;
  line-height: 1.6;
  color: $app-ink-muted;
}

.tip {
  display: block;
  padding-top: 12rpx;
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.bodypg .bodypg__fill {
  align-self: flex-start;
  margin-top: 8rpx;
}

/* ── 四格（个人档案、围度） ── */
.quad {
  display: flex;
}

.quad__cell {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.quad__cell + .quad__cell {
  padding-left: 20rpx;
  border-left: 1rpx solid $app-line-soft;
}

.quad__k {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.quad__v {
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.quad__v--num {
  font-size: $text-xl;
  font-weight: $weight-heavy;
}

/* 今日消耗 */
.energy {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid $app-line-soft;
}

.energy__num-row {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}

.energy__num {
  font-size: $text-2xl;
  font-weight: $weight-heavy;
  line-height: 1.1;
  color: $app-ink;
}

.energy__unit {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.link-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid $app-line-soft;
  font-size: $text-base;
  color: $app-ink-2;
}

.link-row:active {
  opacity: 0.6;
}

/* PR：浅橙底 ＋ 深橙字（PR 是「动作的结果」，归橙色） */
.pr {
  flex-shrink: 0;
  padding: 4rpx 16rpx;
  border-radius: $radius-pill;
  background: $accent-soft;
  font-size: $text-xs;
  font-weight: $weight-bold;
  color: $accent-strong;
}

/* ── 当前体重 ── */
.weight {
  display: flex;
  align-items: baseline;
  gap: 10rpx;
}

.weight__num {
  font-size: $text-display;
  font-weight: $weight-heavy;
  line-height: 1.05;
  letter-spacing: -0.02em;
  color: $app-ink;
}

.weight__unit {
  font-size: $text-lg;
  color: $app-ink-muted;
}

.weight__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding-top: 12rpx;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.weight__diff {
  color: $app-ink-2;
}

.weight__fat {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid $app-line-soft;
}

.weight__fat-k {
  font-size: $text-base;
  color: $app-ink-muted;
}

.weight__fat-v {
  font-size: $text-lg;
  color: $app-ink;
}

.weight__fat-d {
  margin-left: auto;
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* ── 分段控件 ── */
.seg {
  @include seg-track;
  display: flex;
}

.seg--full {
  margin-bottom: 20rpx;
}

.seg__item {
  @include seg-item(10rpx 28rpx);
  text-align: center;
}

.seg--full .seg__item {
  flex: 1;
  padding: 10rpx 0;
}

.seg__item--on {
  @include seg-item-on;
}

/* ── 全部记录 ── */
.mlist {
  padding: 28rpx 32rpx 8rpx;
}

.mlist__head {
  padding: 0 4rpx 12rpx;
}

.mrow {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 22rpx 4rpx;
  border-top: 1rpx solid $app-line-soft;
}

.mrow:active {
  opacity: 0.6;
}

.mrow__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.mrow__line {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  min-width: 0;
}

.mrow__date {
  flex-shrink: 0;
  font-size: $text-base;
  color: $app-ink;
}

.mrow__note {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.mrow__kg {
  flex-shrink: 0;
  margin-left: auto;
  font-size: $text-md;
  color: $app-ink;
}

.mrow__girth {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.mrow__diff {
  flex-shrink: 0;
  margin-left: auto;
  font-size: $text-sm;
  color: $app-ink-2;
}

.mlist__more {
  padding: 24rpx 0;
  border-top: 1rpx solid $app-line-soft;
  text-align: center;
  font-size: $text-sm;
  color: $app-ink-muted;
}

/* 勾选圈（多选、活动量单选共用；与动作库同一个） */
.check {
  width: 40rpx;
  height: 40rpx;
  flex-shrink: 0;
  box-sizing: border-box;
  border-radius: 50%;
  border: 2rpx solid $app-line-dash;
  background: $app-surface;
  display: flex;
  align-items: center;
  justify-content: center;
}

.check--on {
  background: $app-ink;
  border-color: $app-ink;
}

/* ── 底部 ── */
.bodypg__bottom {
  position: fixed;
  left: $tabbar-side;
  right: $tabbar-side;
  bottom: calc(#{$tabbar-gap} + env(safe-area-inset-bottom));
}

.bodypg__bottom .bodypg__record {
  height: 104rpx;
  font-size: 30rpx;
}

.selbar {
  @include selbar;
}

/* ── 弹层（壳是 FtSheet，分组是白卡） ── */
.sh__scroll {
  flex: 1;
  min-height: 0;
  max-height: 60vh;
}

/* 没有副标题时，标题下自己让出副标题那一段的距离 */
.sh__scroll.is-bare {
  margin-top: 28rpx;
}

.bodypg .sh__btn {
  flex-shrink: 0;
  margin-top: 28rpx;
}

.group {
  @include sheet-card;
  padding: 4rpx 28rpx;
}

.group + .group {
  margin-top: 20rpx;
}

.group__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 20rpx 0 4rpx;
}

.group__label {
  display: block;
  padding: 20rpx 0 4rpx;
  font-size: $text-base;
  color: $app-ink-muted;
}

.group__head .group__label {
  padding: 0;
}

.row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-height: 92rpx;
}

.row + .row {
  border-top: 1rpx solid $app-line-soft;
}

.row__k {
  flex-shrink: 0;
  font-size: $text-base;
  color: $app-ink-muted;
}

.row__k-col {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.row__aside {
  font-size: $text-xs;
  color: $app-ink-subtle;
}

.row__input {
  flex: 1;
  min-width: 0;
  text-align: right;
  font-size: $text-lg;
  color: $app-ink;
}

.row__input--text {
  font-size: $text-md;
}

.row__picker {
  display: flex;
  align-items: center;
  gap: 4rpx;
  font-size: $text-md;
  color: $app-ink;
}

.row picker {
  margin-left: auto;
}

.row__picker.is-empty {
  color: $app-ink-subtle;
}

/* 小分段（体重单位、性别）：选中＝深字 ＋ 浅胶囊底 */
.units {
  @include seg-track-outline;
  flex-shrink: 0;
}

.units__item {
  padding: 6rpx 20rpx;
  border-radius: $radius-pill;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.units__item--on {
  @include seg-item-on;
}

/* 性别那一行没有输入框，小分段靠右 */
.row .units:last-child {
  margin-left: auto;
}

/* 围度：2×2 小输入格 */
.girths {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  padding: 12rpx 0 24rpx;
}

.girths__cell {
  width: calc((100% - 16rpx) / 2);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 12rpx;
  height: 80rpx;
  padding: 0 20rpx;
  border-radius: $radius-sm;
  background: $app-surface-2;
}

.girths__k {
  flex-shrink: 0;
  font-size: $text-sm;
  color: $app-ink-muted;
}

.girths__input {
  flex: 1;
  min-width: 0;
  text-align: right;
  font-size: $text-md;
  color: $app-ink;
}

/* 活动量：单选行 */
.opt {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 22rpx 0;
  border-top: 1rpx solid $app-line-soft;
}

.group__head + .opt {
  border-top: none;
}

.opt:active {
  opacity: 0.6;
}

.opt__mid {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.opt__label {
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.opt__desc {
  font-size: $text-sm;
  line-height: 1.5;
  color: $app-ink-muted;
}

/* 「怎么算的」：与饮食页同一套排版 */
.eq {
  @include sheet-card;
  padding: 8rpx 28rpx;
}

.eq__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 16rpx 0;
}

.eq__row + .eq__row,
.eq__note + .eq__row {
  border-top: 1rpx solid $app-line-soft;
}

/* 「＝」那一行是小计：上面压一条深线 */
.eq__row.eq__row--sum {
  border-top: 2rpx solid $app-ink-2;
}

.eq__k {
  font-size: $text-base;
  color: $app-ink-muted;
}

.eq__v {
  font-size: $text-md;
  color: $app-ink;
}

.eq__note {
  display: block;
  padding-bottom: 12rpx;
  font-size: $text-xs;
  line-height: 1.6;
  color: $app-ink-muted;
}

.eq__hint {
  display: block;
  padding-top: 16rpx;
  font-size: $text-sm;
  line-height: 1.7;
  color: $app-ink-muted;
}

@media (prefers-reduced-motion: reduce) {
  .seg__item {
    transition: none;
  }
}
</style>
