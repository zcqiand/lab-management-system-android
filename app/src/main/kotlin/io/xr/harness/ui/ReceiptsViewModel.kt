package io.xr.harness.ui

import io.xr.harness.core.ReceiptFilter
import io.xr.harness.core.ReceiptListQuery
import io.xr.harness.core.ReceiptsPaging
import io.xr.lab.shared.models.FlowActionResult
import io.xr.lab.shared.models.SampleReceipt
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 接样单列表 VM（swift ReceiptListViewModel 同构）：provider 缝注入 + 分页装载。 语义钉死：load 回第 1 页整表替换； loadNextPage
 * 追加、空页回滚页码并停；失败回滚页码报错（可重试）。act 回填/删除归后刀。
 */
class ReceiptsViewModel(internal val seams: Seams = Seams()) {
  data class Seams(
      val list: suspend (ReceiptListQuery) -> List<SampleReceipt> = { _ ->
        throw IllegalStateException("seam 未注入: list")
      },
  )

  private val _items = MutableStateFlow<List<SampleReceipt>>(emptyList())
  val items: StateFlow<List<SampleReceipt>> = _items.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  private val _hasMore = MutableStateFlow(true)
  val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

  var query: ReceiptListQuery = ReceiptListQuery()
    private set

  /** 回第 1 页整表替换（swift load 同构）；filter/keyword 变更经此入查询（keyword trim、空白归 null）。 */
  suspend fun load(filter: ReceiptFilter = query.filter, keyword: String? = query.keyword) {
    query =
        query.copy(
            page = ReceiptsPaging.DEFAULT_PAGE,
            filter = filter,
            keyword = keyword?.trim()?.takeIf { it.isNotEmpty() },
        )
    fetch(reset = true)
  }

  /** 追加下一页（swift loadNextPage 同构）；空页/失败回滚页码，hasMore 停止。 */
  suspend fun loadNextPage() {
    query = query.copy(page = query.page + 1)
    fetch(reset = false)
  }

  /** act 结果回填（swift apply 同构）：按 id 更新 flowStatus；结果不带阶段或空结果集不动表。 */
  fun apply(results: List<FlowActionResult>) {
    if (results.isEmpty()) {
      return
    }
    val byId = results.associateBy { it.id }
    _items.value =
        _items.value.map { item ->
          val result = byId[item.id]
          if (result != null && result.flowStatus != null) {
            item.copy(flowStatus = result.flowStatus)
          } else {
            item
          }
        }
  }

  private suspend fun fetch(reset: Boolean) {
    _isLoading.value = true
    _error.value = null
    val requestedPage = query.page
    val fetched =
        try {
          seams.list(query)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          _error.value = e.message ?: "加载失败"
          if (!reset) {
            query = query.copy(page = max(ReceiptsPaging.DEFAULT_PAGE, requestedPage - 1))
          }
          _isLoading.value = false
          return
        }
    when {
      reset -> _items.value = fetched
      fetched.isEmpty() -> {
        query = query.copy(page = max(ReceiptsPaging.DEFAULT_PAGE, requestedPage - 1))
        _hasMore.value = false
        _isLoading.value = false
        return
      }
      else -> _items.value = _items.value + fetched
    }
    _hasMore.value = fetched.size >= query.pageSize
    _isLoading.value = false
  }
}
