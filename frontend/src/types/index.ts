/**
 * TypeScript 类型定义模块
 *
 * 集中定义前端所有实体的类型接口，与后端 DTO 对齐
 * 包含：
 * - 用户、课程、题目、试卷、考试等业务实体类型
 * - API 请求/响应类型
 * - 分页类型
 * - 枚举值联合类型
 */

// =====================================================
// 用户相关类型
// =====================================================

/** 用户实体 */
export interface User {
  id: number
  username: string
  nickname: string
  avatar?: string
  role: 'ADMIN' | 'TEACHER' | 'STUDENT'
  status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED'
  createdAt?: string
}

/** 登录请求参数 */
export interface LoginRequest {
  username: string
  password: string
}

/** 注册请求参数 */
export interface RegisterRequest {
  username: string
  password: string
  nickname: string
}

/** 登录响应 */
export interface LoginResponse {
  token: string
  user: UserResponse
}

/** 用户信息响应（后端返回格式） */
export interface UserResponse {
  id: number
  username: string
  nickname: string
  avatar?: string
  role: User['role']
  status: User['status']
  createdAt?: string
}

// =====================================================
// 课程相关类型
// =====================================================

/** 课程实体 */
export interface Course {
  id: number
  name: string
  code: string
  description?: string
  coverUrl?: string
  teacherId: number
  teacherName?: string
  credits: number
  status: 'ACTIVE' | 'INACTIVE'
  createdAt?: string
  deadline?: string
}

// =====================================================
// 题目相关类型
// =====================================================

/**
 * 题目实体（题库中的完整题目，包含答案）
 * 题目为纯内容模板，无分值；分值在考试中按题目类型配置
 */
export interface Question {
  id: number
  content: string
  type: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'FILL_BLANK' | 'ESSAY'
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  teacherId: number
  subject: string
  options?: QuestionOption[]
  correctAnswer?: string | string[] | boolean | null
  scoringCriteria?: ScoringCriterion[]
  explanation?: string
  blankCount?: number
}

/**
 * 考试题目（学生视角，不含答案）
 * 分值从考试配置中获取，不暴露正确答案
 */
export interface QuestionForExam {
  id: number
  content: string
  type: string
  difficulty: string
  score: number
  subject?: string
  options?: QuestionOption[]
  blankCount?: number
}

/** 题目选项 */
export interface QuestionOption {
  id: string
  text: string
}

/** 评分标准（简答题等主观题的分点给分规则） */
export interface ScoringCriterion {
  point: string
  score: number
}

// =====================================================
// 试卷相关类型
// =====================================================

/** 试卷实体（仅题目ID列表，用于选题工具） */
export interface Paper {
  id: number
  name: string
  description?: string
  teacherId: number
  questionIds: number[]
  createdAt?: string
}

/** 自动组卷时每种题型的配置 */
export interface TypeConfig {
  count: number
  difficulty?: string
  subject?: string
}

/** 自动组卷请求参数 */
export interface AutoGeneratePaperRequest {
  name: string
  description?: string
  subject?: string
  difficulty?: string
  singleChoice?: TypeConfig
  multipleChoice?: TypeConfig
  trueFalse?: TypeConfig
  fillBlank?: TypeConfig
  essay?: TypeConfig
  singleChoiceCount?: number
  multipleChoiceCount?: number
  trueFalseCount?: number
  fillBlankCount?: number
  essayCount?: number
}

/** 手动组卷提交数据 */
export interface ManualPaperSubmitData {
  name: string
  description: string
  questionIds: number[]
}

// =====================================================
// 考试相关类型
// =====================================================

/** 考试实体 */
export interface Exam {
  id: number
  title: string
  description?: string
  courseId: number
  courseName?: string
  teacherId: number
  teacherName?: string
  startedAt: string
  endedAt: string
  duration: number
  totalScore: number
  passScore: number
  status: 'DRAFT' | 'PUBLISHED' | 'STARTED' | 'ENDED'
  createdAt?: string
  /** 学生视角的考试状态 */
  studentExamStatus?: 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED' | 'GRADED'
  examPaper?: ExamPaperData
  /** 教师视角：参与人数 */
  participantCount?: number
  /** 教师视角：已提交人数 */
  submittedCount?: number
  /** 教师视角：待评分人数 */
  pendingGradingCount?: number
}

/** 考试试卷数据（包含题目和分值配置） */
export interface ExamPaperData {
  items: ExamQuestion[]
  typeScores: Record<string, number>
}

/** 考试中的题目（含正确答案，用于教师视角和考试回顾） */
export interface ExamQuestion {
  questionId: number
  content: string
  type: string
  difficulty: string
  options?: QuestionOption[]
  correctAnswer?: unknown
  explanation?: string
  blankCount?: number
  scoringCriteria?: ScoringCriterion[]
}

/** 考试创建请求（与后端 ExamCreateRequest DTO 对齐） */
export interface ExamCreateData {
  title: string
  description?: string
  courseId: number
  paperId: number
  questionScores: Record<string, number>
  startedAt: string
  endedAt: string
  duration: number
  passScore: number
}

/** 考试更新请求（与后端 ExamUpdateRequest DTO 对齐） */
export interface ExamUpdateData {
  title?: string
  description?: string
  courseId?: number
  questionScores?: Record<string, number>
  startedAt?: string
  endedAt?: string
  duration?: number
  passScore?: number
}

// =====================================================
// 考试记录相关类型
// =====================================================

/** 考试会话（学生的一次考试记录） */
export interface ExamSession {
  id: number
  examId: number
  studentId: number
  studentName?: string
  startedAt: string
  submittedAt?: string
  score?: number
  totalScore: number
  answers?: Answer[]
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED' | 'GRADED'
  gradingStatus?: 'PENDING' | 'GRADING' | 'GRADED' | 'COMPLETED'
}

/** 学生答案 */
export interface Answer {
  questionId: number
  answer: string | string[] | null
  isCorrect?: boolean
  score?: number
  questionType?: string
  gradingStatus?: 'PENDING' | 'GRADED'
  teacherComment?: string
}

// =====================================================
// 公告相关类型
// =====================================================

/** 系统公告 */
export interface Announcement {
  id: number
  title: string
  content: string
  type: 'SYSTEM' | 'EXAM' | 'COURSE'
  priority?: 'LOW' | 'MEDIUM' | 'HIGH'
  status?: 'DRAFT' | 'PUBLISHED'
  publisherId: number
  publisherName?: string
  publishedAt?: string
  createdAt?: string
}

// =====================================================
// AI 配置相关类型
// =====================================================

/** AI 配置（每个用户可配置多个 AI 平台） */
export interface AiConfig {
  id: number
  userId: number
  name: string
  baseUrl: string
  apiKey: string
  models: string[]
  activeModel: string | null
  createdAt?: string
}

/** 当前激活的模型信息 */
export interface ActiveModelInfo {
  configId: number
  configName: string
  baseUrl: string
  apiKey: string
  model: string
}

// =====================================================
// 通用响应类型
// =====================================================

/** 后端统一响应格式 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页请求参数 */
export interface PageRequest {
  current: number
  size: number
  orderBy?: string
  asc?: boolean
}

/** 分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// =====================================================
// 轮播图相关类型
// =====================================================

/** 轮播图 */
export interface Carousel {
  id: number
  title: string
  imageUrl: string
  linkUrl?: string
  description?: string
  sortOrder: number
  status: 'ACTIVE' | 'INACTIVE'
  startAt?: string
  endAt?: string
}

// =====================================================
// AI 生成题目相关类型
// =====================================================

/** AI 生成题目请求 */
export interface GenerateQuestionRequest {
  subject: string
  type: string
  difficulty: string
  count: number
  requirements?: string
}

/** AI 生成题目响应 */
export interface GeneratedQuestionResponse {
  questions: GeneratedQuestion[]
}

/** AI 生成的题目 */
export interface GeneratedQuestion {
  content: string
  type: string
  options?: GeneratedOption[]
  correctAnswer: string | string[] | boolean | null
  explanation?: string
}

/** AI 生成的题目选项 */
export interface GeneratedOption {
  id: string
  text: string
}

// =====================================================
// 评分相关类型
// =====================================================

/** 主观题评分请求 */
export interface GradeSubjectiveRequest {
  examSessionId: number
  grades: SubjectiveGrade[]
}

/** 单题评分 */
export interface SubjectiveGrade {
  questionId: number
  score: number
  comment?: string
}

/** 考试结果响应 */
export interface ExamResultResponse {
  examSessionId: number
  examId: number
  examTitle: string
  studentId: number
  studentName?: string
  startedAt?: string
  submittedAt?: string
  objectiveScore: number
  subjectiveScore: number
  totalScore: number
  maxScore: number
  passScore: number
  gradingStatus: 'PENDING' | 'GRADING' | 'GRADED' | 'COMPLETED'
  answers: AnswerDetail[]
}

/** 答案详情（考试结果中展示） */
export interface AnswerDetail {
  questionId: number
  questionContent: string
  questionType: string
  answer: string | string[] | null
  isCorrect?: boolean
  score?: number
  maxScore: number
  gradingStatus?: 'PENDING' | 'GRADED' | 'COMPLETED'
  teacherComment?: string
}