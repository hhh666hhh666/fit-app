<script setup lang="ts">
// 训练详情（占位）。设计稿：.workbuddy/stitch/screen/detail.png
import { onShow } from '@dcloudio/uni-app'
import { ensureLogin } from '@/utils/guard'

onShow(() => {
  if (!ensureLogin()) return
})
</script>

<template>
  <view class="ft-page">
    <view class="ft-card">
      <view style="font-weight: 600">训练详情</view>
      <view class="ft-muted" style="margin-top: 8rpx">施工中——按定稿设计稿建页。</view>
    </view>
  </view>
</template>
