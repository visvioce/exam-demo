<template>
  <div class="exam-list base-list-page">
    <div class="page-header">
      <h2>{{ isStudent ? '我的考试' : '考试管理' }}</h2>
      <el-button type="primary" @click="handleCreate" v-if="hasPermission(['ADMIN', 'TEACHER'])">
        <el-icon><Plus /></el-icon>
        创建考试
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :model="searchForm" label-width="80px">
        <el-form-item label="课程">
          <el-select v-model="searchForm.courseId" placeholder="全部" clearable @change="handleCourseChange" class="search-control">
            <el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <div class="filter-tabs">
            <button
              type="button"
              v-for="item in (isStudent ? studentStatusOptions : statusOptions)" 
              :key="item.value"
              :class="['tab-item', { active: searchForm.status === item.value }]"
              :aria-pressed="searchForm.status === item.value"
              @click="handleStatusChange(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 学生视图：我的考试 -->
    <template v-if="isStudent">
      <el-card class="table-card">
        <template #header>
          <span>我的考试</span>
        </template>
        <el-table :data="filteredMyExams" v-loading="loading" stripe table-layout="auto" :fit="true">
          <el-table-column prop="title" label="考试名称" min-width="200" />
          <el-table-column prop="courseName" label="课程" min-width="120" />
          <el-table-column prop="startedAt" label="开始时间" min-width="145">
            <template #default="{ row }">
              {{ formatDate(row.startedAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="endedAt" label="结束时间" min-width="145">
            <template #default="{ row }">
              {{ formatDate(row.endedAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="时长" width="64" align="center">
            <template #default="{ row }">{{ row.duration }}′</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="getStatusColor(row.status)">{{ getStatusName(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="88" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button size="small" type="primary" @click="handleTakeExam(row)"
                  :disabled="!canTakeExam(row)">
                  {{ getExamActionText(row) }}
                </el-button>
                <el-button
                  v-if="canViewResult(row)"
                  size="small"
                  @click="handleViewResult(row)"
                >
                  查看回顾
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- 教师/管理员视图：考试列表 -->
    <template v-else>
      <el-card class="table-card">
        <el-table :data="exams" v-loading="loading" stripe table-layout="auto" :fit="true" :scrollbar-always-on="false">
          <el-table-column prop="id" label="ID" width="52" />
          <el-table-column prop="title" label="考试名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="courseName" label="课程" min-width="100" show-overflow-tooltip />
          <el-table-column prop="startedAt" label="开始时间" min-width="145">
            <template #default="{ row }">
              {{ formatDate(row.startedAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="endedAt" label="结束时间" min-width="145">
            <template #default="{ row }">
              {{ formatDate(row.endedAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="时长" width="64" align="center">
            <template #default="{ row }">{{ row.duration }}′</template>
          </el-table-column>
          <el-table-column prop="totalScore" label="总分" width="64" align="center" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="getStatusColor(row.status)">{{ getStatusName(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="考试记录" width="76" align="center" v-if="!allDraft">
            <template #default="{ row }">
              <template v-if="row.participantCount">
                <span class="stat-value">{{ row.participantCount }}</span>
                <span class="stat-sep">/</span>
                <span class="stat-submitted">{{ row.submittedCount || 0 }}</span>
                <span v-if="row.pendingGradingCount" class="stat-pending" :title="row.pendingGradingCount + ' 份待批阅'">
                  <span class="stat-sep">-</span>{{ row.pendingGradingCount }}
                </span>
              </template>
              <span v-else class="stat-empty">-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="88" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <ActionButtons
                  :show-view="false"
                  :show-edit="canEdit(row) && (!row.status || row.status === 'DRAFT')"
                  :show-delete="canEdit(row) && (!row.status || row.status === 'DRAFT')"
                  @edit="handleEdit(row)"
                  @delete="handleDelete(row)"
                />
                <el-button size="small" type="primary" @click="handlePublish(row)"
                  v-if="canEdit(row) && (!row.status || row.status === 'DRAFT')">发布</el-button>
                <el-button size="small" type="warning" @click="handleEndExam(row)"
                  v-if="canEdit(row) && row.status === 'STARTED'">结束考试</el-button>
                <el-button size="small" @click="handleViewPaper(row)">试卷详情</el-button>
                <el-button size="small" @click="handleViewResults(row)"
                  v-if="canEdit(row) && row.status && row.status !== 'DRAFT'">考试记录</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination">
          <el-pagination
            v-model:current-page="pagination.current"
            v-model:page-size="pagination.size"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadExams"
            @current-change="loadExams"
          />
        </div>
      </el-card>
    </template>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="editDialogVisible" :title="isEdit ? '编辑考试' : '创建考试'" width="720px" class="base-dialog" :close-on-click-modal="false">
      <el-form :model="examForm" :rules="rules" ref="examFormRef" label-width="100px">
        <el-form-item label="考试名称" prop="title">
          <el-input v-model="examForm.title" placeholder="请输入考试名称" />
        </el-form-item>
        <el-form-item label="所属课程" prop="courseId">
          <el-select v-model="examForm.courseId" placeholder="请选择课程" class="full-width">
            <el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isEdit" label="选择试卷" prop="paperId">
          <div style="display: flex; align-items: center; gap: 8px; width: 100%">
            <el-select v-model="selectedPaperId" placeholder="请选择试卷" @change="onPaperSelected" clearable style="flex: 1">
              <el-option
                v-for="paper in paperList"
                :key="paper.id"
                :label="paper.name"
                :value="paper.id"
              />
            </el-select>
            <IconActionButton
              v-if="selectedPaperId !== null && examQuestionIds.length > 0"
              :icon="View"
              tooltip="查看试卷"
              show-tooltip
              aria-label="查看试卷"
              button-class="paper-view-btn"
              @click="handleViewCreatePaper"
            />
          </div>
        </el-form-item>
        <el-form-item v-if="selectedPaperId !== null || (isEdit && examQuestionIds.length > 0)" label="题型分值">
          <div class="type-score-config">
            <div v-for="item in questionTypeCounts" :key="item.type" class="type-score-row">
              <el-tag :type="getTypeColorFromFormat(item.type)" size="small">{{ getTypeNameFromFormat(item.type) }}</el-tag>
              <span class="type-count">{{ item.count }} 题 × 每题</span>
              <el-input-number v-model="typeScoreConfig[item.type]" :min="0" :max="100" size="small" controls-position="right" style="width: 100px" />
              <span class="type-subtotal">= {{ item.count * (typeScoreConfig[item.type] || 0) }} 分</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="开始时间" prop="startedAt">
          <el-date-picker
            v-model="examForm.startedAt"
            type="datetime"
            placeholder="选择开始时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
            :shortcuts="dateShortcuts"
            @change="onStartTimeChange"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endedAt">
          <el-date-picker
            v-model="examForm.endedAt"
            type="datetime"
            placeholder="选择结束时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
            :shortcuts="dateShortcuts"
            :disabled-date="disabledEndDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="考试时长" prop="duration">
              <el-input-number v-model="examForm.duration" :min="1" :max="300" />
              <span class="unit-label">分钟</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="及格分率" prop="passScoreRate">
              <el-input-number v-model="examForm.passScoreRate" :min="0" :max="100" :step="5" />
              <span class="unit-label">%（{{ computedPassScore }} 分）</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="考试说明">
          <el-input v-model="examForm.description" type="textarea" :rows="3" placeholder="请输入考试说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看考试元信息 -->
    <el-dialog v-model="viewDialogVisible" title="考试详情" width="600px" class="base-dialog">
      <el-descriptions :column="2" border v-if="currentExam">
        <el-descriptions-item label="考试名称">{{ currentExam.title }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusColor(currentExam.status)">{{ getStatusName(currentExam.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="课程">{{ currentExam.courseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建者">{{ currentExam.teacherName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatDate(currentExam.startedAt) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ formatDate(currentExam.endedAt) }}</el-descriptions-item>
        <el-descriptions-item label="考试时长">{{ currentExam.duration }}分钟</el-descriptions-item>
        <el-descriptions-item label="总分/及格分">{{ currentExam.totalScore }} / {{ currentExam.passScore }}</el-descriptions-item>
        <el-descriptions-item label="考试说明" :span="2">{{ currentExam.description || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 试卷详情（共享组件） -->
    <PaperDetail v-model:visible="paperDialogVisible" :title="paperViewTitle" :description="paperViewDescription" :questions="paperViewQuestions" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { examApi } from '@/api/exam'
import { courseApi } from '@/api/course'
import { questionApi } from '@/api/question'
import { paperApi } from '@/api/paper'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, View } from '@element-plus/icons-vue'
import { getStatusName, getStatusColor, formatDate, getTypeName as getTypeNameFromFormat, getTypeColor as getTypeColorFromFormat } from '@/utils/format'
import { getErrorMessage } from '@/utils/error'
import { useListPage } from '@/composables/useListPage'
import type { FormInstance, FormRules } from 'element-plus'
import type { Exam, Course, Paper, ExamCreateData, Question, ExamQuestion } from '@/types'
import ActionButtons from '@/components/ActionButtons.vue'
import IconActionButton from '@/components/IconActionButton.vue'
import PaperDetail from '@/components/PaperDetail.vue'
import type { PaperQuestionItem } from '@/components/PaperDetail.vue'

const router = useRouter()
const authStore = useAuthStore()

const submitting = ref(false)
const myExams = ref<Exam[]>([])
const courses = ref<Course[]>([])
const editDialogVisible = ref(false)
const viewDialogVisible = ref(false)
const paperDialogVisible = ref(false)
const paperViewTitle = ref('')
const paperViewDescription = ref('')
const paperViewQuestions = ref<PaperQuestionItem[]>([])
const isEdit = ref(false)
const examFormRef = ref<FormInstance>()
const currentExam = ref<Exam | null>(null)

const statusOptions = [
  { label: '全部', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '进行中', value: 'STARTED' },
  { label: '已结束', value: 'ENDED' }
]

const studentStatusOptions = [
  { label: '全部', value: '' },
  { label: '未开始', value: 'PUBLISHED' },
  { label: '进行中', value: 'STARTED' },
  { label: '已结束', value: 'ENDED' }
]

const isStudent = computed(() => authStore.user?.role === 'STUDENT')
const allDraft = computed(() => exams.value.length > 0 && exams.value.every(e => !e.status || e.status === 'DRAFT'))
const myExamsLoading = ref(false)

const searchForm = reactive({
  courseId: null as number | null,
  status: ''
})

const {
  data: exams,
  loading: teacherLoading,
  total,
  pagination,
  loadData: loadTeacherExams,
  reset
} = useListPage<Exam>({
  fetchFn: (params) => examApi.page({
    ...params,
    courseId: searchForm.courseId || undefined,
    status: searchForm.status || undefined
  }),
  immediate: false
})

const loading = computed(() => (isStudent.value ? myExamsLoading.value : teacherLoading.value))

const examForm = reactive({
  id: 0,
  title: '',
  description: '',
  courseId: null as number | null,
  startedAt: null as string | null,
  endedAt: null as string | null,
  duration: 60,
  passScoreRate: 60
})

const dateShortcuts = [
  {
    text: '今天 08:00',
    value: () => {
      const d = new Date()
      d.setHours(8, 0, 0, 0)
      return formatDateTime(d)
    }
  },
  {
    text: '明天 08:00',
    value: () => {
      const d = new Date()
      d.setDate(d.getDate() + 1)
      d.setHours(8, 0, 0, 0)
      return formatDateTime(d)
    }
  },
  {
    text: '三天后 08:00',
    value: () => {
      const d = new Date()
      d.setDate(d.getDate() + 3)
      d.setHours(8, 0, 0, 0)
      return formatDateTime(d)
    }
  },
  {
    text: '一周后 08:00',
    value: () => {
      const d = new Date()
      d.setDate(d.getDate() + 7)
      d.setHours(8, 0, 0, 0)
      return formatDateTime(d)
    }
  }
]

function formatDateTime(d: Date) {
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function toPickerValue(apiDate: string | null | undefined): string | null {
  if (!apiDate) return null
  return apiDate.replace('T', ' ').substring(0, 19)
}

function onStartTimeChange() {
  if (examForm.startedAt && examForm.duration > 0) {
    const start = new Date(examForm.startedAt.replace(' ', 'T'))
    const end = new Date(start.getTime() + examForm.duration * 60000)
    examForm.endedAt = formatDateTime(end)
  }
}

function disabledEndDate(date: Date) {
  if (!examForm.startedAt) return false
  const start = new Date(examForm.startedAt.replace(' ', 'T'))
  start.setHours(0, 0, 0, 0)
  return date.getTime() < start.getTime()
}

const examQuestionIds = ref<number[]>([])
const typeScoreConfig = reactive<Record<string, number>>({
  SINGLE_CHOICE: 0,
  MULTIPLE_CHOICE: 0,
  TRUE_FALSE: 0,
  FILL_BLANK: 0,
  ESSAY: 0
})

const paperList = ref<Paper[]>([])
const selectedPaperId = ref<number | null>(null)

const examPaperQuestionTypeMap = ref<Record<number, string>>({})

const questionTypeCounts = computed(() => {
  const counts: Record<string, number> = {}
  examQuestionIds.value.forEach(qid => {
    const type = examPaperQuestionTypeMap.value[qid] || ''
    if (type) {
      counts[type] = (counts[type] || 0) + 1
    }
  })
  return Object.entries(counts).map(([type, count]) => ({ type, count }))
})

const computedTotalScore = computed(() => {
  let total = 0
  for (const { type, count } of questionTypeCounts.value) {
    total += count * (typeScoreConfig[type] || 0)
  }
  return total
})

const computedPassScore = computed(() => {
  return Math.round(computedTotalScore.value * examForm.passScoreRate / 100)
})

const validatePassScore = (_rule: unknown, _value: unknown, callback: (error?: Error) => void) => {
  if (computedTotalScore.value > 0 && computedPassScore.value > computedTotalScore.value) {
    callback(new Error('及格分不能大于总分'))
  } else {
    callback()
  }
}

const rules = reactive<FormRules>({
  title: [{ required: true, message: '请输入考试名称', trigger: 'blur' }],
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  paperId: [{
    validator: (_rule: unknown, _value: unknown, callback: (error?: Error) => void) => {
      if (!selectedPaperId.value && !isEdit.value) {
        callback(new Error('请选择一份试卷'))
      } else {
        callback()
      }
    },
    trigger: 'change'
  }],
  startedAt: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endedAt: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    { validator: (_rule: any, value: any, callback: any) => {
      if (!value || !examForm.startedAt) { callback(); return; }
      if (new Date(value.replace(' ', 'T')) <= new Date(examForm.startedAt.replace(' ', 'T'))) {
        callback(new Error('结束时间必须晚于开始时间'))
      } else { callback(); }
    }, trigger: 'change' }
  ],
  duration: [
    { required: true, message: '请输入考试时长', trigger: 'blur' },
    { type: 'number', min: 1, message: '考试时长必须大于0', trigger: 'blur' }
  ],
  passScoreRate: [
    { required: true, message: '请输入及格分率', trigger: 'blur' },
    { type: 'number', min: 0, max: 100, message: '及格分率需在0-100之间', trigger: 'blur' },
    { validator: validatePassScore, trigger: 'blur' }
  ]
})

// 学生视图：筛选后的考试列表
const filteredMyExams = computed(() => {
  let result = [...myExams.value]

  // 按课程筛选
  if (searchForm.courseId) {
    result = result.filter(exam => exam.courseId === searchForm.courseId)
  }

  // 按状态筛选
  if (searchForm.status) {
    result = result.filter(exam => exam.status === searchForm.status)
  }

  return result
})

function hasPermission(roles: string[]) {
  return roles.includes(authStore.user?.role || '')
}

function canEdit(exam: Exam) {
  const role = authStore.user?.role
  if (role === 'ADMIN' || role === 'TEACHER') {
    return exam.teacherId === authStore.user?.id
  }
  return false
}

function canTakeExam(exam: Exam) {
  // 如果学生已经提交或已完成考试，则不能再次参加
  if (exam.studentExamStatus === 'SUBMITTED' || exam.studentExamStatus === 'GRADED') {
    return false
  }
  if (exam.status !== 'PUBLISHED' && exam.status !== 'STARTED') return false
  
  // 如果学生有进行中的考试会话，允许继续（与后端30秒宽限时间一致）
  if (exam.studentExamStatus === 'IN_PROGRESS') {
    const now = new Date().getTime()
    const end = new Date(exam.endedAt).getTime()
    // 给予30秒宽限时间
    return now <= end + 30000
  }
  
  const now = new Date().getTime()
  const start = new Date(exam.startedAt).getTime()
  const end = new Date(exam.endedAt).getTime()
  return now >= start && now <= end
}

function getExamActionText(exam: Exam) {
  // 优先显示学生考试状态
  if (exam.studentExamStatus === 'SUBMITTED') return '已提交'
  if (exam.studentExamStatus === 'GRADED') return '已完成'
  if (exam.studentExamStatus === 'IN_PROGRESS') return '继续考试'

  if (exam.status === 'ENDED') return '已结束'
  if (exam.status === 'DRAFT') return '未发布'
  const now = new Date().getTime()
  const start = new Date(exam.startedAt).getTime()
  const end = new Date(exam.endedAt).getTime()
  if (now < start) return '未开始'
  if (now > end) return '已结束'
  return '参加考试'
}

function canViewResult(exam: Exam) {
  // 只有学生已经提交或已完成考试，才能查看回顾
  // 未参加考试的学生不能查看回顾（即使考试已结束）
  return exam.studentExamStatus === 'SUBMITTED' ||
         exam.studentExamStatus === 'GRADED'
}

function handleViewResult(exam: Exam) {
  // 跳转到考试回顾页面
  router.push(`/exam/${exam.id}/review`)
}

async function loadExams() {
  if (isStudent.value) {
    myExamsLoading.value = true
    try {
      const res = await examApi.getMyExams()
      myExams.value = res.data.records || []
    } catch (error) {
      ElMessage.error('加载考试失败')
    } finally {
      myExamsLoading.value = false
    }
    return
  }

  await loadTeacherExams()
}

async function loadCourses() {
  try {
    let res
    if (hasPermission(['ADMIN', 'TEACHER'])) {
      // 管理员和教师查看自己管理的课程
      res = await courseApi.list()
    } else {
      // 学生查看已加入的课程（用于筛选）
      res = await courseApi.getMyCourses()
    }
    courses.value = res.data || []
    if (searchForm.courseId && !courses.value.some((course) => course.id === searchForm.courseId)) {
      searchForm.courseId = null
    }
  } catch (error) {
    ElMessage.error('加载课程失败')
  }
}

function handleCourseChange(value: number | null) {
  void value
  if (isStudent.value) return
  void reset()
}

function handleStatusChange(value: string) {
  searchForm.status = searchForm.status === value ? '' : value
  if (isStudent.value) return
  void reset()
}

function handleReset() {
  if (isStudent.value) {
    searchForm.courseId = null
    searchForm.status = ''
    return
  }
  searchForm.courseId = null
  searchForm.status = ''
  void reset()
}

function handleView(row: Exam) {
  currentExam.value = row
  viewDialogVisible.value = true
}

async function handleViewPaper(row: Exam) {
  try {
    const res = await examApi.getById(row.id)
    const exam = res.data
    paperViewTitle.value = exam.title
    paperViewDescription.value = exam.description || ''
    const items = exam.examPaper?.items || []
    paperViewQuestions.value = items.map((q: ExamQuestion) => ({
      content: q.content || '',
      type: q.type || '',
      difficulty: q.difficulty || '',
      options: q.options || [],
      correctAnswer: q.correctAnswer,
      explanation: q.explanation || ''
    }))
    paperDialogVisible.value = true
  } catch {
    ElMessage.warning('加载试卷详情失败')
  }
}

function handleCreate() {
  isEdit.value = false
  Object.assign(examForm, {
    id: 0,
    title: '',
    description: '',
    courseId: null,
    startedAt: null,
    endedAt: null,
    duration: 60,
    passScoreRate: 60
  })
  examQuestionIds.value = []
  examPaperQuestionTypeMap.value = {}
  selectedPaperId.value = null
  Object.keys(typeScoreConfig).forEach(k => { typeScoreConfig[k] = 0 })
  loadPapers()
  editDialogVisible.value = true
}

async function loadPapers() {
  try {
    const res = await paperApi.list()
    paperList.value = res.data || []
  } catch {
    paperList.value = []
  }
}

async function onPaperSelected(paperId: number | null) {
  if (!paperId) {
    examQuestionIds.value = []
    examPaperQuestionTypeMap.value = {}
    return
  }
  try {
    const res = await paperApi.getById(paperId)
    const paper = res.data
    examQuestionIds.value = paper.questionIds || []
    examPaperQuestionTypeMap.value = {}
    const res2 = await questionApi.getByIds(examQuestionIds.value)
    const questions = res2.data || []
    questions.forEach((q: Question) => {
      examPaperQuestionTypeMap.value[q.id] = q.type
    })
  } catch {
    examQuestionIds.value = []
    examPaperQuestionTypeMap.value = {}
    ElMessage.warning('加载试卷题目失败')
  }
}

async function handleEdit(row: Exam) {
  isEdit.value = true
  Object.assign(examForm, {
    id: row.id,
    title: row.title,
    description: row.description,
    courseId: row.courseId,
    startedAt: toPickerValue(row.startedAt),
    endedAt: toPickerValue(row.endedAt),
    duration: row.duration,
    passScoreRate: row.totalScore > 0 ? Math.round(row.passScore / row.totalScore * 100) : 60
  })
  examQuestionIds.value = []
  examPaperQuestionTypeMap.value = {}
  selectedPaperId.value = null
  Object.keys(typeScoreConfig).forEach(k => { typeScoreConfig[k] = 0 })

  try {
    const res = await examApi.getById(row.id)
    const examDetail = res.data
    if (examDetail?.examPaper) {
      examQuestionIds.value = examDetail.examPaper.items?.map((q: ExamQuestion) => q.questionId) ?? []
      examDetail.examPaper.items?.forEach((q: ExamQuestion) => {
        examPaperQuestionTypeMap.value[q.questionId] = q.type
      })
      if (examDetail.examPaper.typeScores) {
        Object.entries(examDetail.examPaper.typeScores).forEach(([k, v]: [string, any]) => {
          typeScoreConfig[k] = v
        })
      }
    }
  } catch {
    ElMessage.warning('无法加载题目信息')
  }
  editDialogVisible.value = true
}

async function handlePublish(row: Exam) {
  try {
    await ElMessageBox.confirm('确定要发布该考试吗？发布后学生可以参加考试。', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await examApi.publish(row.id)
    ElMessage.success('发布成功')
    void loadExams()
  } catch {
    // 取消
  }
}

async function handleEndExam(row: Exam) {
  try {
    await ElMessageBox.confirm(
      '确定要提前结束该考试吗？结束后学生将无法继续答题。',
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await examApi.end(row.id)
    ElMessage.success('考试已结束')
    void loadExams()
  } catch {
    // 取消
  }
}

async function handleDelete(row: Exam) {
  try {
    await ElMessageBox.confirm('确定要删除该考试吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await examApi.delete(row.id)
    ElMessage.success('删除成功')
    void loadExams()
  } catch {
    // 取消
  }
}

async function handleTakeExam(row: Exam) {
  try {
    // 先调用 start API 创建考试会话
    const res = await examApi.start(row.id)
    const sessionId = res.data.id
    // 带着 sessionId 跳转到考试页面
    router.push(`/exam/${row.id}/take?sessionId=${sessionId}`)
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error, '开始考试失败'))
  }
}

async function handleViewCreatePaper() {
  if (!selectedPaperId.value) return
  try {
    const res = await paperApi.getById(selectedPaperId.value)
    const paper = res.data
    paperViewTitle.value = paper.name || ''
    paperViewDescription.value = paper.description || ''
    const ids = paper.questionIds || []
    if (ids.length > 0) {
      const res2 = await questionApi.getByIds(ids)
      const questions = (res2.data || []) as Question[]
      paperViewQuestions.value = questions.map(q => ({
        content: q.content || '',
        type: q.type || '',
        difficulty: q.difficulty || '',
        options: q.options || [],
        correctAnswer: q.correctAnswer,
        explanation: q.explanation || ''
      }))
    } else {
      paperViewQuestions.value = []
    }
    paperDialogVisible.value = true
  } catch {
    ElMessage.warning('加载试卷详情失败')
  }
}

function handleViewResults(row: Exam) {
  router.push(`/exam/${row.id}/results`)
}

async function handleSubmit() {
  if (!examFormRef.value) return

  if (!isEdit.value) {
    if (!selectedPaperId.value) {
      ElMessage.warning('请选择试卷')
      return
    }
    if (examQuestionIds.value.length === 0) {
      ElMessage.warning('试卷中没有题目')
      return
    }
  }

  await examFormRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        const questionScores: Record<string, number> = {}
        for (const { type } of questionTypeCounts.value) {
          const typeScore = typeScoreConfig[type] ?? 0
          if (typeScore > 0) {
            questionScores[type] = typeScore
          }
        }
        const data: Record<string, unknown> = {
          title: examForm.title,
          description: examForm.description,
          courseId: examForm.courseId!,
          questionScores: questionScores,
          startedAt: examForm.startedAt?.replace(' ', 'T'),
          endedAt: examForm.endedAt?.replace(' ', 'T'),
          duration: examForm.duration,
          passScore: computedPassScore.value
        }
        if (!isEdit.value) {
          data.paperId = selectedPaperId.value
        }

        if (isEdit.value) {
          await examApi.update(examForm.id, data)
          ElMessage.success('更新成功')
        } else {
          await examApi.create(data as unknown as ExamCreateData)
          ElMessage.success('创建成功，请在列表点击“发布”后学生才可见')
        }
        editDialogVisible.value = false
        void loadExams()
      } catch (error: unknown) {
        ElMessage.error(getErrorMessage(error, '操作失败'))
      } finally {
        submitting.value = false
      }
    }
  })
}

onMounted(() => {
  void loadExams()
  loadCourses()
})
</script>

<style scoped lang="scss">
@use '@/styles/design-tokens.scss' as *;
@use '@/styles/views/base-list.scss';

.exam-list {
  .full-width {
    width: 100%;
  }

  .unit-label {
    margin-left: $spacing-sm;
  }

  .table-actions {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 6px;

    .el-button {
      width: 100%;
      margin: 0;
    }
  }

  .stat-value {
    font-weight: 600;
    color: $text-primary;
  }
  .stat-submitted {
    color: $text-secondary;
  }
  .stat-pending {
    color: $text-tertiary;
    cursor: help;
  }
  .stat-sep {
    color: $text-quaternary;
    margin: 0 1px;
  }
  .stat-empty {
    color: $text-quaternary;
  }

  .paper-view-btn {
    color: $text-quaternary;

    &:hover {
      color: $text-tertiary;
    }
  }
}
</style>
