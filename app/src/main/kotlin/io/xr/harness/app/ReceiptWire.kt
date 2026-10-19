package io.xr.harness.app

import io.ktor.client.statement.bodyAsText
import io.xr.harness.core.ApiGlue
import io.xr.harness.core.AuthError
import io.xr.harness.core.ReceiptListQuery
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.lab.shared.api.ReceiptsApi
import io.xr.lab.shared.infrastructure.HttpResponse
import io.xr.lab.shared.models.CreateSampleReceiptRequest
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

/** Api 构造（唯一摸 ReceiptsApi 的入口；baseUrl 缺失显式抛）。 */
private fun receiptsApi(
    store: SessionStore,
    tokens: TokenHolder,
): ReceiptsApi {
  val baseUrl = store.baseUrl ?: throw AuthError.ClientNotConfigured()
  return ReceiptsApi(
      baseUrl = ApiGlue.normalizeBaseURL(baseUrl),
      httpClientConfig = ApiGlue.bearerClientConfig(tokens),
  )
}

/** 非 2xx 手解（生成 client 不抛）→ mapHttpError。 */
private suspend fun <T> requireOk(resp: HttpResponse<T>) {
  if (!resp.success) {
    val bodyText = runCatching { resp.response.bodyAsText() }.getOrNull()
    throw ApiGlue.mapHttpError(resp.status, bodyText)
  }
}
