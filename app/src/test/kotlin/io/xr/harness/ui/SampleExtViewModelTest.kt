package io.xr.harness.ui

import io.xr.harness.Fn
import io.xr.lab.shared.models.ExtFieldDef
import io.xr.lab.shared.models.ExtFieldDefSource
import io.xr.lab.shared.models.ExtFieldDefType
import io.xr.lab.shared.models.InspectionReportName
import io.xr.lab.shared.models.Sample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * SampleExtViewModel 单测（swift SampleExtTests 同构，M03.F01.I07，REQ-2026-008 T-1）：needsForm 判定 / 必填校验 /
 * 合并语义 / persist 缝，镜像家族 SampleExtFieldsModal + ReportPreviewModal.handleExtSubmit。
 */
class SampleExtViewModelTest {

  private fun makeDef(
      key: String,
      label: String,
      type: ExtFieldDefType = ExtFieldDefType.text,
      required: Boolean? = null,
      source: ExtFieldDefSource? = null,
  ): ExtFieldDef =
      ExtFieldDef(key = key, label = label, type = type, required = required, source = source)

  private fun makeSample(ext: Map<String, String>): Sample =
      Sample(
          id = "s-1",
          tenantId = "t-1",
          receiptId = "r-9",
          sampleCode = "YP-1",
          ext = ext,
          createdAt = "2026-09-29T09:00:00Z",
          updatedAt = "2026-09-29T09:00:00Z",
      )

  private fun nameOf(code: String, extFields: List<ExtFieldDef>): InspectionReportName =
      InspectionReportName(
          code = code,
          name = if (code == "110") "防水" else "混凝土抗压",
          extFields = extFields,
          sortOrder = 1,
          createdAt = "2026-09-29T09:00:00Z",
          updatedAt = "2026-09-29T09:00:00Z",
      )

  private fun vmOf(
      extFields: List<ExtFieldDef>,
      persist: suspend (String, Map<String, String>) -> Unit = { _, _ -> },
  ): SampleExtViewModel =
      SampleExtViewModel(
          reportNamesLoad = { _ -> listOf(nameOf("103", extFields)) },
          persist = persist,
      )

  @Test
  @Fn(["M03.F01.I07"])
  fun `needs form when key missing`() {
    val vm =
        vmOf(
            listOf(
                makeDef("sampleModel", "型号", required = true),
                makeDef("grade", "等级"),
            )
        )

    kotlinx.coroutines.runBlocking { vm.load("103", makeSample(emptyMap())) }

    assertTrue(vm.needsForm.value, "缺 key 必须出补录门")
    assertEquals(
        listOf("sampleModel", "grade"),
        vm.formFields.value.map { it.key },
        "receipt 侧字段过滤、其余保序",
    )
    assertEquals("", vm.draft.value["sampleModel"], "缺 key 预填空串")
  }

  @Test
  @Fn(["M03.F01.I07"])
  fun `no form when all keys covered`() {
    val vm = vmOf(listOf(makeDef("sampleModel", "型号", required = true)))

    kotlinx.coroutines.runBlocking { vm.load("103", makeSample(mapOf("sampleModel" to "C30"))) }

    assertFalse(vm.needsForm.value, "key 全覆盖不出门")
    assertTrue(vm.formFields.value.isEmpty())
  }

  @Test
  @Fn(["M03.F01.I07"])
  fun `receipt source fields excluded from form`() {
    val vm = vmOf(listOf(makeDef("pourDate", "浇筑日期", source = ExtFieldDefSource.receipt)))

    kotlinx.coroutines.runBlocking { vm.load("110", makeSample(emptyMap())) }

    assertFalse(vm.needsForm.value, "source=receipt 字段家族未接入，非范围（同款边界）")
    assertTrue(vm.formFields.value.isEmpty())
  }

  @Test
  @Fn(["M03.F01.I07"])
  fun `load prefills existing ext values`() {
    val vm =
        vmOf(
            listOf(
                makeDef("sampleModel", "型号", required = true),
                makeDef("grade", "等级"),
            )
        )

    kotlinx.coroutines.runBlocking { vm.load("103", makeSample(mapOf("sampleModel" to "C30"))) }

    assertTrue(vm.needsForm.value, "覆盖一项仍缺另一项 → 出门")
    assertEquals(mapOf("sampleModel" to "C30", "grade" to ""), vm.draft.value, "现有值预填、缺失键空串")
  }

  @Test
  @Fn(["M03.F01.I07"])
  fun `validate blocks on empty required`() {
    var persisted = false
    val vm =
        vmOf(
            listOf(
                makeDef("sampleModel", "型号", required = true),
                makeDef("grade", "等级"),
            )
        ) { _, _ ->
          persisted = true
        }

    kotlinx.coroutines.runBlocking {
      vm.load("103", makeSample(emptyMap()))
      vm.setValue("  ", "sampleModel")
      val ok = vm.save("s-1")
      assertFalse(ok, "必填空白必须阻断")
    }

    assertEquals("必填", vm.validationErrors.value["sampleModel"])
    assertFalse(persisted, "校验不过不得打端点")
  }

  @Test
  @Fn(["M03.F01.I07"])
  fun `save merges existing keys and new values`() {
    var persistedId: String? = null
    var persistedExt: Map<String, String>? = null
    val vm =
        vmOf(listOf(makeDef("sampleModel", "型号", required = true))) { id, ext ->
          persistedId = id
          persistedExt = ext
        }

    kotlinx.coroutines.runBlocking {
      vm.load("103", makeSample(mapOf("sampleModel" to "C30", "brand" to "金桥")))
      vm.setValue("C40", "sampleModel")
      val ok = vm.save("s-1")
      assertTrue(ok)
    }

    assertTrue(vm.didSave.value)
    assertNull(vm.errorMessage.value)
    assertEquals("s-1", persistedId)
    assertEquals(mapOf("sampleModel" to "C40", "brand" to "金桥"), persistedExt, "现有 key 全保留 + 表单值覆盖")
  }

  @Test
  @Fn(["M03.F01.I07"])
  fun `save failure surfaces error and keeps draft`() {
    val vm =
        vmOf(listOf(makeDef("sampleModel", "型号", required = true))) { _, _ ->
          throw IllegalStateException("boom")
        }

    kotlinx.coroutines.runBlocking {
      vm.load("103", makeSample(emptyMap()))
      vm.setValue("C40", "sampleModel")
      val ok = vm.save("s-1")
      assertFalse(ok)
    }

    assertTrue(vm.errorMessage.value != null)
    assertFalse(vm.didSave.value)
    assertEquals("C40", vm.draft.value["sampleModel"], "失败后草稿保留可重交")
  }
}
