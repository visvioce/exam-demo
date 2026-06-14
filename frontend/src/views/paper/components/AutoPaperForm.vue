<template>
  <div class="auto-paper-form">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="试卷名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入试卷名称" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入试卷描述（可选）" />
      </el-form-item>

      <el-divider content-position="left">题目配置</el-divider>

      <div class="table-wrapper">
        <el-table :data="rows" stripe border class="config-table" table-layout="fixed" :fit="true">
          <el-table-column label="学科" min-width="130">
            <template #default="{ row }">
              <el-select v-model="row.subject" placeholder="学科" clearable allow-create filterable class="full-select" size="small" @change="onRowFilterChange(row)">
                <el-option v-for="subject in subjects" :key="subject" :label="subject" :value="subject" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="题型" min-width="116">
            <template #default="{ row }">
              <el-select v-model="row.type" placeholder="题型" class="full-select" size="small" @change="onRowFilterChange(row)">
                <el-option label="单选题" value="SINGLE_CHOICE" />
                <el-option label="多选题" value="MULTIPLE_CHOICE" />
                <el-option label="判断题" value="TRUE_FALSE" />
                <el-option label="填空题" value="FILL_BLANK" />
                <el-option label="简答题" value="ESSAY" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="难度" min-width="96">
            <template #default="{ row }">
              <el-select v-model="row.difficulty" placeholder="不限" clearable class="full-select" size="small" @change="onRowFilterChange(row)">
                <el-option label="简单" value="EASY" />
                <el-option label="中等" value="MEDIUM" />
                <el-option label="困难" value="HARD" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="题数" min-width="100">
            <template #default="{ row }">
              <div class="count-cell">
                <el-input-number v-model="row.count" :min="1" :max="row.maxCount || 100" controls-position="right" size="small" class="num-input" />
                <span v-if="row.availableCount !== null" class="available-tip">
                  可用 {{ row.availableCount }} 题
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="68" align="center">
            <template #default="{ $index }">
              <DeleteActionButton aria-label="删除配置行" button-class="row-delete-btn" @click="removeRow($index)" />
            </template>
          </el-table-column>
          <template #empty>
            <div class="table-empty">
              <p>暂无配置，点击下方添加按钮添加</p>
            </div>
          </template>
        </el-table>
      </div>

      <el-button plain @click="addRow" class="add-row-btn">
        <el-icon><Plus /></el-icon>
        添加配置行
      </el-button>

      <div class="summary-card">
        <div class="summary-item">
          <span>总题数：</span>
          <span class="highlight">{{ totalQuestions }} 题</span>
        </div>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
/**
 * 自动组卷表单组件
 * 
 * 通过配置规则自动从题库中抽取题目生成试卷：
 * - 试卷基本信息（名称、描述）
 * - 题目配置表格：支持多行，每行指定学科、题型、难度、题目数量
 * - 实时查询可用题目数，限制题数不能超过可用量
 * - 题型重复检测：同一种题型只能有一行配置
 * - 总题数统计
 * 
 * 通过 defineExpose 暴露 handleSubmit 方法供父组件调用。
 */

import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import DeleteActionButton from '@/components/DeleteActionButton.vue'
import { questionApi } from '@/api/question'

interface PaperRow {
  type: string
  subject: string
  difficulty: string
  count: number
  maxCount: number
  availableCount: number | null
}

interface SubmitData {
  name: string
  description: string
  singleChoice: PaperRow | undefined
  multipleChoice: PaperRow | undefined
  trueFalse: PaperRow | undefined
  fillBlank: PaperRow | undefined
  essay: PaperRow | undefined
}

const props = defineProps<{
  subjects: string[]
}>()

const emit = defineEmits<{
  (e: 'submit', data: SubmitData): void
}>()

const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  description: ''
})

function createEmptyRow(): PaperRow {
  return { type: '', subject: '', difficulty: '', count: 5, maxCount: 100, availableCount: null }
}

const rows = ref<PaperRow[]>([createEmptyRow()])

const rules = reactive<FormRules>({
  name: [{ required: true, message: '请输入试卷名称', trigger: 'blur' }]
})

const totalQuestions = computed(() => {
  return rows.value.reduce((sum, row) => sum + (row.type ? row.count : 0), 0)
})

let fetchTimer: ReturnType<typeof setTimeout> | null = null

function queryAvailableCount(row: PaperRow) {
  if (!row.type) {
    row.availableCount = null
    row.maxCount = 100
    return
  }

  if (fetchTimer) clearTimeout(fetchTimer)
  fetchTimer = setTimeout(async () => {
    try {
      const res = await questionApi.getAvailableCount({
        type: row.type,
        subject: row.subject || undefined,
        difficulty: row.difficulty || undefined
      })
      const available = res.data.count
      row.availableCount = available
      row.maxCount = available > 0 ? available : 1
      if (row.count > available && available > 0) {
        row.count = available
      }
      if (available === 0) {
        row.count = 1
        row.maxCount = 1
      }
    } catch {
      row.availableCount = null
      row.maxCount = 100
    }
  }, 300)
}

function onRowFilterChange(row: PaperRow) {
  queryAvailableCount(row)
}

function addRow() {
  rows.value.push(createEmptyRow())
}

function removeRow(index: number) {
  rows.value.splice(index, 1)
}

function handleSubmit() {
  if (!formRef.value) return

  formRef.value.validate(async (valid) => {
    if (valid) {
      const validRows = rows.value.filter(row => row.type && row.subject && row.count > 0)

      if (validRows.length === 0) {
        ElMessage.warning('请至少添加一行完整的题目配置（学科和题型不能为空）')
        return
      }

      const incompleteRows = rows.value.filter(row => row.type && !row.subject)
      if (incompleteRows.length > 0) {
        ElMessage.warning('已选择题型的行必须填写学科')
        return
      }

      const typeSet = new Set<string>()
      const duplicateTypes: string[] = []
      for (const row of validRows) {
        if (typeSet.has(row.type)) {
          if (!duplicateTypes.includes(row.type)) {
            duplicateTypes.push(row.type)
          }
        } else {
          typeSet.add(row.type)
        }
      }
      if (duplicateTypes.length > 0) {
        const typeNames: Record<string, string> = {
          SINGLE_CHOICE: '单选题',
          MULTIPLE_CHOICE: '多选题',
          TRUE_FALSE: '判断题',
          FILL_BLANK: '填空题',
          ESSAY: '简答题'
        }
        const names = duplicateTypes.map(t => typeNames[t] || t).join('、')
        ElMessage.warning(`题型 "${names}" 存在多行配置，请合并为一行或删除重复配置`)
        return
      }

      const singleChoice: PaperRow | undefined = validRows.find(r => r.type === 'SINGLE_CHOICE')
      const multipleChoice: PaperRow | undefined = validRows.find(r => r.type === 'MULTIPLE_CHOICE')
      const trueFalse: PaperRow | undefined = validRows.find(r => r.type === 'TRUE_FALSE')
      const fillBlank: PaperRow | undefined = validRows.find(r => r.type === 'FILL_BLANK')
      const essay: PaperRow | undefined = validRows.find(r => r.type === 'ESSAY')

      const data: SubmitData = {
        ...form,
        singleChoice,
        multipleChoice,
        trueFalse,
        fillBlank,
        essay
      }

      emit('submit', data)
    }
  })
}

defineExpose({
  handleSubmit
})
</script>

<style scoped lang="scss">
@use '@/styles/design-tokens.scss' as *;

.auto-paper-form {
  :deep(.el-form-item__label) {
    color: $text-secondary;
    font-weight: $font-weight-medium;
  }

  .full-width {
    width: 100%;
  }

  .full-select {
    width: 100%;
  }

  .num-input {
    width: 100%;
  }

  .count-cell {
    display: flex;
    flex-direction: column;
    gap: 2px;

    .available-tip {
      font-size: 11px;
      color: $text-tertiary;
      line-height: 1;
      white-space: nowrap;
    }
  }

  .table-wrapper {
    margin-top: $spacing-sm;
    width: 100%;
    border-radius: $radius-xl;
    background: $bg-primary;
    overflow: hidden;
  }

  .config-table {
    width: 100%;
    --el-table-border-color: #{$border-color};
    --el-table-header-bg-color: #{$bg-secondary};
    --el-table-row-hover-bg-color: #{$bg-hover};

    :deep(.el-table__inner-wrapper::before) {
      display: none;
    }

    :deep(.el-table__header-wrapper),
    :deep(.el-table__body-wrapper) {
      width: 100%;
    }

    :deep(.el-table__header th) {
      height: 42px;
      color: $text-tertiary;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.2px;
      border-bottom-color: $border-color;

      .cell {
        white-space: nowrap;
      }
    }

    :deep(.el-table__body td) {
      padding: 10px 0;
      border-bottom-color: $border-light;
    }

    :deep(.el-table .cell) {
      padding: 0 10px;
    }

    :deep(.el-select) {
      .el-input__wrapper {
        border-radius: $radius-lg;
        background: $bg-primary;
        border: 1px solid $border-color;

        &:hover {
          border-color: $text-quaternary;
        }

        &.is-focus {
          border-color: $text-tertiary;
          box-shadow: $focus-ring;
        }
      }
    }

    :deep(.el-input-number) {
      width: 100%;

      .el-input__wrapper {
        padding: 0 8px;
        border-radius: $radius-lg;
        border: 1px solid $border-color;
      }

      .el-input-number__increase,
      .el-input-number__decrease {
        background: transparent;
        border-left-color: $border-light;
      }
    }

    .subtotal {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 34px;
      height: 26px;
      padding: 0 $spacing-sm;
      font-weight: 600;
      color: $text-primary;
      font-size: 12px;
      border: 1px solid $border-color;
      border-radius: 999px;
      background: $bg-hover;
    }

  }

  .table-empty {
    padding: $spacing-xl;
    text-align: center;
    color: $text-tertiary;

    p {
      margin: 0;
    }
  }

  .add-row-btn {
    width: 100%;
    margin-top: 15px;
    border-color: $border-color;
    color: $text-secondary;
    border-radius: $radius-lg;
    height: 38px;

    &:hover {
      color: $text-primary;
      border-color: $text-quaternary;
      background: $bg-hover;
    }
  }

  .summary-card {
    display: flex;
    justify-content: flex-end;
    gap: $spacing-lg;
    margin-top: $spacing-md;
    padding: $spacing-lg;
    background: $bg-secondary;
    border: 1px solid $border-color;
    border-radius: 12px;

    .summary-item {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: $font-size-sm;
      color: $text-tertiary;

      .highlight {
        font-weight: 600;
        color: $text-primary;
        font-size: 18px;
      }
    }
  }

  @media (max-width: 768px) {
    .summary-card {
      justify-content: space-between;
      padding: $spacing-md;
    }
  }
}
</style>