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
import io.xr.harness.app.dataEntryActSeam
import io.xr.harness.app.dataEntryCatalogSeam
import io.xr.harness.app.dataEntryPersistSeam
import io.xr.harness.app.dataEntryRecordsSeam
import io.xr.harness.app.receiptsListSeam
import io.xr.harness.core.ReceiptFilter
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.harness.ui.DataEntryViewModel
import io.xr.harness.ui.ReceiptsViewModel
import io.xr.harness.ui.ReceivingFlowViewModel
import io.xr.lab.shared.models.FlowStatus
import io.xr.lab.shared.models.SampleReceipt
import kotlinx.coroutines.launch

/**
 * 数据录入屏全家（swift DataEntryView 同构，M03.F03.I01/I02/I03/I12）：队列钉 data_entry 环节（I01），行进录入 表单（目录装载 + 键控
 * create/update + verdict 随 body，I02/I03），行勾选 + 提交所选走录入阶段 act 统一端点 （I12，ReceivingFlowViewModel
 * 换缝复用）。
 */
@Composable
internal fun DataEntryQueueScreen(onBack: () -> Unit, store: SessionStore, tokens: TokenHolder) {
  val scope = rememberCoroutineScope()
  val vm = remember {
    ReceiptsViewModel(ReceiptsViewModel.Seams(list = receiptsListSeam(store, tokens)))
  }
  // operator = 真会话身份（REQ-2026-004 同源）；缺席为空串，act 时被 VM 守卫拦下
  val flowVm = remember {
    ReceivingFlowViewModel(
        operatorName = store.username.orEmpty(),
        act = dataEntryActSeam(store, tokens, store.username.orEmpty()),
    )
  }
  val items by vm.items.collectAsState()
  val isLoading by vm.isLoading.collectAsState()
  val error by vm.error.collectAsState()
  val hasMore by vm.hasMore.collectAsState()
  var keyword by remember { mutableStateOf("") }
  val selected = remember { mutableStateListOf<String>() }
  var entryFor by remember { mutableStateOf<SampleReceipt?>(null) }
  var refresh by remember { mutableStateOf(0) }

  LaunchedEffect(refresh) { vm.load(ReceiptFilter.All, keyword, FlowStatus.data_entry) }

  val target = entryFor
  if (target != null) {
    DataEntryScreen(
        receipt = target,
        onSaved = {
          entryFor = null
          refresh += 1
        },
        onCancel = { entryFor = null },
        store = store,
        tokens = tokens,
    )
    return
  }

  Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
    QueueHeader(title = "数据录入", onBack = onBack, isLoading = isLoading)
    Spacer(modifier = Modifier.height(8.dp))
    QueueSearchRow(
        keyword = keyword,
        isLoading = isLoading,
        onKeywordChange = { keyword = it },
        onSearch = { scope.launch { vm.load(ReceiptFilter.All, keyword, FlowStatus.data_entry) } },
    )
    error?.let {
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = it, color = MaterialTheme.colorScheme.error)
    }
    Spacer(modifier = Modifier.height(8.dp))
    ReceiptsActSection(flowVm = flowVm, listVm = vm, selected = selected)
    EntryItemsList(items = items, selected = selected, onOpen = { entryFor = it })
    ReceiptsLoadMoreButton(
        hasMore = hasMore,
        isLoading = isLoading,
        onLoadMore = { scope.launch { vm.loadNextPage() } },
    )
  }
}

/** 录入表单本地态（Compose remembered）：样品/参数选择 + 四个手填字段（vm 侧为权威，此为镜像）。 */
private class EntryFormState {
  var selectedSampleId by mutableStateOf("")
  var selectedParameterCode by mutableStateOf("")
  var result by mutableStateOf("")
  var requirement by mutableStateOf("")
  var standardCode by mutableStateOf("")
  var verdict by mutableStateOf("")
}

/** 选择即回填（AC-3：既有记录已录值呈现，改判在此基础上改）。 */
private fun syncFormFrom(vm: DataEntryViewModel, form: EntryFormState) {
  vm.fillFromExisting()
  form.result = vm.result
  form.requirement = vm.requirement
  form.standardCode = vm.standardCode
  form.verdict = vm.verdict ?: ""
}

/** 录入表单（swift DataEntryView Form 同构）：装载目录 + 表单段。 */
@Composable
internal fun DataEntryScreen(
    receipt: SampleReceipt,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    store: SessionStore,
    tokens: TokenHolder,
) {
  val vm = remember {
    DataEntryViewModel(
        catalogLoad = dataEntryCatalogSeam(store, tokens),
        recordsLoad = dataEntryRecordsSeam(store, tokens),
        persist = dataEntryPersistSeam(store, tokens),
    )
  }
  val isLoading by vm.isLoading.collectAsState()
  val saving by vm.isSaving.collectAsState()
  val form = remember { EntryFormState() }

  LaunchedEffect(receipt.id) { vm.load(receipt.id) }

  Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
    QueueHeader(title = "数据录入", onBack = onCancel, isLoading = saving)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = receipt.commissionCode, style = MaterialTheme.typography.titleMedium)
    if (isLoading) {
      Text(text = "目录装载中…", style = MaterialTheme.typography.bodySmall)
    }
    Spacer(modifier = Modifier.height(8.dp))
    EntryFormContent(vm = vm, form = form, onSaved = onSaved)
  }
}

/** 表单段：样品/参数选择 + 手填字段 + 保存（选择即回填，verdict 空串归一不传）。 */
@Composable
private fun EntryFormContent(vm: DataEntryViewModel, form: EntryFormState, onSaved: () -> Unit) {
  val scope = rememberCoroutineScope()
  val samples by vm.samples.collectAsState()
  val parameters by vm.parameters.collectAsState()
  val isLoading by vm.isLoading.collectAsState()
  val errorMessage by vm.errorMessage.collectAsState()
  val saving by vm.isSaving.collectAsState()
  val pickSample: (String) -> Unit = {
    vm.selectedSampleId = it
    form.selectedSampleId = it
    syncFormFrom(vm, form)
  }
  val pickParameter: (String) -> Unit = {
    vm.selectedParameterCode = it
    form.selectedParameterCode = it
    syncFormFrom(vm, form)
  }

  Column {
    PickerSection(
        title = "样品",
        entries = samples.map { it.id to it.sampleCode },
        selected = form.selectedSampleId,
        enabled = !saving,
        onSelect = pickSample,
    )
    Spacer(modifier = Modifier.height(8.dp))
    PickerSection(
        title = "检测参数",
        entries = parameters.map { it.code to "${it.name}（${it.code}）" },
        selected = form.selectedParameterCode,
        enabled = !saving,
        onSelect = pickParameter,
    )
    EntryValueFields(form = form, saving = saving)
    errorMessage?.let {
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = it, color = MaterialTheme.colorScheme.error)
    }
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = {
          scope.launch {
            vm.selectedSampleId = form.selectedSampleId
            vm.selectedParameterCode = form.selectedParameterCode
            vm.result = form.result
            vm.requirement = form.requirement
            vm.standardCode = form.standardCode
            vm.verdict = form.verdict.ifEmpty { null }
            if (vm.save() != null) {
              onSaved()
            }
          }
        },
        enabled = !saving && !isLoading,
    ) {
      Text(text = if (saving) "保存中…" else "保存")
    }
  }
}

/** 四个手填字段：检测结果/要求指标/标准代号/判定。 */
@Composable
private fun EntryValueFields(form: EntryFormState, saving: Boolean) {
  Spacer(modifier = Modifier.height(8.dp))
  OutlinedTextField(
      value = form.result,
      onValueChange = { form.result = it },
      label = { Text(text = "检测结果") },
      enabled = !saving,
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
  )
  Spacer(modifier = Modifier.height(8.dp))
  OutlinedTextField(
      value = form.requirement,
      onValueChange = { form.requirement = it },
      label = { Text(text = "要求指标") },
      enabled = !saving,
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
  )
  Spacer(modifier = Modifier.height(8.dp))
  OutlinedTextField(
      value = form.standardCode,
      onValueChange = { form.standardCode = it },
      label = { Text(text = "标准代号（可选）") },
      enabled = !saving,
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
  )
  Spacer(modifier = Modifier.height(8.dp))
  OutlinedTextField(
      value = form.verdict,
      onValueChange = { form.verdict = it },
      label = { Text(text = "判定（可选，空=未判定）") },
      enabled = !saving,
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
  )
}

/** 选择段（swift Picker 同位）：标题 + 备选行（✓ 当前项），家族账户页租户行同款形态。 */
@Composable
private fun PickerSection(
    title: String,
    entries: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
  Text(text = title, style = MaterialTheme.typography.titleSmall)
  if (entries.isEmpty()) {
    Text(
        text = "无备选",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  entries.forEach { (value, label) ->
    TextButton(onClick = { onSelect(value) }, enabled = enabled) {
      Text(text = label + if (selected == value) " ✓" else "")
    }
  }
}

/** 队列头行：返回 / 标题。 */
@Composable
private fun QueueHeader(title: String, onBack: () -> Unit, isLoading: Boolean) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(onClick = onBack, enabled = !isLoading) { Text(text = "返回") }
    Text(text = title, style = MaterialTheme.typography.headlineMedium)
  }
}

/** 队列搜索行（委托编号关键字，环节钉死不变）。 */
@Composable
private fun QueueSearchRow(
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

/** 列表体（ColumnScope 扩展取 weight）：行勾选 + 行进录入表单。 */
@Composable
private fun ColumnScope.EntryItemsList(
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
              text = "${r.flowStatus.value} · ${r.createdAt}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
