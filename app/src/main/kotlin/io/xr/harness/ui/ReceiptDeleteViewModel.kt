package io.xr.harness.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 接样单删除 VM（swift ReceiptDeleteViewModel 同构，M03.F01.I03）：确认后单条删除；失败以 error 呈现不崩。 */
class ReceiptDeleteViewModel(
    internal val deleteSeam: suspend (String) -> Unit = { _ ->
      throw IllegalStateException("delete 缝未注入")
    },
) {
  private val _isDeleting = MutableStateFlow(false)
  val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  suspend fun delete(id: String) {
    _isDeleting.value = true
    _error.value = null
    try {
      deleteSeam(id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      _error.value = e.message ?: "删除失败"
    }
    _isDeleting.value = false
  }
}
