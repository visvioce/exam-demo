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
import { Delete } from '@element-plus/icons-vue'
import IconActionButton from './IconActionButton.vue'

withDefaults(defineProps<{
  tooltip?: string
  ariaLabel?: string
  showTooltip?: boolean
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
