package io.xr.harness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.xr.harness.app.reportPreviewRecordsSeam
import io.xr.harness.app.reportPreviewSamplesSeam
import io.xr.harness.app.sampleExtPersistSeam
import io.xr.harness.app.sampleExtReportNamesSeam
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.harness.ui.ReportPreviewViewModel
import io.xr.harness.ui.SampleExtViewModel
import io.xr.lab.shared.models.TestRecord
import kotlinx.coroutines.launch

/**
 * 报告预览屏（swift ReportPreviewSheet 同构，M03.F09.I03，REQ-2026-007 T-2；用户裁定 Q1：纯数据摘要， docx 模板填充/打印/套打为家族
 * Web 专属非范围）。装载路径装 ext 补录门（REQ-2026-008 T-2，AC-1）： 预览装好后按 categoryCode 判 extFields 缺
 * key，缺则先出补录表单不渲染预览；合并落库成功（AC-3）重载预览。
 */
// @entry M03.F09.I03 — 报告预览（逐样品归集数据面摘要，swift sheet 同位）
@Composable
internal fun ReportPreviewScreen(
    receiptId: String,
    categoryCode: String,
    onClose: () -> Unit,
    store: SessionStore,
    tokens: TokenHolder,
) {
  val scope = rememberCoroutineScope()
  val vm = remember {
    ReportPreviewViewModel(
        samplesLoad = reportPreviewSamplesSeam(store, tokens),
        recordsLoad = reportPreviewRecordsSeam(store, tokens),
    )
  }
  val extVm = remember {
    SampleExtViewModel(
        reportNamesLoad = sampleExtReportNamesSeam(store, tokens),
        persist = sampleExtPersistSeam(store, tokens),
    )
  }
  val samples by vm.samples.collectAsState()
  val isLoading by vm.isLoading.collectAsState()
  val errorMessage by vm.errorMessage.collectAsState()
  val needsForm by extVm.needsForm.collectAsState()
  val extDidSave by extVm.didSave.collectAsState()

  // 门判定在预览装载后（家族 needExt 用装载好的 firstSample）。
  LaunchedEffect(receiptId) {
    vm.load(receiptId)
    if (vm.errorMessage.value == null) {
      extVm.load(categoryCode, vm.samples.value.firstOrNull())
    }
  }

  Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = onClose) { Text(text = "关闭") }
      Text(text = "报告预览", style = MaterialTheme.typography.headlineMedium)
    }
    Spacer(modifier = Modifier.height(16.dp))
    when {
      isLoading -> Text(text = "生成预览中…")
      errorMessage != null ->
          Text(text = "预览失败：$errorMessage", color = MaterialTheme.colorScheme.error)
      samples.isEmpty() -> Text(text = "该接样单还没有样品与检测记录")
      needsForm && !extDidSave -> {
        // 补录门（AC-1）：缺 key 先补录，不渲染预览列表。
        SampleExtFormScreen(
            vm = extVm,
            sampleId = samples.first().id,
            onSaved = { scope.launch { vm.load(receiptId) } },
        )
      }
      else -> ReportPreviewList(vm = vm)
    }
  }
}

/** 预览列表：按样品分组，样品下逐条检测记录（判定缺席显示 —，ADR-0019 呈现层占位非身份兜底）。 */
@Composable
private fun ColumnScope.ReportPreviewList(vm: ReportPreviewViewModel) {
  val samples by vm.samples.collectAsState()
  val recordsBySample by vm.recordsBySample.collectAsState()
  LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
    samples.forEach { sample ->
      val records = recordsBySample[sample.id].orEmpty()
      item(key = "header-${sample.id}") {
        Text(
            text = "样品 ${sample.sampleCode}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 6.dp),
        )
      }
      if (records.isEmpty()) {
        item(key = "empty-${sample.id}") {
          Text(
              text = "该样品暂无检测记录",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        items(records, key = { it.id }) { r -> PreviewRecordRow(r) }
      }
    }
  }
}

/** 记录行：参数 + 判定 + 结果/要求。 */
@Composable
private fun PreviewRecordRow(record: TestRecord) {
  Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    HorizontalDivider()
    Row(modifier = Modifier.fillMaxWidth()) {
      Text(text = record.parameterCode, style = MaterialTheme.typography.bodyLarge)
      Spacer(modifier = Modifier.weight(1f))
      Text(
          text = record.verdict ?: "—",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Text(
        text = "结果 ${record.result} · 要求 ${record.requirement}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
