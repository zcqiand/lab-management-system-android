package io.xr.harness.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.xr.harness.ReceiptFormTarget
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.harness.ui.ReceiptDeleteViewModel
import io.xr.harness.ui.ReceiptDetailViewModel
import io.xr.harness.ui.ReceiptFormFields
import io.xr.harness.ui.ReceiptFormViewModel
import io.xr.lab.shared.models.FlowHistoryEntry
import io.xr.lab.shared.models.SampleReceipt
import kotlinx.coroutines.launch

/**
 * 接样单详情/表单屏（swift ReceiptDetailView / ReceiptFormView 同构，M03.F01.I02/I03/I06）。 必填集校验与请求构造全在
 * VM，本层只做绑定与标错；可选字段本切片不做。
 */

/** 样单 → 表单预填（swift prefill 同位）。 */
internal fun fieldsFrom(receipt: SampleReceipt): ReceiptFormFields =
    ReceiptFormFields(
        contractId = receipt.contractId,
        commissionCode = receipt.commissionCode,
        commissionDate = receipt.commissionDate,
        categoryCode = receipt.categoryCode,
        receivedBy = receipt.receivedBy,
        sampleSource = receipt.sampleSource,
        testCategory = receipt.testCategory,
    )

/** 必填集标签（swift ReceiptFormView field 标签同款）。 */
private val formLabels =
    listOf(
        "contractId" to "委托单位 ID",
        "commissionCode" to "委托编号",
        "commissionDate" to "委托日期（YYYY-MM-DD）",
        "categoryCode" to "检测类别代码",
        "receivedBy" to "接样人",
        "sampleSource" to "样品来源",
        "testCategory" to "检测性质",
    )

private fun ReceiptFormFields.valueOf(key: String): String =
    entries().firstOrNull { it.first == key }?.second ?: ""

private fun ReceiptFormFields.withValue(key: String, value: String): ReceiptFormFields =
    when (key) {
      "contractId" -> copy(contractId = value)
      "commissionCode" -> copy(commissionCode = value)
      "commissionDate" -> copy(commissionDate = value)
      "categoryCode" -> copy(categoryCode = value)
      "receivedBy" -> copy(receivedBy = value)
      "sampleSource" -> copy(sampleSource = value)
      else -> copy(testCategory = value)
    }

/** 新建/编辑表单（id=null 新建）。校验失败/网络失败都以红字呈现，不打崩。 */
@Composable
internal fun ReceiptFormScreen(
    target: ReceiptFormTarget,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    store: SessionStore,
    tokens: TokenHolder,
) {
  val scope = rememberCoroutineScope()
  val vm = remember { ReceiptFormViewModel(persist = receiptsFormPersistSeam(store, tokens)) }
  var fields by remember { mutableStateOf(target.fields) }
  var saving by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
    Text(
        text = if (target.id == null) "新建接样单" else "编辑接样单",
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(modifier = Modifier.height(8.dp))
    formLabels.forEach { (key, label) ->
      OutlinedTextField(
          value = fields.valueOf(key),
          onValueChange = { fields = fields.withValue(key, it) },
          label = { Text(text = label) },
          enabled = !saving,
          modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
          singleLine = true,
      )
    }
    error?.let {
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = it, color = MaterialTheme.colorScheme.error)
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
          onClick = {
            saving = true
            error = null
            scope.launch {
              val result = runCatching {
                vm.fields = fields
                vm.save(target.id, if (target.id == null) null else target.fields)
              }
              saving = false
              result.onSuccess { onSaved() }.onFailure { f -> error = f.message ?: "保存失败" }
            }
          },
          enabled = !saving,
      ) {
        Text(text = if (saving) "保存中…" else "保存")
      }
      TextButton(onClick = onCancel, enabled = !saving) { Text(text = "取消") }
    }
  }
}

/** 详情（swift ReceiptDetailView 同构）：字段段 + 流程历史（at 倒序）+ 编辑/删除。 */
@Composable
internal fun ReceiptDetailScreen(
    id: String,
    onBack: () -> Unit,
    onEdit: (SampleReceipt) -> Unit,
    store: SessionStore,
    tokens: TokenHolder,
) {
  val scope = rememberCoroutineScope()
  val vm = remember { ReceiptDetailViewModel(fetch = receiptsDetailSeam(store, tokens)) }
  val delVm = remember { ReceiptDeleteViewModel(deleteSeam = receiptsDeleteSeam(store, tokens)) }
  val receipt by vm.receipt.collectAsState()
  val history by vm.history.collectAsState()
  val error by vm.error.collectAsState()
  val delBusy by delVm.isDeleting.collectAsState()
  val delError by delVm.error.collectAsState()

  LaunchedEffect(id) { vm.load(id) }

  Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = onBack, enabled = !delBusy) { Text(text = "返回") }
      Text(text = "接样单详情", style = MaterialTheme.typography.headlineMedium)
    }
    Spacer(modifier = Modifier.height(8.dp))
    error?.let {
      Text(text = it, color = MaterialTheme.colorScheme.error)
      Spacer(modifier = Modifier.height(8.dp))
    }
    receipt?.let { r -> ReceiptDetailFields(r) }
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = "流程历史", style = MaterialTheme.typography.titleMedium)
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
      items(history, key = { "${it.at}-${it.operator}" }) { h -> HistoryRow(h) }
    }
    delError?.let {
      Text(text = it, color = MaterialTheme.colorScheme.error)
      Spacer(modifier = Modifier.height(8.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = { onEdit(receipt!!) }, enabled = receipt != null && !delBusy) {
        Text(text = "编辑")
      }
      Button(
          onClick = {
            scope.launch {
              delVm.delete(id)
              if (delVm.error.value == null) {
                onBack()
              }
            }
          },
          enabled = !delBusy,
      ) {
        Text(text = if (delBusy) "删除中…" else "删除")
      }
    }
  }
}

/** 字段段（必填集只读呈现）。 */
@Composable
private fun ReceiptDetailFields(r: SampleReceipt) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(text = "委托编号：${r.commissionCode}", style = MaterialTheme.typography.bodyLarge)
    Text(text = "委托单位 ID：${r.contractId}", style = MaterialTheme.typography.bodySmall)
    Text(text = "委托日期：${r.commissionDate}", style = MaterialTheme.typography.bodySmall)
    Text(text = "检测类别代码：${r.categoryCode}", style = MaterialTheme.typography.bodySmall)
    Text(text = "接样人：${r.receivedBy}", style = MaterialTheme.typography.bodySmall)
    Text(text = "样品来源：${r.sampleSource}", style = MaterialTheme.typography.bodySmall)
    Text(text = "检测性质：${r.testCategory}", style = MaterialTheme.typography.bodySmall)
    Text(
        text = "阶段：${r.flowStatus.value}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun HistoryRow(h: FlowHistoryEntry) {
  Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Text(text = "${h.at} · ${h.operator}", style = MaterialTheme.typography.bodySmall)
    Text(
        text = "${h.from.value} → ${h.to.value}" + (h.reason?.let { "（$it）" } ?: ""),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
