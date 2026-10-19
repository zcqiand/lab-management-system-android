package io.xr.harness

import io.xr.lab.shared.models.FlowAction
import io.xr.lab.shared.models.FlowStatus

/**
 * 报告四阶段描述（swift CoreKit.ReportPhase 同构，M03.F05~F08，REQ-2026-006）：四阶段与接样→任务分配→ 数据录入是同一流程线；队列与 act
 * 全复用既有缝（ReceiptsViewModel.load 预设 + ReceivingFlowViewModel 换 phase 缝），本件只做「阶段 → 查询钉态 / 页标题 /
 * 操作按钮文案」的映射（镜像家族 ReportPhasePage 单组件四 wrapper）。发放的报告编号由后端 act submit 语义生成，UI 只显示
 * reportCode——本枚举不发明 客户端状态机。
 */
enum class ReportPhase {
  Review,
  Approval,
  Issuance,
  Archived,
}

/** 队列查询钉死的流程态（每阶段一态，不串档）。 */
val ReportPhase.flowStatus: FlowStatus
  get() =
      when (this) {
        ReportPhase.Review -> FlowStatus.review
        ReportPhase.Approval -> FlowStatus.approval
        ReportPhase.Issuance -> FlowStatus.issuance
        ReportPhase.Archived -> FlowStatus.archived
      }

/** 阶段页标题。 */
val ReportPhase.phaseTitle: String
  get() =
      when (this) {
        ReportPhase.Review -> "报告审核"
        ReportPhase.Approval -> "报告批准"
        ReportPhase.Issuance -> "报告发放"
        ReportPhase.Archived -> "报告归档"
      }

/** 操作按钮文案（submit 语义；家族 submitLabel 同款）。 */
val ReportPhase.submitLabel: String
  get() =
      when (this) {
        ReportPhase.Review -> "审核通过"
        ReportPhase.Approval -> "批准"
        ReportPhase.Issuance -> "发放"
        ReportPhase.Archived -> "归档完成"
      }

/** act 动作文案（确认弹窗「动作」行；swift FlowAction.label 同位）。 */
fun actionLabel(action: FlowAction): String =
    when (action) {
      FlowAction.submit -> "提交"
      FlowAction.`return` -> "退回"
      FlowAction.withdraw -> "撤回"
    }
