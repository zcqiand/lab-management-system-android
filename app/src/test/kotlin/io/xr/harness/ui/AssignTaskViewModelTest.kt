package io.xr.harness.ui

import io.xr.harness.Fn
import io.xr.harness.core.AuthError
import io.xr.lab.shared.models.AssignTaskRequest
import io.xr.lab.shared.models.FlowStatus
import io.xr.lab.shared.models.SampleReceipt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** AssignTaskViewModel 单测（swift TaskAssignmentTests I02 段同构）：安排/取消语义与失败呈现逐条钉死。 */
class AssignTaskViewModelTest {

  /** 样单构造样本（生成模型必填字段全给桩值，可变位仅 id/受派字段）。 */
  private fun receipt(
      id: String = "r-1",
      assigneeName: String? = null,
      plannedTestDate: String? = null,
  ): SampleReceipt =
      SampleReceipt(
          id = id,
          tenantId = "t-1",
          contractId = "c-1",
          commissionCode = "WT-2026-001",
          commissionDate = "2026-09-28",
          categoryCode = "cat-1",
          receivedBy = "接样员",
          sampleSource = "现场",
          testCategory = "检测",
          flowStatus = FlowStatus.task_assignment,
          flowHistory = emptyList(),
          assigneeName = assigneeName,
          plannedTestDate = plannedTestDate,
          createdAt = "2026-09-28T09:00:00Z",
          updatedAt = "2026-09-28T09:00:00Z",
      )

  @Test
  @Fn(["M03.F02.I02"])
  fun `makeRequest trims and requires both fields`() {
    val vm = AssignTaskViewModel(persist = { _, _ -> throw IllegalStateException("不该触发持久化") })
    vm.assigneeName = "  王检测 "
    vm.plannedTestDate = " 2026-10-01 "

    val request = vm.makeRequest()

    assertNull(request.assigneeId, "手填形态不传 assigneeId")
    assertEquals("王检测", request.assigneeName)
    assertEquals("2026-10-01", request.plannedTestDate)

    vm.assigneeName = "   "
    val ex = assertThrows(AssignTaskInvalidFieldsException::class.java) { vm.makeRequest() }
    assertTrue(ex.message!!.contains("检测人员"), "缺姓名必须 fail-fast 带语义文案")
  }

  @Test
  @Fn(["M03.F02.I02"])
  fun `cancel request clears all fields`() {
    val vm = AssignTaskViewModel(persist = { _, _ -> throw IllegalStateException("不该触发持久化") })

    val request = vm.makeRequest()

    assertNull(request.assigneeId)
    assertNull(request.assigneeName)
    assertNull(request.plannedTestDate)
  }

  @Test
  @Fn(["M03.F02.I02"])
  fun `save returns adopted receipt`() {
    val updated = receipt(assigneeName = "王检测", plannedTestDate = "2026-10-01")
    var seenId: String? = null
    var seenRequest: AssignTaskRequest? = null
    val vm =
        AssignTaskViewModel(
            persist = { id, request ->
              seenId = id
              seenRequest = request
              updated
            },
        )
    vm.assigneeName = "王检测"
    vm.plannedTestDate = "2026-10-01"

    val saved = kotlinx.coroutines.runBlocking { vm.save("r-1") }

    assertEquals("r-1", saved?.id)
    assertEquals("r-1", seenId)
    assertEquals("王检测", seenRequest?.assigneeName)
    assertNull(vm.errorMessage.value)
    assertFalse(vm.isSaving.value, "save 返回后忙态必须落回")
  }

  @Test
  @Fn(["M03.F02.I02"])
  fun `save failure reports and keeps fields`() {
    val vm =
        AssignTaskViewModel(
            persist = { _, _ -> throw AuthError.Unreachable(Exception("connection reset")) }
        )
    vm.assigneeName = "王检测"
    vm.plannedTestDate = "2026-10-01"

    val saved = kotlinx.coroutines.runBlocking { vm.save("r-1") }

    assertNull(saved)
    assertNotNull(vm.errorMessage.value, "失败必须呈错不打断")
    assertEquals("王检测", vm.assigneeName, "失败不许清用户输入")
  }
}
