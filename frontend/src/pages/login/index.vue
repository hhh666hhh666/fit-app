<script setup lang="ts">
import { ref } from 'vue'
import { login, register } from '@/api/auth'
import { resetCaches } from '@/api/cache'
import FtButton from '@/components/FtButton.vue'

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const submitting = ref(false)

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
    // switchTab 而不是 reLaunch：首页现在是 tabBar 页（ADR 0038），switchTab 正好
    // 关掉登录页所在的整条非 tab 栈并落到 tab 上
    uni.switchTab({ url: '/pages/home/index' })
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

    <view class="glass form">
      <view class="seg" role="tablist">
        <view
          class="seg__item"
          :class="{ 'seg__item--on': mode === 'login' }"
          role="tab"
          :aria-selected="mode === 'login'"
          @click="mode = 'login'"
        >
          登录
        </view>
        <view
          class="seg__item"
          :class="{ 'seg__item--on': mode === 'register' }"
          role="tab"
          :aria-selected="mode === 'register'"
          @click="mode = 'register'"
        >
          注册
        </view>
      </view>

      <view class="field">
        <text class="field__label">用户名</text>
        <input
          v-model="username"
          class="field__input"
          placeholder="字母、数字或下划线，3–32 位"
          placeholder-class="field__ph"
          :maxlength="32"
        />
      </view>
      <view class="field">
        <text class="field__label">密码</text>
        <input
          v-model="password"
          class="field__input"
          password
          placeholder="至少 6 位"
          placeholder-class="field__ph"
          :maxlength="64"
        />
      </view>

      <FtButton variant="primary" block :disabled="submitting" class="form__submit" @click="submit">
        {{ mode === 'login' ? '登录' : '注册' }}
      </FtButton>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.login {
  min-height: 100vh;
  box-sizing: border-box;
  padding: calc(240rpx + env(safe-area-inset-top)) 40rpx calc(64rpx + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: 64rpx;
}

.login__head {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

/* 品牌字是拉丁字母：走 Nunito 800，和全站大数字同一副字 */
.login__brand {
  font-family: $font-num;
  font-size: $text-display;
  font-weight: $weight-heavy;
  line-height: 1.1;
  color: $app-ink;
}

.login__sub {
  font-size: $text-md;
  color: $app-ink-muted;
}

.glass {
  @include glass-card;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

/* ── 登录／注册 分段 ── */
.seg {
  @include seg-track-solid;
  margin-bottom: 8rpx;
}

.seg__item {
  @include seg-item(14rpx 0);
  flex: 1;
  text-align: center;
}

.seg__item--on {
  @include seg-item-on;
}

/* ── 输入框：白底胶囊，标签在左 ── */
.field {
  display: flex;
  align-items: center;
  gap: 20rpx;
  height: 96rpx;
  padding: 0 28rpx;
  border-radius: $radius-md;
  background: $app-surface;
}

.field__label {
  width: 96rpx;
  flex-shrink: 0;
  font-size: $text-base;
  color: $app-ink-muted;
}

.field__input {
  flex: 1;
  min-width: 0;
  font-size: $text-md;
  color: $app-ink;
}

.field__ph {
  color: $app-ink-subtle;
}

.login .form__submit {
  margin-top: 16rpx;
  height: 104rpx;
  font-size: 30rpx;
}
</style>
