<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, setToken } from '@/api'

const route = useRoute()
const router = useRouter()
const username = ref('')

onMounted(async () => {
  try {
    username.value = (await api.get<{ username: string }>('/admin/me')).username
  } catch {
    // 不是管理员或已过期：api 层会送回登录页
  }
})

function logout() {
  setToken('')
  void router.replace({ name: 'login' })
}

const active = () => '/' + String(route.path.split('/')[1] || 'foods')
</script>

<template>
  <el-container class="shell">
    <el-aside width="200px" class="shell__aside">
      <div class="shell__brand">fit-app 管理端</div>
      <el-menu :default-active="active()" router class="shell__menu">
        <el-menu-item index="/foods">预置食物</el-menu-item>
        <el-menu-item index="/exercises">预置动作</el-menu-item>
        <el-menu-item index="/users">用户</el-menu-item>
      </el-menu>
      <div class="shell__me">
        <span class="shell__name">{{ username }}</span>
        <el-button link type="primary" @click="logout">退出</el-button>
      </div>
    </el-aside>
    <el-main class="shell__main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.shell {
  height: 100%;
}

.shell__aside {
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid var(--el-border-color-lighter);
}

.shell__brand {
  padding: 20px 20px 12px;
  font-weight: 600;
}

.shell__menu {
  flex: 1;
  border-right: none;
}

.shell__me {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
}

.shell__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shell__main {
  padding: 0;
}

@media (max-width: 720px) {
  .shell {
    flex-direction: column;
  }

  .shell__aside {
    width: 100% !important;
    border-right: none;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .shell__menu {
    display: flex;
  }
}
</style>
