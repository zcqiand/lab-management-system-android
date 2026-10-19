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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.xr.harness.app.receiptsListSeam
import io.xr.harness.app.receiptsPhaseActSeam
import io.xr.harness.core.ReceiptFilter
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.harness.ui.ReceiptsViewModel
import io.xr.harness.ui.ReceivingFlowViewModel
import io.xr.lab.shared.models.FlowAction
import io.xr.lab.shared.models.FlowActionResult
import io.xr.lab.shared.models.SampleReceipt
import kotlinx.coroutines.launch

/**
 * 报告屏全家（swift ReportPhaseView 同构，M03.F05~F08，REQ-2026-006）：一个队列屏按 ReportPhase 档位
 * 参数化复用，不复制四份；队列钉本阶段环节，行上三动作（本阶段 submit / 退回 / 撤回）走 phase act 统一端点 （operator=真会话身份）；发放报告编号后端 act
 * 语义生成，行上只显示 reportCode。
 */
@Composable
internal fun ReportHomeScreen(onBack: () -> Unit, store: SessionStore, tokens: TokenHolder) {
  var phase by remember { mutableStateOf<ReportPhase?>(null) }

  val target = phase
  if (target != null) {
    ReportPhaseQueueScreen(
        phase = target,
        onBack = { phase = null },
        store = store,
        tokens = tokens,
    )
    return
  }
  ReportPhaseMenu(onBack = onBack, onOpen = { phase = it })
}

/** 报告菜单（swift toolbar 报告菜单同位）：四阶段入口，一阶段一钮。 */
@Composable
private fun ReportPhaseMenu(onBack: () -> Unit, onOpen: (ReportPhase) -> Unit) {
  Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = onBack) { Text(text = "返回") }
      Text(text = "报告", style = MaterialTheme.typography.headlineMedium)
    }
    Spacer(modifier = Modifier.height(16.dp))
    ReportPhase.entries.forEach { p ->
      Button(onClick = { onOpen(p) }, modifier = Modifier.fillMaxWidth()) {
        Text(text = p.phaseTitle)
      }
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

/** 单阶段队列屏（swift ReportPhaseView 同构）：查询钉死本阶段环节，行三动作走 phase act。 */
@Composable
internal fun ReportPhaseQueueScreen(
    phase: ReportPhase,
    onBack: () -> Unit,
    store: SessionStore,
    tokens: TokenHolder,
) {
  val scope = rememberCoroutineScope()
  val vm = remember {
    ReceiptsViewModel(ReceiptsViewModel.Seams(list = receiptsListSeam(store, tokens)))
  }
  val items by vm.items.collectAsState()
  val isLoading by vm.isLoading.collectAsState()
  val loadError by vm.error.collectAsState()
  val hasMore by vm.hasMore.collectAsState()
  var keyword by remember { mutableStateOf("") }
  var actTarget by remember { mutableStateOf<ActTarget?>(null) }
  var actError by remember { mutableStateOf<String?>(null) }
  var refresh by remember { mutableStateOf(0) }

  // 队列查询钉死本阶段环节（I01），不串档
  LaunchedEffect(refresh) { vm.load(ReceiptFilter.All, keyword, phase.flowStatus) }

  val onActDone: (List<FlowActionResult>, String?) -> Unit = { results, message ->
    vm.apply(results)
    actTarget = null
    actError = message ?: actFailureMessage(results)
  }
  val target = actTarget
  if (target != null) {
    ActConfirmDialog(
        target = target,
        operator = store.username.orEmpty(),
        act = receiptsPhaseActSeam(store, tokens, phase, store.username.orEmpty()),
        onDone = onActDone,
        onDismiss = { actTarget = null },
    )
  }

  Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
    ReportQueueHeader(phase = phase, onBack = onBack, isLoading = isLoading)
    Spacer(modifier = Modifier.height(8.dp))
    ReportQueueSearchRow(
        keyword = keyword,
        isLoading = isLoading,
        onKeywordChange = { keyword = it },
        onSearch = { scope.launch { vm.load(ReceiptFilter.All, keyword, phase.flowStatus) } },
    )
    loadError?.let {
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = it, color = MaterialTheme.colorScheme.error)
    }
    actError?.let {
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = it, color = MaterialTheme.colorScheme.error)
    }
    Spacer(modifier = Modifier.height(8.dp))
    ReportQueueList(items = items, submitLabel = phase.submitLabel, onAct = { actTarget = it })
    ReceiptsLoadMoreButton(
        hasMore = hasMore,
        isLoading = isLoading,
        onLoadMore = { scope.launch { vm.loadNextPage() } },
    )
  }
}

/** 逐单业务失败拼红字（家族同款：id：message 以「；」相连），无失败返回 null。 */
private fun actFailureMessage(results: List<FlowActionResult>): String? =
    results
        .filterNot { it.ok }
        .joinToString("；") { "${it.id}：${it.message ?: "操作失败"}" }
        .ifEmpty { null }

/** 队列头行：返回 / 阶段页标题。 */
@Composable
private fun ReportQueueHeader(phase: ReportPhase, onBack: () -> Unit, isLoading: Boolean) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(onClick = onBack, enabled = !isLoading) { Text(text = "返回") }
    Text(text = phase.phaseTitle, style = MaterialTheme.typography.headlineMedium)
  }
}

/** 队列搜索行（委托编号关键字，环节钉死不变）。 */
@Composable
private fun ReportQueueSearchRow(
    keyword: String,
    isLoading: Boolean,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        label = { Text(text = "委托编号关键字") },
        modifier = Modifier.weight(1f),
        singleLine = true,
    )
    Button(onClick = onSearch, enabled = !isLoading) { Text(text = "搜索") }
  }
}

/** 列表体（ColumnScope 扩展取 weight）。 */
@Composable
private fun ColumnScope.ReportQueueList(
    items: List<SampleReceipt>,
    submitLabel: String,
    onAct: (ActTarget) -> Unit,
) {
  LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
    items(items, key = { it.id }) { r ->
      ReportPhaseRow(receipt = r, submitLabel = submitLabel, onAct = onAct)
    }
  }
}

/** 阶段行：委托编号 + 报告编号（后端生成只显示）+ 三动作（本阶段 submit / 退回 / 撤回）。 */
@Composable
private fun ReportPhaseRow(
    receipt: SampleReceipt,
    submitLabel: String,
    onAct: (ActTarget) -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = receipt.commissionCode, style = MaterialTheme.typography.bodyLarge)
      receipt.reportCode
          ?.takeIf { it.isNotEmpty() }
          ?.let {
            Text(
                text = "报告编号 $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
      Text(
          text = receipt.flowStatus.value,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    TextButton(onClick = { onAct(ActTarget(FlowAction.submit, receipt)) }) {
      Text(text = submitLabel)
    }
    TextButton(onClick = { onAct(ActTarget(FlowAction.`return`, receipt)) }) { Text(text = "退回") }
    TextButton(onClick = { onAct(ActTarget(FlowAction.withdraw, receipt)) }) { Text(text = "撤回") }
  }
}

/** act 行目标：动作 + 目标单。 */
internal data class ActTarget(val action: FlowAction, val receipt: SampleReceipt)

/**
 * act 确认弹窗（swift ActConfirmSheet 同构，endpoint=phase(phase)）：动作/操作人展示，退回带理由； 逐单结果经 onDone
 * 回填列表（ok=false 拼红字），异常呈错不崩。
 */
@Composable
private fun ActConfirmDialog(
    target: ActTarget,
    operator: String,
    act: suspend (FlowAction, List<String>, String?) -> List<FlowActionResult>,
    onDone: (List<FlowActionResult>, String?) -> Unit,
    onDismiss: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val flowVm =
      remember(target.receipt.id) { ReceivingFlowViewModel(operatorName = operator, act = act) }
  var reason by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }

  AlertDialog(
      onDismissRequest = { if (!busy) onDismiss() },
      title = { Text(text = "确认${actionLabel(target.action)}") },
      text = {
        Column {
          Text(text = "动作：${actionLabel(target.action)}")
          Text(text = "单数：1")
          Text(text = "操作人：$operator")
          if (target.action == FlowAction.`return`) {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(text = "退回理由") },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
          }
          if (busy) {
            Text(text = "执行中…", style = MaterialTheme.typography.bodySmall)
          }
        }
      },
      confirmButton = {
        Button(
            onClick = {
              scope.launch {
                busy = true
                val trimmedReason =
                    if (target.action == FlowAction.`return`) reason.trim() else null
                runCatching {
                  flowVm.perform(target.action, listOf(target.receipt.id), trimmedReason)
                }
                    .onSuccess { onDone(it, null) }
                    .onFailure { onDone(emptyList(), it.message ?: "操作失败") }
                busy = false
              }
            },
            enabled = !busy,
        ) {
          Text(text = "确认")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(text = "取消") } },
  )
}
