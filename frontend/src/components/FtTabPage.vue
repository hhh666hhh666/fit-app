<!--
  主 tab 页的外壳：页面根 ＋ 钉在底部的底栏。四个主页面的模板根都换成它，
  页面自己只管正文（`.xx__body`，切页时克隆、滑动的就是它）和弹层。

  - 底部留白由这里给（padding-bottom），正文末尾不用再放占位块；
  - 底栏外层 z-index 20，压住切页克隆层（10）和底栏副本（15），见 App.vue；
  - 横向裁掉切页位移期间的溢出，见下面的注释。
-->
<script setup lang="ts">
import FtTabBar from '@/components/FtTabBar.vue'
import type { TabPage } from '@/utils/tab-page'

defineProps<{ page: TabPage }>()
</script>

<template>
  <view class="ft-tab-page">
    <slot />
    <view class="ft-tab-page__bottom">
      <FtTabBar :active="page.key" :enter-dir="page.dir" :enter-seq="page.seq" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 横向裁掉切页位移期间的溢出：位移会让内容短暂超出页面宽度，不裁的话移动端 WebView
   会把页面根当成可横向滚动的容器，在视口底部画一条横向滚动条（2026-10-21 真机反馈的
   「底部出现长条」）。用 clip 而不是 hidden：hidden 会把页面根变成滚动容器，clip 只裁不滚；
   两者都不影响 fixed 的底栏（fixed 的包含块是视口）。 */
.ft-tab-page {
  overflow-x: hidden; /* 老 WebView 兜底 */
  overflow-x: clip;
  padding-bottom: $tabbar-space;
}

.ft-tab-page__bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  /* 高于切页克隆层（10）与底栏副本（15）：转场期间底栏钉住、不被遮 */
  z-index: 20;
  /* 底栏是悬浮胶囊，四周留白处要让点击穿透到下面的内容（胶囊和圆钮自己接点击） */
  pointer-events: none;
}
</style>
