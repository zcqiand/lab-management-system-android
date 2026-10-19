package io.xr.harness.ui

import io.xr.lab.shared.models.FlowStatus
import io.xr.lab.shared.models.SampleReceipt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 接样单表单 VM（swift ReceiptFormDetailTests 同构，M03.F01.I02）：必填校验 + create/PATCH diff + save 路由。 网络缝 =
 * 注入挂起闭包，单测不发真网络；PATCH 语义 = 仅携带变更字段。
 */
class ReceiptFormViewModelTest {

  private fun filledFields(): ReceiptFormFields =
      ReceiptFormFields(
          contractId = "c-1",
          commissionCode = "WT-2026-001",
          commissionDate = "2026-09-28",
          categoryCode = "CAT01",
          receivedBy = "alice",
          sampleSource = "witness",
          testCategory = "detected",
      )

  private fun receipt(id: String): SampleReceipt =
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
  fun validateRequiresFullRequiredSet() {
    val vm = ReceiptFormViewModel(fields = ReceiptFormFields())
    assertFalse(vm.validate(), "全空必填集不过")
    assertFalse(vm.validationErrors.isEmpty(), "逐项标错")
    vm.fields = filledFields().copy(contractId = "   ")
    assertFalse(vm.validate(), "空白视同缺失")
    vm.fields = filledFields()
    assertTrue(vm.validate())
    assertTrue(vm.validationErrors.isEmpty())
  }

  @Test
  fun makeCreateRequestCarriesRequiredSet() {
    val vm = ReceiptFormViewModel(fields = filledFields())
    val request = vm.makeCreateRequest()
    assertEquals("c-1", request.contractId)
    assertEquals("WT-2026-001", request.commissionCode)
    assertEquals("2026-09-28", request.commissionDate)
    assertEquals("CAT01", request.categoryCode)
    assertEquals("alice", request.receivedBy)
    assertEquals("witness", request.sampleSource)
    assertEquals("detected", request.testCategory)
  }

  @Test
  fun makeCreateRequestFailsFastWhenInvalid() {
    val vm = ReceiptFormViewModel(fields = ReceiptFormFields())
    try {
      vm.makeCreateRequest()
      throw AssertionError("期望 ReceiptFormError")
    } catch (expected: ReceiptFormError) {
      // fail-fast：非法必填集不打端点
    }
  }

  @Test
  fun makeUpdateRequestCarriesOnlyChangedFields() {
    val original = filledFields()
    val changed = original.copy(receivedBy = "bob", sampleSource = "supplied")
    val vm = ReceiptFormViewModel(fields = changed)
    val request = vm.makeUpdateRequest(original = original)
    assertEquals("bob", request.receivedBy, "变更字段必须携带")
    assertEquals("supplied", request.sampleSource)
    assertNull(request.contractId, "未变更字段不许携带（PATCH 语义）")
    assertNull(request.commissionCode)
    assertNull(request.categoryCode)
    assertNull(request.testCategory)
  }

  @Test
  fun updateWithClearedRequiredFieldFailsValidation() {
    // 必填集在编辑态同样成立：清空必填不是合法 PATCH
    val vm = ReceiptFormViewModel(fields = filledFields().copy(sampleSource = "  "))
    assertFalse(vm.validate())
    try {
      vm.makeUpdateRequest(original = filledFields())
      throw AssertionError("期望 ReceiptFormError")
    } catch (expected: ReceiptFormError) {}
  }

  @Test
  fun saveRoutesCreateOrUpdateByPresenceOfID() =
      kotlinx.coroutines.runBlocking {
        val calls = mutableListOf<Triple<String?, Boolean, Boolean>>()
        val vm =
            ReceiptFormViewModel(fields = filledFields()) { id, create, update ->
              calls.add(Triple(id, create != null, update != null))
              receipt(id ?: "r-new")
            }
        vm.save(id = null, original = null)
        vm.save(id = "r-1", original = filledFields())
        assertEquals(2, calls.size)
        assertNull(calls[0].first)
        assertTrue(calls[0].second, "无 id → create 请求")
        assertFalse(calls[0].third)
        assertEquals("r-1", calls[1].first)
        assertTrue(calls[1].third, "有 id → update（PATCH）请求")
        assertFalse(calls[1].second)
      }

  @Test
  fun saveFailsFastOnInvalidFieldsWithoutCallingEndpoint() =
      kotlinx.coroutines.runBlocking {
        var called = false
        val vm =
            ReceiptFormViewModel(fields = ReceiptFormFields()) { _, _, _ ->
              called = true
              receipt("r-x")
            }
        try {
          vm.save(id = null, original = null)
          throw AssertionError("非法必填集必须抛 ReceiptFormError")
        } catch (expected: ReceiptFormError) {}
        assertFalse(called, "校验失败不许打到端点")
      }
}
