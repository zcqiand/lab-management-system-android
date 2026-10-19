package io.xr.harness.ui

import io.xr.lab.shared.models.CreateSampleReceiptRequest
import io.xr.lab.shared.models.SampleReceipt
import io.xr.lab.shared.models.UpdateSampleReceiptRequest

/** 表单错误：必填集不齐 fail-fast，不打端点（§1 禁兜底同源，swift ReceiptFormError 同位）。 */
class ReceiptFormError : Exception("必填项未填齐")

/**
 * 接样单表单字段 = CreateSampleReceiptRequest 必填集（shared tsp models/sample-receipt.tsp）。 可选字段本切片 UI
 * 不做；PATCH diff 依赖 data class 等值语义（swift 同款）。
 */
data class ReceiptFormFields(
    val contractId: String = "",
    val commissionCode: String = "",
    val commissionDate: String = "",
    val categoryCode: String = "",
    val receivedBy: String = "",
    val sampleSource: String = "",
    val testCategory: String = "",
) {
  /** 必填集字段名 → 值（validate 与 PATCH diff 共用一份遍历，防两处漂移）。 */
  fun entries(): List<Pair<String, String>> =
      listOf(
          "contractId" to contractId,
          "commissionCode" to commissionCode,
          "commissionDate" to commissionDate,
          "categoryCode" to categoryCode,
          "receivedBy" to receivedBy,
          "sampleSource" to sampleSource,
          "testCategory" to testCategory,
      )
}

/**
 * 接样单表单 VM（swift ReceiptFormViewModel 同构，M03.F01.I02）：必填校验 + 请求构造 + save 路由。 persist 缝 id=null →
 * create；id≠null → update（PATCH，diff 基准 = original）。
 */
class ReceiptFormViewModel(
    var fields: ReceiptFormFields = ReceiptFormFields(),
    internal val persist:
        suspend (
            String?,
            CreateSampleReceiptRequest?,
            UpdateSampleReceiptRequest?,
        ) -> SampleReceipt =
        { _, _, _ ->
          throw IllegalStateException("persist 缝未注入")
        },
) {
  var validationErrors: List<String> = emptyList()
    private set

  /** 必填集校验：trim 后非空才算有值；缺项逐条进 validationErrors（UI 逐项标错）。 */
  fun validate(): Boolean {
    validationErrors = fields.entries().filter { it.second.isBlank() }.map { it.first }
    return validationErrors.isEmpty()
  }

  /** 新建请求 = 必填集全量。 */
  fun makeCreateRequest(): CreateSampleReceiptRequest {
    requireValid()
    return CreateSampleReceiptRequest(
        contractId = fields.contractId,
        commissionCode = fields.commissionCode,
        commissionDate = fields.commissionDate,
        categoryCode = fields.categoryCode,
        receivedBy = fields.receivedBy,
        sampleSource = fields.sampleSource,
        testCategory = fields.testCategory,
    )
  }

  /** PATCH 语义：只携带与 original 不同的字段；清空（含空白归一后）也是变更。 */
  fun makeUpdateRequest(original: ReceiptFormFields): UpdateSampleReceiptRequest {
    requireValid()
    return UpdateSampleReceiptRequest(
        contractId = changed(fields.contractId, original.contractId),
        commissionCode = changed(fields.commissionCode, original.commissionCode),
        commissionDate = changed(fields.commissionDate, original.commissionDate),
        categoryCode = changed(fields.categoryCode, original.categoryCode),
        receivedBy = changed(fields.receivedBy, original.receivedBy),
        sampleSource = changed(fields.sampleSource, original.sampleSource),
        testCategory = changed(fields.testCategory, original.testCategory),
    )
  }

  /** 持久化路由：id 缺 → create；id 有 → update（PATCH，diff 基准 = original）。 */
  suspend fun save(id: String?, original: ReceiptFormFields?): SampleReceipt =
      if (id == null) {
        persist(null, makeCreateRequest(), null)
      } else {
        persist(id, null, makeUpdateRequest(original ?: ReceiptFormFields()))
      }

  private fun requireValid() {
    if (!validate()) {
      throw ReceiptFormError()
    }
  }

  private fun changed(now: String, before: String): String? = if (now == before) null else now
}
