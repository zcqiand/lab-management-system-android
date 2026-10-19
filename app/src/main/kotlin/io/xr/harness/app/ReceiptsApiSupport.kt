package io.xr.harness.app

import io.ktor.client.statement.bodyAsText
import io.xr.harness.core.ApiGlue
import io.xr.harness.core.AuthError
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.lab.shared.api.ReceiptsApi
import io.xr.lab.shared.infrastructure.HttpResponse

/** Api 构造（唯一摸 ReceiptsApi 的入口；baseUrl 缺失显式抛）。自 ReceiptWire 拆出（TooManyFunctions 阈值）。 */
internal fun receiptsApi(
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
internal suspend fun <T> requireOk(resp: HttpResponse<T>) {
  if (!resp.success) {
    val bodyText = runCatching { resp.response.bodyAsText() }.getOrNull()
    throw ApiGlue.mapHttpError(resp.status, bodyText)
  }
}
