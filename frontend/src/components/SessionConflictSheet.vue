<!--
  「上一次训练还没结束」二选一（ADR 0020）：底栏圆钮长按、计划页「开始训练」撞上本机还没结束的
  会话时弹。继续上次＝橙色主按钮；结束并开新的＝红字按钮，点了还要再确认一次——会话到结束时才入库，
  开新的会把上一次移到回收站（ADR 0047）。
  不用系统确认框：它的取消位会被安卓返回键点到，危险动作不能放在那（utils/confirm.ts）。
-->
<script setup lang="ts">
import { computed } from 'vue'
import FtSheet from '@/components/FtSheet.vue'
import FtButton from '@/components/FtButton.vue'
import { formatClock } from '@/utils/format'
import { ask, TRASH_HINT } from '@/utils/confirm'
import { netElapsedSec, type Session } from '@/utils/session'

const props = defineProps<{ session: Session }>()
const emit = defineEmits<{ resume: []; fresh: []; close: [] }>()

const done = computed(() => props.session.blocks.reduce((n, b) => n + b.sets.filter((x) => x.done).length, 0))
const sub = computed(() => `已记 ${done.value} 组 · 已进行 ${formatClock(netElapsedSec(props.session, Date.now()))}`)

async function startFresh() {
  const ok = await ask({
    title: '结束上一次、开新的？',
    content:
      done.value > 0
        ? `上一次记的 ${done.value} 组还没入库。${TRASH_HINT}`
        : `上一次还没记任何一组。${TRASH_HINT}`,
    confirmText: '结束并开新的',
    danger: true
  })
  if (ok) emit('fresh')
}
</script>

<template>
  <FtSheet title="上一次训练还没结束" :sub="sub" @close="emit('close')">
    <view class="sc">
      <FtButton variant="primary" block @click="emit('resume')">继续上次</FtButton>
      <FtButton variant="danger" block @click="startFresh">结束并开新的</FtButton>
    </view>
  </FtSheet>
</template>

<style lang="scss" scoped>
.sc {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
</style>
