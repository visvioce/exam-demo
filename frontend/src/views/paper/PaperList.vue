<template>
  <div class="paper-list base-list-page">
    <div class="page-header">
      <h2>试卷管理</h2>
      <el-button type="primary" @click="handleCreate" v-if="hasPermission(['ADMIN', 'TEACHER'])">
        <el-icon><Plus /></el-icon>
        创建试卷
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :model="searchForm" label-width="80px" class="search-form">
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="试卷名称" clearable @input="handleKeywordInput" class="keyword-input" />
        </el-form-item>
        <el-form-item>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 试卷列表 -->
    <el-card class="table-card">
      <el-table :data="papers" v-loading="loading" stripe table-layout="auto" :fit="true">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="试卷名称" min-width="200" />
        <el-table-column prop="description" label="描述" min-width="150">
          <template #default="{ row }">
            {{ row.description || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="120">
          <template #default="{ row }">
            <ActionButtons
              @view="handleView(row)"
              @edit="handleEdit(row)"
              @delete="handleDelete(row)"
              :show-edit="canEdit(row)"
              :show-delete="canEdit(row)"
            />
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && papers.length === 0" description="暂无试卷数据" />

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadPapers"
          @current-change="loadPapers"
        />
      </div>
    </el-card>

    <!-- 查看试卷对话框 -->
    <PaperDetail v-model:visible="viewDialogVisible" :title="currentPaper?.name || ''" :description="currentPaper?.description" :questions="paperDetailQuestions" />

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="editDialogVisible" :title="isEdit ? '编辑试卷' : '创建试卷'" width="800px" top="5vh" class="base-dialog">
      <el-tabs v-model="activeTab" v-if="!isEdit">
        <el-tab-pane label="手动组卷" name="manual">
          <ManualPaperForm
            ref="manualFormRef"
            :questions="availableQuestions"
            @submit="handleManualSubmit"
          />
        </el-tab-pane>
        <el-tab-pane label="自动组卷" name="auto">
          <AutoPaperForm
            ref="autoFormRef"
            :subjects="subjects"
            @submit="handleAutoSubmit"
          />
        </el-tab-pane>
      </el-tabs>

      <ManualPaperForm
        v-else
        ref="manualFormRef"
        :questions="availableQuestions"
        :initial-data="editFormData"
        @submit="handleManualSubmit"
      />
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCurrentTabSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 试卷管理页面组件
 * 
 * 教师/管理员的试卷管理页面，包含：
 * ● 试卷列表 - 分页展示，按关键字搜索
 * ● 创建试卷 - 支持两种组卷方式：
 *   - 手动组卷（ManualPaperForm）：从题库中选择题目组成试卷
 *   - 自动组卷（AutoPaperForm）：按学科/题型/难度配置规则，系统自动抽取题目
 * ● 编辑试卷（仅支持手动组卷方式编辑）
 * ● 查看试卷详情（通过 PaperDetail 组件展示题目列表）
 * ● 删除试卷
 * ● 权限控制：仅 ADMIN/TEACHER 可管理，且只能管理自己创建的试卷
 */

import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { paperApi } from '@/api/paper'
import { questionApi } from '@/api/question'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getErrorMessage } from '@/utils/error'
import { usePagedList } from '@/composables/usePagedList'
import type { Paper, Question, AutoGeneratePaperRequest, ManualPaperSubmitData } from '@/types'
import ActionButtons from '@/components/ActionButtons.vue'
import ManualPaperForm from './components/ManualPaperForm.vue'
import AutoPaperForm from './components/AutoPaperForm.vue'
import PaperDetail from '@/components/PaperDetail.vue'
import type { PaperQuestionItem } from '@/components/PaperDetail.vue'
import { hasRolePermission } from '@/utils/permission'

const authStore = useAuthStore()

const submitting = ref(false)
const questions = ref<Question[]>([])
const subjects = ref<string[]>([])
const viewDialogVisible = ref(false)
const editDialogVisible = ref(false)
const isEdit = ref(false)
const currentPaper = ref<Paper | null>(null)
const activeTab = ref<'manual' | 'auto'>('manual')
const manualFormRef = ref()
const autoFormRef = ref()
const editFormData = ref<Paper | null>(null)

const {
  records: papers,
  loading,
  searchForm,
  pagination,
  load: loadPapers,
  loadFromFirstPage,
  resetSearch,
  toggleSearch
} = usePagedList<Paper, { keyword: string }>({
  createSearchForm: () => ({
    keyword: ''
  }),
  fetchPage: async ({ current, size, keyword }) => {
    const res = await paperApi.page({
      current,
      size,
      keyword: keyword || undefined
    })
    return {
      records: res.data.records,
      total: res.data.total
    }
  },
  onError: () => {
    ElMessage.error('加载试卷失败')
  }
})

const availableQuestions = computed(() => questions.value)

function hasPermission(roles: string[]) {
  return hasRolePermission(authStore.user?.role, roles)
}

function canEdit(paper: Paper) {
  const user = authStore.user
  if (!user) return false
  if (user.role === 'ADMIN' || user.role === 'TEACHER') {
    return paper.teacherId === user.id
  }
  return false
}

const paperDetailQuestions = computed<PaperQuestionItem[]>(() => {
  if (!currentPaper.value?.questionIds?.length) return []
  return currentPaper.value.questionIds
    .map(qid => {
      const q = questions.value.find(item => item.id === qid)
      if (!q) return null
      return {
        content: q.content || '',
        type: q.type || '',
        difficulty: q.difficulty || '',
        options: q.options || [],
        correctAnswer: q.correctAnswer,
        explanation: q.explanation || ''
      }
    })
    .filter((item): item is PaperQuestionItem => item !== null)
})

async function loadQuestions() {
  try {
    const res = await questionApi.list()
    questions.value = res.data
  } catch (error) {
    ElMessage.error('加载题目失败')
  }
}

async function loadSubjects() {
  try {
    const res = await questionApi.getSubjects()
    subjects.value = res.data || []
  } catch {
    // 静默处理
  }
}

function handleKeywordInput() {
  void loadFromFirstPage()
}

function handleReset() {
  void resetSearch()
}

async function handleView(row: Paper) {
  try {
    const res = await paperApi.getById(row.id)
    currentPaper.value = res.data
    viewDialogVisible.value = true
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error, '加载试卷详情失败'))
  }
}

function handleCreate() {
  isEdit.value = false
  activeTab.value = 'manual'
  editFormData.value = null
  editDialogVisible.value = true
}

function handleEdit(row: Paper) {
  isEdit.value = true
  editFormData.value = { ...row }
  editDialogVisible.value = true
}

async function handleDelete(row: Paper) {
  try {
    await ElMessageBox.confirm('确定要删除该试卷吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await paperApi.delete(row.id)
    ElMessage.success('删除成功')
    void loadPapers()
  } catch {
    // 取消删除
  }
}

async function handleManualSubmit(data: ManualPaperSubmitData) {
  submitting.value = true
  try {
    if (isEdit.value) {
      await paperApi.update(editFormData.value!.id, data)
      ElMessage.success('更新成功')
    } else {
      await paperApi.create(data)
      ElMessage.success('创建成功')
    }
    editDialogVisible.value = false
    void loadPapers()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error, '操作失败'))
  } finally {
    submitting.value = false
  }
}

async function handleAutoSubmit(data: AutoGeneratePaperRequest) {
  submitting.value = true
  try {
    await paperApi.autoGenerate(data)
    ElMessage.success('自动组卷成功')
    editDialogVisible.value = false
    void loadPapers()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error, '自动组卷失败'))
  } finally {
    submitting.value = false
  }
}

function handleCurrentTabSubmit() {
  if (isEdit.value) {
    manualFormRef.value?.handleSubmit()
  } else if (activeTab.value === 'manual') {
    manualFormRef.value?.handleSubmit()
  } else {
    autoFormRef.value?.handleSubmit()
  }
}

onMounted(() => {
  void loadPapers()
  loadQuestions()
  loadSubjects()
})
</script>

<style scoped lang="scss">
@use '@/styles/design-tokens.scss' as *;
@use '@/styles/views/base-list.scss';

.paper-list {
  .search-bar {
    .search-form {
      :deep(.el-form-item) {
        display: flex;
        align-items: center;
        margin-bottom: $spacing-md;
      }

      :deep(.el-form-item__label) {
        white-space: nowrap;
        padding-right: $spacing-lg;
        color: $text-secondary;
        font-size: $font-size-sm;
      }

      :deep(.el-form-item__content) {
        flex: 1;
        display: flex;
        align-items: center;
      }
    }

    .keyword-input {
      width: 240px;
    }
  }

  .table-card {
    margin-bottom: $spacing-xl;

    :deep(.el-table) {
      .el-table__header th {
        background: $bg-secondary;
        font-weight: $font-weight-medium;
      }
    }
  }
}
</style>
