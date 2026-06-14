<template>
  <el-tooltip v-if="showTooltip" :content="tooltip" placement="top">
    <el-button
      link
      size="small"
      :type="type || undefined"
      :aria-label="ariaLabel || tooltip"
      :class="['icon-action-btn', buttonClass]"
      @click="handleClick"
    >
      <el-icon><component :is="icon" /></el-icon>
    </el-button>
  </el-tooltip>
  <el-button
    v-else
    link
    size="small"
    :type="type || undefined"
    :aria-label="ariaLabel || tooltip"
    :class="['icon-action-btn', buttonClass]"
    @click="handleClick"
  >
    <el-icon><component :is="icon" /></el-icon>
  </el-button>
</template>

<script setup lang="ts">
/**
 * 图标操作按钮组件（原子组件）
 *
 * 通用的图标按钮，支持：
 * - 可选 tooltip 提示
 * - 事件冒泡控制（stopPropagation）
 * - 无障碍属性（aria-label）
 * - 自定义样式类（buttonClass）
 *
 * 被 ActionButtons 和 DeleteActionButton 组合使用
 */
import type { Component } from 'vue'

const props = withDefaults(defineProps<{
  /** 图标组件 */
  icon: Component
  /** tooltip 提示文字 */
  tooltip?: string
  /** 无障碍标签 */
  ariaLabel?: string
  /** 是否显示 tooltip */
  showTooltip?: boolean
  /** 是否阻止事件冒泡到父元素，默认 true */
  stopPropagation?: boolean
  /** 额外样式类名 */
  buttonClass?: string
  /** Element Plus 按钮类型 */
  type?: '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'
}>(), {
  tooltip: '',
  ariaLabel: '',
  showTooltip: false,
  stopPropagation: true,
  buttonClass: '',
  type: ''
})

const emit = defineEmits<{
  (e: 'click', event: MouseEvent): void
}>()

/** 处理点击事件：可选择阻止事件冒泡 */
function handleClick(event: MouseEvent) {
  if (props.stopPropagation) {
    event.stopPropagation()
  }
  emit('click', event)
}
</script>

<style scoped lang="scss">
@use '@/styles/design-tokens.scss' as *;

.icon-action-btn {
  width: 22px;
  min-width: 22px;
  height: 22px;
  padding: 0 !important;
  border: none !important;
  border-radius: $radius-sm !important;
  opacity: 0.75;
  transition: opacity $transition-fast;

  &:hover {
    opacity: 1;
  }

  &:not(.is-disabled):focus-visible {
    box-shadow: $focus-ring;
  }
}

.icon-action-btn :deep(.el-icon) {
  font-size: 13px;
}
</style>
