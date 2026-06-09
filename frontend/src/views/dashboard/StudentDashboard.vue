<template>
  <div class="student-dashboard">
    <!-- 轮播图 -->
    <section class="carousel-section" v-if="carousels.length > 0">
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
    </section>

    <!-- 欢迎语 -->
    <div class="welcome-section">
      <div class="welcome-text">
        <h1>{{ greeting }}，{{ authStore.user?.nickname || authStore.user?.username }}</h1>
        <p class="welcome-subtitle">{{ welcomeMessage }}</p>
      </div>
      <span class="page-date">{{ getCurrentDate() }}</span>
    </div>

    <!-- 快捷入口 -->
    <div class="bento-grid">
      <div class="bento-card bento-card--large" @click="router.push('/course')">
        <div class="bento-card__bg" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);"></div>
        <div class="bento-card__content">
          <el-icon class="bento-icon"><Reading /></el-icon>
          <div class="bento-info">
            <span class="bento-label">我的课程</span>
            <span class="bento-desc">已加入 {{ joinedCourseCount }} 门课程</span>
          </div>
        </div>
      </div>

      <div class="bento-card" @click="router.push('/exam')">
        <div class="bento-card__bg" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);"></div>
        <div class="bento-card__content">
          <el-icon class="bento-icon"><Edit /></el-icon>
          <div class="bento-info">
            <span class="bento-label">我的考试</span>
            <span class="bento-desc">{{ pendingExamCount }} 场待考</span>
          </div>
        </div>
      </div>

      <div class="bento-card" @click="router.push('/announcement')">
        <div class="bento-card__bg" style="background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);"></div>
        <div class="bento-card__content">
          <el-icon class="bento-icon"><Bell /></el-icon>
          <div class="bento-info">
            <span class="bento-label">公告</span>
            <span class="bento-desc">查看最新通知</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 下方内容区 -->
    <el-row :gutter="16" class="bottom-section">
      <!-- 最近公告 -->
      <el-col :xs="24" :lg="12">
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

      <!-- 即将开始的考试 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="info-card" v-loading="loadingExams">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Timer /></el-icon>
                即将开始的考试
              </span>
              <el-button text type="primary" size="small" @click="router.push('/exam')">
                查看全部
              </el-button>
            </div>
          </template>

          <div v-if="upcomingExams.length > 0" class="exam-list">
            <div
              v-for="exam in upcomingExams"
              :key="exam.id"
              class="exam-item ui-interactive-surface"
              @click="router.push(`/exam/${exam.id}`)"
            >
              <div class="exam-main">
                <span class="exam-title">{{ exam.title }}</span>
                <span class="exam-course" v-if="exam.courseName">{{ exam.courseName }}</span>
              </div>
              <div class="exam-meta">
                <el-tag size="small" :type="getExamStatusType(exam.status)" effect="light">
                  {{ getExamStatusLabel(exam.status) }}
                </el-tag>
                <span class="exam-time">{{ formatDateTime(exam.startedAt) }}</span>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无即将开始的考试" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 课程总览 -->
    <section class="my-course-section">
      <div class="section-header">
        <div>
          <h2>课程总览</h2>
          <p>显示全部可查看课程，已参加课程会标注</p>
        </div>
        <el-button text type="primary" @click="router.push('/course')">进入课程页</el-button>
      </div>

      <el-card shadow="never" class="course-panel" v-loading="loadingCourses">
        <el-row v-if="allCourses.length > 0" :gutter="16">
          <el-col v-for="course in allCourses.slice(0, 6)" :key="course.id" :xs="24" :sm="12" :lg="8">
            <el-card shadow="never" class="course-card ui-interactive-surface" @click="router.push(`/course/${course.id}`)">
              <div class="course-card__cover">
                <img v-if="course.coverUrl" :src="course.coverUrl" :alt="course.name" />
                <div v-else class="course-card__cover-placeholder">课程封面</div>
              </div>
              <div class="course-card__head">
                <h3 class="ui-title-clamp-2">{{ course.name }}</h3>
                <div class="course-card__tags">
                  <el-tag size="small" :type="isJoined(course.id) ? 'success' : 'info'">
                    {{ isJoined(course.id) ? '已参加' : '未参加' }}
                  </el-tag>
                  <el-tag size="small" type="info">{{ course.code }}</el-tag>
                </div>
              </div>
              <div class="course-card__meta">
                <div><span class="label">教师：</span>{{ getTeacherName(course) }}</div>
                <div><span class="label">学分：</span>{{ course.credits }}</div>
                <div v-if="course.deadline"><span class="label">截止：</span>{{ formatDate(course.deadline) }}</div>
              </div>
              <div class="course-card__foot">
                <el-tag size="small" :type="course.status === 'ACTIVE' ? 'success' : 'info'">
                  {{ course.status === 'ACTIVE' ? '进行中' : '已结束' }}
                </el-tag>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-else description="暂无课程" />
      </el-card>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { carouselApi } from '@/api/carousel'
import { courseApi } from '@/api/course'
import { examApi } from '@/api/exam'
import { announcementApi } from '@/api/announcement'
import { Reading, Edit, Bell, Timer } from '@element-plus/icons-vue'
import type { Carousel, Course, Exam, Announcement } from '@/types'
import { formatDate } from '@/utils/format'

const router = useRouter()
const authStore = useAuthStore()

const carousels = ref<Carousel[]>([])
const allCourses = ref<Course[]>([])
const myCourses = ref<Course[]>([])
const exams = ref<Exam[]>([])
const announcements = ref<Announcement[]>([])
const loadingCourses = ref(false)
const loadingExams = ref(false)
const loadingAnnouncements = ref(false)

const joinedIds = computed(() => new Set(myCourses.value.map((course) => course.id)))
const joinedCourseCount = computed(() => myCourses.value.length)

const pendingExamCount = computed(() => {
  return exams.value.filter(e =>
    e.status === 'PUBLISHED' && e.studentExamStatus === 'NOT_STARTED'
  ).length
})

const upcomingExams = computed(() => {
  const now = new Date().getTime()
  return exams.value
    .filter(e => {
      const startTime = new Date(e.startedAt).getTime()
      return e.status === 'PUBLISHED' && startTime > now
    })
    .sort((a, b) => new Date(a.startedAt).getTime() - new Date(b.startedAt).getTime())
    .slice(0, 5)
})

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const welcomeMessage = computed(() => {
  if (pendingExamCount.value > 0) {
    return `你有 ${pendingExamCount.value} 场考试待参加，加油！`
  }
  return '今天也要好好学习哦'
})

function isJoined(courseId: number) {
  return joinedIds.value.has(courseId)
}

function getTeacherName(course: Course) {
  if (course.teacherName?.trim()) return course.teacherName
  return course.teacherId ? `教师#${course.teacherId}` : '-'
}

function getCurrentDate() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const weekDays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  const weekDay = weekDays[now.getDay()]
  return `${year}.${month}.${day} · ${weekDay}`
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

function getExamStatusType(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'info',
    PUBLISHED: 'success',
    STARTED: 'warning',
    ENDED: 'info'
  }
  return map[status] || 'info'
}

function getExamStatusLabel(status: string) {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    STARTED: '进行中',
    ENDED: '已结束'
  }
  return map[status] || status
}

function formatDateTime(dateStr?: string) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

function formatRelativeTime(dateStr?: string) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString('zh-CN')
}

async function loadCarousels() {
  try {
    const res = await carouselApi.getActive()
    carousels.value = res.data || []
  } catch {
    carousels.value = []
  }
}

async function loadCourses() {
  loadingCourses.value = true
  try {
    const [myRes, activeRes] = await Promise.all([
      courseApi.getMyCourses(),
      courseApi.getActiveCourses()
    ])
    myCourses.value = myRes.data || []

    const merged = new Map<number, Course>()
    for (const course of myCourses.value) {
      merged.set(course.id, course)
    }
    for (const course of activeRes.data || []) {
      if (!merged.has(course.id)) {
        merged.set(course.id, course)
      }
    }

    const courses = Array.from(merged.values())
    allCourses.value = courses.sort((a, b) => {
      const joinedDelta = Number(isJoined(b.id)) - Number(isJoined(a.id))
      if (joinedDelta !== 0) return joinedDelta
      return a.name.localeCompare(b.name, 'zh-CN')
    })
  } catch {
    allCourses.value = []
    myCourses.value = []
  } finally {
    loadingCourses.value = false
  }
}

async function loadExams() {
  loadingExams.value = true
  try {
    const res = await examApi.getMyExams()
    exams.value = res.data?.records || []
  } catch {
    exams.value = []
  } finally {
    loadingExams.value = false
  }
}

async function loadAnnouncements() {
  loadingAnnouncements.value = true
  try {
    const res = await announcementApi.list()
    announcements.value = (res.data || []).sort((a, b) => {
      return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime()
    })
  } catch {
    announcements.value = []
  } finally {
    loadingAnnouncements.value = false
  }
}

onMounted(() => {
  loadCarousels()
  loadCourses()
  loadExams()
  loadAnnouncements()
})
</script>

<style scoped lang="scss">
@use '@/styles/design-tokens.scss' as *;

.student-dashboard {
  padding: $spacing-xl;
  display: grid;
  gap: $spacing-2xl;
}

.carousel-section {
  border-radius: $radius-md;
  overflow: hidden;

  :deep(.el-carousel) {
    border-radius: $radius-md;
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
    left: 0;
    right: 0;
    bottom: 0;
    padding: $spacing-xl;
    background: linear-gradient(to top, rgba(0, 0, 0, 0.7), transparent);
    color: #ffffff;

    .carousel-title {
      margin: 0 0 $spacing-xs;
      font-size: $font-size-xl;
      font-weight: $font-weight-medium;
    }

    .carousel-description {
      margin: 0;
      font-size: $font-size-sm;
      opacity: 0.92;
    }
  }
}

// 欢迎语
.welcome-section {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;

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

// Bento Grid
.bento-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: 160px;
  gap: $spacing-md;

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
      font-size: 32px;
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
      grid-column: span 1;
      grid-row: span 1;

      .bento-icon {
        font-size: 36px;
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

// 考试列表
.exam-list {
  .exam-item {
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

    .exam-main {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
      flex: 1;

      .exam-title {
        font-size: $font-size-base;
        color: $text-primary;
        font-weight: $font-weight-medium;
      }

      .exam-course {
        font-size: $font-size-sm;
        color: $text-tertiary;
      }
    }

    .exam-meta {
      display: flex;
      align-items: center;
      gap: $spacing-sm;
      white-space: nowrap;
      margin-left: $spacing-md;

      .exam-time {
        font-size: $font-size-sm;
        color: $text-tertiary;
      }
    }
  }
}

// 课程区域
.my-course-section {
  .section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-md;

    h2 {
      margin: 0;
      color: $text-primary;
      font-size: $font-size-2xl;
      font-weight: $font-weight-medium;
    }

    p {
      margin: $spacing-xs 0 0;
      color: $text-tertiary;
      font-size: $font-size-sm;
    }
  }
}

.course-panel {
  border: 1px solid $border-color;
  border-radius: $radius-md;
  background: $bg-primary;
}

.course-card {
  border: 1px solid $border-color;
  cursor: pointer;
  margin-bottom: $spacing-md;

  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    margin-bottom: $spacing-md;

    .course-card__tags {
      display: flex;
      gap: $spacing-xs;
      align-items: center;
    }

    h3 {
      margin: 0;
      font-size: $font-size-lg;
      color: $text-primary;
      line-height: 1.35;
    }
  }

  &__cover {
    width: 100%;
    aspect-ratio: 16 / 9;
    border-radius: $radius-sm;
    overflow: hidden;
    margin-bottom: $spacing-md;
    border: 1px solid $border-light;
    background: $bg-hover;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
  }

  &__cover-placeholder {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: $text-tertiary;
    font-size: $font-size-sm;
  }

  &__meta {
    display: grid;
    gap: $spacing-xs;
    color: $text-secondary;
    font-size: $font-size-sm;
    margin-bottom: $spacing-md;

    .label {
      color: $text-tertiary;
    }
  }

  &__foot {
    border-top: 1px solid $border-light;
    padding-top: $spacing-md;
  }
}

// 响应式
@media (max-width: $breakpoint-md) {
  .bento-grid {
    grid-template-columns: repeat(2, 1fr);
    grid-template-rows: repeat(2, 120px);

    .bento-card--large {
      grid-column: span 2;
      grid-row: span 1;
    }
  }
}

@media (max-width: $breakpoint-sm) {
  .student-dashboard {
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
      }
    }
  }

  .bottom-section .el-col {
    margin-bottom: $spacing-md;
  }

  .my-course-section .section-header h2 {
    font-size: $font-size-xl;
  }
}
</style>
