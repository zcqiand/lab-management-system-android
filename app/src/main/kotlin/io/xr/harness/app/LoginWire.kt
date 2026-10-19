package io.xr.harness.app

import io.ktor.client.statement.bodyAsText
import io.xr.harness.core.ApiGlue
import io.xr.harness.core.AuthError
import io.xr.harness.core.LoginOutcome
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.harness.core.WhoamiUser
import io.xr.lab.shared.api.AuthApi
import io.xr.lab.shared.models.AuthLogoutRequest
import io.xr.lab.shared.models.LoginRequest
import io.xr.lab.shared.models.LoginResponse

/**
 * 真 seams（swift App 层 APIGlue 同构）：唯一摸生成 Api 类的地方。 非 2xx 手解（生成 client 不抛，expectSuccess 未开）→
 * ApiGlue.mapHttpError； 传输层异常归一 AuthError.Unreachable；login 一律返回 outcome（零 throw，VM 据型映射文案）， 成功把
 * access/refresh 交 TokenHolder（内存）。 lab 无 clientId（登录代理化 REQ-2026-023，走 /api/auth/native-login）。
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
          handleLoginBody(resp.body(), tokens)
        }
      } catch (e: AuthError) {
        LoginOutcome.Failure(e)
      } catch (e: Exception) {
        LoginOutcome.Failure(AuthError.Unreachable(e))
      }
    }
  }
}

/** 登录 200 体收编：lab LoginResponse.token 必填（生成契约保证非空），token 交内存持有。 租户取首个 my-tenant（当前租户切 换归后刀）。 */
private fun handleLoginBody(body: LoginResponse, tokens: TokenHolder): LoginOutcome {
  tokens.accessToken = body.token
  tokens.refreshToken = body.refreshToken
  return LoginOutcome.Success(
      username = body.user.username,
      accessToken = body.token,
      refreshToken = body.refreshToken,
      tenantId = body.tenants.firstOrNull()?.tenantId,
  )
}

/**
 * whoami seam（restore 自愈用）：AuthApi.authGetCurrentUser 校验 token，成功归一 WhoamiUser。 非 2xx（含 401 过期）或
 * 任何异常 → null，由 VM 清密回登录。 lab 返回 CurrentUserSession——user.username/user.displayName 齐备，无 saas 侧 无
 * username 的回退问题。
 */
fun whoamiSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend () -> WhoamiUser? {
  return {
    val baseUrl = store.baseUrl
    if (baseUrl == null) {
      null
    } else {
      try {
        val authApi =
            AuthApi(
                baseUrl = ApiGlue.normalizeBaseURL(baseUrl),
                httpClientConfig = ApiGlue.bearerClientConfig(tokens),
            )
        val resp = authApi.authGetCurrentUser()
        if (!resp.success) {
          null
        } else {
          val body = resp.body()
          WhoamiUser(
              username = body.user.username,
              displayName = body.user.displayName,
              tenantId = body.currentTenantId ?: body.tenants.firstOrNull()?.tenantId,
          )
        }
      } catch (e: Exception) {
        null
      }
    }
  }
}

/**
 * logout seam：AuthLogoutRequest.token 必填（服务端按 token 吊销）——无 token 直接跳过网络（本地清密照走）。 吊销尽力而
 * 为，失败不阻断本地清密；本地清密归 SessionStore.clearSession。
 */
fun logoutSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend () -> Unit {
  return {
    val baseUrl = store.baseUrl
    val access = tokens.accessToken
    if (baseUrl != null && access != null) {
      try {
        AuthApi(
                baseUrl = ApiGlue.normalizeBaseURL(baseUrl),
                httpClientConfig = ApiGlue.bearerClientConfig(tokens),
            )
            .authLogout(AuthLogoutRequest(token = access))
      } catch (e: Exception) {
        // 吊销失败不阻断登出
      }
    }
  }
}
