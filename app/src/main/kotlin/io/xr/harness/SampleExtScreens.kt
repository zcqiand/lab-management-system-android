package io.xr.harness

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.xr.harness.ui.SampleExtViewModel
import io.xr.lab.shared.models.ExtFieldDef
import io.xr.lab.shared.models.ExtFieldDefType
import kotlinx.coroutines.launch

/**
 * 样品扩展字段补录表单（swift SampleExtFormView 同构，M03.F01.I07，REQ-2026-008 T-2）：逐字段行 （select →
 * 选项钮，text/number/date → 输入框），必填带「（必填）」后缀，逐字段红字校验； 草稿即 VM draft 态，保存直接落 VM（合并语义在
 * VM）。宿主（报告预览）归预览刀，本刀只落组件。
 */
@Composable
internal fun SampleExtFormScreen(vm: SampleExtViewModel, sampleId: String, onSaved: () -> Unit) {
  val scope = rememberCoroutineScope()
  val allFields by vm.allFields.collectAsState()
  val draft by vm.draft.collectAsState()
  val validationErrors by vm.validationErrors.collectAsState()
  val isSaving by vm.isSaving.collectAsState()
  val errorMessage by vm.errorMessage.collectAsState()
  val didSave by vm.didSave.collectAsState()

  LaunchedEffect(didSave) {
    if (didSave) {
      onSaved()
    }
  }

  Column(
      modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
  ) {
    Text(text = "样品信息补录", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))
    errorMessage?.let {
      Text(text = it, color = MaterialTheme.colorScheme.error)
      Spacer(modifier = Modifier.height(8.dp))
    }
    allFields.forEach { field ->
      SampleExtFieldRow(
          field = field,
          value = draft[field.key].orEmpty(),
          errorText = validationErrors[field.key],
          onValueChange = { vm.setValue(it, field.key) },
      )
      Spacer(modifier = Modifier.height(8.dp))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = { scope.launch { vm.save(sampleId) } },
        enabled = !isSaving,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(text = "保存并继续预览")
    }
  }
}

/** 单字段行：必填后缀 + 输入控件（select 选项钮，其余输入框）+ 逐字段红字。 */
@Composable
private fun SampleExtFieldRow(
    field: ExtFieldDef,
    value: String,
    errorText: String?,
    onValueChange: (String) -> Unit,
) {
  val label = if (field.required == true) "${field.label}（必填）" else field.label
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(4.dp))
    if (field.type == ExtFieldDefType.select) {
      SampleExtSelectOptions(field = field, value = value, onValueChange = onValueChange)
    } else {
      OutlinedTextField(
          value = value,
          onValueChange = onValueChange,
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
      )
    }
    errorText?.let { Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
  }
}

/** select 选项钮行（DataEntryScreens PickerSection 同款：选中带 ✓）。 */
@Composable
private fun SampleExtSelectOptions(
    field: ExtFieldDef,
    value: String,
    onValueChange: (String) -> Unit,
) {
  Column {
    field.options.orEmpty().forEach { option ->
      TextButton(onClick = { onValueChange(option) }) {
        Text(text = if (option == value) "✓ $option" else option)
      }
    }
  }
}
