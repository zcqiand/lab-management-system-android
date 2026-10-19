package io.xr.harness.ui

import io.xr.harness.core.AuthError
import io.xr.harness.core.ReceiptFilter
import io.xr.harness.core.ReceiptListQuery
import io.xr.harness.core.ReceiptsPaging
import io.xr.lab.shared.models.FlowActionResult
import io.xr.lab.shared.models.FlowStatus
import io.xr.lab.shared.models.SampleReceipt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ReceiptsViewModel fake-seam JVM 单测（swift ReceiptListViewModel 测试同构）：不真连，分页/回滚/过滤语义逐条钉死。 */
class ReceiptsViewModelTest {

  /** 样单构造样本（生成模型必填字段全给桩值，仅 id/flowStatus 可变）。 */
  private fun receipt(id: String, status: FlowStatus = FlowStatus.receiving): SampleReceipt =
      SampleReceipt(
          id = id,
          tenantId = "t-1",
          contractId = "c-1",
          commissionCode = "WT-$id",
          commissionDate = "2026-10-19",
          categoryCode = "cat",
          receivedBy = "alice",
          sampleSource = "src",
          testCategory = "test",
          flowStatus = status,
          flowHistory = emptyList(),
          createdAt = "2026-10-19T00:00:00Z",
          updatedAt = "2026-10-19T00:00:00Z",
      )

  @Test
  fun `load success replaces items and computes hasMore`() {
    val seams = ReceiptsViewModel.Seams(list = { listOf(receipt("r1"), receipt("r2")) })
    val vm = ReceiptsViewModel(seams)

    kotlinx.coroutines.runBlocking { vm.load() }

    assertEquals(listOf("r1", "r2"), vm.items.value.map { it.id })
    assertFalse(vm.hasMore.value) // 2 < pageSize 20 → 无更多
    assertNull(vm.error.value)
    assertFalse(vm.isLoading.value)
  }

  @Test
  fun `load failure sets error and leaves items empty`() {
    val seams = ReceiptsViewModel.Seams(list = { _ -> throw AuthError.Server("服务器错误（HTTP 500）") })
    val vm = ReceiptsViewModel(seams)

    kotlinx.coroutines.runBlocking { vm.load() }

    assertTrue(vm.items.value.isEmpty())
    assertEquals("服务器错误（HTTP 500）", vm.error.value)
    assertFalse(vm.isLoading.value)
  }

  @Test
  fun `filter and keyword propagate into provider query`() {
    var seen: ReceiptListQuery? = null
    val seams =
        ReceiptsViewModel.Seams(
            list = { q ->
              seen = q
              emptyList()
            },
        )
    val vm = ReceiptsViewModel(seams)

    kotlinx.coroutines.runBlocking { vm.load(ReceiptFilter.NotYet, "  WT-1  ") }

    assertEquals(ReceiptFilter.NotYet, seen?.filter)
    assertEquals("not_yet", seen?.filter?.queryValue)
    assertEquals("WT-1", seen?.keyword) // trim 后入查询
    assertEquals(1, seen?.page)
  }

  @Test
  fun `blank keyword normalizes to null`() {
    var seen: ReceiptListQuery? = null
    val seams =
        ReceiptsViewModel.Seams(
            list = { q ->
              seen = q
              emptyList()
            },
        )
    val vm = ReceiptsViewModel(seams)

    kotlinx.coroutines.runBlocking { vm.load(ReceiptFilter.All, "   ") }

    assertNull(seen?.keyword)
  }

  @Test
  fun `loadNextPage appends full page and keeps hasMore`() {
    val fullPage = (1..ReceiptsPaging.DEFAULT_PAGE_SIZE).map { receipt("r$it") }
    var call = 0
    val seams =
        ReceiptsViewModel.Seams(
            list = { _ ->
              call += 1
              fullPage
            },
        )
    val vm = ReceiptsViewModel(seams)
    kotlinx.coroutines.runBlocking { vm.load() }

    kotlinx.coroutines.runBlocking { vm.loadNextPage() }

    assertEquals(ReceiptsPaging.DEFAULT_PAGE_SIZE * 2, vm.items.value.size)
    assertTrue(vm.hasMore.value) // 整页 → 可能还有更多
    assertEquals(2, call)
  }

  @Test
  fun `loadNextPage empty page rolls back page number and stops`() {
    val fullPage = (1..ReceiptsPaging.DEFAULT_PAGE_SIZE).map { receipt("r$it") }
    val seams =
        ReceiptsViewModel.Seams(
            list = { q -> if (q.page == 1) fullPage else emptyList() },
        )
    val vm = ReceiptsViewModel(seams)
    kotlinx.coroutines.runBlocking { vm.load() }

    kotlinx.coroutines.runBlocking { vm.loadNextPage() }

    assertEquals(ReceiptsPaging.DEFAULT_PAGE_SIZE, vm.items.value.size) // 不追加空页
    assertFalse(vm.hasMore.value)
    assertEquals(1, vm.query.page) // 页码回滚
  }

  @Test
  fun `loadNextPage failure rolls back page and reports error`() {
    val fullPage = (1..ReceiptsPaging.DEFAULT_PAGE_SIZE).map { receipt("r$it") }
    val seams =
        ReceiptsViewModel.Seams(
            list = { q ->
              if (q.page == 1) {
                fullPage
              } else {
                throw AuthError.Unreachable(Exception("connection reset"))
              }
            },
        )
    val vm = ReceiptsViewModel(seams)
    kotlinx.coroutines.runBlocking { vm.load() }

    kotlinx.coroutines.runBlocking { vm.loadNextPage() }

    assertEquals(ReceiptsPaging.DEFAULT_PAGE_SIZE, vm.items.value.size)
    assertEquals(1, vm.query.page)
    assertTrue(vm.error.value!!.contains("无法连接"))
  }

  @Test
  fun `reload after filter change replaces items from page 1`() {
    val seams =
        ReceiptsViewModel.Seams(
            list = { q ->
              if (q.filter == ReceiptFilter.Submitted) {
                listOf(receipt("done-1", FlowStatus.completed))
              } else {
                listOf(receipt("new-1"))
              }
            },
        )
    val vm = ReceiptsViewModel(seams)
    kotlinx.coroutines.runBlocking { vm.load() }

    kotlinx.coroutines.runBlocking { vm.load(ReceiptFilter.Submitted, "") }

    assertEquals(listOf("done-1"), vm.items.value.map { it.id })
    assertEquals(ReceiptFilter.Submitted, vm.query.filter)
  }

  @Test
  fun `apply updates flowStatus by id and leaves uninvolved rows untouched`() {
    val seams = ReceiptsViewModel.Seams(list = { listOf(receipt("r1"), receipt("r2")) })
    val vm = ReceiptsViewModel(seams)
    kotlinx.coroutines.runBlocking { vm.load() }

    vm.apply(
        listOf(
            FlowActionResult(id = "r1", ok = true, flowStatus = FlowStatus.task_assignment),
            FlowActionResult(id = "r9", ok = false, message = "状态不允许提交"),
        ),
    )

    assertEquals(FlowStatus.task_assignment, vm.items.value.first { it.id == "r1" }.flowStatus)
    assertEquals(FlowStatus.receiving, vm.items.value.first { it.id == "r2" }.flowStatus)
  }

  @Test
  fun `apply with no flowStatus on result leaves row untouched`() {
    val seams = ReceiptsViewModel.Seams(list = { listOf(receipt("r1")) })
    val vm = ReceiptsViewModel(seams)
    kotlinx.coroutines.runBlocking { vm.load() }

    vm.apply(listOf(FlowActionResult(id = "r1", ok = false, message = "状态不允许提交")))

    assertEquals(FlowStatus.receiving, vm.items.value.first { it.id == "r1" }.flowStatus)
  }

  @Test
  fun `apply with empty results is a no-op`() {
    val seams = ReceiptsViewModel.Seams(list = { listOf(receipt("r1")) })
    val vm = ReceiptsViewModel(seams)
    kotlinx.coroutines.runBlocking { vm.load() }

    vm.apply(emptyList())

    assertEquals(FlowStatus.receiving, vm.items.value.first { it.id == "r1" }.flowStatus)
  }
}
