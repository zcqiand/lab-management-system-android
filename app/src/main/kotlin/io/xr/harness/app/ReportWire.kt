package io.xr.harness.app

import io.xr.harness.ReportPhase
import io.xr.harness.core.AuthError
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.lab.shared.api.ReceiptsApi
import io.xr.lab.shared.infrastructure.HttpResponse
import io.xr.lab.shared.models.FlowAction
import io.xr.lab.shared.models.FlowActionRequest
import io.xr.lab.shared.models.FlowActionResult

/**
 * 报告四阶段 act 缝（swift APIGlue.phaseAct 同位，M03.F05~F08.I0x，REQ-2026-006）：按阶段档位路由到
 * review/approve/issuance/archived 统一端点；队列缝复用 receiptsListSeam（刀 7）。
 */
fun receiptsPhaseActSeam(
    store: SessionStore,
    tokens: TokenHolder,
    phase: ReportPhase,
    operator: String,
): suspend (FlowAction, List<String>, String?) -> List<FlowActionResult> {
  return { action, ids, reason ->
    try {
      val resp =
          receiptsApi(store, tokens)
              .receiptsActFlow(
                  phase = phase,
                  request =
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

/** 阶段档位 → 端点路由（swift switch phase 同位，收口一处便于测试与档位增补）。 */
private suspend fun ReceiptsApi.receiptsActFlow(
    phase: ReportPhase,
    request: FlowActionRequest,
): HttpResponse<List<FlowActionResult>> =
    when (phase) {
      ReportPhase.Review -> receiptsActFlowReview(request)
      ReportPhase.Approval -> receiptsActFlowApprove(request)
      ReportPhase.Issuance -> receiptsActFlowIssuance(request)
      ReportPhase.Archived -> receiptsActFlowArchived(request)
    }
