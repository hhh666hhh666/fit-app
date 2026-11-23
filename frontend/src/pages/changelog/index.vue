<script setup lang="ts">
// ── 更新日志（ADR 0068）：内容就是仓根 CHANGELOG.md，从新到旧；最新一版展开，更早的折叠成一行 ──
//    开发字眼（ADR 编号、迁移号、代码片段）普通账号一律去掉；管理员默认显示，可以在右上角关掉（只记在这台手机上）。
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getAccount } from '@/api/account'
import ChangelogVersion from '@/components/ChangelogVersion.vue'
import FtEmpty from '@/components/FtEmpty.vue'
import FtPageHeader from '@/components/FtPageHeader.vue'
import { allVersions, APP_VERSION, devDetailsOn, markSeen, setDevDetails } from '@/utils/changelog'
import { ensureLogin } from '@/utils/guard'

const versions = allVersions()
const open = reactive<Record<string, boolean>>(versions[0] ? { [versions[0].version]: true } : {})

const admin = ref(false)
const devPref = ref(devDetailsOn())
const dev = computed(() => admin.value && devPref.value)

onShow(async () => {
  if (!ensureLogin()) return
  // 打开过更新日志＝看过了，升级弹层不用再弹
  markSeen()
  try {
    admin.value = !!(await getAccount()).admin
  } catch {
    admin.value = false
  }
})

function toggleDev() {
  devPref.value = !devPref.value
  setDevDetails(devPref.value)
}
</script>

<template>
  <view class="cl">
    <view class="cl__body">
      <FtPageHeader title="更新日志" :eyebrow="APP_VERSION === 'dev' ? '开发版' : `当前 v${APP_VERSION}`" back>
        <template v-if="admin" #right>
          <view class="devpill" :class="{ 'devpill--on': devPref }" role="switch" :aria-checked="devPref" @click="toggleDev">
            开发细节{{ devPref ? '开' : '关' }}
          </view>
        </template>
      </FtPageHeader>

      <FtEmpty v-if="!versions.length" title="还没有更新日志" sub="这个版本没带上更新日志" />

      <view v-for="(v, i) in versions" :key="v.version" class="glass card">
        <ChangelogVersion
          :v="v"
          :dev="dev"
          :open="!!open[v.version]"
          :collapsible="i > 0"
          @toggle="open[v.version] = !open[v.version]"
        />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.cl__body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 28rpx 40rpx calc(80rpx + env(safe-area-inset-bottom));
}

.glass {
  @include glass-card;
}

.card {
  padding: 32rpx 36rpx;
}

.devpill {
  @include glass-pill(14rpx 24rpx);
  @include press(dim);
  font-size: $text-sm;
  color: $app-ink-muted;
}

.devpill--on {
  color: $app-ink;
}
</style>
