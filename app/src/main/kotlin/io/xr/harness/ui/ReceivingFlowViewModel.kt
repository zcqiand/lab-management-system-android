package io.xr.harness.ui

import io.xr.lab.shared.models.FlowAction
import io.xr.lab.shared.models.FlowActionRequest
import io.xr.lab.shared.models.FlowActionResult

/** act 错误：空选区 fail-fast，不打端点（§1 禁兜底同源，swift FlowActionError.emptySelection 同位）。 */
class FlowActionEmptySelectionException : Exception("未选择接样单")

/** act 错误：操作人身份缺席 fail-fast（§3 禁 demo 身份兜底，swift FlowActionError.missingOperator 同位）。 */
class FlowActionMissingOperatorException : Exception("缺少操作人身份")

/**
 * 接样阶段流转 VM（swift ReceivingFlowViewModel 同构，M03.F01.I04/I08）：选区/身份校验 + 请求组装 + 结果透传。 能否
 * submit/return/withdraw 的流转语义在后端裁决——ok=false 是逐单业务失败，透传不 throw，由 UI 按单呈现。 act 缝签 (action, ids,
 * reason) → 逐单结果；operator 由 VM 持有（真会话身份，不兜底）。
 */
class ReceivingFlowViewModel(
    private val operatorName: String,
    internal val act: suspend (FlowAction, List<String>, String?) -> List<FlowActionResult> =
        { _, _, _ ->
          throw IllegalStateException("act 缝未注入")
        },
) {

  /** 执行流转（swift perform 同构）：守卫失败即抛，成功透传逐单结果。 */
  suspend fun perform(
      action: FlowAction,
      ids: List<String>,
      reason: String? = null,
  ): List<FlowActionResult> {
    requireGuards(ids)
    return act(action, ids, reason)
  }

  /** 组装生成层 FlowActionRequest（UI 壳直连生成 API 时复用；守卫同 perform）。 */
  fun makeRequest(
      action: FlowAction,
      ids: List<String>,
      reason: String? = null,
  ): FlowActionRequest {
    requireGuards(ids)
    return FlowActionRequest(ids = ids, action = action, `operator` = operatorName, reason = reason)
  }

  private fun requireGuards(ids: List<String>) {
    if (ids.isEmpty()) {
      throw FlowActionEmptySelectionException()
    }
    if (operatorName.isBlank()) {
      throw FlowActionMissingOperatorException()
    }
  }
}
