@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.xr.harness.app

import io.ktor.client.statement.bodyAsText
import io.xr.harness.core.ApiGlue
import io.xr.harness.core.AuthError
import io.xr.harness.core.LoginOutcome
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.lab.shared.api.AuthApi
import io.xr.lab.shared.models.LoginRequest

/**
 * 真 login seam（swift App 层 APIGlue 同构）：唯一摸生成 AuthApi 的地方。 非 2xx 手解（生成 client 不抛，expectSuccess 未开）→
 * ApiGlue.mapHttpError； 传输层异常归一 AuthError.Unreachable；一律返回 outcome（零 throw，VM 据型映射文案）， 成功把
 * accessToken 交 TokenHolder（内存）。
 */
fun loginSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend (String, String) -> LoginOutcome {
  return { username, password ->
    val baseUrl = store.baseUrl
    if (baseUrl == null) {
      LoginOutcome.Failure(AuthError.ClientNotConfigured())
    } else {
      try {
        val authApi =
            AuthApi(
                baseUrl = ApiGlue.normalizeBaseURL(baseUrl),
                httpClientConfig = ApiGlue.bearerClientConfig(tokens),
            )
        val resp = authApi.authNativeLogin(LoginRequest(username = username, password = password))
        if (!resp.success) {
          val bodyText = runCatching { resp.response.bodyAsText() }.getOrNull()
          LoginOutcome.Failure(ApiGlue.mapHttpError(resp.status, bodyText))
        } else {
          val body = resp.body()
          tokens.accessToken = body.token
          LoginOutcome.Success(
              username = body.user.username,
              accessToken = body.token,
              tenantId = body.tenants.firstOrNull()?.tenantId,
          )
        }
      } catch (e: AuthError) {
        LoginOutcome.Failure(e)
      } catch (e: Exception) {
        LoginOutcome.Failure(AuthError.Unreachable(e))
      }
    }
  }
}
