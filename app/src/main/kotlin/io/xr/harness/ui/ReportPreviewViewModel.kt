package io.xr.harness.ui

import io.xr.lab.shared.models.Sample
import io.xr.lab.shared.models.TestRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 报告预览 VM（swift ReportPreviewViewModel 同构，M03.F09.I03，REQ-2026-007 T-1）： 契约 list 端点只支持 sampleId
 * 过滤（无 receiptId），按样品逐个取再归集——家族 ReportPreviewModal.recordsOfSamples 同款策略。 数据面预览 =
 * 用户裁定（Q1）：纯数据摘要，docx 模板填充/打印/套打为家族 Web 专属，非范围。 任何失败清空整体，不留半写状态。
 */
class ReportPreviewViewModel(
    internal val samplesLoad: suspend (String) -> List<Sample> = { _ ->
      throw IllegalStateException("samplesLoad 缝未注入")
    },
    internal val recordsLoad: suspend (String) -> List<TestRecord> = { _ ->
      throw IllegalStateException("recordsLoad 缝未注入")
    },
) {
  private val _samples = MutableStateFlow<List<Sample>>(emptyList())
  val samples: StateFlow<List<Sample>> = _samples.asStateFlow()

  /** 键 = sampleId（值按 updatedAt 或创建序，透传生成层返回序）。 */
  private val _recordsBySample = MutableStateFlow<Map<String, List<TestRecord>>>(emptyMap())
  val recordsBySample: StateFlow<Map<String, List<TestRecord>>> = _recordsBySample.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  /** 装载：样品单次取，记录按样品逐个取；全部到手才落态（swift 同语义，无半写窗口）。 */
  suspend fun load(receiptId: String) {
    _isLoading.value = true
    _errorMessage.value = null
    try {
      val fetchedSamples = samplesLoad(receiptId)
      val grouped = mutableMapOf<String, List<TestRecord>>()
      for (sample in fetchedSamples) {
        grouped[sample.id] = recordsLoad(sample.id)
      }
      _samples.value = fetchedSamples
      _recordsBySample.value = grouped
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      _errorMessage.value = e.message ?: "加载预览失败"
      _samples.value = emptyList()
      _recordsBySample.value = emptyMap()
    }
    _isLoading.value = false
  }
}
