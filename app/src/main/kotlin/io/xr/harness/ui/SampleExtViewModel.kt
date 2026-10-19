package io.xr.harness.ui

import io.xr.harness.core.AuthError
import io.xr.lab.shared.models.ExtFieldDef
import io.xr.lab.shared.models.ExtFieldDefSource
import io.xr.lab.shared.models.InspectionReportName
import io.xr.lab.shared.models.Sample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 样品扩展字段补录 VM（swift SampleExtViewModel 同构，M03.F01.I07，REQ-2026-008 T-1）： 报告名 extFields 中
 * source=receipt 的字段家族未接入（同款边界，不进门）； usable 缺 key 的子集 = 需补录表单；草稿以现有 ext 预填；保存 = 必填校验 → 合并（现有 key
 * 全保留，非空表单值覆盖）→ persist 打合并后全量 ext；失败呈错保草稿可重交。
 */
class SampleExtViewModel(
    internal val reportNamesLoad: suspend (Int) -> List<InspectionReportName> = { _ ->
      throw AuthError.Unreachable(IllegalStateException("reportNamesLoad seam 未接线"))
    },
    internal val persist: suspend (String, Map<String, String>) -> Unit = { _, _ ->
      throw AuthError.Unreachable(IllegalStateException("persist seam 未接线"))
    },
) {
  private companion object {
    /** 报告名目录页大小（家族同款 200 镜像，swift APIGlue.reportNames 同值）。 */
    const val REPORT_NAMES_PAGE_SIZE = 200
  }

  private val _allFields = MutableStateFlow<List<ExtFieldDef>>(emptyList())
  val allFields: StateFlow<List<ExtFieldDef>> = _allFields.asStateFlow()

  private val _formFields = MutableStateFlow<List<ExtFieldDef>>(emptyList())
  val formFields: StateFlow<List<ExtFieldDef>> = _formFields.asStateFlow()

  private val _needsForm = MutableStateFlow(false)
  val needsForm: StateFlow<Boolean> = _needsForm.asStateFlow()

  private val _draft = MutableStateFlow<Map<String, String>>(emptyMap())
  val draft: StateFlow<Map<String, String>> = _draft.asStateFlow()

  private val _validationErrors = MutableStateFlow<Map<String, String>>(emptyMap())
  val validationErrors: StateFlow<Map<String, String>> = _validationErrors.asStateFlow()

  private val _isSaving = MutableStateFlow(false)
  val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _didSave = MutableStateFlow(false)
  val didSave: StateFlow<Boolean> = _didSave.asStateFlow()

  private var originalExt: Map<String, String> = emptyMap()

  /** 拉报告名取 extFields 并对准样品建草稿；样品为空或拉取失败 → 全清不出门（swift load 同语义）。 */
  suspend fun load(categoryCode: String?, firstSample: Sample?) {
    _errorMessage.value = null
    val sample = firstSample
    if (sample == null) {
      clearForm()
      return
    }
    try {
      val names = reportNamesLoad(REPORT_NAMES_PAGE_SIZE)
      val fields =
          names
              .firstOrNull { it.code == categoryCode }
              ?.extFields
              .orEmpty()
              .filter { it.source != ExtFieldDefSource.receipt }
      applyFields(fields, sample)
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
      throw e
    } catch (e: Exception) {
      clearForm()
      _errorMessage.value = e.message ?: "加载扩展字段失败"
    }
  }

  private fun applyFields(fields: List<ExtFieldDef>, sample: Sample) {
    originalExt = sample.ext
    _allFields.value = fields
    val usable = fields
    _formFields.value = usable.filter { (originalExt[it.key] ?: "").isEmpty() }
    _draft.value = usable.associate { it.key to (originalExt[it.key] ?: "") }
    _needsForm.value = _formFields.value.isNotEmpty()
  }

  private fun clearForm() {
    originalExt = emptyMap()
    _allFields.value = emptyList()
    _formFields.value = emptyList()
    _draft.value = emptyMap()
    _needsForm.value = false
    _validationErrors.value = emptyMap()
  }

  fun setValue(value: String, forKey: String) {
    _draft.value = _draft.value + (forKey to value)
  }

  /** 必填字段 trim 后为空 → errors[key]=必填，返回是否放行（swift validate 同语义）。 */
  fun validate(): Boolean {
    val errors = mutableMapOf<String, String>()
    val current = _draft.value
    for (field in _allFields.value) {
      if (field.required == true && current[field.key].orEmpty().trim().isEmpty()) {
        errors[field.key] = "必填"
      }
    }
    _validationErrors.value = errors
    return errors.isEmpty()
  }

  /**
   * 校验 → 合并（现有 ext key 全保留 + 非空 trim 后表单值覆盖）→ persist； 成功置 didSave 返回 true，失败呈错保草稿返回
   * false；取消异常原样上抛（家族 VM 同款）。
   */
  suspend fun save(sampleId: String): Boolean {
    if (!validate()) {
      return false
    }
    _isSaving.value = true
    val ok =
        try {
          val overrides =
              _draft.value.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
          val merged = originalExt + overrides
          persist(sampleId, merged)
          _didSave.value = true
          true
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
          throw e
        } catch (e: Exception) {
          _errorMessage.value = e.message ?: "保存失败"
          false
        } finally {
          _isSaving.value = false
        }
    return ok
  }
}
