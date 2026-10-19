package io.xr.harness.ui

import io.xr.lab.shared.models.FlowAction
import io.xr.lab.shared.models.FlowHistoryEntry
import io.xr.lab.shared.models.FlowStatus
import io.xr.lab.shared.models.SampleReceipt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 接样单详情 VM（swift 同构，M03.F01.I06）：一次 load 同时取接样信息与流程历史（at 倒序）。 */
class ReceiptDetailViewModelTest {

  private fun receipt(id: String = "r-1"): SampleReceipt =
      SampleReceipt(
          id = id,
          tenantId = "t-1",
          contractId = "c-1",
          commissionCode = "WT-2026-001",
          commissionDate = "2026-09-28",
          categoryCode = "CAT01",
          receivedBy = "alice",
          sampleSource = "witness",
          testCategory = "detected",
          flowStatus = FlowStatus.receiving,
          flowHistory = emptyList(),
          createdAt = "2026-09-28T08:00:00Z",
          updatedAt = "2026-09-28T08:00:00Z",
      )

  @Test
  fun loadFetchesReceiptAndHistorySortedDesc() =
      kotlinx.coroutines.runBlocking {
        val entry = { at: String ->
          FlowHistoryEntry(
              action = FlowAction.submit,
              from = FlowStatus.receiving,
              to = FlowStatus.task_assignment,
              operator = "alice",
              at = at,
          )
        }
        var fetchedId: String? = null
        val vm = ReceiptDetailViewModel { id ->
          fetchedId = id
          receipt() to listOf(entry("2026-09-28T09:00:00Z"), entry("2026-09-28T10:00:00Z"))
        }
        vm.load("r-1")
        assertEquals("r-1", fetchedId)
        assertEquals("r-1", vm.receipt.value?.id)
        assertEquals("2026-09-28T10:00:00Z", vm.history.value.first().at, "时间线按 at 倒序")
        assertNull(vm.error.value)
        assertFalse(vm.isLoading.value)
      }

  @Test
  fun loadErrorSurfacesMessageAndClearsState() =
      kotlinx.coroutines.runBlocking {
        val vm = ReceiptDetailViewModel { _ -> throw IllegalStateException("boom") }
        vm.load("r-1")
        assertNotNull(vm.error.value)
        assertTrue(vm.error.value!!.contains("boom"))
        assertNull(vm.receipt.value)
        assertTrue(vm.history.value.isEmpty())
        assertFalse(vm.isLoading.value)
      }
}
