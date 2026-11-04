<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'
import { METRICS, MUSCLES, metricLabel, muscleLabel, timeText, type ExerciseRow } from '@/types'

// 预置动作（ADR 0048）：增改、下架与上架；不做导入导出（几十条，手改就够）。
// 有人练过或排进过计划的动作锁定记录口径——改了会让历史组次的含义变掉（ADR 0025）。

const filters = reactive({ q: '', muscle: '', status: 'all' })
const rows = ref<ExerciseRow[]>([])
const loading = ref(false)
const selected = ref<ExerciseRow[]>([])

onMounted(() => void load())

async function load() {
  loading.value = true
  try {
    rows.value = await api.get<ExerciseRow[]>('/admin/exercises', {
      q: filters.q.trim(),
      muscle: filters.muscle,
      status: filters.status
    })
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
      <el-button type="primary" @click="openCreate">新增预置动作</el-button>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model="filters.q" placeholder="搜名称" clearable style="width: 220px" @change="load" />
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
        :data="rows"
        row-key="id"
        stripe
        style="width: 100%"
        @selection-change="(v: ExerciseRow[]) => (selected = v)"
        @row-click="openEdit"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column prop="sortOrder" label="排序" width="70" align="right" />
        <el-table-column prop="name" label="名称" min-width="180" />
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
    </div>

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
