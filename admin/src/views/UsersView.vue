<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '@/api'
import { timeText, type UserRow } from '@/types'

// 用户列表（只读，ADR 0048）：注册口子开着、没有风控，这是发现陌生账号最便宜的办法。
// 禁用和删除这次不做（docs/todo/2026-11-04-TODO-管理端后续.md）。

const rows = ref<UserRow[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    rows.value = await api.get<UserRow[]>('/admin/users')
  } catch {
    // api 层已经提示
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <div class="page__head">
      <div>
        <h2 class="page__title">用户</h2>
        <p class="page__hint">只读。最近记录取训练开始时间和饮食条目创建时间里最晚的一个。</p>
      </div>
    </div>
    <div class="card">
      <el-table v-loading="loading" :data="rows" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="160" />
        <el-table-column label="身份" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.admin" size="small">管理员</el-tag>
            <span v-else>用户</span>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }"><span class="num">{{ timeText(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column label="训练次数" width="100" align="right">
          <template #default="{ row }"><span class="num">{{ row.workoutCount }}</span></template>
        </el-table-column>
        <el-table-column label="饮食条目" width="100" align="right">
          <template #default="{ row }"><span class="num">{{ row.mealCount }}</span></template>
        </el-table-column>
        <el-table-column label="最近记录" width="160">
          <template #default="{ row }"><span class="num">{{ timeText(row.lastRecordAt) }}</span></template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>
