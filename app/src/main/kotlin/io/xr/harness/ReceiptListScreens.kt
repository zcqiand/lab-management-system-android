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
import androidx.compose.material3.TextButton
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
import io.xr.harness.app.receiptsActSeam
import io.xr.harness.app.receiptsListSeam
import io.xr.harness.core.ReceiptFilter
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.harness.ui.ReceiptsViewModel
import io.xr.harness.ui.ReceivingFlowViewModel
import io.xr.lab.shared.models.FlowAction
import io.xr.lab.shared.models.SampleReceipt
import kotlinx.coroutines.launch

/**
 * 接样单列表屏全家（swift ReceiptListView 同构，M03.F01.I01/I04）：三态过滤 + 关键字 + 分页 + 行进详情 + 新建 + 多选提交。 act 提交走
 * ReceivingFlowViewModel（选区/身份守卫，M03.F01.I08），逐单结果 apply 回填阶段，业务失败按单红字呈现。
 */
@Composable
internal fun ReceiptsListScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    store: SessionStore,
    tokens: TokenHolder,
) {
  val scope = rememberCoroutineScope()
  val vm = remember {
    ReceiptsViewModel(ReceiptsViewModel.Seams(list = receiptsListSeam(store, tokens)))
  }
  // operator = 真会话身份（REQ-2026-004 同源）；缺席为空串，act 时被 VM 守卫拦下
  val flowVm = remember {
    ReceivingFlowViewModel(
        operatorName = store.username.orEmpty(),
        act = receiptsActSeam(store, tokens, store.username.orEmpty()),
    )
  }
  val items by vm.items.collectAsState()
  val isLoading by vm.isLoading.collectAsState()
  val error by vm.error.collectAsState()
  val hasMore by vm.hasMore.collectAsState()
  var keyword by remember { mutableStateOf("") }
  var filter by remember { mutableStateOf(ReceiptFilter.All) }
  val selected = remember { mutableStateListOf<String>() }

  LaunchedEffect(Unit) { vm.load(filter, keyword) }

  Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
    ReceiptsListHeader(onBack, onCreate, isLoading)
    ReceiptsFilterRow(
        selected = filter,
        isLoading = isLoading,
        onSelect = { f ->
          filter = f
          scope.launch { vm.load(f, keyword) }
        },
    )
    ReceiptsSearchRow(
        keyword = keyword,
        isLoading = isLoading,
        onKeywordChange = { keyword = it },
        onSearch = { scope.launch { vm.load(filter, keyword) } },
    )
    error?.let {
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = it, color = MaterialTheme.colorScheme.error)
    }
    Spacer(modifier = Modifier.height(8.dp))
    ReceiptsActSection(flowVm = flowVm, listVm = vm, selected = selected)
    ReceiptsItemsList(items = items, selected = selected, onOpen = onOpen)
    ReceiptsLoadMoreButton(
        hasMore = hasMore,
        isLoading = isLoading,
        onLoadMore = { scope.launch { vm.loadNextPage() } },
    )
  }
}

/** act 提交段（多选态在父级，回填走 listVm.apply）：逐单业务失败拼接红字，不 throw 不吞。 */
@Composable
private fun ReceiptsActSection(
    flowVm: ReceivingFlowViewModel,
    listVm: ReceiptsViewModel,
    selected: SnapshotStateList<String>,
) {
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  ReceiptsActBar(
      error = error,
      selectedCount = selected.size,
      busy = busy,
      onSubmit = {
        scope.launch {
          busy = true
          val result = runCatching { flowVm.perform(FlowAction.submit, selected.toList()) }
          busy = false
          result
              .onSuccess { results ->
                listVm.apply(results)
                selected.clear()
                error =
                    results
                        .filterNot { it.ok }
                        .joinToString("；") { "${it.id}：${it.message ?: "操作失败"}" }
                        .ifEmpty { null }
              }
              .onFailure { f -> error = f.message ?: "提交失败" }
        }
      },
  )
}

/** act 操作行（swift 提交所选同位）：错误红字，空选/忙时禁点。 */
@Composable
private fun ReceiptsActBar(
    error: String?,
    selectedCount: Int,
    busy: Boolean,
    onSubmit: () -> Unit,
) {
  error?.let {
    Text(text = it, color = MaterialTheme.colorScheme.error)
    Spacer(modifier = Modifier.height(4.dp))
  }
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
        text = "已选 $selectedCount 单",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
    )
    Button(onClick = onSubmit, enabled = !busy && selectedCount > 0) {
      Text(text = if (busy) "提交中…" else "提交所选")
    }
  }
}

/** 列表体（ColumnScope 扩展取 weight）：行勾选 + 行进详情。 */
@Composable
private fun ColumnScope.ReceiptsItemsList(
    items: List<SampleReceipt>,
    selected: SnapshotStateList<String>,
    onOpen: (String) -> Unit,
) {
  LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
    items(items, key = { it.id }) { r ->
      ReceiptsRow(
          r = r,
          checked = r.id in selected,
          onToggle = {
            if (r.id in selected) {
              selected.remove(r.id)
            } else {
              selected.add(r.id)
            }
          },
          onOpen = onOpen,
      )
    }
  }
}

/** 列表头行：返回 / 标题 / 新建。 */
@Composable
private fun ReceiptsListHeader(onBack: () -> Unit, onCreate: () -> Unit, isLoading: Boolean) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(onClick = onBack, enabled = !isLoading) { Text(text = "返回") }
    Text(text = "接样单", style = MaterialTheme.typography.headlineMedium)
    TextButton(onClick = onCreate, enabled = !isLoading) { Text(text = "新建") }
  }
}

/** 加载更多行（整页才有更多；忙时禁点防连击）。 */
@Composable
private fun ReceiptsLoadMoreButton(hasMore: Boolean, isLoading: Boolean, onLoadMore: () -> Unit) {
  if (hasMore) {
    TextButton(
        onClick = onLoadMore,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(text = if (isLoading) "加载中…" else "加载更多")
    }
  }
}

/** 关键字搜索行。 */
@Composable
private fun ReceiptsSearchRow(
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

/** 三态过滤行（swift 同款 全部/未提交/已提交 + ✓ 当前项）。 */
@Composable
private fun ReceiptsFilterRow(
    selected: ReceiptFilter,
    isLoading: Boolean,
    onSelect: (ReceiptFilter) -> Unit,
) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    ReceiptFilter.entries.forEach { f ->
      TextButton(onClick = { onSelect(f) }, enabled = !isLoading) {
        Text(
            text =
                when (f) {
                  ReceiptFilter.All -> "全部"
                  ReceiptFilter.NotYet -> "未提交"
                  ReceiptFilter.Submitted -> "已提交"
                } + if (selected == f) " ✓" else "",
        )
      }
    }
  }
}

/** 列表行：勾选 + 委托编号 + 阶段 · 时间（勾选区切多选，其余整行点击进详情）。 */
@Composable
private fun ReceiptsRow(
    r: SampleReceipt,
    checked: Boolean,
    onToggle: () -> Unit,
    onOpen: (String) -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onOpen(r.id) },
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = checked, onCheckedChange = { onToggle() })
    Column {
      Text(text = r.commissionCode, style = MaterialTheme.typography.bodyLarge)
      Text(
          text = "${r.flowStatus.value} · ${r.createdAt}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
