package io.xr.harness.ui

import io.xr.harness.Fn
import io.xr.lab.shared.models.Sample
import io.xr.lab.shared.models.TestRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ReportPreviewViewModel 单测（swift ReportPreviewTests I03 同构，M03.F09.I03，REQ-2026-007 T-1）： 契约 list
 * 端点只支持 sampleId 过滤，按样品逐个取再归集——家族 ReportPreviewModal.recordsOfSamples 同款。
 */
class ReportPreviewViewModelTest {

  private fun sample(id: String): Sample =
      Sample(
          id = id,
          tenantId = "t-1",
          receiptId = "r-9",
          sampleCode = "YP-$id",
          ext = emptyMap(),
          createdAt = "2026-09-29T09:00:00Z",
          updatedAt = "2026-09-29T09:00:00Z",
      )

  private fun record(sampleId: String, code: String, verdict: String?): TestRecord =
      TestRecord(
          id = "$sampleId#$code",
          tenantId = "t-1",
          sampleId = sampleId,
          parameterCode = code,
          requirement = ">=42.5",
          result = "48.2",
          verdict = verdict,
          createdAt = "2026-09-29T09:00:00Z",
          updatedAt = "2026-09-29T09:00:00Z",
      )

  @Test
  @Fn(["M03.F09.I03"])
  fun `load groups records by sample`() {
    val queried = mutableListOf<String>()
    val vm =
        ReportPreviewViewModel(
            samplesLoad = { receiptId ->
              assertEquals("r-9", receiptId)
              listOf(sample("s-1"), sample("s-2"))
            },
            recordsLoad = { sampleId ->
              queried.add(sampleId)
              if (sampleId == "s-1") {
                listOf(
                    record("s-1", "P1", "合格"),
                    record("s-1", "P2", null),
                )
              } else {
                listOf(record("s-2", "P1", "不合格"))
              }
            },
        )

    kotlinx.coroutines.runBlocking { vm.load("r-9") }

    assertEquals(2, vm.samples.value.size)
    assertEquals(2, vm.recordsBySample.value["s-1"]?.size)
    assertEquals(1, vm.recordsBySample.value["s-2"]?.size)
    assertEquals("P1", vm.recordsBySample.value["s-1"]?.first()?.parameterCode)
    assertEquals(listOf("s-1", "s-2"), queried, "契约 list 无 receiptId 参数，逐样品取（家族同款）")
    assertNull(vm.errorMessage.value)
  }

  @Test
  @Fn(["M03.F09.I03"])
  fun `empty samples yields empty preview`() {
    val vm =
        ReportPreviewViewModel(
            samplesLoad = { _ -> emptyList() },
            recordsLoad = { _ -> emptyList() },
        )

    kotlinx.coroutines.runBlocking { vm.load("r-9") }

    assertTrue(vm.samples.value.isEmpty())
    assertTrue(vm.recordsBySample.value.isEmpty())
    assertNull(vm.errorMessage.value)
  }

  @Test
  @Fn(["M03.F09.I03"])
  fun `load failure surfaces message and clears state`() {
    val vm =
        ReportPreviewViewModel(
            samplesLoad = { _ -> throw IllegalStateException("boom") },
            recordsLoad = { _ -> emptyList() },
        )

    kotlinx.coroutines.runBlocking { vm.load("r-9") }

    assertTrue(vm.errorMessage.value != null)
    assertTrue(vm.samples.value.isEmpty())
    assertTrue(vm.recordsBySample.value.isEmpty())
  }
}
