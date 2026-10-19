package io.xr.harness.ui

import io.xr.lab.shared.models.FlowHistoryEntry
import io.xr.lab.shared.models.SampleReceipt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 接样单详情 VM（swift ReceiptDetailViewModel 同构，M03.F01.I06）：一次 load 同时取接样信息与流程历史。 fetch 缝返回 (接样单,
 * 历史)；真实现由 wire 层串行取生成层两接口。历史按 at 倒序（ISO 字符串序即时间序）。
 */
class ReceiptDetailViewModel(
    internal val fetch: suspend (String) -> Pair<SampleReceipt, List<FlowHistoryEntry>> = { _ ->
      throw IllegalStateException("fetch 缝未注入")
    },
) {
  private val _receipt = MutableStateFlow<SampleReceipt?>(null)
  val receipt: StateFlow<SampleReceipt?> = _receipt.asStateFlow()

  private val _history = MutableStateFlow<List<FlowHistoryEntry>>(emptyList())
  val history: StateFlow<List<FlowHistoryEntry>> = _history.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  suspend fun load(id: String) {
    _isLoading.value = true
    _error.value = null
    try {
      val (fetchedReceipt, fetchedHistory) = fetch(id)
      _receipt.value = fetchedReceipt
      _history.value = fetchedHistory.sortedByDescending { it.at }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      _error.value = e.message ?: "加载失败"
      _receipt.value = null
      _history.value = emptyList()
    }
    _isLoading.value = false
  }
}
