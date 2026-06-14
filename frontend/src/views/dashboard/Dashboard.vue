<template>
  <StudentDashboard v-if="isStudent" />
  <TeacherDashboard v-else />
</template>

<script setup lang="ts">
/**
 * 仪表盘入口组件
 * 
 * 根据用户角色分发到不同的仪表盘视图：
 * - STUDENT -> StudentDashboard（学生仪表盘：课程、考试、公告概览）
 * - 其他角色 -> TeacherDashboard（教师/管理员仪表盘：管理入口、待办提醒）
 */

import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import StudentDashboard from './StudentDashboard.vue'
import TeacherDashboard from './TeacherDashboard.vue'

const authStore = useAuthStore()
const isStudent = computed(() => authStore.user?.role === 'STUDENT')
</script>
