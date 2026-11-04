<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'
import { timeText, type FoodRow, type ImportPreview, type PageResult } from '@/types'

// 预置食物列表（ADR 0048）：搜名称、按分类和状态筛、每页 50 条、批量下架或上架、导入导出。
// 没有删除：预置的删除就是下架。

const router = useRouter()
const PAGE_SIZE = 50

const filters = reactive({ q: '', category: '', status: 'all', sort: 'name' })
const page = ref(1)
const total = ref(0)
const rows = ref<FoodRow[]>([])
const loading = ref(false)
const categories = ref<string[]>([])
const selected = ref<FoodRow[]>([])

onMounted(() => {
  void load()
  void api.get<string[]>('/admin/foods/categories').then((c) => (categories.value = c)).catch(() => {})
})

async function load() {
  loading.value = true
  try {
    const r = await api.get<PageResult<FoodRow>>('/admin/foods', {
      q: filters.q.trim(),
      category: filters.category,
      status: filters.status,
      sort: filters.sort,
      page: page.value,
      size: PAGE_SIZE
    })
    rows.value = r.list
    total.value = r.total
  } catch {
    // api 层已经提示
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void load()
}

function onPage(p: number) {
  page.value = p
  void load()
}

async function setArchived(archived: boolean) {
  const ids = selected.value.map((r) => r.id)
  if (!ids.length) return
  const verb = archived ? '下架' : '上架'
  const tip = archived
    ? '下架后，没用过它们的人再也搜不到；用过的人仍能看到，带「已下架」标签，可以自己删掉。'
    : '重新上架后所有人都能看到，用户之前对它们的删除会作废（回收站里那条也一并清掉）。'
  try {
    await ElMessageBox.confirm(tip, `${verb}选中的 ${ids.length} 条？`, {
      confirmButtonText: verb,
      cancelButtonText: '取消',
      type: archived ? 'warning' : 'info'
    })
  } catch {
    return
  }
  try {
    await api.post(`/admin/foods/${archived ? 'archive' : 'unarchive'}`, { ids })
    ElMessage.success(`已${verb} ${ids.length} 条`)
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
    const data = await api.get<unknown[]>('/admin/foods/export')
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `preset-foods-${new Date().toISOString().slice(0, 10)}.json`
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success(`已导出 ${data.length} 条`)
  } catch {
    // api 层已经提示
  } finally {
    exporting.value = false
  }
}

// ── 导入：粘贴或选文件 → 预检 → 确认 ──
const imp = reactive({ show: false, text: '', preview: null as ImportPreview | null, busy: false })

function openImport() {
  imp.show = true
  imp.text = ''
  imp.preview = null
}

function onFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    imp.text = String(reader.result ?? '')
    imp.preview = null
  }
  reader.readAsText(file, 'utf-8')
}

async function previewImport() {
  if (!imp.text.trim()) {
    ElMessage.warning('先粘贴或选一个 JSON 文件')
    return
  }
  imp.busy = true
  try {
    imp.preview = await api.post<ImportPreview>('/admin/foods/import/preview', { text: imp.text })
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
    const r = await api.post<ImportPreview>('/admin/foods/import', { text: imp.text })
    ElMessage.success(`已新增 ${r.create} 条、更新 ${r.update} 条`)
    imp.show = false
    void load()
  } catch {
    // api 层已经提示；整批已回滚
  } finally {
    imp.busy = false
  }
}

function openRow(r: FoodRow) {
  void router.push({ name: 'food-edit', params: { id: r.id } })
}

const per = (r: { unit?: string }) => `每 100${r.unit}`
const num = (v: number | null) => (v == null ? '—' : String(Number(v)))
</script>

<template>
  <div class="page">
    <div class="page__head">
      <div>
        <h2 class="page__title">预置食物</h2>
        <p class="page__hint">数据库就是预置数据的唯一来源（ADR 0049）：批量修改请先导出，改好再导回来。</p>
      </div>
      <el-button type="primary" @click="router.push({ name: 'food-new' })">新增预置食物</el-button>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model="filters.q" placeholder="搜名称" clearable style="width: 220px" @change="search" />
        <el-select v-model="filters.category" placeholder="全部分类" clearable filterable style="width: 220px" @change="search">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
        <el-radio-group v-model="filters.status" @change="search">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="listed">上架</el-radio-button>
          <el-radio-button value="archived">已下架</el-radio-button>
        </el-radio-group>
        <el-select v-model="filters.sort" style="width: 140px" @change="search">
          <el-option label="按名称" value="name" />
          <el-option label="最近更新在前" value="updated" />
        </el-select>
        <span class="toolbar__spacer" />
        <el-button :disabled="!selected.length" @click="setArchived(true)">下架</el-button>
        <el-button :disabled="!selected.length" @click="setArchived(false)">上架</el-button>
        <el-button @click="openImport">导入</el-button>
        <el-button :loading="exporting" @click="doExport">导出</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        row-key="id"
        stripe
        style="width: 100%"
        @selection-change="(v: FoodRow[]) => (selected = v)"
        @row-click="openRow"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" min-width="170" show-overflow-tooltip />
        <el-table-column label="单位" width="90">
          <template #default="{ row }">{{ per(row) }}</template>
        </el-table-column>
        <el-table-column label="能量 kcal" width="96" align="right">
          <template #default="{ row }"><span class="num">{{ num(row.kcal) }}</span></template>
        </el-table-column>
        <el-table-column label="蛋白质 g" width="90" align="right">
          <template #default="{ row }"><span class="num">{{ num(row.protein) }}</span></template>
        </el-table-column>
        <el-table-column label="脂肪 g" width="84" align="right">
          <template #default="{ row }"><span class="num">{{ num(row.fat) }}</span></template>
        </el-table-column>
        <el-table-column label="碳水 g" width="84" align="right">
          <template #default="{ row }"><span class="num">{{ num(row.carb) }}</span></template>
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

      <div class="pager">
        <span class="page__hint num">共 {{ total }} 条</span>
        <el-pagination
          layout="prev, pager, next, jumper"
          :page-size="PAGE_SIZE"
          :total="total"
          :current-page="page"
          @current-change="onPage"
        />
      </div>
    </div>

    <el-dialog v-model="imp.show" title="导入预置食物" width="720px">
      <p class="page__hint">
        格式和导出的文件一样。带 <code>id</code> 的按 id 更新，不带的新增；id 找不到的跳过。导入不会改上下架状态。
        先预检，确认后一次性写入，中途出错整批回滚。
      </p>
      <div class="toolbar" style="margin-top: 12px">
        <input type="file" accept=".json,application/json" @change="onFile" />
      </div>
      <el-input
        v-model="imp.text"
        type="textarea"
        :rows="10"
        placeholder='[{"name": "燕麦片", "category": "谷类及制品-其他", "kcal": 377, "protein": 15, "fat": 6.7, "carb": 66.9}]'
        @input="imp.preview = null"
      />
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

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
