/**
 * Vue Router 路由配置
 *
 * 路由结构：
 * - /login - 登录页（无需认证）
 * - /register - 注册页（无需认证）
 * - /exam/:id/take - 答题页面（独立全屏，不显示侧边栏）
 * - / (MainLayout) - 主布局路由，所有子路由共享侧边栏+顶栏
 *   - /dashboard - 首页仪表盘
 *   - /user - 用户管理（仅 ADMIN）
 *   - /course - 课程管理
 *   - /question - 题库管理（ADMIN/TEACHER）
 *   - /paper - 试卷管理（ADMIN/TEACHER）
 *   - /exam - 考试管理
 *   - /announcement - 公告管理
 *   - /carousel - 轮播图管理（仅 ADMIN）
 *   - /aiconfig - AI 配置（ADMIN/TEACHER）
 *   - /profile - 个人中心
 *
 * 路由守卫（beforeEach）：
 * 1. 设置页面标题为 "XXX - 南方职业学院在线考试系统"
 * 2. 需要认证的页面：未登录时跳转登录页，登录后保存重定向 URL
 * 3. 页面刷新后自动恢复用户信息（从 localStorage token -> 请求 /auth/me）
 * 4. 角色权限校验：ADMIN 拥有 TEACHER 的全部权限，不匹配时跳转首页
 */
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { hasRolePermission } from '@/utils/permission'

// 路由定义
const routes: RouteRecordRaw[] = [
  {
    // 登录页 - 无需认证即可访问
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { requiresAuth: false, title: '登录' }
  },
  {
    // 注册页 - 无需认证即可访问
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { requiresAuth: false, title: '注册' }
  },
  {
    // 答题页面 - 独立全屏页面，不显示侧边栏导航
    // 学生在此页面作答，需要全屏空间以专注于考试
    path: '/exam/:id/take',
    name: 'ExamTake',
    component: () => import('@/views/exam/ExamTake.vue'),
    meta: { requiresAuth: true, roles: ['STUDENT'], title: '参加考试' }
  },
  {
    // 主布局路由 - 所有需要侧边栏和顶栏的页面都放在此路由下
    path: '/',
    component: () => import('@/components/Layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { requiresAuth: true, title: '首页' }
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/UserList.vue'),
        meta: { requiresAuth: true, roles: ['ADMIN'], title: '用户管理' }
      },
      {
        path: 'course',
        name: 'CourseList',
        component: () => import('@/views/course/CourseList.vue'),
        meta: { requiresAuth: true, title: '课程' }
      },
      {
        path: 'course/:id',
        name: 'CourseDetail',
        component: () => import('@/views/course/CourseDetail.vue'),
        meta: { requiresAuth: true, title: '课程详情' }
      },
      {
        path: 'question',
        name: 'QuestionList',
        component: () => import('@/views/question/QuestionList.vue'),
        meta: { requiresAuth: true, roles: ['ADMIN', 'TEACHER'], title: '题库管理' }
      },
      {
        // 创建题目 - 实际重定向到题库列表页并弹出创建对话框
        path: 'question/create',
        name: 'QuestionCreate',
        redirect: { name: 'QuestionList', query: { action: 'create' } },
        meta: { requiresAuth: true, roles: ['ADMIN', 'TEACHER'], title: '创建题目' }
      },
      {
        // 编辑题目 - 实际重定向到题库列表页并弹出编辑对话框
        path: 'question/:id/edit',
        name: 'QuestionEdit',
        redirect: to => ({ name: 'QuestionList', query: { action: 'edit', id: String(to.params.id) } }),
        meta: { requiresAuth: true, roles: ['ADMIN', 'TEACHER'], title: '编辑题目' }
      },
      {
        path: 'paper',
        name: 'PaperList',
        component: () => import('@/views/paper/PaperList.vue'),
        meta: { requiresAuth: true, roles: ['ADMIN', 'TEACHER'], title: '试卷管理' }
      },
      {
        path: 'exam',
        name: 'ExamList',
        component: () => import('@/views/exam/ExamList.vue'),
        meta: { requiresAuth: true, title: '考试' }
      },
      {
        path: 'exam/:id',
        name: 'ExamDetail',
        component: () => import('@/views/exam/ExamDetail.vue'),
        meta: { requiresAuth: true, title: '考试详情' }
      },
      {
        path: 'exam/:id/results',
        name: 'ExamResults',
        component: () => import('@/views/exam/ExamResults.vue'),
        meta: { requiresAuth: true, title: '考试结果' }
      },
      {
        path: 'exam/:id/review',
        name: 'ExamReview',
        component: () => import('@/views/exam/ExamReview.vue'),
        meta: { requiresAuth: true, title: '考试回顾' }
      },
      {
        // 教师/管理员查看指定学生的考试结果
        path: 'exam/:id/student/:studentId',
        name: 'ExamStudentResult',
        component: () => import('@/views/exam/ExamResults.vue'),
        meta: { requiresAuth: true, roles: ['ADMIN', 'TEACHER'], title: '学生考试结果' }
      },
      {
        path: 'announcement',
        name: 'AnnouncementList',
        component: () => import('@/views/announcement/AnnouncementList.vue'),
        meta: { requiresAuth: true, title: '公告' }
      },
      {
        path: 'carousel',
        name: 'CarouselList',
        component: () => import('@/views/carousel/CarouselList.vue'),
        meta: { requiresAuth: true, roles: ['ADMIN'], title: '轮播图管理' }
      },
      {
        path: 'aiconfig',
        name: 'AiConfigList',
        component: () => import('@/views/aiconfig/AiConfigList.vue'),
        meta: { requiresAuth: true, roles: ['ADMIN', 'TEACHER'], title: 'AI配置' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/user/Profile.vue'),
        meta: { requiresAuth: true, title: '个人中心' }
      }
    ]
  }
]

// 创建路由实例，使用 HTML5 History 模式（无 # 号）
const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置路由守卫
// 在每次路由跳转前执行，负责认证检查和权限校验
router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()

  // 1. 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - 南方职业学院在线考试系统`
  }

  // 2. 认证检查：需要认证的页面
  if (to.meta.requiresAuth !== false) {
    // 未登录：跳转到登录页，并保存目标 URL 以便登录后跳回
    if (!authStore.token) {
      next({ name: 'Login', query: { redirect: to.fullPath } })
      return
    }

    // 刷新后先恢复用户信息，避免角色判断误拦截
    // 场景：页面刷新后 token 还在 localStorage 中，但内存中的 user 为空
    if (!authStore.user) {
      const loaded = await authStore.getCurrentUser()
      if (!loaded || !authStore.user) {
        // token 无效或网络错误，跳转到登录页
        next({ name: 'Login', query: { redirect: to.fullPath } })
        return
      }
    }

    // 3. 角色权限检查
    // ADMIN 角色拥有 TEACHER 的全部权限（通过 hasRolePermission 处理）
    const roles = to.meta.roles as string[] | undefined
    if (roles && roles.length > 0) {
      const userRole = authStore.user?.role
      if (!userRole || !hasRolePermission(userRole, roles)) {
        ElMessage.error('无权访问该页面')
        next({ name: 'Dashboard' })
        return
      }
    }
  }

  next()
})

export default router
