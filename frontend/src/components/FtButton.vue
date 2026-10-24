<!--
  按钮（晴窗）：primary＝橙色渐变，**只给动作**，一屏最多一个；secondary＝玻璃胶囊；
  ghost＝只有描边；danger＝描边 ＋ 危险红字，给「结束并开新的」这类会丢数据的次要选项。按下统一轻微下沉。
-->
<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
    block?: boolean
    disabled?: boolean
    size?: 'md' | 'sm'
  }>(),
  { variant: 'secondary', block: false, disabled: false, size: 'md' }
)
const emit = defineEmits<{ click: [] }>()

function onClick() {
  if (!props.disabled) emit('click')
}
</script>

<template>
  <view
    class="ft-button"
    :class="['ft-button--' + variant, 'ft-button--' + size, { 'is-block': block, 'is-disabled': disabled }]"
    role="button"
    :aria-disabled="disabled"
    @click="onClick"
  >
    <slot />
  </view>
</template>

<style lang="scss" scoped>
.ft-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  box-sizing: border-box;
  border-radius: $radius-pill;
  border: 1rpx solid transparent;
  font-weight: $weight-bold;
  line-height: 1;
  @include press(shrink);
}

.ft-button--md {
  height: 92rpx;
  padding: 0 40rpx;
  font-size: $text-md;
}

.ft-button--sm {
  height: 64rpx;
  padding: 0 28rpx;
  font-size: $text-base;
}

.ft-button--primary {
  background: $accent-bg;
  color: $app-surface;
  box-shadow: $accent-shadow;
}

.ft-button--secondary {
  @include glass($glass-bg-strong);
  border-color: $glass-edge;
  color: $app-ink-2;
  box-shadow: $shadow-sm;
}

.ft-button--ghost {
  background: transparent;
  border-color: $app-line;
  color: $app-ink-muted;
}

.ft-button--danger {
  background: transparent;
  border-color: $app-line;
  color: $app-danger;
}

.is-block {
  display: flex;
  width: 100%;
}

.is-disabled {
  opacity: 0.4;
}

.is-disabled:active {
  transform: none;
}
</style>
