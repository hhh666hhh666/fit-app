<!--
  自动更新后第一次打开弹一次「这次更新了什么」（ADR 0068）：只列这次升级还没看过的版本，跨了几个就合起来；
  点「知道了」或用任何方式关掉都记下看过，以后不再弹。挂在首页，自己决定弹不弹。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAccount } from '@/api/account'
import ChangelogVersion from '@/components/ChangelogVersion.vue'
import FtButton from '@/components/FtButton.vue'
import FtSheet from '@/components/FtSheet.vue'
import { APP_VERSION, devDetailsOn, markSeen, unseenVersions, type ChangeVersion } from '@/utils/changelog'

const list = ref<ChangeVersion[]>([])
const show = ref(false)
const admin = ref(false)
const dev = computed(() => admin.value && devDetailsOn())

onMounted(async () => {
  list.value = unseenVersions()
  if (!list.value.length) return
  try {
    admin.value = !!(await getAccount()).admin
  } catch {
    admin.value = false
  }
  show.value = true
})

function close() {
  markSeen()
  show.value = false
}

function goAll() {
  close()
  uni.navigateTo({ url: '/pages/changelog/index' })
}

const sub = computed(() =>
  list.value.length > 1 ? `从 v${list.value[list.value.length - 1].version} 到 v${APP_VERSION}，一共 ${list.value.length} 版` : `当前 v${APP_VERSION}`
)
</script>

<template>
  <Transition name="ft-sheet">
    <FtSheet v-if="show" title="这次更新了什么" :sub="sub" @close="close">
      <scroll-view scroll-y class="wn">
        <view v-for="v in list" :key="v.version" class="wn__card">
          <ChangelogVersion :v="v" :dev="dev" />
        </view>
      </scroll-view>
      <view class="wn__ops">
        <text class="wn__all" role="button" @click="goAll">看全部更新日志</text>
        <FtButton variant="secondary" class="wn__ok" @click="close">知道了</FtButton>
      </view>
    </FtSheet>
  </Transition>
</template>

<style lang="scss" scoped>
.wn {
  max-height: 60vh;
}

.wn__card {
  padding: 28rpx;
  @include sheet-card;
}

.wn__card + .wn__card {
  margin-top: 20rpx;
}

.wn__ops {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 28rpx;
}

.wn__all {
  @include press(dim);
  padding: 16rpx 8rpx;
  font-size: $text-base;
  color: $app-ink-muted;
}

.wn__ok {
  min-width: 240rpx;
}
</style>
