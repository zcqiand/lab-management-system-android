package io.xr.harness.app

import io.xr.harness.core.ApiGlue
import io.xr.harness.core.AuthError
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.lab.shared.api.ReportNamesApi
import io.xr.lab.shared.api.SamplesApi
import io.xr.lab.shared.models.InspectionReportName
import io.xr.lab.shared.models.UpdateSampleExtRequest

/** 报告名目录缝（swift APIGlue.reportNames 同位，M03.F01.I07）：页大小 200 镜像家族，Q1 裁定走 live 契约端点。 */
fun sampleExtReportNamesSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend (Int) -> List<InspectionReportName> {
  return { pageSize ->
    try {
      val resp =
          reportNamesApi(store, tokens)
              .reportNamesListReportNames(page = 1, pageSize = pageSize, keyword = null)
      requireOk(resp)
      resp.body().items
    } catch (e: AuthError) {
      throw e
    } catch (e: Exception) {
      throw AuthError.Unreachable(e)
    }
  }
}

/** 样品 ext 持久化缝（swift APIGlue.updateSampleExt 同位）：合并语义在 VM，端点收合并后全量 ext。 */
fun sampleExtPersistSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend (String, Map<String, String>) -> Unit {
  return { id, ext ->
    try {
      val resp =
          samplesExtApi(store, tokens).samplesUpdateSampleExt(id, UpdateSampleExtRequest(ext = ext))
      requireOk(resp)
      Unit
    } catch (e: AuthError) {
      throw e
    } catch (e: Exception) {
      throw AuthError.Unreachable(e)
    }
  }
}

private fun reportNamesApi(
    store: SessionStore,
    tokens: TokenHolder,
): ReportNamesApi {
  val baseUrl = store.baseUrl ?: throw AuthError.ClientNotConfigured()
  return ReportNamesApi(
      baseUrl = ApiGlue.normalizeBaseURL(baseUrl),
      httpClientConfig = ApiGlue.bearerClientConfig(tokens),
  )
}

private fun samplesExtApi(
    store: SessionStore,
    tokens: TokenHolder,
): SamplesApi {
  val baseUrl = store.baseUrl ?: throw AuthError.ClientNotConfigured()
  return SamplesApi(
      baseUrl = ApiGlue.normalizeBaseURL(baseUrl),
      httpClientConfig = ApiGlue.bearerClientConfig(tokens),
  )
}
