<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { api } from '@/api'
import { timeText, type FoodDetail } from '@/types'
import { MAIN_FIELDS, OPTIONAL_GROUPS } from '@shared/food-fields'

// 新建 / 编辑预置食物（ADR 0048）：能量、蛋白质、脂肪、碳水、单位、分类必填，钠可以不填；
// 其余字段按七类分组折叠。字段清单和 App 共用 frontend/src/utils/food-fields.ts。

const route = useRoute()
const router = useRouter()
const id = computed(() => (route.params.id ? Number(route.params.id) : null))
const isNew = computed(() => id.value == null)

const REQUIRED = ['kcal', 'protein', 'fat', 'carb']
const NUM_KEYS = [...MAIN_FIELDS.map((f) => f.key), ...OPTIONAL_GROUPS.flatMap((g) => g.fields.map((f) => f.key)), 'gi']

const formRef = ref<FormInstance>()
// eslint-disable-next-line @typescript-eslint/no-explicit-any -- 表单字段由字段表动态生成
const form = reactive<Record<string, any>>({ name: '', unit: 'g', category: '', portionLabel: '', portionGrams: null })
for (const k of NUM_KEYS) form[k] = null
const archived = ref(false)
const updatedAt = ref<string | undefined>()
const loading = ref(false)
const saving = ref(false)
const categories = ref<string[]>([])
const nameTaken = ref(false)
const openGroups = ref<string[]>([])

const rules: FormRules = {
  name: [{ required: true, whitespace: true, message: '名称不能为空', trigger: 'blur' }],
  category: [{ required: true, message: '分类不能为空', trigger: 'change' }],
  ...Object.fromEntries(REQUIRED.map((k) => [k, [{ required: true, message: '必填', trigger: 'blur' }]]))
}

onMounted(async () => {
  void api.get<string[]>('/admin/foods/categories').then((c) => (categories.value = c)).catch(() => {})
  if (isNew.value) return
  loading.value = true
  try {
    const f = await api.get<FoodDetail>(`/admin/foods/${id.value}`)
    form.name = f.name
    form.unit = f.unit || 'g'
    form.category = f.category ?? ''
    form.portionLabel = f.portionLabel ?? ''
    form.portionGrams = f.portionGrams ?? null
    for (const k of NUM_KEYS) form[k] = f[k] == null ? null : Number(f[k])
    archived.value = f.archived === true
    updatedAt.value = f.updatedAt
    // 有值的分组默认展开
    openGroups.value = OPTIONAL_GROUPS.filter((g) => g.fields.some((fd) => form[fd.key] != null)).map((g) => g.title)
  } catch {
    // api 层已经提示
  } finally {
    loading.value = false
  }
})

/** 同名只提示，不拦截：国标数据本身就有重名条目。 */
async function checkName() {
  const n = String(form.name ?? '').trim()
  if (!n) {
    nameTaken.value = false
    return
  }
  try {
    nameTaken.value = await api.get<boolean>('/admin/foods/name-taken', { name: n, excludeId: id.value })
  } catch {
    nameTaken.value = false
  }
}

function payload() {
  const body: Record<string, unknown> = {
    name: String(form.name ?? '').trim(),
    unit: form.unit,
    category: String(form.category ?? '').trim()
  }
  for (const k of NUM_KEYS) body[k] = form[k] === '' || form[k] == null ? null : Number(form[k])
  const label = String(form.portionLabel ?? '').trim()
  const grams = form.portionGrams == null || form.portionGrams === '' ? null : Number(form.portionGrams)
  // 常用份成对：只填了一半就当没填
  body.portionLabel = label && grams != null ? label : null
  body.portionGrams = label && grams != null ? grams : null
  return body
}

async function save() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    if (isNew.value) {
      await api.post<FoodDetail>('/admin/foods', payload())
      ElMessage.success('已新增')
    } else {
      await api.put<FoodDetail>(`/admin/foods/${id.value}`, payload())
      ElMessage.success('已保存')
    }
    void router.push({ name: 'foods' })
  } catch {
    // api 层已经提示
  } finally {
    saving.value = false
  }
}

async function toggleArchived() {
  if (id.value == null) return
  const next = !archived.value
  const verb = next ? '下架' : '重新上架'
  try {
    await ElMessageBox.confirm(
      next
        ? '没用过它的人再也搜不到；用过的人仍能看到，带「已下架」标签，可以自己删掉。已有记录和组合不受影响。'
        : '所有人都能再看到它，用户之前对它的删除会作废。',
      `${verb}「${form.name}」？`,
      { confirmButtonText: verb, cancelButtonText: '取消', type: next ? 'warning' : 'info' }
    )
  } catch {
    return
  }
  try {
    await api.post(`/admin/foods/${next ? 'archive' : 'unarchive'}`, { ids: [id.value] })
    archived.value = next
    ElMessage.success(`已${verb}`)
  } catch {
    // api 层已经提示
  }
}

const unitText = computed(() => (form.unit === 'ml' ? '100ml' : '100g'))
</script>

<template>
  <div class="page">
    <div class="page__head">
      <div>
        <h2 class="page__title">
          {{ isNew ? '新增预置食物' : '编辑预置食物' }}
          <el-tag v-if="archived" type="info" size="small" style="margin-left: 8px">已下架</el-tag>
        </h2>
        <p v-if="!isNew" class="page__hint">
          最近更新 {{ timeText(updatedAt) }}。改动对所有人生效：组合按新数据重算，过去的饮食记录不变（有快照）。
        </p>
      </div>
      <div>
        <el-button @click="router.push({ name: 'foods' })">返回列表</el-button>
        <el-button v-if="!isNew" @click="toggleArchived">{{ archived ? '重新上架' : '下架' }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </div>

    <el-form ref="formRef" v-loading="loading" :model="form" :rules="rules" label-width="150px" class="card form">
      <h3 class="form__group">基本信息</h3>
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" maxlength="64" show-word-limit @blur="checkName" />
        <div class="form__tip">
          名字末尾加 <code>*</code> 表示营养数据来自外部补全、可信度低一档（App 里会注明）。
          <span v-if="nameTaken" class="form__warn">已有同名的预置食物，确认不是重复录入。</span>
        </div>
      </el-form-item>
      <el-form-item label="单位" prop="unit">
        <el-radio-group v-model="form.unit">
          <el-radio-button value="g">每 100g</el-radio-button>
          <el-radio-button value="ml">每 100ml</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="分类" prop="category">
        <el-select
          v-model="form.category"
          filterable
          allow-create
          default-first-option
          placeholder="选一个已有分类，或直接输入新分类"
          style="width: 360px"
        >
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
      </el-form-item>

      <h3 class="form__group">主要营养（每 {{ unitText }}）</h3>
      <el-form-item v-for="f in MAIN_FIELDS" :key="f.key" :label="f.label" :prop="f.key">
        <el-input-number v-model="form[f.key]" :min="0" :precision="2" :controls="false" style="width: 200px" />
        <span v-if="f.key === 'sodium'" class="form__tip form__tip--inline">预置可以不填（国标数据里有不少没测钠的）</span>
      </el-form-item>
      <el-form-item label="升糖指数 GI">
        <el-input-number v-model="form.gi" :min="0" :precision="1" :controls="false" style="width: 200px" />
        <span class="form__tip form__tip--inline">葡萄糖＝100 基准，选填</span>
      </el-form-item>

      <h3 class="form__group">常用份（选填，成对）</h3>
      <el-form-item label="份名">
        <el-input v-model="form.portionLabel" placeholder="如：1 个" maxlength="16" style="width: 200px" />
      </el-form-item>
      <el-form-item :label="`克重（${form.unit === 'ml' ? 'ml' : 'g'}）`">
        <el-input-number v-model="form.portionGrams" :min="0" :precision="2" :controls="false" style="width: 200px" />
      </el-form-item>

      <h3 class="form__group">其余营养（选填）</h3>
      <el-collapse v-model="openGroups">
        <el-collapse-item v-for="g in OPTIONAL_GROUPS" :key="g.title" :name="g.title" :title="g.title">
          <el-form-item v-for="f in g.fields" :key="f.key" :label="f.label">
            <el-input-number v-model="form[f.key]" :min="0" :precision="2" :controls="false" style="width: 200px" />
          </el-form-item>
        </el-collapse-item>
      </el-collapse>
    </el-form>
  </div>
</template>

<style scoped>
.form {
  max-width: 820px;
}

.form__group {
  margin: 8px 0 16px;
  font-size: 15px;
  font-weight: 600;
}

.form__group:not(:first-child) {
  margin-top: 28px;
}

.form__tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.form__tip--inline {
  width: auto;
  margin: 0 0 0 12px;
}

.form__warn {
  display: block;
  color: var(--el-color-warning);
}
</style>
