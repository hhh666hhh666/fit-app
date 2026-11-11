<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'
import { METRICS, MUSCLES, metricLabel, muscleLabel, timeText, type ExerciseRow, type ImportPreview } from '@/types'

// 预置动作（ADR 0048）：增改、下架与上架；导入导出（ADR 0055）——开源动作数据集一千多条就从这里进库，
// 文件由 scripts/exercise-dataset/build.py 生成。动图文件不走这里，另由 sync-media.sh 放到服务器。
// 有人练过或排进过计划的动作锁定记录口径——改了会让历史组次的含义变掉（ADR 0025）。

const filters = reactive({ q: '', muscle: '', status: 'all' })
const rows = ref<ExerciseRow[]>([])
const loading = ref(false)
const selected = ref<ExerciseRow[]>([])

// 一千多行一次给全，前端分页，免得表格一次渲染太多
const PAGE_SIZE = 100
const page = ref(1)
const pageRows = computed(() => rows.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))

onMounted(() => void load())

async function load() {
  loading.value = true
  try {
    rows.value = await api.get<ExerciseRow[]>('/admin/exercises', {
      q: filters.q.trim(),
      muscle: filters.muscle,
      status: filters.status
    })
    page.value = 1
  } catch {
    // api 层已经提示
  } finally {
    loading.value = false
  }
}

async function setArchived(archived: boolean) {
  const ids = selected.value.map((r) => r.id)
  if (!ids.length) return
  const verb = archived ? '下架' : '上架'
  try {
    await ElMessageBox.confirm(
      archived
        ? '下架后，没练过、没排进计划的人看不到它们；用过的人仍能看到，带「已下架」标签。历史训练和计划不受影响。'
        : '重新上架后所有人都能看到，用户之前对它们的删除会作废。',
      `${verb}选中的 ${ids.length} 个动作？`,
      { confirmButtonText: verb, cancelButtonText: '取消', type: archived ? 'warning' : 'info' }
    )
  } catch {
    return
  }
  try {
    await api.post(`/admin/exercises/${archived ? 'archive' : 'unarchive'}`, { ids })
    ElMessage.success(`已${verb} ${ids.length} 个`)
    void load()
  } catch {
    // api 层已经提示
  }
}

// ── 导出 ──
const exporting = ref(false)

async function doExport() {
  exporting.value = true
  try {
    const data = await api.get<unknown[]>('/admin/exercises/export')
    const blob = new Blob([JSON.stringify(data, null, 1)], { type: 'application/json' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `preset-exercises-${new Date().toISOString().slice(0, 10)}.json`
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success(`已导出 ${data.length} 条`)
  } catch {
    // api 层已经提示
  } finally {
    exporting.value = false
  }
}

// ── 导入：选文件 → 预检 → 确认（文件近 1 MB，不往文本框里塞）──
const imp = reactive({ show: false, text: '', file: '', preview: null as ImportPreview | null, busy: false })

function openImport() {
  Object.assign(imp, { show: true, text: '', file: '', preview: null })
}

function onFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    imp.text = String(reader.result ?? '')
    imp.file = `${file.name}（${Math.round(file.size / 1024)} KB）`
    imp.preview = null
    void previewImport()
  }
  reader.readAsText(file, 'utf-8')
}

async function previewImport() {
  if (!imp.text.trim()) {
    ElMessage.warning('先选一个 JSON 文件')
    return
  }
  imp.busy = true
  try {
    imp.preview = await api.post<ImportPreview>('/admin/exercises/import/preview', { text: imp.text })
  } catch {
    imp.preview = null
  } finally {
    imp.busy = false
  }
}

async function commitImport() {
  const p = imp.preview
  if (!p || p.create + p.update === 0) return
  imp.busy = true
  try {
    const r = await api.post<ImportPreview>('/admin/exercises/import', { text: imp.text })
    ElMessage.success(`已新增 ${r.create} 条、更新 ${r.update} 条`)
    imp.show = false
    void load()
  } catch {
    // api 层已经提示；整批已回滚
  } finally {
    imp.busy = false
  }
}

// ── 新增 / 编辑弹窗 ──
const dlg = reactive({
  show: false,
  id: null as number | null,
  name: '',
  muscle: 'chest',
  metric: 'weight_reps',
  sortOrder: null as number | null,
  used: false,
  saving: false
})

function openCreate() {
  Object.assign(dlg, { show: true, id: null, name: '', muscle: filters.muscle || 'chest', metric: 'weight_reps', sortOrder: null, used: false })
}

function openEdit(r: ExerciseRow) {
  Object.assign(dlg, { show: true, id: r.id, name: r.name, muscle: r.muscle, metric: r.metric, sortOrder: r.sortOrder, used: r.used })
}

const dlgTitle = computed(() => (dlg.id == null ? '新增预置动作' : '编辑预置动作'))

async function save() {
  const name = dlg.name.trim()
  if (!name) {
    ElMessage.warning('动作名不能为空')
    return
  }
  dlg.saving = true
  const body = { name, muscle: dlg.muscle, metric: dlg.metric, sortOrder: dlg.sortOrder ?? undefined }
  try {
    if (dlg.id == null) await api.post('/admin/exercises', body)
    else await api.put(`/admin/exercises/${dlg.id}`, body)
    ElMessage.success('已保存')
    dlg.show = false
    void load()
  } catch {
    // api 层已经提示
  } finally {
    dlg.saving = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page__head">
      <div>
        <h2 class="page__title">预置动作</h2>
        <p class="page__hint">按排序值从小到大排在 App 的动作库里。</p>
      </div>
      <div>
        <el-button @click="openImport">导入</el-button>
        <el-button :loading="exporting" @click="doExport">导出</el-button>
        <el-button type="primary" @click="openCreate">新增预置动作</el-button>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model="filters.q" placeholder="搜名称（中英文）" clearable style="width: 220px" @change="load" />
        <el-select v-model="filters.muscle" placeholder="全部肌群" clearable style="width: 140px" @change="load">
          <el-option v-for="m in MUSCLES" :key="m.code" :label="m.label" :value="m.code" />
        </el-select>
        <el-radio-group v-model="filters.status" @change="load">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="listed">上架</el-radio-button>
          <el-radio-button value="archived">已下架</el-radio-button>
        </el-radio-group>
        <span class="toolbar__spacer" />
        <el-button :disabled="!selected.length" @click="setArchived(true)">下架</el-button>
        <el-button :disabled="!selected.length" @click="setArchived(false)">上架</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="pageRows"
        row-key="id"
        stripe
        style="width: 100%"
        @selection-change="(v: ExerciseRow[]) => (selected = v)"
        @row-click="openEdit"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column prop="sortOrder" label="排序" width="70" align="right" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="nameEn" label="英文名" min-width="200" show-overflow-tooltip />
        <el-table-column label="动图" width="70">
          <template #default="{ row }">{{ row.media ? '有' : '—' }}</template>
        </el-table-column>
        <el-table-column label="肌群" width="90">
          <template #default="{ row }">{{ muscleLabel(row.muscle) }}</template>
        </el-table-column>
        <el-table-column label="记录口径" width="110">
          <template #default="{ row }">{{ metricLabel(row.metric) }}</template>
        </el-table-column>
        <el-table-column label="被引用" width="90">
          <template #default="{ row }">{{ row.used ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="86">
          <template #default="{ row }">
            <el-tag v-if="row.archived" type="info" size="small">已下架</el-tag>
            <el-tag v-else type="success" size="small">上架</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }"><span class="num">{{ timeText(row.updatedAt) }}</span></template>
        </el-table-column>
      </el-table>
      <div v-if="rows.length > PAGE_SIZE" class="pager">
        <span class="page__hint">共 {{ rows.length }} 条</span>
        <el-pagination v-model:current-page="page" :page-size="PAGE_SIZE" :total="rows.length" layout="prev, pager, next" />
      </div>
    </div>

    <el-dialog v-model="imp.show" title="导入预置动作" width="720px">
      <p class="page__hint">
        格式和导出的文件一样。带 <code>id</code> 的按 id 更新；带 <code>sourceId</code>（数据集编号）且已导入过的，更新那一条；
        和没有编号的老预置同名的，只给它补上资料，名字和肌群不动；其余新增，排到最后。记录口径和上下架状态都不动。
        先预检，确认后一次性写入，中途出错整批回滚。
      </p>
      <div class="toolbar" style="margin-top: 12px">
        <input type="file" accept=".json,application/json" @change="onFile" />
        <span v-if="imp.file" class="page__hint">{{ imp.file }}</span>
      </div>
      <div v-if="imp.preview" class="preview">
        <p>
          新增 <b class="num">{{ imp.preview.create }}</b> 条，更新 <b class="num">{{ imp.preview.update }}</b> 条，
          无效 <b class="num">{{ imp.preview.invalid.length }}</b> 条（会跳过）。
        </p>
        <el-table v-if="imp.preview.invalid.length" :data="imp.preview.invalid" size="small" max-height="220">
          <el-table-column prop="index" label="第几条" width="80" />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="reason" label="原因" min-width="220" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="imp.show = false">取消</el-button>
        <el-button :loading="imp.busy" @click="previewImport">预检</el-button>
        <el-button
          type="primary"
          :loading="imp.busy"
          :disabled="!imp.preview || imp.preview.create + imp.preview.update === 0"
          @click="commitImport"
        >
          确认导入
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dlg.show" :title="dlgTitle" width="480px">
      <el-form label-width="90px" @submit.prevent="save">
        <el-form-item label="名称" required>
          <el-input v-model="dlg.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="肌群" required>
          <el-select v-model="dlg.muscle" style="width: 200px">
            <el-option v-for="m in MUSCLES" :key="m.code" :label="m.label" :value="m.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="记录口径">
          <el-radio-group v-model="dlg.metric" :disabled="dlg.used">
            <el-radio-button v-for="m in METRICS" :key="m.code" :value="m.code">{{ m.label }}</el-radio-button>
          </el-radio-group>
          <div v-if="dlg.used" class="tip">已经有人练过或排进计划，记录口径不能再改。</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dlg.sortOrder" :min="0" :controls="false" placeholder="留空排到最后" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.show = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}

.preview {
  margin-top: 12px;
}

.tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
