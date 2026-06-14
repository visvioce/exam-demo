/**
 * 带搜索表单的通用分页列表 Composable
 *
 * 在 useListPage 的基础上增加了搜索表单管理功能
 * 适用于需要搜索表单（而非简单的搜索参数）的列表页面
 *
 * 核心功能：
 * - 分页加载（支持页码和每页条数切换）
 * - 搜索表单状态管理（resetSearch / toggleSearch）
 * - 搜索参数转换（transformSearch 将表单数据转为 API 参数）
 * - 竞态请求防护（通过 requestId 确保只应用最后一次请求结果）
 *
 * @example
 * ```ts
 * const { records, loading, pagination, searchForm, loadFromFirstPage } = usePagedList({
 *   createSearchForm: () => ({ keyword: '', status: '' }),
 *   fetchPage: (params) => examApi.page(params),
 * })
 * ```
 */
import { reactive, ref } from 'vue'

export interface PagedListResponse<T> {
  records: T[]
  total: number
}

interface UsePagedListOptions<T, TSearch extends Record<string, unknown>> {
  /** 创建初始搜索表单的函数 */
  createSearchForm: () => TSearch
  /** 分页数据获取函数 */
  fetchPage: (params: { current: number; size: number } & Partial<TSearch>) => Promise<PagedListResponse<T>>
  /** 默认每页条数，默认 10 */
  pageSize?: number
  /** 搜索表单数据转为 API 参数的转换函数（用于过滤空值、格式化等） */
  transformSearch?: (search: TSearch) => Partial<TSearch>
  /** 请求失败时的回调 */
  onError?: (error: unknown) => void
}

export function usePagedList<T, TSearch extends Record<string, unknown>>(options: UsePagedListOptions<T, TSearch>) {
  // 列表数据
  const records = ref<T[]>([])
  // 加载状态
  const loading = ref(false)
  // 分页参数
  const pagination = reactive({
    current: 1,
    size: options.pageSize ?? 10,
    total: 0
  })
  // 搜索表单（响应式对象，绑定到模板中的 el-input 等）
  const searchForm = reactive(options.createSearchForm()) as TSearch

  // 竞态请求 ID
  let latestRequestId = 0

  /** 获取搜索表单的快照（避免异步过程中表单被修改） */
  function getSearchSnapshot(): TSearch {
    return { ...(searchForm as TSearch) }
  }

  /**
   * 加载数据
   * 每次调用递增 requestId，防止快速操作时旧请求覆盖新结果
   */
  async function load() {
    const requestId = ++latestRequestId
    loading.value = true
    try {
      const searchSnapshot = getSearchSnapshot()
      const searchParams = options.transformSearch
        ? options.transformSearch(searchSnapshot)
        : (searchSnapshot as Partial<TSearch>)
      const result = await options.fetchPage({
        current: pagination.current,
        size: pagination.size,
        ...searchParams
      })

      // 仅应用最后一次请求结果，避免慢请求覆盖新筛选结果
      if (requestId !== latestRequestId) {
        return
      }

      records.value = result.records
      pagination.total = result.total
    } catch (error) {
      if (requestId === latestRequestId) {
        options.onError?.(error)
      }
    } finally {
      if (requestId === latestRequestId) {
        loading.value = false
      }
    }
  }

  /** 从第一页开始加载（搜索或筛选变更时使用） */
  async function loadFromFirstPage() {
    pagination.current = 1
    await load()
  }

  /** 重置搜索表单为初始值并重新加载 */
  async function resetSearch() {
    Object.assign(searchForm, options.createSearchForm())
    await loadFromFirstPage()
  }

  /**
   * 切换搜索字段值（适用于标签切换等场景）
   * 如果当前值等于目标值，则恢复为默认值；否则设为目标值
   */
  async function toggleSearch<K extends keyof TSearch>(key: K, value: TSearch[K], defaultValue: TSearch[K]) {
    searchForm[key] = searchForm[key] === value ? defaultValue : value
    await loadFromFirstPage()
  }

  return {
    records,
    loading,
    pagination,
    searchForm,
    load,
    loadFromFirstPage,
    resetSearch,
    toggleSearch
  }
}
