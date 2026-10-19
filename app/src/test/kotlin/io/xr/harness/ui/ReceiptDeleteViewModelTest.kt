package io.xr.harness.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** 接样单删除 VM（swift 同构，M03.F01.I03）：确认后单条删除；失败以 error 呈现不崩。 */
class ReceiptDeleteViewModelTest {

  @Test
  fun deleteCallsSeamWithConfirmedID() =
      kotlinx.coroutines.runBlocking {
        var deleted: String? = null
        val vm = ReceiptDeleteViewModel { id -> deleted = id }
        vm.delete("r-1")
        assertEquals("r-1", deleted)
        assertNull(vm.error.value)
        assertFalse(vm.isDeleting.value)
      }

  @Test
  fun deleteFailureSurfacesMessageWithoutThrow() =
      kotlinx.coroutines.runBlocking {
        val vm = ReceiptDeleteViewModel { _ -> throw IllegalStateException("boom") }
        vm.delete("r-1")
        assertNotNull(vm.error.value, "删除失败以 error 呈现，不许崩")
        assertFalse(vm.isDeleting.value)
      }
}
