<script setup lang="ts">
import { ref } from 'vue'
import { login, register } from '@/api/auth'
import { resetCaches } from '@/api/cache'

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const submitting = ref(false)

function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
}

async function submit() {
  if (submitting.value) return
  const u = username.value.trim()
  if (!u || !password.value) {
    uni.showToast({ title: '请填写用户名和密码', icon: 'none' })
    return
  }
  if (mode.value === 'register' && password.value.length < 6) {
    uni.showToast({ title: '密码至少 6 位', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const doAuth = mode.value === 'login' ? login : register
    const res = await doAuth(u, password.value)
    uni.setStorageSync('fitapp_token', res.token)
    uni.setStorageSync('fitapp_username', res.username)
    // 换人就用：登录成功即清掉上一个人留下的数据/照片缓存（ADR 0035）
    resetCaches()
    uni.reLaunch({ url: '/pages/home/index' })
  } catch {
    // request 层已统一弹出错误提示（用户名已存在 / 用户名或密码不正确 等）
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="login">
    <view class="login__head">
      <text class="login__brand">fit-app</text>
      <text class="login__sub">把每一次训练记下来</text>
    </view>

    <view class="form">
      <view class="form__field">
        <text class="form__label">用户名</text>
        <input
          v-model="username"
          class="form__input"
          placeholder="字母、数字或下划线，3-32 位"
          placeholder-class="form__ph"
          :maxlength="32"
        />
      </view>
      <view class="form__field">
        <text class="form__label">密码</text>
        <input
          v-model="password"
          class="form__input"
          password
          placeholder="至少 6 位"
          placeholder-class="form__ph"
          :maxlength="64"
        />
      </view>

      <view
        class="ft-btn ft-btn--primary ft-btn--block login__submit"
        :class="{ 'ft-btn--disabled': submitting }"
        @click="submit"
      >
        {{ mode === 'login' ? '登录' : '注册' }}
      </view>
    </view>

    <text class="login__switch" @click="switchMode">
      {{ mode === 'login' ? '注册新账号' : '已有账号？去登录' }}
    </text>
  </view>
</template>

<style lang="scss" scoped>
.login {
  min-height: 100vh;
  padding: 0 48rpx;
  display: flex;
  flex-direction: column;
}

.login__head {
  padding-top: 360rpx;
  text-align: center;
}

.login__brand {
  font-size: 52rpx;
  font-weight: 700;
  color: $app-ink;
  letter-spacing: 0.02em;
}

.login__sub {
  display: block;
  margin-top: 16rpx;
  font-size: $text-base;
  color: $app-ink-muted;
}

.form {
  margin-top: 96rpx;
}

.form__field + .form__field {
  margin-top: 36rpx;
}

.form__label {
  display: block;
  font-size: $text-sm;
  color: $app-ink-muted;
  margin-bottom: 12rpx;
}

.form__input {
  width: 100%;
  height: 96rpx;
  background: $app-surface;
  border: 1rpx solid $app-line;
  border-radius: $radius-md;
  padding: 0 28rpx;
  font-size: $text-md;
  color: $app-ink;
  box-sizing: border-box;
}

.form__ph {
  color: $app-ink-subtle;
}

.login__submit {
  margin-top: 56rpx;
  height: 96rpx;
  border-radius: 24rpx;
}

.login__switch {
  display: block;
  text-align: center;
  margin-top: 40rpx;
  font-size: $text-base;
  color: $app-ink-muted;
}
</style>
