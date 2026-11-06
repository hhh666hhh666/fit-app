<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  changePassword,
  deleteAccount,
  getAccount,
  updateNickname,
  updateUsername,
  type AccountInfo
} from '@/api/account'
import { resetCaches } from '@/api/cache'
import { ensureLogin } from '@/utils/guard'
import { ask } from '@/utils/confirm'
import FtButton from '@/components/FtButton.vue'
import FtListRow from '@/components/FtListRow.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import FtSheet from '@/components/FtSheet.vue'

// ── 账号（ADR 0050）：昵称、用户名、密码、退出登录、注销 ──
//    和「设置」分开：设置放的是怎么用这个 App，这里放的是这个账号是谁。

const NICKNAME_MAX = 20

const account = ref<AccountInfo | null>(null)

onShow(() => {
  if (!ensureLogin()) return
  void load()
})

async function load() {
  try {
    account.value = await getAccount()
  } catch {
    // request 层已提示
  }
}

type SheetKind = 'nickname' | 'username' | 'password' | 'delete' | null
const sheet = ref<SheetKind>(null)
const busy = ref(false)
const form = reactive({ nickname: '', username: '', password: '', newPassword: '', confirm: '' })

function open(kind: Exclude<SheetKind, null>) {
  form.nickname = account.value?.nickname ?? ''
  form.username = kind === 'username' ? (account.value?.username ?? '') : ''
  form.password = ''
  form.newPassword = ''
  form.confirm = ''
  sheet.value = kind
}

function close() {
  sheet.value = null
}

function toast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

async function run(task: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  try {
    await task()
  } catch {
    // request 层已提示（密码不正确、用户名已存在……）
  } finally {
    busy.value = false
  }
}

function saveNickname() {
  const n = form.nickname.trim()
  if ([...n].length > NICKNAME_MAX) return toast(`昵称最多 ${NICKNAME_MAX} 个字`)
  void run(async () => {
    account.value = await updateNickname(n)
    close()
    toast(n ? '昵称已保存' : '已清掉昵称')
  })
}

function saveUsername() {
  const u = form.username.trim()
  if (!/^[A-Za-z0-9_]{3,32}$/.test(u)) return toast('用户名需 3-32 位，只能用字母、数字、下划线')
  if (!form.password) return toast('请输入当前密码')
  void run(async () => {
    account.value = await updateUsername(u, form.password)
    close()
    toast('用户名已修改，下次用新用户名登录')
  })
}

function savePassword() {
  if (!form.password) return toast('请输入旧密码')
  if (form.newPassword.length < 6 || form.newPassword.length > 64) return toast('新密码需 6-64 位')
  if (form.newPassword !== form.confirm) return toast('两次输入的新密码不一样')
  void run(async () => {
    await changePassword(form.password, form.newPassword)
    close()
    toast('密码已修改，其他设备需要重新登录')
  })
}

function confirmDelete() {
  if (!form.password) return toast('请输入密码')
  if (form.confirm.trim() !== account.value?.username) return toast('用户名不对')
  void run(async () => {
    const ok = await ask({
      title: '确定注销？',
      content: '全部训练、饮食、身体、日记和照片会立刻彻底删除，无法恢复。',
      confirmText: '注销',
      danger: true
    })
    if (!ok) return
    await deleteAccount(form.password, form.confirm.trim())
    clearLocal()
    uni.reLaunch({ url: '/pages/login/index' })
    toast('账号已注销')
  })
}

function clearLocal() {
  uni.removeStorageSync('fitapp_token')
  uni.removeStorageSync('fitapp_username')
  // 换人就用：数据缓存与照片缓存一并清掉（ADR 0035），别把上一个人的东西留在内存里
  resetCaches()
}

function logout() {
  void ask({
    title: '退出登录',
    content: '将清除本机登录状态；训练数据保存在服务器，不受影响。',
    confirmText: '退出'
  }).then((confirm) => {
    if (!confirm) return
    clearLocal()
    uni.reLaunch({ url: '/pages/login/index' })
  })
}
</script>

<template>
  <view class="acct">
    <view class="acct__body">
      <FtPageHeader title="账号" back />

      <view class="glass entries">
        <FtListRow title="昵称" :value="account?.nickname || '未设置'" arrow @click="open('nickname')" />
        <FtListRow title="用户名" :value="account?.username ?? '—'" arrow @click="open('username')" />
        <FtListRow title="密码" value="修改" arrow @click="open('password')" />
      </view>

      <view class="acct__logout">
        <FtButton variant="ghost" size="sm" @click="logout">退出登录</FtButton>
      </view>

      <view class="glass danger">
        <text class="card-title">注销账号</text>
        <text class="danger__hint">立刻彻底删除这个账号的全部数据和图片，不进回收站，无法恢复。服务器备份里最多还会留 30 天。</text>
        <FtButton variant="danger" size="sm" class="danger__btn" @click="open('delete')">注销账号</FtButton>
      </view>
    </view>

    <Transition name="ft-sheet">
      <FtSheet v-if="sheet === 'nickname'" title="昵称" sub="设了就在「我的」页代替用户名；留空＝不设" guard @close="close">
        <view class="ns">
          <view class="field">
            <input
              v-model="form.nickname"
              class="field__input"
              placeholder="不设就显示用户名"
              placeholder-class="field__ph"
              :maxlength="40"
            />
            <text class="field__count ft-num">{{ [...form.nickname.trim()].length }}/{{ NICKNAME_MAX }}</text>
          </view>
        </view>
        <FtButton variant="primary" block :disabled="busy" class="ns__btn" @click="saveNickname">保存</FtButton>
      </FtSheet>
    </Transition>

    <Transition name="ft-sheet">
      <FtSheet v-if="sheet === 'username'" title="用户名" sub="登录时用；改完旧名会被释放" guard @close="close">
        <view class="ns">
          <view class="field">
            <text class="field__label">新用户名</text>
            <input v-model="form.username" class="field__input" placeholder="字母、数字、下划线" placeholder-class="field__ph" maxlength="32" />
          </view>
          <view class="field">
            <text class="field__label">当前密码</text>
            <input v-model="form.password" class="field__input" password placeholder="确认是本人" placeholder-class="field__ph" maxlength="64" />
          </view>
        </view>
        <FtButton variant="primary" block :disabled="busy" class="ns__btn" @click="saveUsername">保存</FtButton>
      </FtSheet>
    </Transition>

    <Transition name="ft-sheet">
      <FtSheet v-if="sheet === 'password'" title="修改密码" sub="改完以后，其他设备都要重新登录" guard @close="close">
        <view class="ns">
          <view class="field">
            <text class="field__label">旧密码</text>
            <input v-model="form.password" class="field__input" password placeholder="当前的密码" placeholder-class="field__ph" maxlength="64" />
          </view>
          <view class="field">
            <text class="field__label">新密码</text>
            <input v-model="form.newPassword" class="field__input" password placeholder="6-64 位" placeholder-class="field__ph" maxlength="64" />
          </view>
          <view class="field">
            <text class="field__label">再输一次</text>
            <input v-model="form.confirm" class="field__input" password placeholder="确认新密码" placeholder-class="field__ph" maxlength="64" />
          </view>
        </view>
        <FtButton variant="primary" block :disabled="busy" class="ns__btn" @click="savePassword">保存</FtButton>
      </FtSheet>
    </Transition>

    <Transition name="ft-sheet">
      <FtSheet v-if="sheet === 'delete'" title="注销账号" sub="立刻彻底删除，不进回收站，无法恢复" guard @close="close">
        <view class="ns">
          <view class="field">
            <text class="field__label">密码</text>
            <input v-model="form.password" class="field__input" password placeholder="当前的密码" placeholder-class="field__ph" maxlength="64" />
          </view>
          <view class="field">
            <text class="field__label">用户名</text>
            <input
              v-model="form.confirm"
              class="field__input"
              :placeholder="`手打一遍：${account?.username ?? ''}`"
              placeholder-class="field__ph"
              maxlength="32"
            />
          </view>
        </view>
        <FtButton variant="danger" block :disabled="busy" class="ns__btn" @click="confirmDelete">彻底删除我的账号</FtButton>
      </FtSheet>
    </Transition>
  </view>
</template>

<style lang="scss" scoped>
.acct__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(80rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

.card-title {
  @include card-title;
}

.entries {
  padding-top: 8rpx;
  padding-bottom: 8rpx;
}

.entries :deep(.ft-row__value) {
  font-size: $text-sm;
  color: $app-ink-muted;
}

.acct__logout {
  display: flex;
  justify-content: center;
  padding: 8rpx 0;
}

.danger {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding-top: 28rpx;
  padding-bottom: 28rpx;
}

.danger__hint {
  font-size: $text-sm;
  line-height: 1.6;
  color: $app-ink-muted;
}

.danger__btn {
  align-self: flex-start;
  margin-top: 8rpx;
}

.ns {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 28rpx;
}

.ns__btn {
  margin-top: 32rpx;
}

.field {
  display: flex;
  align-items: center;
  gap: 20rpx;
  height: 92rpx;
  padding: 0 28rpx;
  background: $app-surface;
  border-radius: $radius-lg;
}

.field__label {
  flex-shrink: 0;
  width: 136rpx;
  font-size: $text-base;
  color: $app-ink-muted;
}

.field__input {
  flex: 1;
  min-width: 0;
  font-size: $text-md;
  font-weight: $weight-bold;
  color: $app-ink;
}

.field__ph {
  color: $app-ink-subtle;
  font-weight: $weight-regular;
}

.field__count {
  flex-shrink: 0;
  font-size: $text-sm;
  color: $app-ink-subtle;
}
</style>
