/**
 * Vue 应用入口文件
 *
 * 负责初始化 Vue 应用，注册核心插件和全局资源：
 * 1. Pinia - 状态管理（替代 Vuex）
 * 2. Vue Router - 前端路由
 * 3. Element Plus - UI 组件库（中文）
 * 4. Element Plus Icons - 图标库（全局注册所有图标组件）
 * 5. 全局样式 - 设计令牌、基础样式
 *
 * 应用挂载到 index.html 中的 #app 元素上
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import router from './router'
import App from './App.vue'
import './styles/index.scss'

// 创建 Vue 应用实例
const app = createApp(App)
// 创建 Pinia 状态管理实例
const pinia = createPinia()

// 全局注册所有 Element Plus 图标组件，避免在每个组件中单独导入
// 遍历图标库，将每个图标注册为全局组件，可直接在模板中使用
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 注册插件：顺序为 Pinia -> Router -> ElementPlus
app.use(pinia)
app.use(router)
app.use(ElementPlus)

// 将 Vue 应用挂载到 DOM 的 #app 元素上
app.mount('#app')
