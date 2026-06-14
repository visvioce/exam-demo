<template>
  <div class="dashboard">
    <!-- 轮播图 -->
    <div class="carousel-section" v-if="carousels.length > 0">
      <el-carousel height="clamp(200px, 30vw, 400px)" :interval="5000" arrow="hover">
        <el-carousel-item v-for="item in carousels" :key="item.id">
          <div class="carousel-item" @click="handleCarouselClick(item)">
            <img :src="item.imageUrl" :alt="item.title" class="carousel-image" />
            <div class="carousel-content">
              <h3 class="carousel-title">{{ item.title }}</h3>
              <p v-if="item.description" class="carousel-description">{{ item.description }}</p>
            </div>
          </div>
        </el-carousel-item>
      </el-carousel>
    </div>

    <!-- 欢迎语 -->
    <div class="welcome-section">
      <div class="welcome-text">
        <h1>{{ greeting }}，{{ authStore.user?.nickname || authStore.user?.username }}</h1>
        <p class="welcome-subtitle">{{ welcomeMessage }}</p>
      </div>
      <span class="page-date">{{ getCurrentDate() }}</span>
    </div>

    <!-- 快捷入口 - Bento Grid 风格 -->
    <div class="bento-grid">
      <div class="bento-card bento-card--large" @click="router.push('/course')">
        <div class="bento-card__bg" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);"></div>
        <div class="bento-card__content">
          <el-icon class="bento-icon"><Reading /></el-icon>
          <div class="bento-info">
            <span class="bento-label">课程管理</span>
            <span class="bento-desc">创建、编辑和管理课程</span>
          </div>
        </div>
      </div>

      <div class="bento-card" @click="router.push('/question')">
        <div class="bento-card__bg" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);"></div>
        <div class="bento-card__content">
          <el-icon class="bento-icon"><Document /></el-icon>
          <div class="bento-info">
            <span class="bento-label">题库管理</span>
            <span class="bento-desc">维护考试题目</span>
          </div>
        </div>
      </div>

      <div class="bento-card" @click="router.push('/exam')">
        <div class="bento-card__bg" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);"></div>
        <div class="bento-card__content">
          <el-icon class="bento-icon"><Edit /></el-icon>
          <div class="bento-info">
            <span class="bento-label">考试管理</span>
            <span class="bento-desc">发布和监控考试</span>
          </div>
        </div>
      </div>

      <div class="bento-card" @click="router.push('/announcement')">
        <div class="bento-card__bg" style="background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);"></div>
        <div class="bento-card__content">
          <el-icon class="bento-icon"><Bell /></el-icon>
          <div class="bento-info">
            <span class="bento-label">公告管理</span>
            <span class="bento-desc">发布系统通知</span>
          </div>
        </div>
      </div>

      <div class="bento-card" @click="router.push('/paper')">
        <div class="bento-card__bg" style="background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);"></div>
        <div class="bento-card__content">
          <el-icon class="bento-icon"><Notebook /></el-icon>
          <div class="bento-info">
            <span class="bento-label">试卷管理</span>
            <span class="bento-desc">组卷与试卷维护</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 下方内容区：公告 + 待办 -->
    <el-row :gutter="16" class="bottom-section">
      <!-- 最近公告 -->
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="info-card" v-loading="loadingAnnouncements">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Bell /></el-icon>
                最近公告
              </span>
              <el-button text type="primary" size="small" @click="router.push('/announcement')">
                查看全部
              </el-button>
            </div>
          </template>

          <div v-if="announcements.length > 0" class="announcement-list">
            <div
              v-for="item in announcements.slice(0, 5)"
              :key="item.id"
              class="announcement-item ui-interactive-surface"
              @click="router.push('/announcement')"
            >
              <div class="announcement-main">
                <el-tag size="small" :type="getAnnouncementType(item.type)" effect="light">
                  {{ getAnnouncementTypeLabel(item.type) }}
                </el-tag>
                <span class="announcement-title">{{ item.title }}</span>
              </div>
              <span class="announcement-time">{{ formatRelativeTime(item.createdAt) }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无公告" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 待办提醒 -->
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="info-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Timer /></el-icon>
                待办提醒
              </span>
            </div>
          </template>

          <div v-if="todos.length > 0" class="todo-list">
            <div
              v-for="(todo, index) in todos"
              :key="index"
              class="todo-item"
              :class="`todo-item--${todo.type}`"
            >
              <div class="todo-dot"></div>
              <div class="todo-content">
                <span class="todo-text">{{ todo.text }}</span>
                <span class="todo-meta">{{ todo.meta }}</span>
              </div>
              <el-button
                v-if="todo.action"
                text
                type="primary"
                size="small"
                @click="todo.action"
              >
                去处理
              </el-button>
            </div>
          </div>
          <el-empty v-else description="暂无待办事项" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
/**
 * 教师/管理员仪表盘页面
 * 
 * 教师和管理员登录后的首页，展示：
 * - 轮播图（系统公告/活动推广）
 * - 个性化欢迎语（根据时间段和角色变化）
 * - Bento Grid 快捷管理入口（课程管理、题库管理、考试管理、公告管理、试卷管理）
 * - 最近公告列表
 * - 智能待办提醒（待发布考试、进行中考试、未创建课程提醒）
 * 
 * 数据来源：轮播图 API、公告 API、考试 API、课程 API
 * 待办事项基于实时数据动态生成，提供快捷操作入口
 */

import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { carouselApi } from '@/api/carousel'
import { announcementApi } from '@/api/announcement'
import { examApi } from '@/api/exam'
import { courseApi } from '@/api/course'
import {
  Reading,
  Document,
  Edit,
  Bell,
  Notebook,
  Timer
} from '@element-plus/icons-vue'
import type { Carousel, Announcement, Exam, Course } from '@/types'
import { dayjs } from '@/utils/format'

const router = useRouter()
const authStore = useAuthStore()

const carousels = ref<Carousel[]>([])
const announcements = ref<Announcement[]>([])
const loadingAnnouncements = ref(false)
const exams = ref<Exam[]>([])        // 所有考试（用于计算待办）
const courses = ref<Course[]>([])    // 课程列表（用于检查是否为空）

// 根据时间计算问候语
const greeting = computed(() => {
  const hour = dayjs().hour()
  if (hour < 6) return '夜深了'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const welcomeMessage = computed(() => {
  const role = authStore.user?.role
  if (role === 'ADMIN') return '管理员，今日系统运行正常'
  return '教师，准备好开始教学了吗'
})

// 待办事项
const todos = computed(() => {
  const items: { text: string; meta: string; type: 'warning' | 'info' | 'success'; action?: () => void }[] = []

  // 待发布的考试
  const draftExams = exams.value.filter(e => e.status === 'DRAFT')
  if (draftExams.length > 0) {
    items.push({
      text: `有 ${draftExams.length} 场考试待发布`,
      meta: '请及时发布以便学生参加',
      type: 'warning',
      action: () => router.push('/exam')
    })
  }

  // 进行中的考试
  const activeExams = exams.value.filter(e => e.status === 'STARTED')
  if (activeExams.length > 0) {
    items.push({
      text: `有 ${activeExams.length} 场考试正在进行`,
      meta: '监控考试状态',
      type: 'info',
      action: () => router.push('/exam')
    })
  }

  // 课程数量提示
  if (courses.value.length === 0) {
    items.push({
      text: '您还没有创建课程',
      meta: '创建第一门课程开始教学',
      type: 'warning',
      action: () => router.push('/course')
    })
  }

  return items
})

function getCurrentDate() {
  const now = dayjs()
  const weekDays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return `${now.format('YYYY.MM.DD')} · ${weekDays[now.day()]}`
}

function handleCarouselClick(item: Carousel) {
  if (item.linkUrl) {
    window.open(item.linkUrl, '_blank')
  }
}

function getAnnouncementType(type: string) {
  const map: Record<string, string> = {
    SYSTEM: 'danger',
    EXAM: 'warning',
    COURSE: 'success'
  }
  return map[type] || 'info'
}

function getAnnouncementTypeLabel(type: string) {
  const map: Record<string, string> = {
    SYSTEM: '系统',
    EXAM: '考试',
    COURSE: '课程'
  }
  return map[type] || type
}

function formatRelativeTime(dateStr?: string) {
  if (!dateStr) return ''
  const date = dayjs(dateStr)
  const now = dayjs()
  const minutes = now.diff(date, 'minute')
  const hours = now.diff(date, 'hour')
  const days = now.diff(date, 'day')

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return date.format('YYYY年M月D日')
}

async function loadCarousels() {
  try {
    const res = await carouselApi.getActive()
    carousels.value = res.data
  } catch {
    carousels.value = []
  }
}

async function loadAnnouncements() {
  loadingAnnouncements.value = true
  try {
    const res = await announcementApi.list()
    announcements.value = (res.data || []).sort((a, b) => {
      return dayjs(b.createdAt || 0).valueOf() - dayjs(a.createdAt || 0).valueOf()
    })
  } catch {
    announcements.value = []
  } finally {
    loadingAnnouncements.value = false
  }
}

async function loadExams() {
  try {
    const res = await examApi.list()
    exams.value = res.data || []
  } catch {
    exams.value = []
  }
}

async function loadCourses() {
  try {
    const res = await courseApi.list()
    courses.value = res.data || []
  } catch {
    courses.value = []
  }
}

onMounted(() => {
  loadCarousels()
  loadAnnouncements()
  loadExams()
  loadCourses()
})
</script>

<style scoped lang="scss">
@use '@/styles/design-tokens.scss' as *;

.dashboard {
  padding: $spacing-xl;

  .carousel-section {
    margin-bottom: $spacing-2xl;
    border-radius: $radius-md;
    overflow: hidden;
    box-shadow: none;

    :deep(.el-carousel) {
      border-radius: $radius-md;

      .el-carousel__arrow {
        background: rgba(0, 0, 0, 0.3);
        border-radius: $radius-full;

        &:hover {
          background: rgba(0, 0, 0, 0.5);
        }
      }

      .el-carousel__indicators .el-carousel__indicator {
        .el-carousel__button {
          background: rgba(255, 255, 255, 0.5);
          width: 12px;
          height: 4px;
          border-radius: 2px;
        }

        &.is-active .el-carousel__button {
          background: $primary;
        }
      }
    }
  }

  .carousel-item {
    position: relative;
    width: 100%;
    height: 100%;
    cursor: pointer;
    overflow: hidden;

    .carousel-image {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: transform 0.5s ease;
    }

    &:hover .carousel-image {
      transform: scale(1.05);
    }

    .carousel-content {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      padding: $spacing-xl;
      background: linear-gradient(to top, rgba(0, 0, 0, 0.7), transparent);
      color: #ffffff;

      .carousel-title {
        font-size: $font-size-xl;
        font-weight: $font-weight-medium;
        margin: 0 0 $spacing-sm;
      }

      .carousel-description {
        font-size: $font-size-sm;
        opacity: 0.9;
        margin: 0;
      }
    }
  }
}

// 欢迎语
.welcome-section {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: $spacing-2xl;

  .welcome-text {
    h1 {
      font-size: $font-size-3xl;
      font-weight: $font-weight-medium;
      color: $text-primary;
      margin: 0 0 $spacing-sm;
      letter-spacing: -0.5px;
    }

    .welcome-subtitle {
      font-size: $font-size-base;
      color: $text-tertiary;
      margin: 0;
    }
  }

  .page-date {
    font-size: $font-size-sm;
    color: $text-tertiary;
    white-space: nowrap;
  }
}

// Bento Grid 快捷入口
.bento-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: repeat(2, 140px);
  gap: $spacing-md;
  margin-bottom: $spacing-2xl;

  .bento-card {
    position: relative;
    border-radius: $radius-lg;
    overflow: hidden;
    cursor: pointer;
    transition: transform 0.3s ease, box-shadow 0.3s ease;

    &:hover {
      transform: translateY(-4px);
      box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
    }

    &__bg {
      position: absolute;
      inset: 0;
      opacity: 0.12;
      transition: opacity 0.3s ease;
    }

    &:hover &__bg {
      opacity: 0.2;
    }

    &__content {
      position: relative;
      z-index: 1;
      height: 100%;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      padding: $spacing-lg;
      background: $bg-primary;
      border: 1px solid $border-color;
      border-radius: $radius-lg;
      transition: border-color 0.3s ease;
    }

    &:hover &__content {
      border-color: transparent;
    }

    .bento-icon {
      font-size: 28px;
      color: $text-tertiary;
    }

    .bento-info {
      display: flex;
      flex-direction: column;
      gap: $spacing-xs;

      .bento-label {
        font-size: $font-size-lg;
        font-weight: $font-weight-medium;
        color: $text-primary;
      }

      .bento-desc {
        font-size: $font-size-sm;
        color: $text-tertiary;
      }
    }

    &--large {
      grid-column: span 2;
      grid-row: span 2;

      .bento-icon {
        font-size: 40px;
      }

      .bento-label {
        font-size: $font-size-xl;
      }

      .bento-desc {
        font-size: $font-size-base;
      }
    }
  }
}

// 下方内容区
.bottom-section {
  .info-card {
    border: 1px solid $border-color;
    border-radius: $radius-md;
    background: $bg-primary;
    height: 100%;

    :deep(.el-card__header) {
      border-bottom: 1px solid $border-light;
      padding: $spacing-lg $spacing-xl;
    }

    :deep(.el-card__body) {
      padding: $spacing-md 0;
    }

    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .card-title {
        display: flex;
        align-items: center;
        gap: $spacing-sm;
        font-weight: $font-weight-medium;
        color: $text-primary;
        font-size: $font-size-lg;

        .el-icon {
          color: $text-tertiary;
        }
      }
    }
  }
}

// 公告列表
.announcement-list {
  .announcement-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-md $spacing-xl;
    cursor: pointer;
    transition: background-color 0.2s ease;
    border-bottom: 1px solid $border-light;

    &:last-child {
      border-bottom: none;
    }

    &:hover {
      background-color: $bg-hover;
    }

    .announcement-main {
      display: flex;
      align-items: center;
      gap: $spacing-sm;
      min-width: 0;
      flex: 1;

      .announcement-title {
        font-size: $font-size-base;
        color: $text-primary;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    .announcement-time {
      font-size: $font-size-sm;
      color: $text-tertiary;
      white-space: nowrap;
      margin-left: $spacing-md;
    }
  }
}

// 待办列表
.todo-list {
  padding: 0 $spacing-xl;

  .todo-item {
    display: flex;
    align-items: center;
    gap: $spacing-md;
    padding: $spacing-md 0;
    border-bottom: 1px solid $border-light;

    &:last-child {
      border-bottom: none;
    }

    .todo-dot {
      width: 8px;
      height: 8px;
      border-radius: $radius-full;
      flex-shrink: 0;
    }

    &--warning .todo-dot {
      background: #e6a23c;
    }

    &--info .todo-dot {
      background: #409eff;
    }

    &--success .todo-dot {
      background: #67c23a;
    }

    .todo-content {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;

      .todo-text {
        font-size: $font-size-base;
        color: $text-primary;
      }

      .todo-meta {
        font-size: $font-size-sm;
        color: $text-tertiary;
      }
    }
  }
}

// 响应式
@media (max-width: $breakpoint-md) {
  .bento-grid {
    grid-template-columns: repeat(2, 1fr);
    grid-template-rows: repeat(3, 120px);

    .bento-card--large {
      grid-column: span 2;
      grid-row: span 1;
    }
  }
}

@media (max-width: $breakpoint-sm) {
  .dashboard {
    padding: $spacing-md;
  }

  .welcome-section {
    flex-direction: column;
    align-items: flex-start;
    gap: $spacing-sm;

    h1 {
      font-size: $font-size-2xl;
    }
  }

  .bento-grid {
    grid-template-columns: 1fr;
    grid-template-rows: auto;

    .bento-card {
      min-height: 100px;

      &--large {
        grid-column: span 1;
        grid-row: span 1;
      }
    }
  }

  .bottom-section {
    .el-col {
      margin-bottom: $spacing-md;
    }
  }
}
</style>
