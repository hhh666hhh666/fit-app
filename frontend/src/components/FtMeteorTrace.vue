<!--
  底栏中间圆钮外圈的「训练进行中」动效（晴窗，ADR 0041）：一颗流星绕圆匀速跑。

  为什么不再用 Canvas：旧版绕的是扁长横幅，圆角矩形上「角速度匀速」会在长边慢爬、
  转角猛甩，只能逐帧按周长取点画。现在绕的是正圆，**转一个 conic 渐变就是严格的
  弧长匀速**，纯 CSS 就够了，还不用操心保活页切走时停帧（合成层动画，切走即不绘制）。

  fx：meteor＝一颗流星（默认）；glow＝两段柔光对转（旧「流光」档重画到圆上）。
  「关闭」档由父组件直接不渲染本组件。

  用法：放在 `position: relative` 的圆钮容器里，和圆钮同心；本组件不接收点击。
-->
<script setup lang="ts">
withDefaults(defineProps<{ fx?: 'meteor' | 'glow' }>(), { fx: 'meteor' })
</script>

<template>
  <view class="ft-orbit" :class="'ft-orbit--' + fx" aria-hidden="true">
    <view class="ft-orbit__track" />
    <view class="ft-orbit__spin">
      <view class="ft-orbit__tail" />
      <view v-if="fx === 'meteor'" class="ft-orbit__head" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 环的中线半径 ≈ 圆钮半径 ＋ 3px（定稿样稿：60px 圆钮、r=33 的轨道） */
$ring: 5rpx; // 2.5px 粗

.ft-orbit {
  position: absolute;
  inset: -10rpx;
  pointer-events: none;
}

.ft-orbit__track {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 4rpx solid $accent-track;
}

.ft-orbit__spin {
  position: absolute;
  inset: 0;
  animation: ft-orbit-spin 5s linear infinite;
}

/* 尾巴：conic 渐变在 0°（正上方）最亮、逆时针渐隐；遮罩只留一圈细环（遮罩只看不透明度，black 不是设计色） */
.ft-orbit__tail {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  -webkit-mask: radial-gradient(farthest-side, transparent calc(100% - #{$ring}), black calc(100% - #{$ring} + 1rpx));
  mask: radial-gradient(farthest-side, transparent calc(100% - #{$ring}), black calc(100% - #{$ring} + 1rpx));
}

.ft-orbit--meteor .ft-orbit__tail {
  background: conic-gradient(from 0deg, transparent 0turn, transparent 0.7turn, $accent-comet 1turn);
}

/* 头：环正上方的亮点 ＋ 一点光晕；跟着 spin 一起转 */
.ft-orbit__head {
  position: absolute;
  left: 50%;
  top: 0;
  width: 10rpx;
  height: 10rpx;
  margin: -2rpx 0 0 -5rpx;
  border-radius: 50%;
  background: $accent-comet;
  box-shadow: 0 0 12rpx 2rpx $accent-glow;
}

/* 流光：两段对称柔光，转得快一点、带模糊——更像一圈在流动的光 */
.ft-orbit--glow .ft-orbit__spin {
  animation-duration: 4s;
  filter: blur(2px);
}

.ft-orbit--glow .ft-orbit__tail {
  background: conic-gradient(
    from 0deg,
    transparent 0turn,
    $accent-comet 0.12turn,
    transparent 0.32turn,
    transparent 0.5turn,
    $accent-comet 0.62turn,
    transparent 0.82turn
  );
}

@keyframes ft-orbit-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 减少动态效果：停在起始相位（流星停在正上方） */
@media (prefers-reduced-motion: reduce) {
  .ft-orbit__spin {
    animation: none;
  }
}
</style>
