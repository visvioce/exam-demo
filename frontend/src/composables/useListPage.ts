/**
 * 通用列表页 Composable
 *
 * 封装列表页面的通用逻辑：分页加载、搜索、刷新、错误处理
 * 适用于需要分页展示数据的列表页面（公告、课程、考试等）
 *
 * 核心功能：
 * - 自动分页加载（支持页码切换和每页条数切换）
 * - 搜索参数管理（setSearchParams / clearSearchParams）
 * - 竞态请求防护（通过 requestId 确保只应用最后一次请求结果）
 * - 加载状态管理（loading、hasData、isEmpty）
 *
 * @example
 * ```ts
 * const { data, loading, pagination, handlePageChange } = useListPage({
 *   fetchFn: (params) => announcementApi.page(params),
 *   defaultPageSize: 10
 * })
 * ```
 */
import { ref, reactive, computed, onMounted } from 'vue'
import type { PageRequest, PageResult } from '@/types'

export interface UseListPageOptions<T> {
  /** 分页数据获取函数，接收分页参数，返回分页结果 */
  fetchFn: (params: PageRequest) => Promise<{ data: PageResult<T> }>
  /** 默认每页条数，默认 10 */
  defaultPageSize?: number
  /** 是否在组件挂载时立即加载数据，默认 true */
  immediate?: boolean
  /** 请求失败时的回调 */
  onError?: (error: unknown) => void
}

export function useListPage<T>(options: UseListPageOptions<T>) {
  const { fetchFn, defaultPageSize = 10, immediate = true } = options

  // 列表数据
  const data = ref<T[]>([])
  // 加载状态
  const loading = ref(false)
  // 总记录数
  const total = ref(0)

  // 分页参数（响应式，修改后自动触发重渲染）
  const pagination = reactive({
    current: 1,
    size: defaultPageSize
  })

  // 搜索参数（响应式对象，可动态添加/删除属性）
  const searchParams = reactive<Record<string, unknown>>({})

  // 计算属性：是否有数据
  const hasData = computed(() => data.value.length > 0)
  // 计算属性：是否为空状态（非加载中且无数据）
  const isEmpty = computed(() => !loading.value && data.value.length === 0)

  // 竞态请求 ID，用于防止快速切换页面时旧请求覆盖新结果
  let latestRequestId = 0

  /**
   * 加载数据
   * 每次调用时递增 requestId，只有最新请求的结果才会被应用
   */
  async function loadData() {
    const requestId = ++latestRequestId
    loading.value = true
    try {
      const params: PageRequest = {
        current: pagination.current,
        size: pagination.size,
        ...searchParams
      }
      const response = await fetchFn(params)
      // 竞态检查：只应用最新请求的结果
      if (requestId !== latestRequestId) return
      data.value = response.data.records
      total.value = response.data.total
    } catch (error) {
      if (requestId === latestRequestId) {
        options.onError?.(error)
        data.value = []
        total.value = 0
      }
    } finally {
      if (requestId === latestRequestId) {
        loading.value = false
      }
    }
  }

  /** 刷新当前页数据（保持当前页码和搜索条件） */
  async function refresh() {
    await loadData()
  }

  /** 重置到第一页并重新加载 */
  async function reset() {
    pagination.current = 1
    await loadData()
  }

  /** 页码变化处理 */
  async function handlePageChange(page: number) {
    pagination.current = page
    await loadData()
  }

  /** 每页条数变化处理（切换条数时重置到第一页） */
  async function handleSizeChange(size: number) {
    pagination.size = size
    pagination.current = 1
    await loadData()
  }

  /** 设置搜索参数并重新加载（重置到第一页） */
  async function setSearchParams(params: Record<string, unknown>) {
    Object.assign(searchParams, params)
    await reset()
  }

  /** 清空所有搜索参数并重新加载 */
  async function clearSearchParams() {
    Object.keys(searchParams).forEach(key => {
      delete searchParams[key]
    })
    await reset()
  }

  // 组件挂载时自动加载数据（若 immediate 为 true）
  if (immediate) {
    onMounted(loadData)
  }

  return {
    data,
    loading,
    total,
    pagination,
    searchParams,
    hasData,
    isEmpty,
    loadData,
    refresh,
    reset,
    handlePageChange,
    handleSizeChange,
    setSearchParams,
    clearSearchParams
  }
}
