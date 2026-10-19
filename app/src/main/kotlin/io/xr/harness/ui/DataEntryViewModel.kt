package io.xr.harness.ui

import io.xr.lab.shared.models.CreateTestRecordRequest
import io.xr.lab.shared.models.InspectionParameter
import io.xr.lab.shared.models.Sample
import io.xr.lab.shared.models.TestRecord
import io.xr.lab.shared.models.UpdateTestRecordRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 录入表单错误：样品/参数未选或必填字段缺失 fail-fast，不打端点（§1 禁兜底同源，swift DataEntryError 同位）。 */
class DataEntryInvalidFieldsException : Exception("样品、参数、检测结果与要求指标均为必填")

/**
 * 录入 VM（swift DataEntryViewModel 同构，M03.F03.I01/I02/I03）：装载样品/参数/既有记录目录，按同键记录有无路由
 * create/update。键控家族同款 `sampleId#parameterCode`。verdict 随请求体一并提交（Q3 裁定，不走专用 setVerdict 端点）。失败以
 * errorMessage 呈现，不清用户输入（AC-6）。
 */
class DataEntryViewModel(
    /** 网络缝：目录装载（receiptId → 样品 + 参数字典）；真实现由 wire 层并发取生成层两接口。 */
    internal val catalogLoad: suspend (String) -> Pair<List<Sample>, List<InspectionParameter>> =
        { _ ->
          throw IllegalStateException("catalogLoad 缝未注入")
        },
    /** 网络缝：逐样品检测记录（家族同款一页 200）。 */
    internal val recordsLoad: suspend (String) -> List<TestRecord> = { _ ->
      throw IllegalStateException("recordsLoad 缝未注入")
    },
    /** 网络缝：id=nil → create；id+update → update。二选一，由 save 保证。 */
    internal val persist:
        suspend (
            String?,
            CreateTestRecordRequest?,
            UpdateTestRecordRequest?,
        ) -> TestRecord =
        { _, _, _ ->
          throw IllegalStateException("persist 缝未注入")
        },
) {
  private val _samples = MutableStateFlow<List<Sample>>(emptyList())
  val samples: StateFlow<List<Sample>> = _samples.asStateFlow()

  private val _parameters = MutableStateFlow<List<InspectionParameter>>(emptyList())
  val parameters: StateFlow<List<InspectionParameter>> = _parameters.asStateFlow()

  /** 键 = "sampleId#parameterCode"，值 = 该样品检测记录（逐样品装载）。 */
  private val _recordsBySample = MutableStateFlow<Map<String, List<TestRecord>>>(emptyMap())
  val recordsBySample: StateFlow<Map<String, List<TestRecord>>> = _recordsBySample.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _isSaving = MutableStateFlow(false)
  val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  // 录入表单：样品/参数来自选择器，其余手填；verdict 可空（随 body，Q3）。
  var selectedSampleId: String = ""
  var selectedParameterCode: String = ""
  var result: String = ""
  var requirement: String = ""
  var standardCode: String = ""
  var verdict: String? = null

  /** 家族同款记录键：`sampleId#parameterCode`（vue DataEntryPage 实证）。 */
  fun recordKey(sampleId: String, parameterCode: String): String = "$sampleId#$parameterCode"

  /** 当前选中样品+参数下的既有记录（有 = update 路，无 = create 路）。 */
  val existingRecord: TestRecord?
    get() =
        _recordsBySample.value[selectedSampleId]?.firstOrNull {
          it.parameterCode == selectedParameterCode
        }

  /** 装载目录：样品+参数并发（wire 层），随后逐样品拉记录建键控索引。任一缝失败 = 整体失败（目录不全的录入页是半残态）。 */
  suspend fun load(receiptId: String) {
    _isLoading.value = true
    _errorMessage.value = null
    try {
      val (fetchedSamples, fetchedParameters) = catalogLoad(receiptId)
      val records = mutableMapOf<String, List<TestRecord>>()
      for (sample in fetchedSamples) {
        records[sample.id] = recordsLoad(sample.id)
      }
      _samples.value = fetchedSamples
      _parameters.value = fetchedParameters
      _recordsBySample.value = records
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      _errorMessage.value = e.message ?: "加载失败"
      _samples.value = emptyList()
      _parameters.value = emptyList()
      _recordsBySample.value = emptyMap()
    }
    _isLoading.value = false
  }

  /** 既有记录回填表单（AC-3：打开录入即呈现已录值）。 */
  fun fillFromExisting() {
    val record = existingRecord ?: return
    result = record.result
    requirement = record.requirement
    standardCode = record.standardCode ?: ""
    verdict = record.verdict
  }

  /** 组装创建请求：样品/参数/result/requirement 必填（trim 后），standardCode 空串归一为不传；verdict 原样随 body（Q3）。 */
  fun makeCreateRequest(): CreateTestRecordRequest {
    val sampleId = selectedSampleId.trim()
    val parameterCode = selectedParameterCode.trim()
    val resultValue = result.trim()
    val requirementValue = requirement.trim()
    if (listOf(sampleId, parameterCode, resultValue, requirementValue).any { it.isEmpty() }) {
      throw DataEntryInvalidFieldsException()
    }
    val standardCodeValue = standardCode.trim()
    return CreateTestRecordRequest(
        sampleId = sampleId,
        parameterCode = parameterCode,
        standardCode = standardCodeValue.ifEmpty { null },
        requirementCode = null,
        requirement = requirementValue,
        result = resultValue,
        verdict = verdict,
    )
  }

  /** 组装更新请求：键（样品/参数）不变，表单值全量随 body（家族同款整提交）。 */
  fun makeUpdateRequest(): UpdateTestRecordRequest {
    makeCreateRequest() // 同一套必填校验
    val standardCodeValue = standardCode.trim()
    return UpdateTestRecordRequest(
        sampleId = selectedSampleId,
        parameterCode = selectedParameterCode,
        standardCode = standardCodeValue.ifEmpty { null },
        requirementCode = null,
        requirement = requirement.trim(),
        result = result.trim(),
        verdict = verdict,
    )
  }

  /** 保存：同键有记录走 update(record.id)，无记录走 create；失败返回 null 并落 errorMessage（不打断 UI，AC-5/AC-6）。 */
  suspend fun save(): TestRecord? {
    _isSaving.value = true
    _errorMessage.value = null
    val existing = existingRecord
    return try {
      if (existing != null) {
        persist(existing.id, null, makeUpdateRequest())
      } else {
        persist(null, makeCreateRequest(), null)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      _errorMessage.value = e.message ?: "保存失败"
      null
    } finally {
      _isSaving.value = false
    }
  }
}
