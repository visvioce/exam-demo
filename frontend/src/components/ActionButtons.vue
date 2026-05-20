<template>
  <div class="action-buttons">
    <IconActionButton
      v-if="showView"
      :icon="View"
      tooltip="查看"
      show-tooltip
      aria-label="查看"
      button-class="view-btn"
      @click="$emit('view')"
    />
    <IconActionButton
      v-if="showEdit"
      :icon="Edit"
      tooltip="编辑"
      show-tooltip
      aria-label="编辑"
      button-class="edit-btn"
      @click="$emit('edit')"
    />
    <IconActionButton
      v-if="showDelete"
      :icon="Delete"
      tooltip="删除"
      show-tooltip
      aria-label="删除"
      button-class="delete-btn"
      @click="$emit('delete')"
    />
  </div>
</template>

<script setup lang="ts">
import { View, Edit, Delete } from '@element-plus/icons-vue'
import IconActionButton from './IconActionButton.vue'

withDefaults(defineProps<{
  showView?: boolean
  showEdit?: boolean
  showDelete?: boolean
}>(), {
  showView: true,
  showEdit: true,
  showDelete: true
})

defineEmits<{
  (e: 'view'): void
  (e: 'edit'): void
  (e: 'delete'): void
}>()
</script>

<style scoped lang="scss">
@use '@/styles/design-tokens.scss' as *;

.action-buttons {
  display: inline-flex;
  gap: $spacing-xs;
  align-items: center;
}

.view-btn {
  color: $text-quaternary;

  &:hover {
    color: $text-tertiary;
  }
}

.edit-btn {
  color: $text-secondary;

  &:hover {
    color: $text-primary;
  }
}

.delete-btn {
  color: $text-primary;

  &:hover {
    color: $black;
  }
}
</style>
