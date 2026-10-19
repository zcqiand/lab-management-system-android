package io.xr.harness

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.xr.harness.app.receiptsAssignActSeam
import io.xr.harness.app.receiptsAssignTaskSeam
import io.xr.harness.app.receiptsListSeam
import io.xr.harness.core.ReceiptFilter
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.harness.ui.AssignTaskViewModel
import io.xr.harness.ui.ReceiptsViewModel
import io.xr.harness.ui.ReceivingFlowViewModel
import io.xr.lab.shared.models.FlowStatus
import io.xr.lab.shared.models.SampleReceipt
import kotlinx.coroutines.launch

/**
 * 任务分配屏全家（swift TaskAssignment 队列/安排 sheet 同构，M03.F02.I01/I02/I05）：队列钉 task_assignment 环节
 * （I01），行勾选 + 提交所选走分配阶段 act 统一端点（I05，ReceivingFlowViewModel 换缝复用），行进安排表单（I02， 手填姓名+日期）。
 */
@Composable
internal fun AssignQueueScreen(onBack: () -> Unit, store: SessionStore, tokens: TokenHolder) {
  val scope = rememberCoroutineScope()
  val vm = remember {
    ReceiptsViewModel(ReceiptsViewModel.Seams(list = receiptsListSeam(store, tokens)))
  }
  // operator = 真会话身份（REQ-2026-004 Q3 同源）；缺席为空串，act 时被 VM 守卫拦下
  val flowVm = remember {
    ReceivingFlowViewModel(
        operatorName = store.username.orEmpty(),
        act = receiptsAssignActSeam(store, tokens, store.username.orEmpty()),
    )
  }
  val items by vm.items.collectAsState()
  val isLoading by vm.isLoading.collectAsState()
  val error by vm.error.collectAsState()
  val hasMore by vm.hasMore.collectAsState()
  var keyword by remember { mutableStateOf("") }
  val selected = remember { mutableStateListOf<String>() }
  var assignFor by remember { mutableStateOf<SampleReceipt?>(null) }
  var refresh by remember { mutableStateOf(0) }

  LaunchedEffect(refresh) { vm.load(ReceiptFilter.All, keyword, FlowStatus.task_assignment) }

  val target = assignFor
  if (target != null) {
    AssignTaskScreen(
        receipt = target,
        onSaved = {
          assignFor = null
          refresh += 1
        },
        onCancel = { assignFor = null },
        store = store,
        tokens = tokens,
    )
    return
  }

  Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
    AssignQueueHeader(onBack = onBack, isLoading = isLoading)
    Spacer(modifier = Modifier.height(8.dp))
    AssignSearchRow(
        keyword = keyword,
        isLoading = isLoading,
        onKeywordChange = { keyword = it },
        onSearch = {
          scope.launch { vm.load(ReceiptFilter.All, keyword, FlowStatus.task_assignment) }
        },
    )
    error?.let {
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = it, color = MaterialTheme.colorScheme.error)
    }
    Spacer(modifier = Modifier.height(8.dp))
    ReceiptsActSection(flowVm = flowVm, listVm = vm, selected = selected)
    AssignItemsList(items = items, selected = selected, onOpen = { assignFor = it })
    ReceiptsLoadMoreButton(
        hasMore = hasMore,
        isLoading = isLoading,
        onLoadMore = { scope.launch { vm.loadNextPage() } },
    )
  }
}

/** 队列头行：返回 / 标题。 */
@Composable
private fun AssignQueueHeader(onBack: () -> Unit, isLoading: Boolean) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(onClick = onBack, enabled = !isLoading) { Text(text = "返回") }
    Text(text = "任务分配", style = MaterialTheme.typography.headlineMedium)
  }
}

/** 队列搜索行（委托编号关键字，环节钉死不变）。 */
@Composable
private fun AssignSearchRow(
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

/** 列表体（ColumnScope 扩展取 weight）：行勾选 + 行进安排表单。 */
@Composable
private fun ColumnScope.AssignItemsList(
    items: List<SampleReceipt>,
    selected: SnapshotStateList<String>,
    onOpen: (SampleReceipt) -> Unit,
) {
  LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
    items(items, key = { it.id }) { r ->
      Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onOpen(r) },
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(
            checked = r.id in selected,
            onCheckedChange = {
              if (r.id in selected) {
                selected.remove(r.id)
              } else {
                selected.add(r.id)
              }
            },
        )
        Column {
          Text(text = r.commissionCode, style = MaterialTheme.typography.bodyLarge)
          Text(
              text =
                  (r.assigneeName?.let { "$it · ${r.plannedTestDate ?: "日期未定"}" } ?: "未安排") +
                      " · ${r.flowStatus.value}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

/** 安排表单（swift AssignTaskView sheet 同构，M03.F02.I02）：手填姓名+日期，保存安排/取消安排双动作。 */
@Composable
internal fun AssignTaskScreen(
    receipt: SampleReceipt,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    store: SessionStore,
    tokens: TokenHolder,
) {
  val scope = rememberCoroutineScope()
  val vm = remember { AssignTaskViewModel(persist = receiptsAssignTaskSeam(store, tokens)) }
  var name by remember { mutableStateOf(receipt.assigneeName ?: "") }
  var date by remember { mutableStateOf(receipt.plannedTestDate ?: "") }
  val errorMessage by vm.errorMessage.collectAsState()
  val saving by vm.isSaving.collectAsState()
  // 双动作同一条保存路：取消安排 = 清空两字段（VM 全空 → 全 nil 清空请求）
  val doSave: (String, String) -> Unit = { n, d ->
    scope.launch {
      vm.assigneeName = n
      vm.plannedTestDate = d
      if (vm.save(receipt.id) != null) {
        onSaved()
      }
    }
  }

  Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = onCancel, enabled = !saving) { Text(text = "返回") }
      Text(text = "安排检测", style = MaterialTheme.typography.headlineMedium)
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = receipt.commissionCode, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(text = "检测人员姓名") },
        enabled = !saving,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = date,
        onValueChange = { date = it },
        label = { Text(text = "计划检测日期（YYYY-MM-DD）") },
        enabled = !saving,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    errorMessage?.let {
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = it, color = MaterialTheme.colorScheme.error)
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = { doSave(name, date) }, enabled = !saving) {
        Text(text = if (saving) "保存中…" else "保存安排")
      }
      Button(onClick = { doSave("", "") }, enabled = !saving) { Text(text = "取消安排") }
    }
  }
}
