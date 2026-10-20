package io.xr.harness.ui

import io.xr.harness.Fn
import io.xr.harness.core.AuthError
import io.xr.lab.shared.models.CreateTestRecordRequest
import io.xr.lab.shared.models.InspectionParameter
import io.xr.lab.shared.models.InspectionParameterSourceType
import io.xr.lab.shared.models.Sample
import io.xr.lab.shared.models.TestRecord
import io.xr.lab.shared.models.UpdateTestRecordRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * DataEntryViewModel 单测（swift DataEntryTests I01/I02/I03 段同构）：目录键控/create-update 路由/verdict 随 body
 * 逐条钉死。
 */
class DataEntryViewModelTest {

  private fun sample(id: String = "s-1", code: String = "YP-001"): Sample =
      Sample(
          id = id,
          tenantId = "t-1",
          receiptId = "r-1",
          sampleCode = code,
          ext = emptyMap(),
          createdAt = "2026-09-28T09:00:00Z",
          updatedAt = "2026-09-28T09:00:00Z",
      )

  private fun parameter(code: String = "P-CE", name: String = "抗压强度"): InspectionParameter =
      InspectionParameter(
          code = code,
          name = name,
          rawName = name,
          canonicalName = name,
          aliases = emptyList(),
          sourceType = InspectionParameterSourceType.official,
          sortOrder = 0,
          createdAt = "2026-09-28T09:00:00Z",
          updatedAt = "2026-09-28T09:00:00Z",
      )

  private fun record(
      id: String = "tr-1",
      sampleId: String = "s-1",
      parameterCode: String = "P-CE",
      result: String = "45.2",
      verdict: String? = "合格",
  ): TestRecord =
      TestRecord(
          id = id,
          tenantId = "t-1",
          sampleId = sampleId,
          parameterCode = parameterCode,
          requirement = "≥42.5",
          result = result,
          verdict = verdict,
          createdAt = "2026-09-28T09:00:00Z",
          updatedAt = "2026-09-28T09:00:00Z",
      )

  /** 目录/记录给空、persist 未接线的 VM（表单校验类测试用）。 */
  private fun emptyVm() =
      DataEntryViewModel(
          catalogLoad = { Pair(emptyList(), emptyList()) },
          recordsLoad = { emptyList() },
          persist = { _, _, _ -> throw IllegalStateException("不该触发持久化") },
      )

  @Test
  @Fn(["M03.F03.I01"])
  fun `load assembles catalog and keys records`() {
    val vm =
        DataEntryViewModel(
            catalogLoad = { receiptId ->
              assertEquals("r-1", receiptId)
              Pair(listOf(sample("s-1"), sample("s-2", "YP-002")), listOf(parameter()))
            },
            recordsLoad = { sampleId -> if (sampleId == "s-1") listOf(record()) else emptyList() },
            persist = { _, _, _ -> throw IllegalStateException("不该触发持久化") },
        )

    kotlinx.coroutines.runBlocking { vm.load("r-1") }

    assertNull(vm.errorMessage.value)
    assertEquals(2, vm.samples.value.size)
    assertEquals(1, vm.parameters.value.size)
    vm.selectedSampleId = "s-1"
    vm.selectedParameterCode = "P-CE"
    assertEquals("tr-1", vm.existingRecord?.id, "同键记录必须能查到")
    vm.selectedParameterCode = "P-OTHER"
    assertNull(vm.existingRecord, "不同参数 = 无既有记录")
  }

  @Test
  @Fn(["M03.F03.I02"])
  fun `create request trims and requires core fields`() {
    val vm = emptyVm()
    vm.selectedSampleId = "s-1"
    vm.selectedParameterCode = "P-CE"
    vm.result = " 45.2 "
    vm.requirement = " ≥42.5 "
    vm.standardCode = "  "
    vm.verdict = "合格"

    val request = vm.makeCreateRequest()

    assertEquals("s-1", request.sampleId)
    assertEquals("P-CE", request.parameterCode)
    assertEquals("45.2", request.result)
    assertEquals("≥42.5", request.requirement)
    assertNull(request.standardCode, "空串可选字段归一为不传")
    assertEquals("合格", request.verdict, "verdict 必须随创建请求体")

    vm.result = "   "
    assertThrows(DataEntryInvalidFieldsException::class.java) { vm.makeCreateRequest() }
    vm.result = "45.2"
    vm.selectedSampleId = ""
    assertThrows(DataEntryInvalidFieldsException::class.java) { vm.makeCreateRequest() }
  }

  @Test
  @Fn(["M03.F03.I02"])
  fun `save without record creates with verdict in body`() {
    val vm =
        DataEntryViewModel(
            catalogLoad = { Pair(listOf(sample()), listOf(parameter())) },
            recordsLoad = { emptyList() },
            persist = { id, create, _ ->
              assertNull(id, "无既有记录必须走 create 路")
              assertEquals("合格", create?.verdict, "verdict 必须随创建请求体")
              record(id = "tr-new", verdict = "合格")
            },
        )
    kotlinx.coroutines.runBlocking { vm.load("r-1") }
    vm.selectedSampleId = "s-1"
    vm.selectedParameterCode = "P-CE"
    vm.result = "45.2"
    vm.requirement = "≥42.5"
    vm.verdict = "合格"

    val saved = kotlinx.coroutines.runBlocking { vm.save() }

    assertEquals("tr-new", saved?.id)
    assertNull(vm.errorMessage.value)
    assertFalse(vm.isSaving.value, "save 返回后忙态必须落回")
  }

  @Test
  @Fn(["M03.F03.I03"])
  fun `verdict revision with existing record goes update`() {
    val vm =
        DataEntryViewModel(
            catalogLoad = { Pair(listOf(sample()), listOf(parameter())) },
            recordsLoad = { listOf(record(verdict = "合格")) },
            persist = { id, create, update ->
              assertNull(create, "已有记录不许走 create")
              assertEquals("tr-1", id, "update 必须携带既有记录 id")
              assertEquals("s-1", update?.sampleId)
              assertEquals("P-CE", update?.parameterCode)
              assertEquals("47.8", update?.result, "改判连带改值随 body")
              assertEquals("不合格", update?.verdict, "改判 verdict 必须随更新请求体")
              record(result = "47.8", verdict = "不合格")
            },
        )
    kotlinx.coroutines.runBlocking { vm.load("r-1") }
    vm.selectedSampleId = "s-1"
    vm.selectedParameterCode = "P-CE"
    vm.fillFromExisting()
    vm.result = "47.8"
    vm.verdict = "不合格"

    val saved = kotlinx.coroutines.runBlocking { vm.save() }

    assertEquals("不合格", saved?.verdict)
    assertNull(vm.errorMessage.value)
  }

  @Test
  @Fn(["M03.F03.I02"])
  fun `save failure reports and keeps fields`() {
    val vm =
        DataEntryViewModel(
            catalogLoad = { Pair(listOf(sample()), listOf(parameter())) },
            recordsLoad = { emptyList() },
            persist = { _, _, _ -> throw AuthError.Unreachable(Exception("connection reset")) },
        )
    kotlinx.coroutines.runBlocking { vm.load("r-1") }
    vm.selectedSampleId = "s-1"
    vm.selectedParameterCode = "P-CE"
    vm.result = "45.2"
    vm.requirement = "≥42.5"

    val saved = kotlinx.coroutines.runBlocking { vm.save() }

    assertNull(saved)
    assertNotNull(vm.errorMessage.value, "失败必须呈错不打断")
    assertEquals("45.2", vm.result, "失败不许清用户输入")
    assertEquals("≥42.5", vm.requirement)
  }

  @Test
  @Fn(["M03.F03.I01"])
  fun `load failure clears catalog and reports`() {
    val vm =
        DataEntryViewModel(
            catalogLoad = { _ -> throw AuthError.Server("服务器错误（HTTP 500）") },
            recordsLoad = { emptyList() },
            persist = { _, _, _ -> throw IllegalStateException("不该触发持久化") },
        )

    kotlinx.coroutines.runBlocking { vm.load("r-1") }

    assertTrue(vm.samples.value.isEmpty())
    assertTrue(vm.parameters.value.isEmpty())
    assertEquals("服务器错误（HTTP 500）", vm.errorMessage.value)
    assertFalse(vm.isLoading.value)
  }

  @Test
  @Fn(["M03.F03.I02"])
  fun `makeUpdateRequest carries form values with keys unchanged`() {
    val vm = emptyVm()
    vm.selectedSampleId = "s-1"
    vm.selectedParameterCode = "P-CE"
    vm.result = "47.8"
    vm.requirement = "≥42.5"
    vm.standardCode = "GB/T 50081"

    val request: UpdateTestRecordRequest = vm.makeUpdateRequest()

    assertEquals("s-1", request.sampleId)
    assertEquals("P-CE", request.parameterCode)
    assertEquals("47.8", request.result)
    assertEquals("GB/T 50081", request.standardCode)
    assertEquals(CreateTestRecordRequest::class, vm.makeCreateRequest()::class)
  }
}
