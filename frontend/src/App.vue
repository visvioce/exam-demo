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
/**
 * 根组件
 *
 * 职责：
 * 1. 全局错误边界 - 捕获子组件未处理的异常，展示友好的错误提示页面
 * 2. 应用初始化 - 从 localStorage 恢复登录状态，自动获取用户信息
 * 3. 路由出口 - 通过 <router-view> 渲染当前路由对应的页面组件
 */
import { ref, onMounted, onErrorCaptured } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { WarningFilled } from '@element-plus/icons-vue'

// 用户认证状态管理
const authStore = useAuthStore()
// 全局错误标记，为 true 时显示错误边界页面而非正常内容
const appError = ref(false)

/** 刷新页面，用于从错误状态恢复 */
function handleRefresh() {
  window.location.reload()
}

// 全局错误捕获钩子：拦截子组件中未被 try/catch 处理的异常
// 返回 false 阻止错误继续向上传播到浏览器控制台
onErrorCaptured((err) => {
  console.error('Global error captured:', err)
  appError.value = true
  return false
})

// 应用挂载时自动恢复登录状态
// 场景：页面刷新后，localStorage 中仍有 token，需要重新获取用户信息
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