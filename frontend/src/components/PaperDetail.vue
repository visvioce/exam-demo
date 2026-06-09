<template>
  <el-dialog :model-value="visible" @update:model-value="$emit('update:visible', $event)" title="试卷详情" width="95%" top="5vh" class="base-dialog">
    <el-descriptions :column="2" border>
      <el-descriptions-item label="试卷名称">{{ title }}</el-descriptions-item>
      <el-descriptions-item v-if="description" label="描述" :span="2">{{ description }}</el-descriptions-item>
    </el-descriptions>

    <div class="question-list-header">
      题目列表（共 {{ questions.length }} 题）
    </div>

    <div v-if="questions.length > 0" class="questions-detail-list">
      <div v-for="(q, index) in questions" :key="index" class="question-detail-item">
        <div class="question-item-header">
          <span class="question-number">{{ index + 1 }}.</span>
          <el-tag :type="getTypeColor(q.type) || undefined" size="small" class="item-tag">
            {{ getTypeName(q.type) }}
          </el-tag>
          <el-tag :type="getDifficultyColor(q.difficulty) || undefined" size="small" class="item-tag">
            {{ getDifficultyName(q.difficulty) }}
          </el-tag>
        </div>

        <div class="question-content-display">{{ q.content }}</div>

        <div v-if="q.options && q.options.length > 0" class="options-display">
          <span v-for="opt in q.options" :key="opt.id" class="option-display-item">
            <em>{{ opt.id }}.</em> {{ opt.text }}
          </span>
        </div>

        <div v-if="q.correctAnswer !== undefined && q.correctAnswer !== null && q.correctAnswer !== ''" class="answer-line">
          <span class="meta-label">答案：</span>
          <span class="answer-value">{{ formatAnswerDisplay(q.correctAnswer) }}</span>
        </div>

        <div v-if="q.explanation" class="explanation-line">
          <span class="meta-label">解析：</span>
          <span>{{ q.explanation }}</span>
        </div>
      </div>
    </div>

    <el-empty v-else description="暂无题目" />
  </el-dialog>
</template>

<script setup lang="ts">
import { getTypeName, getTypeColor, getDifficultyName, getDifficultyColor } from '@/utils/format'
import type { QuestionOption } from '@/types'

export interface PaperQuestionItem {
  content: string
  type: string
  difficulty: string
  options?: QuestionOption[]
  correctAnswer?: unknown
  explanation?: string
}

defineProps<{
  visible: boolean
  title: string
  description?: string
  questions: PaperQuestionItem[]
}>()

defineEmits<{
  'update:visible': [value: boolean]
}>()

function formatAnswerDisplay(answer: unknown): string {
  if (Array.isArray(answer)) return answer.join(', ')
  return String(answer)
}
</script>

<style scoped lang="scss">
@use '@/styles/design-tokens.scss' as *;

.question-list-header {
  font-size: 15px;
  font-weight: $font-weight-semibold;
  color: $text-primary;
  margin: $spacing-xl 0 $spacing-md;
}

.questions-detail-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.question-detail-item {
  padding: $spacing-md;
  background: $bg-secondary;
  border-radius: $radius-md;
  border: 1px solid $border-light;
}

.question-item-header {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-sm;

  .question-number {
    font-weight: $font-weight-semibold;
    color: $text-primary;
    min-width: 24px;
  }

  .item-tag {
    flex-shrink: 0;
  }
}

.question-content-display {
  font-size: 14px;
  color: $text-primary;
  line-height: 1.6;
  margin-bottom: $spacing-sm;
}

.options-display {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-md;
  margin-bottom: $spacing-sm;
}

.option-display-item {
  font-size: 13px;
  color: $text-secondary;

  em {
    font-style: normal;
    font-weight: $font-weight-semibold;
    color: $text-primary;
  }
}

.answer-line {
  font-size: 13px;
  color: $text-primary;

  .meta-label {
    color: $text-tertiary;
  }
}

.explanation-line {
  font-size: 13px;
  color: $text-secondary;
  margin-top: $spacing-xs;

  .meta-label {
    color: $text-tertiary;
  }
}
</style>