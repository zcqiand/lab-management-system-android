package io.xr.harness.ui

import io.xr.harness.Fn
import io.xr.lab.shared.models.FlowAction
import io.xr.lab.shared.models.FlowActionRequest
import io.xr.lab.shared.models.FlowActionResult
import io.xr.lab.shared.models.FlowStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ReceivingFlowViewModel act 单测（swift ReceiptViewModelTests act 段同构）：选区/身份 fail-fast 不打端点，结果透传不吞。
 */
class ReceivingFlowViewModelTest {

  @Test
  @Fn(["M03.F01.I08"])
  fun `act with empty selection fails fast without calling endpoint`() {
    var called = false
    val vm =
        ReceivingFlowViewModel(
            operatorName = "alice",
            act = { _, _, _ ->
              called = true
              emptyList()
            },
        )

    val ex =
        assertThrows(FlowActionEmptySelectionException::class.java) {
          kotlinx.coroutines.runBlocking { vm.perform(FlowAction.submit, emptyList()) }
        }

    assertEquals("未选择接样单", ex.message)
    assertFalse(called, "空选区不得打端点")
  }

  @Test
  @Fn(["M03.F01.I08"])
  fun `act with missing operator fails fast without calling endpoint`() {
    var called = false
    val vm =
        ReceivingFlowViewModel(
            operatorName = "   ",
            act = { _, _, _ ->
              called = true
              emptyList()
            },
        )

    val ex =
        assertThrows(FlowActionMissingOperatorException::class.java) {
          kotlinx.coroutines.runBlocking { vm.perform(FlowAction.submit, listOf("r-1")) }
        }

    assertEquals("缺少操作人身份", ex.message)
    assertFalse(called, "身份缺席不得打端点")
  }

  @Test
  @Fn(["M03.F01.I04", "M03.F01.I08"])
  fun `act sends action ids reason and passes results through`() {
    var seenAction: FlowAction? = null
    var seenIds: List<String>? = null
    var seenReason: String? = null
    val results =
        listOf(FlowActionResult(id = "r-1", ok = true, flowStatus = FlowStatus.task_assignment))
    val vm =
        ReceivingFlowViewModel(
            operatorName = "alice",
            act = { action, ids, reason ->
              seenAction = action
              seenIds = ids
              seenReason = reason
              results
            },
        )

    val out =
        kotlinx.coroutines.runBlocking { vm.perform(FlowAction.submit, listOf("r-1"), "补录完成") }

    assertEquals(FlowAction.submit, seenAction)
    assertEquals(listOf("r-1"), seenIds)
    assertEquals("补录完成", seenReason)
    assertEquals(results, out)
  }

  @Test
  @Fn(["M03.F01.I08"])
  fun `failure result carries message not throw`() {
    // ok=false 是逐单业务失败（状态不允许提交），透传给 UI 按单呈现，不 throw
    val failing =
        listOf(
            FlowActionResult(id = "r-9", ok = false, message = "状态不允许提交"),
        )
    val vm =
        ReceivingFlowViewModel(
            operatorName = "alice",
            act = { _, _, _ -> failing },
        )

    val out = kotlinx.coroutines.runBlocking { vm.perform(FlowAction.submit, listOf("r-9")) }

    assertFalse(out[0].ok)
    assertEquals("状态不允许提交", out[0].message)
    assertNull(out[0].flowStatus)
  }

  @Test
  @Fn(["M03.F01.I08"])
  fun `makeRequest builds request and guards`() {
    val vm = ReceivingFlowViewModel(operatorName = "alice", act = { _, _, _ -> emptyList() })

    val req = vm.makeRequest(FlowAction.submit, listOf("r-1"), "补录完成")

    assertEquals(
        FlowActionRequest(listOf("r-1"), FlowAction.submit, "alice", "补录完成"),
        req,
    )

    assertThrows(FlowActionEmptySelectionException::class.java) {
      vm.makeRequest(FlowAction.submit, emptyList())
    }
    assertTrue(req.ids.isNotEmpty())
  }
}
