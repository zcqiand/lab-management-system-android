package io.xr.harness.app

import io.xr.harness.core.ApiGlue
import io.xr.harness.core.AuthError
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.lab.shared.api.InspectionDictionaryApi
import io.xr.lab.shared.api.SamplesApi
import io.xr.lab.shared.api.TestRecordsApi
import io.xr.lab.shared.models.CreateTestRecordRequest
import io.xr.lab.shared.models.FlowAction
import io.xr.lab.shared.models.FlowActionRequest
import io.xr.lab.shared.models.FlowActionResult
import io.xr.lab.shared.models.InspectionParameter
import io.xr.lab.shared.models.Sample
import io.xr.lab.shared.models.TestRecord
import io.xr.lab.shared.models.UpdateTestRecordRequest

/** 目录缝（swift APIGlue.receiptSamples+parameters 同位，M03.F03.I01）：按单拉样品 + 参数字典，页大小 200 镜像家族。 */
fun dataEntryCatalogSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend (String) -> Pair<List<Sample>, List<InspectionParameter>> {
  return { receiptId ->
    try {
      val respSamples =
          samplesApi(store, tokens)
              .samplesListSamples(page = 1, pageSize = 200, receiptId = receiptId, keyword = null)
      requireOk(respSamples)
      val respParameters =
          dictionaryApi(store, tokens)
              .inspectionDictionaryListParameters(
                  page = 1,
                  pageSize = 200,
                  keyword = null,
                  sourceType = null,
              )
      requireOk(respParameters)
      Pair(respSamples.body().items, respParameters.body().items)
    } catch (e: AuthError) {
      throw e
    } catch (e: Exception) {
      throw AuthError.Unreachable(e)
    }
  }
}

/** 逐样品记录缝（swift APIGlue.testRecords 同位）：家族同款一页 200。 */
fun dataEntryRecordsSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend (String) -> List<TestRecord> {
  return { sampleId ->
    try {
      val resp =
          recordsApi(store, tokens)
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

/**
 * 录入持久化缝（swift APIGlue.persistTestRecord 同位，I02/I03）：id+update → update；否则 create。verdict 均随
 * body（Q3，不走 setVerdict）。
 */
fun dataEntryPersistSeam(
    store: SessionStore,
    tokens: TokenHolder,
): suspend (String?, CreateTestRecordRequest?, UpdateTestRecordRequest?) -> TestRecord {
  return { id, create, update ->
    try {
      if (id != null && update != null) {
        val resp = recordsApi(store, tokens).testRecordsUpdateTestRecord(id, update)
        requireOk(resp)
        resp.body()
      } else {
        val resp = recordsApi(store, tokens).testRecordsCreateTestRecord(requireNotNull(create))
        requireOk(resp)
        resp.body()
      }
    } catch (e: AuthError) {
      throw e
    } catch (e: Exception) {
      throw AuthError.Unreachable(e)
    }
  }
}

/** 录入阶段 act 缝（swift APIGlue.dataEntryAct 同位，M03.F03.I12）：统一端点 receiptsActFlowDataEntry。 */
fun dataEntryActSeam(
    store: SessionStore,
    tokens: TokenHolder,
    operator: String,
): suspend (FlowAction, List<String>, String?) -> List<FlowActionResult> {
  return { action, ids, reason ->
    try {
      val resp =
          receiptsApi(store, tokens)
              .receiptsActFlowDataEntry(
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

private fun samplesApi(
    store: SessionStore,
    tokens: TokenHolder,
): SamplesApi {
  val baseUrl = store.baseUrl ?: throw AuthError.ClientNotConfigured()
  return SamplesApi(
      baseUrl = ApiGlue.normalizeBaseURL(baseUrl),
      httpClientConfig = ApiGlue.bearerClientConfig(tokens),
  )
}

private fun dictionaryApi(
    store: SessionStore,
    tokens: TokenHolder,
): InspectionDictionaryApi {
  val baseUrl = store.baseUrl ?: throw AuthError.ClientNotConfigured()
  return InspectionDictionaryApi(
      baseUrl = ApiGlue.normalizeBaseURL(baseUrl),
      httpClientConfig = ApiGlue.bearerClientConfig(tokens),
  )
}

private fun recordsApi(
    store: SessionStore,
    tokens: TokenHolder,
): TestRecordsApi {
  val baseUrl = store.baseUrl ?: throw AuthError.ClientNotConfigured()
  return TestRecordsApi(
      baseUrl = ApiGlue.normalizeBaseURL(baseUrl),
      httpClientConfig = ApiGlue.bearerClientConfig(tokens),
  )
}
