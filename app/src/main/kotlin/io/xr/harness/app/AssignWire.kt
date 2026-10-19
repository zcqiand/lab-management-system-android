package io.xr.harness.app

import io.xr.harness.core.AuthError
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.lab.shared.models.AssignTaskRequest
import io.xr.lab.shared.models.FlowAction
import io.xr.lab.shared.models.FlowActionRequest
import io.xr.lab.shared.models.FlowActionResult
import io.xr.lab.shared.models.SampleReceipt

/** 任务分配缝（swift AssignTaskViewModel.persist 同位，M03.F02.I02）：receiptsAssignTask 生成端点。 */
fun receiptsAssignTaskSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend (String, AssignTaskRequest) -> SampleReceipt {
  return { id, request ->
    try {
      val resp = receiptsApi(store, tokens).receiptsAssignTask(id, request)
      requireOk(resp)
      resp.body()
    } catch (e: AuthError) {
      throw e
    } catch (e: Exception) {
      throw AuthError.Unreachable(e)
    }
  }
}

/**
 * 分配阶段 act 缝（swift APIGlue.assigningAct 同位，M03.F02.I05）：统一端点 receiptsActFlowAssigning， operator
 * 取真会话身份（REQ-2026-004 Q3，缺席走 VM 守卫不兜底）。调用体内联（ReceiptWire act 缝同款）。
 */
fun receiptsAssignActSeam(
    store: SessionStore,
    tokens: TokenHolder,
    operator: String,
): suspend (FlowAction, List<String>, String?) -> List<FlowActionResult> {
  return { action, ids, reason ->
    try {
      val resp =
          receiptsApi(store, tokens)
              .receiptsActFlowAssigning(
                  FlowActionRequest(
                      ids = ids,
                      action = action,
                      `operator` = operator,
                      reason = reason,
                  ),
              )
      requireOk(resp)
      resp.body()
    } catch (e: AuthError) {
      throw e
    } catch (e: Exception) {
      throw AuthError.Unreachable(e)
    }
  }
}
