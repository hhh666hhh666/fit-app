<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, ApiError, setToken } from '@/api'

// 复用 App 的登录接口；登录后立刻问一次 /admin/me，不是管理员就当场退出（ADR 0048）。
const router = useRouter()
const form = reactive({ username: '', password: '' })
const busy = ref(false)

async function submit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('用户名和密码都要填')
    return
  }
  busy.value = true
  try {
    const { token } = await api.post<{ token: string }>('/auth/login', {
      username: form.username.trim(),
      password: form.password
    })
    setToken(token)
    try {
      await api.get('/admin/me')
    } catch (e) {
      setToken('')
      if (e instanceof ApiError && e.code === 403) ElMessage.error('这个账号没有管理权限')
      return
    }
    void router.replace({ name: 'foods' })
  } catch {
    // api 层已经提示
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="card login__card">
      <h1 class="login__title">fit-app 管理端</h1>
      <p class="page__hint">用 App 的账号登录；只有管理员能进。</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" autofocus />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="busy" class="login__btn">登录</el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 16px;
  box-sizing: border-box;
}

.login__card {
  width: 100%;
  max-width: 360px;
  padding: 28px;
}

.login__title {
  margin: 0;
  font-size: 22px;
}

.login__btn {
  width: 100%;
  margin-top: 8px;
}
</style>
