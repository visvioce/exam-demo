<template>
  <router-view v-if="!appError" />
  <div v-else class="app-error-boundary">
    <div class="app-error-boundary__content">
      <el-icon :size="64" color="#dcdfe6"><WarningFilled /></el-icon>
      <h2>页面出现了问题</h2>
      <p>应用遇到了未预期的错误，请尝试刷新页面</p>
      <el-button type="primary" @click="handleRefresh">刷新页面</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onErrorCaptured } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { WarningFilled } from '@element-plus/icons-vue'

const authStore = useAuthStore()
const appError = ref(false)

function handleRefresh() {
  window.location.reload()
}

onErrorCaptured((err) => {
  console.error('Global error captured:', err)
  appError.value = true
  return false
})

onMounted(async () => {
  if (authStore.token && !authStore.user) {
    await authStore.getCurrentUser()
  }
})
</script>

<style scoped lang="scss">
@use '@/styles/design-tokens.scss' as *;

.app-error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: $bg-secondary;
}

.app-error-boundary__content {
  text-align: center;
  padding: 48px;
}

.app-error-boundary__content h2 {
  margin: 24px 0 12px;
  font-size: $font-size-3xl;
  color: $text-primary;
  font-weight: $font-weight-medium;
}

.app-error-boundary__content p {
  margin: 0 0 24px;
  color: $text-tertiary;
  font-size: $font-size-base;
}
</style>