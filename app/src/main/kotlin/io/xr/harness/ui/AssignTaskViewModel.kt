package io.xr.harness.ui

import io.xr.lab.shared.models.AssignTaskRequest
import io.xr.lab.shared.models.SampleReceipt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 安排/取消表单错误：安排语义下两字段都必填，缺一 fail-fast 不打端点（swift AssignTaskError.invalidFields 同位）。 */
class AssignTaskInvalidFieldsException : Exception("安排需同时填写检测人员姓名与计划检测日期")

/**
 * 安排/取消 VM（swift AssignTaskViewModel 同构，M03.F02.I02）：手填「检测人员姓名 + 计划检测日期」（Q2 裁定， assigneeId
 * 恒不传）。任一字段非空（trim 后）= 安排语义，两字段都必填；全空 = 取消安排（全 nil 清空请求）。 失败以 errorMessage 呈现，不清用户输入（AC-6）。
 */
class AssignTaskViewModel(
    internal val persist: suspend (String, AssignTaskRequest) -> SampleReceipt = { _, _ ->
      throw IllegalStateException("persist 缝未注入")
    },
) {
  private val _isSaving = MutableStateFlow(false)
  val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  var assigneeName: String = ""
  var plannedTestDate: String = ""

  /** 组装请求（swift makeRequest 同构）：全空 = 取消安排，缺一 = fail-fast。 */
  fun makeRequest(): AssignTaskRequest {
    val name = assigneeName.trim()
    val date = plannedTestDate.trim()
    if (name.isEmpty() && date.isEmpty()) {
      return AssignTaskRequest(assigneeId = null, assigneeName = null, plannedTestDate = null)
    }
    if (name.isEmpty() || date.isEmpty()) {
      throw AssignTaskInvalidFieldsException()
    }
    return AssignTaskRequest(assigneeId = null, assigneeName = name, plannedTestDate = date)
  }

  /** 保存：校验不过或端点失败都返回 null 并落 errorMessage（不打断 UI，swift save 同构）。 */
  suspend fun save(id: String): SampleReceipt? {
    _isSaving.value = true
    _errorMessage.value = null
    val request =
        try {
          makeRequest()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          _errorMessage.value = e.message ?: "保存失败"
          _isSaving.value = false
          return null
        }
    return try {
      persist(id, request)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      _errorMessage.value = e.message ?: "保存失败"
      null
    } finally {
      _isSaving.value = false
    }
  }
}
