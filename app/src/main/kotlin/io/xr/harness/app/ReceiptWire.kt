package io.xr.harness.app

import io.xr.harness.core.AuthError
import io.xr.harness.core.ReceiptListQuery
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.lab.shared.models.CreateSampleReceiptRequest
import io.xr.lab.shared.models.FlowAction
import io.xr.lab.shared.models.FlowActionRequest
import io.xr.lab.shared.models.FlowActionResult
import io.xr.lab.shared.models.FlowHistoryEntry
import io.xr.lab.shared.models.SampleReceipt
import io.xr.lab.shared.models.UpdateSampleReceiptRequest

/**
 * 接样单列表 seam（swift APIGlue.list 同位）：唯一摸 ReceiptsApi 的地方。 非 2xx 手解（生成 client 不抛）→ mapHttpError
 * 抛出；传输层异常归一 Unreachable——swift provider 契约就是 throw，VM catch 入 error 态。
 */
fun receiptsListSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend (ReceiptListQuery) -> List<SampleReceipt> {
  return { query ->
    try {
      callReceipts(store, tokens, query)
    } catch (e: AuthError) {
      throw e
    } catch (e: Exception) {
      throw AuthError.Unreachable(e)
    }
  }
}

/** 单次列表调用（handleLoginBody 同位：抽薄 seam 主函数）。 */
private suspend fun callReceipts(
    store: SessionStore,
    tokens: TokenHolder,
    query: ReceiptListQuery,
): List<SampleReceipt> {
  val resp =
      receiptsApi(store, tokens)
          .receiptsListReceipts(
              page = query.page,
              pageSize = query.pageSize,
              keyword = query.keyword,
              flowStatus = query.flowStatus,
              filter = query.filter.queryValue,
          )
  requireOk(resp)
  return resp.body().items
}

/** 表单持久化缝（swift ReceiptFormViewModel.persist 同位）：id 缺 → create；id 有 → update（PATCH）。 */
fun receiptsFormPersistSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend (String?, CreateSampleReceiptRequest?, UpdateSampleReceiptRequest?) -> SampleReceipt {
  return { id, create, update ->
    try {
      persistCall(store, tokens, id, create, update)
    } catch (e: AuthError) {
      throw e
    } catch (e: Exception) {
      throw AuthError.Unreachable(e)
    }
  }
}

private suspend fun persistCall(
    store: SessionStore,
    tokens: TokenHolder,
    id: String?,
    create: CreateSampleReceiptRequest?,
    update: UpdateSampleReceiptRequest?,
): SampleReceipt {
  val api = receiptsApi(store, tokens)
  val resp =
      if (id == null) {
        api.receiptsCreateReceipt(requireNotNull(create))
      } else {
        api.receiptsUpdateReceipt(id, requireNotNull(update))
      }
  requireOk(resp)
  return resp.body()
}

/** 详情缝（swift ReceiptDetailViewModel.fetch 同位）：接样单 + 流程历史两接口一次取齐。 */
fun receiptsDetailSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend (String) -> Pair<SampleReceipt, List<FlowHistoryEntry>> {
  return { id ->
    try {
      detailCall(store, tokens, id)
    } catch (e: AuthError) {
      throw e
    } catch (e: Exception) {
      throw AuthError.Unreachable(e)
    }
  }
}

private suspend fun detailCall(
    store: SessionStore,
    tokens: TokenHolder,
    id: String,
): Pair<SampleReceipt, List<FlowHistoryEntry>> {
  val api = receiptsApi(store, tokens)
  val respReceipt = api.receiptsGetReceipt(id)
  requireOk(respReceipt)
  val respHistory = api.receiptsGetReceiptHistory(id)
  requireOk(respHistory)
  return respReceipt.body() to respHistory.body()
}

/** 删除缝（swift ReceiptDeleteViewModel.delete 同位）。 */
fun receiptsDeleteSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend (String) -> Unit {
  return { id ->
    try {
      deleteCall(store, tokens, id)
    } catch (e: AuthError) {
      throw e
    } catch (e: Exception) {
      throw AuthError.Unreachable(e)
    }
  }
}

private suspend fun deleteCall(
    store: SessionStore,
    tokens: TokenHolder,
    id: String,
) {
  val resp = receiptsApi(store, tokens).receiptsDeleteReceipt(id)
  requireOk(resp)
}

/**
 * act 缝（swift APIGlue.act 接样阶段同位，M03.F01.I04/I08）：接样阶段流转统一端点，operator 由调用方给真会话身份。 调用体 内联在缝
 * lambda（actCall 六参超 LongParameterList 阈值，且本文件函数数贴 TooManyFunctions 上限）。
 */
fun receiptsActSeam(
    store: SessionStore,
    tokens: TokenHolder,
    operator: String,
): suspend (FlowAction, List<String>, String?) -> List<FlowActionResult> {
  return { action, ids, reason ->
    try {
      val resp =
          receiptsApi(store, tokens)
              .receiptsActFlowReceiving(
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
