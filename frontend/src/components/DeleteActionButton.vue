<template>
  <IconActionButton
    :icon="Delete"
    :tooltip="tooltip"
    :aria-label="ariaLabel"
    :show-tooltip="showTooltip"
    :stop-propagation="stopPropagation"
    :button-class="`delete-btn ${buttonClass}`.trim()"
    @click="(event) => emit('click', event)"
  />
</template>

<script setup lang="ts">
/**
 * 删除操作按钮组件
 *
 * 基于 IconActionButton 封装的删除按钮，预设删除图标和红色样式
 * 默认阻止事件冒泡，避免触发行点击事件
 */
import { Delete } from '@element-plus/icons-vue'
import IconActionButton from './IconActionButton.vue'

withDefaults(defineProps<{
  tooltip?: string
  ariaLabel?: string
  showTooltip?: boolean
  /** 是否阻止事件冒泡，默认 true（避免触发行点击） */
  stopPropagation?: boolean
  buttonClass?: string
}>(), {
  tooltip: '删除',
  ariaLabel: '',
  showTooltip: false,
  stopPropagation: true,
  buttonClass: ''
})

const emit = defineEmits<{
  (e: 'click', event: MouseEvent): void
}>()
</script>

<style scoped lang="scss">
@use '@/styles/design-tokens.scss' as *;

.delete-btn {
  color: $text-primary;

  &:hover {
    color: $black;
  }
}
</style>
