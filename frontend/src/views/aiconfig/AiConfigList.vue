<template>
  <div class="ai-config-list base-list-page">
    <div class="page-header">
      <h2>AI 配置管理</h2>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        添加配置
      </el-button>
    </div>

    <!-- 全局模型切换器 -->
    <el-card v-if="allModels.length > 0" class="global-model-switcher">
      <div class="switcher-content">
        <span class="label">当前使用模型：</span>
        <el-select
          v-model="currentActiveModel"
          placeholder="选择要使用的模型"
          @change="handleGlobalModelChange"
          class="model-select"
        >
          <el-option-group
            v-for="config in configs"
            :key="config.id"
            :label="config.name"
          >
            <el-option
              v-for="model in config.models"
              :key="`${config.id}-${model}`"
              :label="formatModelOptionLabel(config.name, model)"
              :value="`${config.id}|${model}`"
            >
              <div class="option-item">
                <span class="option-combined-label">{{ formatModelOptionLabel(config.name, model) }}</span>
              </div>
            </el-option>
          </el-option-group>
        </el-select>
        <el-tag v-if="activeConfig" type="primary" class="active-tag" effect="light">
          {{ currentActiveModelLabel }}
        </el-tag>
      </div>
    </el-card>

    <!-- 配置卡片列表 -->
    <div class="config-cards" v-loading="loading">
      <el-card v-for="config in configs" :key="config.id" class="config-card">
        <template #header>
          <div class="card-header">
            <span class="config-name">{{ config.name }}</span>
            <div class="card-actions">
              <ActionButtons
                :show-view="false"
                @edit="handleEdit(config)"
                @delete="handleDelete(config)"
              />
            </div>
          </div>
        </template>

        <div class="config-info">
          <div class="info-row">
            <span class="label">API地址：</span>
            <span class="value url">{{ config.baseUrl }}</span>
          </div>
          <div class="info-row">
            <span class="label">API Key：</span>
            <span class="value">{{ maskApiKey(config.apiKey) }}</span>
          </div>
        </div>

        <el-divider />

        <div class="models-section">
          <div class="models-header">
            <span class="label">模型列表</span>
          </div>

          <div class="model-tags" v-if="config.models && config.models.length > 0">
            <el-tag
              v-for="model in config.models"
              :key="model"
              :type="model === config.activeModel ? 'success' : 'info'"
              closable
              :disable-transitions="false"
              @close="handleRemoveModel(config, model)"
              @click="handleActivateModel(config, model)"
              class="model-tag"
            >
              {{ model }}
              <el-icon v-if="model === config.activeModel" class="active-icon"><Check /></el-icon>
            </el-tag>
          </div>
          <el-empty v-else description="暂无模型" :image-size="60" />
        </div>
      </el-card>

      <!-- 空状态 -->
      <el-empty v-if="!loading && configs.length === 0" description="暂无AI配置" />
    </div>

    <!-- 创建/编辑配置对话框 -->
    <el-dialog v-model="editDialogVisible" :title="isEdit ? '编辑配置' : '添加配置'" width="600px" class="base-dialog">
      <el-form :model="configForm" :rules="currentRules" ref="configFormRef" label-width="100px">
        <el-form-item label="预设模板">
          <el-select v-model="selectedPreset" placeholder="选择预设模板快速填充" clearable @change="handlePresetChange">
            <el-option
              v-for="preset in AI_PRESETS"
              :key="preset.name"
              :label="preset.name"
              :value="preset.name"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="配置名称" prop="name">
          <el-input v-model="configForm.name" placeholder="如：我的通义千问" />
        </el-form-item>
        <el-form-item label="API地址" prop="baseUrl">
          <el-input v-model="configForm.baseUrl" placeholder="如：https://dashscope.aliyuncs.com/compatible-mode/v1" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="configForm.apiKey"
            type="password"
            :placeholder="isEdit ? '留空则保持原值不变' : '请输入API Key'"
            show-password
          />
        </el-form-item>

        <!-- 模型列表管理 -->
        <el-form-item label="模型列表">
          <div class="models-editor">
            <div class="model-tags-editor" v-if="configForm.models.length > 0">
              <el-tag
                v-for="(model, index) in configForm.models"
                :key="index"
                closable
                @close="handleRemoveModelFromEditor(index)"
                class="model-tag-editor"
              >
                {{ model }}
              </el-tag>
            </div>
            <el-empty v-else description="暂无模型，请添加" :image-size="40" />

            <div class="add-model-input">
              <el-input
                v-model="newModelInput"
                placeholder="输入模型名称，如：qwen-plus"
                @keyup.enter="handleAddModelToEditor"
                class="model-input"
              >
                <template #append>
                  <el-button
                    :icon="Plus"
                    @click="handleAddModelToEditor"
                    :disabled="!newModelInput.trim()"
                  >
                    添加
                  </el-button>
                </template>
              </el-input>
            </div>

            <div class="preset-models" v-if="availablePresetModelsForEditor.length > 0">
              <span class="hint">快速添加：</span>
              <el-tag
                v-for="model in availablePresetModelsForEditor"
                :key="model"
                @click="handleQuickAddModel(model)"
                class="preset-model-tag"
              >
                + {{ model }}
              </el-tag>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * AI 配置管理页面组件
 * 
 * 管理用户的 AI API 配置，用于 AI 智能出题等功能：
 * ● 全局模型切换器 - 顶部下拉框快速切换当前使用的 AI 模型
 * ● 配置卡片列表 - 每个配置包含 API 地址、API Key（脱敏显示）、模型列表
 * ● 模型管理 - 添加/删除/激活模型
 *   - 点击模型标签可激活该模型
 *   - 关闭标签可删除模型（至少保留一个）
 * ● 创建/编辑配置：
 *   - 预设模板快速填充（通义千问、DeepSeek 等常见 AI 平台）
 *   - 模型列表编辑器（输入添加、预设模型快速添加）
 *   - 编辑时 API Key 选填（留空保持原值）
 * 
 * 数据来源：aiConfigApi（获取我的配置、激活模型等）
 */

import { ref, reactive, computed, onMounted } from 'vue'
import { aiConfigApi, AI_PRESETS } from '@/api/aiconfig'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Check } from '@element-plus/icons-vue'
import { getErrorMessage } from '@/utils/error'
import type { FormInstance, FormRules } from 'element-plus'
import type { AiConfig } from '@/types'
import ActionButtons from '@/components/ActionButtons.vue'

const loading = ref(false)
const submitting = ref(false)
const configs = ref<AiConfig[]>([])
const activeConfig = ref<AiConfig | null>(null)
const editDialogVisible = ref(false)
const isEdit = ref(false)
const configFormRef = ref<FormInstance>()
const selectedPreset = ref('')
const newModelInput = ref('')
const isSwitchingModel = ref(false)  // 防止重复切换

const configForm = reactive({
  id: 0,
  name: '',
  baseUrl: '',
  apiKey: '',
  models: [] as string[]
})

// 新建时的验证规则
const createRules: FormRules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入API地址', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入API Key', trigger: 'blur' }]
}

// 编辑时的验证规则
const editRules: FormRules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入API地址', trigger: 'blur' }]
}

const currentRules = computed(() => isEdit.value ? editRules : createRules)

const currentActiveModelLabel = computed(() => {
  if (!activeConfig.value || !activeConfig.value.activeModel) return ''
  return formatModelOptionLabel(activeConfig.value.name, activeConfig.value.activeModel)
})

// 当前激活的模型（格式：configId|model）
const currentActiveModel = computed({
  get: () => {
    if (!activeConfig.value || !activeConfig.value.activeModel) return ''
    return `${activeConfig.value.id}|${activeConfig.value.activeModel}`
  },
  set: () => {
    // 由 handleGlobalModelChange 处理
  }
})

// 所有模型的扁平化列表
const allModels = computed(() => {
  const result: Array<{ configId: number; configName: string; model: string }> = []
  configs.value.forEach(config => {
    config.models?.forEach(model => {
      result.push({
        configId: config.id,
        configName: config.name,
        model
      })
    })
  })
  return result
})

function formatModelOptionLabel(configName: string, model: string) {
  return `【${configName}】${model}`
}

function maskApiKey(apiKey?: string) {
  if (!apiKey || apiKey.length <= 4) return '****'
  return '****' + apiKey.substring(apiKey.length - 4)
}

// 根据编辑器中的baseUrl获取可用的预设模型（用于快速添加）
const availablePresetModelsForEditor = computed(() => {
  const preset = AI_PRESETS.find(p => p.baseUrl === configForm.baseUrl)
  if (!preset) return []
  return preset.models.filter(m => !configForm.models.includes(m))
})

async function loadConfigs() {
  loading.value = true
  try {
    const res = await aiConfigApi.getMyConfigs()
    configs.value = res.data || []
  } catch (error) {
    ElMessage.error('加载配置失败')
  } finally {
    loading.value = false
  }
}

async function loadActiveConfig() {
  try {
    const res = await aiConfigApi.getMyActiveConfig()
    activeConfig.value = res.data
  } catch (error) {
    // 静默处理
  }
}

function handleCreate() {
  isEdit.value = false
  selectedPreset.value = ''
  newModelInput.value = ''
  Object.assign(configForm, {
    id: 0,
    name: '',
    baseUrl: '',
    apiKey: '',
    models: []
  })
  editDialogVisible.value = true
}

function handleEdit(row: AiConfig) {
  isEdit.value = true
  selectedPreset.value = ''
  newModelInput.value = ''
  const preset = AI_PRESETS.find(p => p.baseUrl === row.baseUrl)
  if (preset) {
    selectedPreset.value = preset.name
  }
  Object.assign(configForm, {
    id: row.id,
    name: row.name,
    baseUrl: row.baseUrl,
    apiKey: '',
    models: row.models ? [...row.models] : []
  })
  editDialogVisible.value = true
}

function handlePresetChange(presetName: string) {
  const preset = AI_PRESETS.find(p => p.name === presetName)
  if (preset) {
    configForm.name = configForm.name || preset.name
    configForm.baseUrl = preset.baseUrl
    // 清空现有模型，自动添加第一个推荐模型
    if (preset.models.length > 0 && preset.models[0]) {
      configForm.models = [preset.models[0]]
    }
    // 检查兼容性
    if (preset.compatible === false) {
      ElMessage.warning(preset.note || '该平台需要使用兼容代理服务')
    }
  }
}

// 在编辑器中添加模型
function handleAddModelToEditor() {
  const model = newModelInput.value.trim()
  if (!model) return

  if (configForm.models.includes(model)) {
    ElMessage.warning('模型已存在')
    return
  }

  configForm.models.push(model)
  newModelInput.value = ''
}

// 从编辑器中移除模型
function handleRemoveModelFromEditor(index: number) {
  configForm.models.splice(index, 1)
}

// 快速添加预设模型
function handleQuickAddModel(model: string) {
  if (configForm.models.includes(model)) {
    ElMessage.warning('模型已存在')
    return
  }
  configForm.models.push(model)
}

async function handleDelete(row: AiConfig) {
  try {
    await ElMessageBox.confirm('确定要删除该配置吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await aiConfigApi.delete(row.id)
    ElMessage.success('删除成功')
    loadConfigs()
    loadActiveConfig()
  } catch (error: unknown) {
    if (error !== 'cancel') {
      ElMessage.error(getErrorMessage(error, '删除失败'))
    }
  }
}

async function handleSubmit() {
  if (!configFormRef.value) return

  // 验证是否有模型
  if (configForm.models.length === 0) {
    ElMessage.warning('请至少添加一个模型')
    return
  }

  await configFormRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        if (isEdit.value) {
          const updateData: {
            name: string
            baseUrl: string
            models: string[]
            apiKey?: string
          } = {
            name: configForm.name,
            baseUrl: configForm.baseUrl,
            models: configForm.models
            // 编辑时不自动发送 activeModel，避免错误覆盖其他配置的激活状态
          }
          if (configForm.apiKey) {
            updateData.apiKey = configForm.apiKey
          }
          await aiConfigApi.update(configForm.id, updateData)
          ElMessage.success('更新成功')
        } else {
          await aiConfigApi.create({
            name: configForm.name,
            baseUrl: configForm.baseUrl,
            apiKey: configForm.apiKey,
            models: configForm.models,
            activeModel: configForm.models[0] || '' // 默认激活第一个
          })
          ElMessage.success('创建成功')
        }
        editDialogVisible.value = false
        loadConfigs()
        loadActiveConfig()
      } catch (error: unknown) {
        ElMessage.error(getErrorMessage(error, '操作失败'))
      } finally {
        submitting.value = false
      }
    }
  })
}

// 删除模型
async function handleRemoveModel(config: AiConfig, model: string) {
  if (config.models.length <= 1) {
    ElMessage.warning('至少保留一个模型')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除模型 "${model}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await aiConfigApi.removeModel(config.id, model)
    ElMessage.success('删除成功')
    loadConfigs()
    loadActiveConfig()
  } catch (error: unknown) {
    if (error !== 'cancel') {
      ElMessage.error(getErrorMessage(error, '移除失败'))
    }
  }
}

// 激活模型（设为当前使用）
async function handleActivateModel(config: AiConfig, model: string) {
  try {
    await aiConfigApi.activateModel(config.id, model)
    ElMessage.success(`已切换到模型 ${model}`)
    await loadConfigs()
    await loadActiveConfig()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error, '切换失败'))
  }
}

// 全局模型切换（带防抖）
async function handleGlobalModelChange(value: string) {
  // 防止重复切换
  if (isSwitchingModel.value) {
    return
  }

  const parts = value.split('|')
  const configIdStr = parts[0]
  const model = parts.slice(1).join('|')

  if (!configIdStr || !model) {
    ElMessage.error('模型信息无效')
    return
  }

  const configId = parseInt(configIdStr)
  isSwitchingModel.value = true

  try {
    await aiConfigApi.activateModel(configId, model)
    ElMessage.success(`已切换到模型 ${model}`)
    await loadConfigs()
    await loadActiveConfig()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error, '切换失败'))
  } finally {
    isSwitchingModel.value = false
  }
}

onMounted(() => {
  loadConfigs()
  loadActiveConfig()
})
</script>

<style scoped lang="scss">
@use '@/styles/design-tokens.scss' as *;
@use '@/styles/views/base-list.scss';

.ai-config-list {
  .model-select {
    width: min(320px, 100%);
  }

  .option-item {
    display: flex;
    align-items: center;
    width: 100%;
    padding-right: $spacing-sm;

    .option-combined-label {
      color: $text-primary;
      font-weight: $font-weight-medium;
      font-family: monospace;
    }
  }

  .active-tag {
    margin-left: $spacing-md;
  }

  .active-icon {
    margin-left: 4px;
  }

  .model-input {
    flex: 1;
  }

  // 全局模型切换器
  .global-model-switcher {
    margin-bottom: $spacing-xl;
    background: $bg-primary;
    border: 1px solid $border-color;

    .switcher-content {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: $spacing-md;

      .label {
        font-size: $font-size-base;
        font-weight: $font-weight-medium;
        color: $text-primary;
        white-space: nowrap;
      }
    }
  }

  // 配置卡片网格
  .config-cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
    gap: $spacing-xl;

    @media (max-width: $breakpoint-md) {
      grid-template-columns: 1fr;
    }
  }

  .config-card {
    border: 1px solid $border-color;
    border-radius: $radius-md;
    transition: box-shadow $transition-fast;

    &:hover {
      box-shadow: none;
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .config-name {
        font-size: $font-size-lg;
        font-weight: $font-weight-medium;
        color: $text-primary;
      }

      .card-actions {
        display: flex;
        gap: $spacing-xs;
      }
    }

    .config-info {
      .info-row {
        display: flex;
        align-items: flex-start;
        gap: $spacing-sm;
        margin-bottom: $spacing-sm;

        .label {
          color: $text-tertiary;
          font-size: $font-size-sm;
          min-width: 70px;
        }

        .value {
          color: $text-secondary;
          font-size: $font-size-sm;
          word-break: break-all;

          &.url {
            font-family: monospace;
          }
        }
      }
    }

    .models-section {
      .models-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: $spacing-md;

        .label {
          font-weight: $font-weight-medium;
          color: $text-primary;
        }
      }

      // 模型标签样式
      .model-tags {
        display: flex;
        flex-wrap: wrap;
        gap: $spacing-sm;

        .model-tag {
          cursor: pointer;
          font-family: monospace;
          transition: $transition-fast;
          user-select: none;

          &:hover {
            transform: translateY(-1px);
            box-shadow: none;
          }

          &:active {
            transform: translateY(0);
          }
        }
      }
    }
  }
}

// 模型编辑器样式
.models-editor {
  width: 100%;

  .model-tags-editor {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-sm;
    margin-bottom: $spacing-md;
    min-height: 32px;
    padding: $spacing-sm;
    background: $bg-secondary;
    border-radius: $radius-md;
    border: 1px dashed $border-color;

    .model-tag-editor {
      font-family: monospace;
    }
  }

  .add-model-input {
    display: flex;
    gap: $spacing-sm;
    margin-bottom: $spacing-sm;
  }

  .preset-models {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    flex-wrap: wrap;

    .hint {
      font-size: $font-size-sm;
      color: $text-tertiary;
    }

    .preset-model-tag {
      cursor: pointer;
      transition: $transition-fast;

      &:hover {
        transform: translateY(-1px);
        box-shadow: none;
      }
    }
  }
}

</style>
