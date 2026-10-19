package io.xr.harness.app

import io.xr.harness.core.ApiGlue
import io.xr.harness.core.AuthError
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.lab.shared.api.SamplesApi
import io.xr.lab.shared.api.TestRecordsApi
import io.xr.lab.shared.models.Sample
import io.xr.lab.shared.models.TestRecord

/** 预览样品缝（swift APIGlue.receiptSamples 同位，M03.F09.I03）：按单拉样品，页大小 200 镜像家族。 */
fun reportPreviewSamplesSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend (String) -> List<Sample> {
  return { receiptId ->
    try {
      val resp =
          previewSamplesApi(store, tokens)
              .samplesListSamples(page = 1, pageSize = 200, receiptId = receiptId, keyword = null)
      requireOk(resp)
      resp.body().items
    } catch (e: AuthError) {
      throw e
    } catch (e: Exception) {
      throw AuthError.Unreachable(e)
    }
  }
}

/** 预览记录缝（swift APIGlue.testRecords 同位）：契约 list 只支持 sampleId 过滤，逐样品取。 */
fun reportPreviewRecordsSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend (String) -> List<TestRecord> {
  return { sampleId ->
    try {
      val resp =
          previewRecordsApi(store, tokens)
              .testRecordsListTestRecords(
                  page = 1,
                  pageSize = 200,
                  sampleId = sampleId,
                  parameterCode = null,
              )
      requireOk(resp)
      resp.body().items
    } catch (e: AuthError) {
      throw e
    } catch (e: Exception) {
      throw AuthError.Unreachable(e)
    }
  }
}

private fun previewSamplesApi(
    store: SessionStore,
    tokens: TokenHolder,
): SamplesApi {
  val baseUrl = store.baseUrl ?: throw AuthError.ClientNotConfigured()
  return SamplesApi(
      baseUrl = ApiGlue.normalizeBaseURL(baseUrl),
      httpClientConfig = ApiGlue.bearerClientConfig(tokens),
  )
}

private fun previewRecordsApi(
    store: SessionStore,
    tokens: TokenHolder,
): TestRecordsApi {
  val baseUrl = store.baseUrl ?: throw AuthError.ClientNotConfigured()
  return TestRecordsApi(
      baseUrl = ApiGlue.normalizeBaseURL(baseUrl),
      httpClientConfig = ApiGlue.bearerClientConfig(tokens),
  )
}
