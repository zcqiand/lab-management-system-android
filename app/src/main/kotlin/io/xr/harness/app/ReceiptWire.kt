package io.xr.harness.app

import io.ktor.client.statement.bodyAsText
import io.xr.harness.core.ApiGlue
import io.xr.harness.core.AuthError
import io.xr.harness.core.ReceiptListQuery
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.lab.shared.api.ReceiptsApi
import io.xr.lab.shared.models.SampleReceipt

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
  val baseUrl = store.baseUrl ?: throw AuthError.ClientNotConfigured()
  val api =
      ReceiptsApi(
          baseUrl = ApiGlue.normalizeBaseURL(baseUrl),
          httpClientConfig = ApiGlue.bearerClientConfig(tokens),
      )
  val resp =
      api.receiptsListReceipts(
          page = query.page,
          pageSize = query.pageSize,
          keyword = query.keyword,
          flowStatus = query.flowStatus,
          filter = query.filter.queryValue,
      )
  if (!resp.success) {
    val bodyText = runCatching { resp.response.bodyAsText() }.getOrNull()
    throw ApiGlue.mapHttpError(resp.status, bodyText)
  }
  return resp.body().items
}
