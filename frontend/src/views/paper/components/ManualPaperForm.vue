<template>
  <div class="manual-paper-form">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="试卷名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入试卷名称" />
      </el-form-item>
      <div class="section-title">题目管理</div>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入试卷描述（可选）" />
      </el-form-item>
      <el-form-item label="题目" prop="questions">
        <div class="questions-editor">
          <div class="questions-toolbar">
            <span class="toolbar-label">题目列表</span>
            <div class="toolbar-actions">
              <span class="question-count">共 {{ form.questions.length }} 题</span>
              <el-button size="small" @click="removeDuplicates" :disabled="!hasDuplicates">
                去重
                <el-badge v-if="duplicateCount > 0" :value="duplicateCount" type="warning" />
              </el-button>
            </div>
          </div>

          <div class="questions-list">
            <div v-for="(_questionId, index) in form.questions" :key="index" class="question-row">
              <span class="question-index-label">{{ index + 1 }}.</span>
              <el-select v-model="form.questions[index]" placeholder="选择题目" class="question-select" filterable size="small">
                <el-option
                  v-for="q in questions"
                  :key="q.id"
                  :label="getQuestionOptionLabel(q)"
                  :value="q.id"
                />
              </el-select>
              <DeleteActionButton aria-label="删除题目" @click="removeQuestion(index)" />
            </div>
            <div v-if="form.questions.length === 0" class="questions-empty">
              暂无题目，从题库中选择或添加
            </div>
          </div>
          <div class="add-buttons">
            <el-button type="primary" @click="showQuestionSelector" size="small">从题库选择</el-button>
          </div>
        </div>
      </el-form-item>
    </el-form>

    <!-- 题库选择对话框 -->
    <el-dialog v-model="questionSelectorVisible" title="从题库选择题目" width="90%" top="3vh" class="selector-dialog">
      <div class="selector-filter-bar">
        <el-form :inline="true" :model="questionFilters">
          <el-form-item label="类型">
            <el-select v-model="questionFilters.type" placeholder="全部" clearable size="small" class="filter-type">
              <el-option label="单选题" value="SINGLE_CHOICE" />
              <el-option label="多选题" value="MULTIPLE_CHOICE" />
              <el-option label="判断题" value="TRUE_FALSE" />
              <el-option label="填空题" value="FILL_BLANK" />
              <el-option label="简答题" value="ESSAY" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="questionFilters.difficulty" placeholder="全部" clearable size="small" class="filter-difficulty">
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
            </el-select>
          </el-form-item>
          <el-form-item label="学科">
            <el-input v-model="questionFilters.subject" placeholder="学科" clearable size="small" class="filter-subject" />
          </el-form-item>
          <el-form-item label="关键字">
            <el-input v-model="questionFilters.keyword" placeholder="题目内容" clearable size="small" class="filter-keyword" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="small" @click="handleQuestionSearch">搜索</el-button>
            <el-button size="small" @click="handleQuestionReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table
        ref="questionTableRef"
        :data="questionTableData"
        @selection-change="handleSelectionChange"
        table-layout="fixed"
        :fit="true"
        max-height="420"
        class="selector-table"
        row-key="id"
      >
        <el-table-column type="selection" width="42" reserve-selection />
        <el-table-column prop="id" label="ID" width="56" />
        <el-table-column label="题目信息" min-width="320">
          <template #default="{ row }">
            <div class="question-preview-cell">
              <div class="question-content-text">{{ row.content }}</div>
              <div class="question-tags">
                <el-tag :type="getTypeColor(row.type)" size="small">{{ getTypeName(row.type) }}</el-tag>
                <el-tag :type="getDifficultyColor(row.difficulty)" size="small">
                  {{ getDifficultyName(row.difficulty) }}
                </el-tag>
                <span v-if="row.subject" class="subject-badge">{{ row.subject }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="subject" label="学科" min-width="72" />
      </el-table>

      <div class="question-pagination">
        <el-pagination
          v-model:current-page="questionPagination.current"
          v-model:page-size="questionPagination.size"
          :page-sizes="[10, 20, 50]"
          :total="questionPagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadQuestions"
          @current-change="loadQuestions"
        />
      </div>

      <template #footer>
        <span class="selected-summary">已选择 {{ selectedQuestions.length }} 道题目</span>
        <el-button size="small" @click="questionSelectorVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="confirmSelectQuestions" :disabled="selectedQuestions.length === 0">
          确定添加
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 手动组卷表单组件
 * 
 * 从题库中手动选择题目组成试卷：
 * - 试卷基本信息（名称、描述）
 * - 题目管理：支持添加/删除/去重/清空操作
 * - 题库选择对话框：支持按类型/难度/学科/关键字筛选，跨页多选
 * - 编辑模式：支持传入初始数据回填（通过 initialData prop）
 * - 重复题目检测与一键去重
 * 
 * 通过 defineExpose 暴露 handleSubmit 方法供父组件调用。
 */

import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { Paper, Question, ManualPaperSubmitData } from '@/types'
import DeleteActionButton from '@/components/DeleteActionButton.vue'

const props = defineProps<{
  questions: Question[]
  initialData?: Paper | null
}>()

const emit = defineEmits<{
  (e: 'submit', data: ManualPaperSubmitData): void
  (e: 'cancel'): void
}>()

const formRef = ref<FormInstance>()
const questionTableRef = ref()
const submitting = ref(false)
const questionSelectorVisible = ref(false)
// 跨页持久化所有已选题目
const selectedQuestions = ref<Question[]>([])

const form = reactive({
  name: '',
  description: '',
  questions: [] as number[]
})

const rules = reactive<FormRules>({
  name: [{ required: true, message: '请输入试卷名称', trigger: 'blur' }]
})

const questionFilters = reactive({
  type: '',
  difficulty: '',
  subject: '',
  keyword: ''
})

const questionPagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

const duplicateCount = computed(() => {
  const questionIds = form.questions.filter(id => id !== 0)
  const uniqueIds = new Set(questionIds)
  return questionIds.length - uniqueIds.size
})

const hasDuplicates = computed(() => duplicateCount.value > 0)

const filteredQuestions = computed(() => {
  let filtered = props.questions

  if (questionFilters.type) {
    filtered = filtered.filter(q => q.type === questionFilters.type)
  }
  if (questionFilters.difficulty) {
    filtered = filtered.filter(q => q.difficulty === questionFilters.difficulty)
  }
  if (questionFilters.subject) {
    const subjectLower = questionFilters.subject.toLowerCase()
    filtered = filtered.filter(q => q.subject?.toLowerCase().includes(subjectLower))
  }
  if (questionFilters.keyword) {
    const keywordLower = questionFilters.keyword.toLowerCase()
    filtered = filtered.filter(q => q.content?.toLowerCase().includes(keywordLower))
  }

  return filtered
})

const questionTableData = computed(() => {
  const start = (questionPagination.current - 1) * questionPagination.size
  const end = start + questionPagination.size
  return filteredQuestions.value.slice(start, end)
})

watch(() => props.initialData, (val) => {
  if (val) {
    form.name = val.name
    form.description = val.description || ''
    form.questions = val.questionIds ? [...val.questionIds] : []
  }
}, { immediate: true })

watch(filteredQuestions, (list) => {
  questionPagination.total = list.length
  const maxPage = Math.max(1, Math.ceil(list.length / questionPagination.size))
  if (questionPagination.current > maxPage) {
    questionPagination.current = maxPage
  }
}, { immediate: true })

function getTypeName(type: string) {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    FILL_BLANK: '填空题',
    ESSAY: '简答题'
  }
  return map[type] || type
}

function getQuestionOptionLabel(question: Question): string {
  const compact = (question.content || '').replace(/\s+/g, ' ').trim()
  const preview = compact.length > 50 ? `${compact.slice(0, 50)}...` : compact
  return `${question.id}. ${preview} (${getTypeName(question.type)})`
}

function getTypeColor(type: string) {
  const map: Record<string, string> = {
    SINGLE_CHOICE: 'info',
    MULTIPLE_CHOICE: 'info',
    TRUE_FALSE: 'info',
    FILL_BLANK: 'info',
    ESSAY: 'info'
  }
  return map[type] || undefined
}

function getDifficultyName(difficulty: string) {
  const map: Record<string, string> = {
    EASY: '简单',
    MEDIUM: '中等',
    HARD: '困难'
  }
  return map[difficulty] || difficulty
}

function getDifficultyColor(difficulty: string) {
  const map: Record<string, string> = {
    EASY: 'info',
    MEDIUM: 'info',
    HARD: 'primary'
  }
  return map[difficulty] || undefined
}

function removeQuestion(index: number) {
  form.questions.splice(index, 1)
}

function showQuestionSelector() {
  selectedQuestions.value = []
  questionSelectorVisible.value = true
}

function handleQuestionSearch() {
  questionPagination.current = 1
  loadQuestions()
}

function handleQuestionReset() {
  questionFilters.type = ''
  questionFilters.difficulty = ''
  questionFilters.subject = ''
  questionFilters.keyword = ''
  questionPagination.current = 1
  loadQuestions()
}

function loadQuestions() {
  questionPagination.total = filteredQuestions.value.length
}

function handleSelectionChange(selection: Question[]) {
  // 获取当前页所有题目 ID
  const currentPageIds = new Set(questionTableData.value.map((q: Question) => q.id))

  // 更新 selectedQuestions：保留不在当前页的旧选择 + 当前页的新选择
  const otherPageQuestions = selectedQuestions.value.filter(q => !currentPageIds.has(q.id))
  selectedQuestions.value = [...otherPageQuestions, ...selection]
}

function confirmSelectQuestions() {
  let addedCount = 0
  selectedQuestions.value.forEach(q => {
    if (!form.questions.includes(q.id)) {
      form.questions.push(q.id)
      addedCount += 1
    }
  })
  questionSelectorVisible.value = false
  if (addedCount === 0) {
    ElMessage.info('所选题目已全部存在')
    return
  }
  ElMessage.success(`已添加 ${addedCount} 道题目`)
}

function removeDuplicates() {
  const seen = new Set<number>()
  const unique: number[] = []

  form.questions.forEach(q => {
    if (q !== 0 && !seen.has(q)) {
      seen.add(q)
      unique.push(q)
    }
  })

  const removedCount = form.questions.length - unique.length
  form.questions = unique

  if (removedCount > 0) {
    ElMessage.success(`已移除 ${removedCount} 个重复或无效题目`)
  } else {
    ElMessage.info('没有重复题目')
  }
}

function clearAllQuestions() {
  ElMessageBox.confirm('确定要清空所有题目吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    form.questions = []
    ElMessage.success('已清空所有题目')
  }).catch(() => {
  })
}

async function handleSubmit() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      if (form.questions.length === 0) {
        ElMessage.warning('请至少添加一道题目')
        return
      }

      submitting.value = true
      try {
        const data = {
          name: form.name,
          description: form.description,
          questionIds: form.questions
        }
        emit('submit', data)
      } finally {
        submitting.value = false
      }
    }
  })
}

defineExpose({
  handleSubmit
})
</script>

<style scoped lang="scss">
@use '@/styles/design-tokens.scss' as *;

.manual-paper-form {
  .section-title {
    font-size: $font-size-sm;
    font-weight: $font-weight-semibold;
    color: $text-tertiary;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin: $spacing-md 0 $spacing-sm 0;
    padding-left: $spacing-sm;
    border-left: 2px solid $text-quaternary;
  }

  .full-width {
    width: 100%;
  }

  .questions-editor {
    width: 100%;

    .questions-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: $spacing-sm;
      padding-bottom: $spacing-sm;
      border-bottom: 1px solid $border-light;

      .toolbar-label {
        font-size: $font-size-sm;
        font-weight: $font-weight-medium;
        color: $text-secondary;
      }

      .toolbar-actions {
        display: flex;
        align-items: center;
        gap: $spacing-sm;

        .question-count {
          color: $text-tertiary;
          font-size: $font-size-xs;
        }
      }
    }

    .questions-list {
      .questions-empty {
        padding: $spacing-lg 0;
        text-align: center;
        color: $text-quaternary;
        font-size: $font-size-sm;
        border: 1px dashed $border-color;
        border-radius: $radius-sm;
      }
    }

    .question-row {
      display: flex;
      flex-wrap: wrap;
      gap: $spacing-sm;
      margin-bottom: $spacing-sm;
      align-items: center;
      padding: $spacing-xs 0;

      .question-index-label {
        font-weight: $font-weight-medium;
        color: $text-tertiary;
        min-width: 24px;
        font-size: $font-size-sm;
        text-align: right;
      }
    }

    .add-buttons {
      margin-top: $spacing-sm;
    }
  }

  .question-select {
    flex: 1;
  }

  .filter-type {
    width: 112px;
  }

  .filter-difficulty {
    width: 96px;
  }

  .filter-subject {
    width: 130px;
  }

  .filter-keyword {
    width: 170px;
  }

  .difficulty-tag {
    margin-left: $spacing-xs;
  }

  .score-input {
    width: 80px;
  }

  .selected-summary {
    font-size: $font-size-sm;
    color: $text-tertiary;
    margin-right: auto;
  }

  .selector-filter-bar {
    padding: $spacing-sm $spacing-md;
    margin-bottom: $spacing-sm;
    border-bottom: 1px solid $border-light;

    :deep(.el-form) {
      display: flex;
      flex-wrap: wrap;
      gap: $spacing-xs $spacing-sm;
      align-items: center;

      .el-form-item {
        margin-bottom: 0;
      }

      .el-form-item__label {
        font-size: $font-size-xs;
        color: $text-tertiary;
      }
    }
  }

  .selector-dialog {
    :deep(.el-dialog__body) {
      padding: $spacing-md $spacing-lg;
    }
  }

  .selector-table {
    :deep(.el-table__header th) {
      background: $bg-secondary;
      color: $text-tertiary;
      font-size: $font-size-xs;
      font-weight: $font-weight-medium;
      padding: $spacing-sm 0;
    }

    :deep(.el-table__body td) {
      padding: $spacing-sm 0;
      vertical-align: top;
    }

    :deep(.el-table .cell) {
      padding: $spacing-xs $spacing-sm;
      word-break: break-word;
      overflow-wrap: anywhere;
      line-height: $line-height-normal;
    }
  }

  .question-preview-cell {
    .question-content-text {
      font-size: $font-size-sm;
      line-height: $line-height-relaxed;
      margin-bottom: $spacing-xs;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .question-tags {
      display: flex;
      align-items: center;
      gap: $spacing-xs;
      flex-wrap: wrap;

      .subject-badge {
        font-size: $font-size-xs;
        color: $text-tertiary;
        padding: 0 4px;
        background: $bg-secondary;
        border-radius: $radius-sm;
        line-height: 20px;
      }
    }
  }

  .question-pagination {
    margin-top: $spacing-md;
    display: flex;
    justify-content: flex-end;
  }

  @media (max-width: $breakpoint-md) {
    .questions-editor {
      .questions-toolbar {
        flex-direction: column;
        align-items: flex-start;
        gap: $spacing-sm;
      }
    }

    .question-select {
      flex: 1 1 100%;
    }

    .filter-type,
    .filter-difficulty,
    .filter-subject,
    .filter-keyword {
      width: min(180px, 100%);
    }
  }
}
</style>
