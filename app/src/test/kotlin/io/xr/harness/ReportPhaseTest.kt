package io.xr.harness

import io.xr.harness.core.ReceiptFilter
import io.xr.harness.core.ReceiptListQuery
import io.xr.harness.ui.ReceiptsViewModel
import io.xr.lab.shared.models.FlowAction
import io.xr.lab.shared.models.FlowStatus
import io.xr.lab.shared.models.SampleReceipt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * ReportPhase 单测（swift ReportPhaseTests M03.F05~F08 同构）：本刀唯一新事 = 阶段描述映射 （阶段 → flowStatus 钉态 / 页标题 /
 * submit 按钮文案）；队列与 act 全复用既有缝 （ReceiptsViewModel.load 预设 + ReceivingFlowViewModel，刀 7/8 已各自钉死，不重复）。
 */
class ReportPhaseTest {

  /** 样单构造样本（生成模型必填字段全给桩值）。 */
  private fun receipt(id: String, status: FlowStatus): SampleReceipt =
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

  /** 四阶段队列钉测共用：钉 phase.flowStatus 拉队列，回传 provider 看到的查询与条数。 */
  private fun loadPinned(
      phase: ReportPhase,
      seen: MutableList<ReceiptListQuery>,
  ): Int {
    val vm =
        ReceiptsViewModel(
            ReceiptsViewModel.Seams(
                list = { query ->
                  seen.add(query)
                  listOf(receipt("r-1", phase.flowStatus))
                },
            )
        )
    kotlinx.coroutines.runBlocking { vm.load(ReceiptFilter.All, "WT-2026", phase.flowStatus) }
    return vm.items.value.size
  }

  @Test
  fun `four phases map to distinct flow statuses`() {
    val statuses = ReportPhase.entries.map { it.flowStatus }

    assertEquals(4, statuses.toSet().size, "四阶段必须各钉一态，不许串档")
    assertEquals(
        listOf(FlowStatus.review, FlowStatus.approval, FlowStatus.issuance, FlowStatus.archived),
        statuses,
    )
  }

  @Test
  fun `phase titles follow stage semantics`() {
    assertEquals("报告审核", ReportPhase.Review.phaseTitle)
    assertEquals("报告批准", ReportPhase.Approval.phaseTitle)
    assertEquals("报告发放", ReportPhase.Issuance.phaseTitle)
    assertEquals("报告归档", ReportPhase.Archived.phaseTitle)
  }

  @Test
  fun `submit labels follow phase semantics`() {
    assertEquals("审核通过", ReportPhase.Review.submitLabel)
    assertEquals("批准", ReportPhase.Approval.submitLabel)
    assertEquals("发放", ReportPhase.Issuance.submitLabel)
    assertEquals("归档完成", ReportPhase.Archived.submitLabel)
  }

  @Test
  fun `queue pinning per phase with keyword`() {
    for (phase in ReportPhase.entries) {
      val seen = mutableListOf<ReceiptListQuery>()
      val count = loadPinned(phase, seen)

      assertEquals(phase.flowStatus, seen.single().flowStatus, "${phase.name} 队列必须钉本阶段环节")
      assertEquals("WT-2026", seen.single().keyword)
      assertEquals(1, count)
    }
  }

  @Test
  fun `action labels for act confirm dialog`() {
    assertEquals("提交", actionLabel(FlowAction.submit))
    assertEquals("退回", actionLabel(FlowAction.`return`))
    assertEquals("撤回", actionLabel(FlowAction.withdraw))
  }
}
