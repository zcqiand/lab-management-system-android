package io.xr.harness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.xr.harness.app.ReceiptDetailScreen
import io.xr.harness.app.ReceiptFormScreen
import io.xr.harness.app.SharedPreferencesStore
import io.xr.harness.app.SharedPreferencesTokenStore
import io.xr.harness.app.fieldsFrom
import io.xr.harness.app.listTenantsSeam
import io.xr.harness.app.loginSeam
import io.xr.harness.app.logoutSeam
import io.xr.harness.app.receiptsListSeam
import io.xr.harness.app.switchTenantSeam
import io.xr.harness.app.whoamiSeam
import io.xr.harness.core.ApiGlue
import io.xr.harness.core.ReceiptFilter
import io.xr.harness.core.SessionState
import io.xr.harness.core.SessionStore
import io.xr.harness.core.TokenHolder
import io.xr.harness.core.WhoamiUser
import io.xr.harness.ui.AuthPhase
import io.xr.harness.ui.AuthViewModel
import io.xr.harness.ui.ReceiptFormFields
import io.xr.harness.ui.ReceiptsViewModel
import io.xr.lab.shared.models.SampleReceipt
import kotlinx.coroutines.launch

/** 三态路由（swift App 层同构）：needsSetup → 配置表单 / needsLogin → 登录表单 / ready → 账户。 */
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { MaterialTheme { AppRoot() } }
  }
}

@Composable
fun AppRoot() {
  val context = LocalContext.current
  val prefsStore = remember { SharedPreferencesStore(context) }
  val tokenStore = remember { SharedPreferencesTokenStore(context) }
  // 持久化 token 启动预载进内存持有（ktor bearer 插件读 TokenHolder）
  val tokens = remember {
    TokenHolder().also {
      it.accessToken = tokenStore.loadAccess()
      it.refreshToken = tokenStore.loadRefresh()
    }
  }
  val store = remember { SessionStore(prefsStore, tokenStore) }
  val scope = rememberCoroutineScope()
  val vm = remember {
    AuthViewModel(
        store,
        AuthViewModel.Seams(
            login = loginSeam(store, tokens),
            whoami = whoamiSeam(store, tokens),
            logout = logoutSeam(store, tokens),
            switchTenant = switchTenantSeam(store, tokens),
            listTenants = listTenantsSeam(store, tokens),
        ),
    )
  }
  val session by vm.session.collectAsState()
  val user by vm.user.collectAsState()
  var showReceipts by remember { mutableStateOf(false) }

  // 启动自愈：持久化会话 whoami 校验，失败即清密回登录
  LaunchedEffect(Unit) { vm.restore() }

  when (val s = session) {
    is SessionState.NeedsSetup ->
        ConfigScreen(
            onSave = { baseUrl ->
              store.saveConfig(baseUrl)
              vm.refreshSession()
            },
        )
    is SessionState.NeedsLogin ->
        LoginScreen(
            vm = vm,
            onReconfigure = { vm.reconfigure() },
        )
    is SessionState.Ready ->
        if (showReceipts) {
          ReceiptsScreen(onBack = { showReceipts = false }, store = store, tokens = tokens)
        } else {
          AccountScreen(
              vm = vm,
              ready = s,
              user = user,
              onOpenReceipts = { showReceipts = true },
              onLogout = { scope.launch { vm.logout() } },
          )
        }
  }
}

/** lab 无 clientId（登录代理化 REQ-2026-023）：配置页只填 API 地址（SSO clientId 归 SSO 刀）。 */
@Composable
fun ConfigScreen(onSave: (String) -> Unit) {
  var baseUrl by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
  ) {
    Text(text = "配置", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = baseUrl,
        onValueChange = { baseUrl = it },
        label = { Text(text = "API 地址") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    error?.let {
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = it, color = MaterialTheme.colorScheme.error)
    }
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = {
          try {
            onSave(ApiGlue.normalizeBaseURL(baseUrl))
          } catch (e: IllegalArgumentException) {
            error = e.message
          }
        },
        enabled = baseUrl.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(text = "保存")
    }
  }
}

@Composable
fun LoginScreen(vm: AuthViewModel, onReconfigure: () -> Unit) {
  val scope = rememberCoroutineScope()
  val phase by vm.phase.collectAsState()
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  val busy = phase is AuthPhase.Busy

  Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
  ) {
    Text(text = "登录", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text(text = "用户名") },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(text = "密码") },
        enabled = !busy,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    (phase as? AuthPhase.Failed)?.let { failed ->
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = failed.message, color = MaterialTheme.colorScheme.error)
    }
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = { scope.launch { vm.login(username, password) } },
        enabled = !busy && username.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(text = if (busy) "登录中…" else "登录")
    }
    TextButton(onClick = onReconfigure, enabled = !busy) { Text(text = "重新配置") }
  }
}

/** 账户页（swift AccountView 同构）：当前用户 + 租户成员段（点选切换，当前带 ✓，busy 禁点防连击）。 */
@Composable
fun AccountScreen(
    vm: AuthViewModel,
    ready: SessionState.Ready,
    user: WhoamiUser?,
    onOpenReceipts: () -> Unit,
    onLogout: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val phase by vm.phase.collectAsState()
  val tenants by vm.tenants.collectAsState()
  val busy = phase is AuthPhase.Busy

  // 进页拉租户成员真值（swift .task listTenants 同位）；切租户成功后重拉跟随新 ✓
  LaunchedEffect(ready.tenantId) { vm.loadTenants() }

  Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
  ) {
    Text(text = "已登录", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))
    user?.displayName?.let { Text(text = "昵称：$it") }
    Text(text = "用户：${ready.username}")
    ready.tenantId?.let { Text(text = "租户：$it") }
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onOpenReceipts, modifier = Modifier.fillMaxWidth()) { Text(text = "接样单列表") }
    Spacer(modifier = Modifier.height(16.dp))
    Text(text = "租户成员", style = MaterialTheme.typography.titleMedium)
    if (tenants.isEmpty()) {
      Text(text = "无成员关系", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    tenants.forEach { t ->
      val isCurrent = t.tenantId == ready.tenantId
      TextButton(
          onClick = { scope.launch { vm.switchTenant(t.tenantId) } },
          enabled = !busy && !isCurrent,
      ) {
        Text(
            text =
                buildString {
                  append(t.name ?: t.tenantId)
                  if (isCurrent) {
                    append(" ✓")
                  }
                  t.detail?.let { append("（$it）") }
                },
        )
      }
    }
    (phase as? AuthPhase.Failed)?.let { failed ->
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = failed.message, color = MaterialTheme.colorScheme.error)
    }
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onLogout, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
      Text(text = "退出登录")
    }
  }
}

/** 表单导航目标：id=null 新建，非 null 编辑（fields=预填基准，也是 PATCH diff 的 original）。 */
internal class ReceiptFormTarget(val id: String?, val fields: ReceiptFormFields)

/** 接样单三态路由（swift ReceiptListView 同构）：列表 → 详情 → 表单（新建/编辑）。 */
@Composable
fun ReceiptsScreen(onBack: () -> Unit, store: SessionStore, tokens: TokenHolder) {
  var detailId by remember { mutableStateOf<String?>(null) }
  var form by remember { mutableStateOf<ReceiptFormTarget?>(null) }

  val target = form
  if (target != null) {
    ReceiptFormScreen(
        target = target,
        onSaved = { form = null },
        onCancel = { form = null },
        store = store,
        tokens = tokens,
    )
  } else {
    val id = detailId
    if (id != null) {
      ReceiptDetailScreen(
          id = id,
          onBack = { detailId = null },
          onEdit = { r -> form = ReceiptFormTarget(id, fieldsFrom(r)) },
          store = store,
          tokens = tokens,
      )
    } else {
      ReceiptsListScreen(
          onBack = onBack,
          onOpen = { detailId = it },
          onCreate = { form = ReceiptFormTarget(null, ReceiptFormFields()) },
          store = store,
          tokens = tokens,
      )
    }
  }
}

/** 接样单列表（swift ReceiptListView 只读面同构）：三态过滤 + 关键字 + 分页 + 行进详情 + 新建。 */
@Composable
private fun ReceiptsListScreen(
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
  val items by vm.items.collectAsState()
  val isLoading by vm.isLoading.collectAsState()
  val error by vm.error.collectAsState()
  val hasMore by vm.hasMore.collectAsState()
  var keyword by remember { mutableStateOf("") }
  var filter by remember { mutableStateOf(ReceiptFilter.All) }

  LaunchedEffect(Unit) { vm.load(filter, keyword) }

  Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = onBack, enabled = !isLoading) { Text(text = "返回") }
      Text(text = "接样单", style = MaterialTheme.typography.headlineMedium)
      TextButton(onClick = onCreate, enabled = !isLoading) { Text(text = "新建") }
    }
    Spacer(modifier = Modifier.height(8.dp))
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
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
      items(items, key = { it.id }) { r -> ReceiptsRow(r, onOpen) }
    }
    if (hasMore) {
      TextButton(
          onClick = { scope.launch { vm.loadNextPage() } },
          enabled = !isLoading,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Text(text = if (isLoading) "加载中…" else "加载更多")
      }
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

/** 列表行：委托编号 + 阶段 · 时间（整行点击进详情）。 */
@Composable
private fun ReceiptsRow(r: SampleReceipt, onOpen: (String) -> Unit) {
  Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onOpen(r.id) },
  ) {
    Text(text = r.commissionCode, style = MaterialTheme.typography.bodyLarge)
    Text(
        text = "${r.flowStatus.value} · ${r.createdAt}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
