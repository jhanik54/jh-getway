package com.jh.gateway

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.app.KeyguardManager
import android.view.WindowManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {
    private lateinit var securitySettings: GatewaySettings
    private var lockPromptShowing = false
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        securitySettings = GatewaySettings(this)
        applyScreenshotProtection()
        setContent { GatewayApp(onRequestNotifications = { requestNotificationPermission() }) }
    }

    override fun onResume() {
        super.onResume()
        if (!::securitySettings.isInitialized) return
        if (lockPromptShowing) return
        val needsLock = securitySettings.appLock && System.currentTimeMillis() - securitySettings.lastUnlock > 30_000
        if (needsLock) {
            val keyguard = getSystemService(KeyguardManager::class.java)
            if (keyguard.isKeyguardSecure) {
                lockPromptShowing = true
                startActivityForResult(keyguard.createConfirmDeviceCredentialIntent(getString(R.string.str_unlock_title_282692), getString(R.string.str_unlock_subtitle_a7b10d)), 701)
            } else securitySettings.lastUnlock = System.currentTimeMillis()
        } else if (!lockPromptShowing) securitySettings.lastUnlock = System.currentTimeMillis()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 701) {
            lockPromptShowing = false
            if (resultCode == RESULT_OK) securitySettings.lastUnlock = System.currentTimeMillis() else finish()
        }
    }

    private fun applyScreenshotProtection() {
        if (securitySettings.screenshotBlock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        }
    }
}

private val Navy = Color(0xFF111827)
private val Blue = Color(0xFF4F67E8)
private val Green = Color(0xFF15803D)
private val Muted = Color(0xFF667085)

private data class ProviderPreset(val name: String, val url: String, val model: String, val protocol: ProviderProtocol = ProviderProtocol.OPENAI)
private val providerPresets = listOf(
    ProviderPreset("OpenAI", "https://api.openai.com", "gpt-4o-mini"),
    ProviderPreset("Anthropic", "https://api.anthropic.com", "claude-3-5-sonnet-latest", ProviderProtocol.ANTHROPIC),
    ProviderPreset("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash"),
    ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "auto"),
    ProviderPreset("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
    ProviderPreset("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
    ProviderPreset("Mistral", "https://api.mistral.ai/v1", "mistral-small-latest"),
    ProviderPreset("OpenCode Zen", "https://opencode.ai/zen/v1", "auto"),
    ProviderPreset("Custom OpenAI-compatible", "https://", "auto")
)

private enum class KeyStatus { ACTIVE, COOLDOWN, DISABLED }
private data class ProviderKey(
    val name: String,
    val provider: String,
    val model: String,
    val status: KeyStatus,
    val priority: Int,
    val maskedKey: String,
    val id: String,
    val healthStatus: String = "unknown",
    val healthLatencyMs: Long = 0L,
    val healthError: String = ""
)

/** Strategy label. [priorityLabel] and [roundRobinLabel] are resolved by the caller so the
 *  labels follow the active locale. */
private fun strategyLabel(strategy: String, priorityLabel: String, roundRobinLabel: String, leastLatencyLabel: String): String = when (strategy.uppercase()) {
    "PRIORITY" -> priorityLabel
    "ROUND_ROBIN" -> roundRobinLabel
    "LEAST_LATENCY" -> leastLatencyLabel
    else -> strategy
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GatewayApp(onRequestNotifications: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    val providerStore = remember { ProviderStore(context) }
    val gatewaySecurity = remember { GatewaySecurity(context) }
    val providerHealth = remember { ProviderHealthStore(context) }
    var keys by remember { mutableStateOf(providerStore.list().map { it.toProviderKey(providerStore, providerHealth) }) }
    var showAddDialog by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showChainDialog by remember { mutableStateOf(false) }
    var showMonitoringDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showRegenerateDialog by remember { mutableStateOf(false) }
    var showPlayground by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<StoredProvider?>(null) }
    var detailProvider by remember { mutableStateOf<StoredProvider?>(null) }
    var providerMessage by remember { mutableStateOf("") }
    val gatewaySettings = remember { GatewaySettings(context) }
    val gatewayState = remember { GatewayStateStore(context) }
    val updateManager = remember { UpdateManager(context) }
    val chainCount = remember(keys) { ChainStore(context).load(providerStore.list()).count { it.enabled && it.id != "empty" } }
    LaunchedEffect(Unit) { running = gatewayState.state == GatewayRunState.RUNNING }
    LaunchedEffect(running) {
        if (running && gatewayState.state != GatewayRunState.RUNNING) {
            // User toggled start; wait briefly then re-read actual state from service.
            running = gatewayState.state == GatewayRunState.RUNNING
        }
    }
    val gatewayFailedToStart = stringResource(R.string.str_gateway_failed_to_start_555ba8)
    LaunchedEffect(gatewayState.state) {
        running = gatewayState.state == GatewayRunState.RUNNING
        if (gatewayState.state == GatewayRunState.ERROR) {
            updateMessage = gatewayState.error.ifBlank { gatewayFailedToStart }
        }
    }
    var theme by remember { mutableStateOf(gatewaySettings.theme) }
    val credentialUnavailable = stringResource(R.string.str_credential_unavailable_e32e40)
    val providerUpdated = stringResource(R.string.str_provider_updated_d9bc02)
    val updateFailed = stringResource(R.string.str_update_failed_4de04c)
    val couldNotAddProvider = stringResource(R.string.str_could_not_add_provider_f41402)
    val tabs = listOf(stringResource(R.string.str_home_70f8bb), stringResource(R.string.str_providers_87b7c0), stringResource(R.string.str_more_4bab2d))

    val darkTheme = when (theme) { "DARK" -> true; "SYSTEM" -> isSystemInDarkTheme(); else -> false }
    var splashDone by rememberSaveable { mutableStateOf(false) }
    MaterialTheme(colorScheme = if (darkTheme) androidx.compose.material3.darkColorScheme(primary = Color(0xFF9DAAFF)) else androidx.compose.material3.lightColorScheme(primary = Blue)) {
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.str_jh_getway_d50ce4), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.str_personal_ai_gateway_77c98c), style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Security, stringResource(R.string.str_open_settings_fd7108)) }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(if (index == 0) Icons.Default.Home else if (index == 1) Icons.Default.Key else Icons.Default.MoreHoriz, label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            when (tab) {
                0 -> HomeScreen(Modifier.padding(padding), running, gatewaySecurity.maskedKey(), gatewaySettings.port, gatewaySettings.wifiMode, keys, strategyLabel(gatewaySettings.routingStrategy, stringResource(R.string.str_detail_priority_f31aca), stringResource(R.string.str_strategy_round_robin_3d7c5a), stringResource(R.string.str_strategy_least_latency_dddcc6)), chainCount, error = updateMessage, onCopyKey = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("JH GetWay key", gatewaySecurity.gatewayKey()))
                }, onToggle = {
                    running = !running
                    onRequestNotifications()
                    val intent = Intent(context, GatewayService::class.java).apply {
                        if (!running) action = GatewayService.ACTION_STOP
                    }
                    if (running) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
                }, onViewChain = { showChainDialog = true }, onViewLogs = { showMonitoringDialog = true })
                1 -> ProvidersScreen(Modifier.padding(padding), keys, providerMessage, unreadableKeys = providerStore.hasUnreadableKeys(), onInfo = { key -> detailProvider = providerStore.list().firstOrNull { provider -> provider.id == key.id } }, onAdd = { showAddDialog = true }, onToggle = { key, enabled -> providerStore.setEnabled(key.id, enabled); keys = providerStore.list().map { it.toProviderKey(providerStore, providerHealth) } }, onEdit = { key -> editingProvider = providerStore.list().firstOrNull { provider -> provider.id == key.id } }, onDelete = { key -> providerStore.remove(key.id); keys = providerStore.list().map { it.toProviderKey(providerStore, providerHealth) } }, onTest = { key -> val provider = providerStore.list().firstOrNull { it.id == key.id }; val secret = provider?.let { providerStore.apiKey(it) }; if (provider == null || secret == null) providerMessage = credentialUnavailable else { providerMessage = context.getString(R.string.str_testing_provider_8d3a16, key.name); ProviderModelFetcher.fetch(provider.baseUrl, secret, provider.protocol) { result -> (context as? ComponentActivity)?.runOnUiThread { providerMessage = result.fold({ context.getString(R.string.str_test_ok_models_2c7b45, key.name, it.size) }, { context.getString(R.string.str_test_failed_provider_9e4d18, key.name, it.message ?: "test failed") }) } } } })
                else -> MoreScreen(Modifier.padding(padding), updateMessage, updateBadge = updateManager.hasUpdateAvailable(), onSettings = { showSettings = true }, onPlayground = { showPlayground = true }, onChain = { showChainDialog = true }, onMonitoring = { showMonitoringDialog = true }, onBackup = { showBackupDialog = true }, onHelp = { showHelpDialog = true }, onUpdate = { showUpdateDialog = true })
            }
        }
        if (showAddDialog) {
            AddProviderDialog(
                onDismiss = { showAddDialog = false },
                onSave = { name, url, model, key, protocol, aliases ->
                    runCatching { providerStore.add(name, url, model, key, protocol, aliases) }.onSuccess { keys = providerStore.list().map { it.toProviderKey(providerStore, providerHealth) }; showAddDialog = false }.onFailure { providerMessage = it.message ?: couldNotAddProvider }
                }
            )
        }
        editingProvider?.let { provider ->
            AddProviderDialog(initial = provider, initialKey = providerStore.apiKey(provider).orEmpty(), onDismiss = { editingProvider = null }, onSave = { name, url, model, key, protocol, aliases ->
                runCatching { providerStore.update(provider.id, name, url, model, key, protocol, aliases) }.onSuccess { keys = providerStore.list().map { it.toProviderKey(providerStore, providerHealth) }; editingProvider = null; providerMessage = providerUpdated }.onFailure { providerMessage = it.message ?: updateFailed }
            })
        }
        detailProvider?.let { provider ->
            ProviderDetailDialog(provider, providerHealth.get(provider.id), providerStore.apiKey(provider)?.let { "${it.take(4)}••••••${it.takeLast(4)}" } ?: "••••••••", GatewayStats(context), onDismiss = { detailProvider = null })
        }
        if (showSettings) SettingsDialog(gatewaySettings, gatewaySecurity, onSecurityChanged = { (context as? MainActivity)?.applySecurityFromUi() }, onAppearanceChanged = { theme = gatewaySettings.theme }, onServerSettingsChanged = { restartGatewayIfRunning(context, gatewayState) }, onRegenerateKey = { showRegenerateDialog = true }, onDismiss = { showSettings = false })
        if (showUpdateDialog) UpdateDialog(gatewaySettings, onDismiss = { showUpdateDialog = false })
        if (showChainDialog) ChainDialog(providerStore, onDismiss = { showChainDialog = false })
        if (showMonitoringDialog) MonitoringDialog(GatewayStats(context), onDismiss = { showMonitoringDialog = false })
        if (showBackupDialog) BackupDialog(providerStore, onDismiss = { showBackupDialog = false })
        if (showHelpDialog) HelpDialog(gatewaySettings, gatewaySecurity, onDismiss = { showHelpDialog = false })
        if (showPlayground) PlaygroundDialog(providerStore, gatewaySettings, gatewaySecurity, onDismiss = { showPlayground = false })
        if (showRegenerateDialog) {
            AlertDialog(
                onDismissRequest = { showRegenerateDialog = false },
                title = { Text(stringResource(R.string.str_regenerate_key_title_4c8e21), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.str_regenerate_key_body_a1f7d3), color = Muted) },
                confirmButton = {
                    Button(onClick = {
                        gatewaySecurity.regenerate()
                        showRegenerateDialog = false
                        updateMessage = context.getString(R.string.str_regenerate_key_done_5e3b9c)
                    }) { Text(stringResource(R.string.str_regenerate_confirm_7d1a86)) }
                },
                dismissButton = { OutlinedButton(onClick = { showRegenerateDialog = false }) { Text(stringResource(R.string.str_cancel_77dfd2)) } }
            )
        }
        if (!splashDone) SplashOverlay(darkTheme = darkTheme, onDone = { splashDone = true })
        }
    }
}

/**
 * Restarts the gateway so changed server settings (port, Wi-Fi mode, concurrency limit)
 * take effect. The running server keeps its own copy of the settings, so without a
 * restart the new values would be ignored until the service was stopped manually.
 */
private fun restartGatewayIfRunning(context: android.content.Context, gatewayState: GatewayStateStore) {
    if (gatewayState.state != GatewayRunState.RUNNING && gatewayState.state != GatewayRunState.STARTING) return
    val intent = Intent(context, GatewayService::class.java).apply { action = GatewayService.ACTION_RESTART }
    runCatching { ContextCompat.startForegroundService(context, intent) }
}

private fun MainActivity.applySecurityFromUi() {
    if (GatewaySettings(this).screenshotBlock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

private fun StoredProvider.toProviderKey(store: ProviderStore, health: ProviderHealthStore? = null): ProviderKey {
    val record = health?.get(id)
    return ProviderKey(
        name, baseUrl, model,
        when { !enabled -> KeyStatus.DISABLED; isCoolingDown -> KeyStatus.COOLDOWN; else -> KeyStatus.ACTIVE },
        priority, store.apiKey(this)?.let { "${it.take(4)}••••••${it.takeLast(4)}" } ?: "••••••••", id,
        healthStatus = record?.status ?: "unknown",
        healthLatencyMs = record?.lastLatencyMs ?: 0L,
        healthError = record?.lastError.orEmpty()
    )
}

@Composable
private fun AddProviderDialog(initial: StoredProvider? = null, initialKey: String = "", onDismiss: () -> Unit, onSave: (String, String, String, String, ProviderProtocol, List<String>) -> Unit) {
    val context = LocalContext.current
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var url by remember(initial?.id) { mutableStateOf(initial?.baseUrl ?: "https://api.openai.com") }
    var model by remember(initial?.id) { mutableStateOf(initial?.model ?: "auto") }
    var key by remember(initial?.id) { mutableStateOf(initialKey) }
    var aliases by remember(initial?.id) { mutableStateOf(initial?.aliases?.joinToString(", ") ?: "") }
    var showPresets by remember { mutableStateOf(false) }
    var protocol by remember { mutableStateOf(ProviderProtocol.OPENAI) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var fetching by remember { mutableStateOf(false) }
    var fetchMessage by remember { mutableStateOf("") }
    val fetchingModels = stringResource(R.string.str_fetching_models_9b2b7d)
    val fetchFailed = stringResource(R.string.str_model_fetch_failed_337b77)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.str_add_provider_key_b52d47) else stringResource(R.string.str_edit_provider_c3c1d1)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.str_choose_a_provider_preset_or_en_e20bec), color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { showPresets = true }, modifier = Modifier.fillMaxWidth()) { Text(if (name.isBlank()) stringResource(R.string.str_choose_provider_preset_30169e) else name) }
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.str_name_709a23)) }, singleLine = true)
            OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.str_base_url_1dbd61)) }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.str_default_model_5fbae1)) }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                OutlinedButton(enabled = !fetching && url.isNotBlank() && key.isNotBlank(), onClick = {
                    fetching = true; fetchMessage = fetchingModels
                    val activity = context as? ComponentActivity
                    ProviderModelFetcher.fetch(url, key, protocol) { result -> activity?.runOnUiThread { fetching = false; result.onSuccess { fetched -> models = fetched; fetchMessage = "${fetched.size} models found" }.onFailure { fetchMessage = it.message ?: fetchFailed } } }
                }) { Text(if (fetching) stringResource(R.string.str_x_df316e) else stringResource(R.string.str_fetch_d48aaf)) }
            }
            if (fetchMessage.isNotBlank()) Text(fetchMessage, color = if (models.isNotEmpty()) Green else Muted, style = MaterialTheme.typography.labelSmall)
            if (models.isNotEmpty()) {
                Text(stringResource(R.string.str_select_a_model_9eea85), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                Column(Modifier.height(120.dp)) { models.take(12).forEach { item -> FilterChip(selected = model == item, onClick = { model = item }, label = { Text(item, maxLines = 1) }) } }
            }
            OutlinedTextField(key, { key = it }, label = { Text(stringResource(R.string.str_api_key_cf678c)) }, singleLine = true)
            OutlinedTextField(aliases, { aliases = it }, label = { Text(stringResource(R.string.str_model_aliases_comma_separated_8cb80b)) }, singleLine = true)
        } },
        confirmButton = { Button(enabled = name.isNotBlank() && url.isNotBlank() && key.isNotBlank(), onClick = { onSave(name, url, model, key, protocol, aliases.split(',').map(String::trim).filter(String::isNotBlank)) }) { Text(stringResource(R.string.str_save_securely_803e41)) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.str_cancel_77dfd2)) } }
    )
    if (showPresets) {
        AlertDialog(
            onDismissRequest = { showPresets = false },
            title = { Text(stringResource(R.string.str_provider_presets_0facf4)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { providerPresets.forEach { preset -> TextButton(onClick = { name = preset.name; url = preset.url; model = preset.model; protocol = preset.protocol; showPresets = false }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) { Text(preset.name, fontWeight = FontWeight.SemiBold); Text(preset.url, color = Muted, style = MaterialTheme.typography.labelSmall) } } } } },
            confirmButton = { OutlinedButton(onClick = { showPresets = false }) { Text(stringResource(R.string.str_cancel_77dfd2)) } }
        )
    }
}

@Composable
private fun HomeScreen(modifier: Modifier, running: Boolean, gatewayKey: String, port: Int, wifiMode: Boolean, keys: List<ProviderKey>, strategy: String, chainCount: Int, error: String, onCopyKey: () -> Unit, onToggle: () -> Unit, onViewChain: () -> Unit, onViewLogs: () -> Unit) {
    val context = LocalContext.current
    val stats = remember { GatewayStats(context) }
    var statsTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2_000); statsTick++ } }
    val requests = remember(statsTick) { stats.requestCount() }
    val recentLogs = remember(statsTick) { stats.recent().take(2) }
    val readyKeys = keys.count { it.status == KeyStatus.ACTIVE }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.str_good_morning_1f1c44), style = MaterialTheme.typography.titleMedium, color = Muted)
            Text(stringResource(R.string.str_your_ai_traffic_simplified_2b2277), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Navy)
            Spacer(Modifier.height(4.dp))
        }
        item { StatusCard(running, port, wifiMode, error, onToggle) }
        item { GatewayKeyCard(gatewayKey, onCopyKey) }
        item { MetricRow(requests, readyKeys, keys.size) }
        item { SectionTitle(stringResource(R.string.str_active_route_9ad8bd), stringResource(R.string.str_view_chain_43303f), onViewChain) }
        item { RouteCard(strategy, chainCount) }
        item { SectionTitle(stringResource(R.string.str_recent_activity_72d561), stringResource(R.string.str_view_logs_4c3958), onViewLogs) }
        if (recentLogs.isEmpty()) {
            item { Text(stringResource(R.string.str_no_requests_yet_start_the_gate_763d4b), color = Muted, style = MaterialTheme.typography.bodySmall) }
        } else {
            items(recentLogs) { LogRow(it) }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun GatewayKeyCard(key: String, onCopy: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7FF)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E2FF))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(38.dp), shape = RoundedCornerShape(11.dp), color = Color(0xFFE9EDFF)) { Icon(Icons.Default.Security, null, tint = Blue, modifier = Modifier.padding(9.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(stringResource(R.string.str_gateway_key_c7864a), fontWeight = FontWeight.SemiBold, color = Navy); Text(key, color = Muted, style = MaterialTheme.typography.bodySmall); Text(stringResource(R.string.str_required_by_opencode_and_other_001a4a), color = Muted, style = MaterialTheme.typography.labelSmall) }
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, stringResource(R.string.str_copy_gateway_key_824df3), tint = Blue) }
        }
    }
}

@Composable
private fun StatusCard(running: Boolean, port: Int, wifiMode: Boolean, error: String, onToggle: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (running) Navy else Color(0xFF29344B)), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (running) Color(0xFF56E39F) else Color(0xFFFFC857)))
                Spacer(Modifier.width(10.dp))
                Text(if (running) stringResource(R.string.str_gateway_is_running_eb4929) else stringResource(R.string.str_gateway_is_paused_845491), color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Dns, null, tint = Color.White.copy(alpha = .7f))
            }
            Text(if (running) stringResource(R.string.str_ready_to_receive_requests_from_2d07a9) else stringResource(R.string.str_start_the_gateway_to_connect_y_2050e0), color = Color.White.copy(alpha = .72f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (running) "${if (wifiMode) localIpAddress() else "127.0.0.1"}:$port" else stringResource(R.string.str_local_only_dd2a30), color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Button(onClick = onToggle, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Navy)) {
                    Text(if (running) stringResource(R.string.str_stop_9e2534) else stringResource(R.string.str_start_gateway_4901dd))
                }
            }
            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = Color(0xFFFF9999), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MetricRow(requests: Int, readyKeys: Int, totalKeys: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard(stringResource(R.string.str_requests_f7194e), requests.toString(), Icons.Default.Sync, Modifier.weight(1f))
        MetricCard(stringResource(R.string.str_keys_ready_c82114), "$readyKeys / $totalKeys", Icons.Default.Key, Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F6FA))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Blue, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Navy)
            Text(title, style = MaterialTheme.typography.labelMedium, color = Muted)
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.weight(1f))
        if (onClick != null) {
            Text(action, color = Blue, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable(onClick = onClick))
        } else {
            Text(action, color = Blue, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun RouteCard(strategy: String, chainCount: Int) {
    val subtitle = if (chainCount <= 0) "No providers in chain · routing by $strategy" else "$strategy · $chainCount provider${if (chainCount == 1) "" else "s"} in chain"
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6EAF0))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(42.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFFE9EDFF)) { Icon(Icons.Default.CloudQueue, null, tint = Blue, modifier = Modifier.padding(10.dp)) }
            Spacer(Modifier.width(12.dp))
            Column { Text(stringResource(R.string.str_best_available_1f0017), fontWeight = FontWeight.SemiBold, color = Navy); Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.CheckCircle, stringResource(R.string.str_active_route_9ad8bd), tint = Green)
        }
    }
}

@Composable
private fun ProvidersScreen(modifier: Modifier, keys: List<ProviderKey>, message: String, unreadableKeys: Boolean = false, onInfo: (ProviderKey) -> Unit, onAdd: () -> Unit, onToggle: (ProviderKey, Boolean) -> Unit, onEdit: (ProviderKey) -> Unit, onDelete: (ProviderKey) -> Unit, onTest: (ProviderKey) -> Unit) {
    var filter by remember { mutableIntStateOf(0) }
    val activeCount = keys.count { it.status == KeyStatus.ACTIVE }
    val cooldownCount = keys.count { it.status == KeyStatus.COOLDOWN }
    val disabledCount = keys.count { it.status == KeyStatus.DISABLED }
    val filteredKeys = when (filter) {
        1 -> keys.filter { it.status == KeyStatus.ACTIVE }
        2 -> keys.filter { it.status == KeyStatus.COOLDOWN }
        3 -> keys.filter { it.status == KeyStatus.DISABLED }
        else -> keys
    }
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(stringResource(R.string.str_providers_87b7c0), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Navy); Text(stringResource(R.string.str_manage_your_ai_keys_and_models_71313e), color = Muted) }; Button(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.str_add_key_49e644)) } }
        if (message.isNotBlank()) Text(message, color = Muted, style = MaterialTheme.typography.bodySmall)
        if (unreadableKeys) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.str_keys_unreadable_title_5d3a71), fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                    Text(stringResource(R.string.str_keys_unreadable_body_7b91c4), style = MaterialTheme.typography.bodySmall, color = Color(0xFF92400E))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(filter == 0, { filter = 0 }, label = { Text(stringResource(R.string.str_all_6a7208) + "  ${keys.size}") })
            FilterChip(filter == 1, { filter = 1 }, label = { Text(stringResource(R.string.str_active_a733b8) + "  $activeCount") })
            FilterChip(filter == 2, { filter = 2 }, label = { Text(stringResource(R.string.str_cooldown_98fd67) + "  $cooldownCount") })
            FilterChip(filter == 3, { filter = 3 }, label = { Text(stringResource(R.string.str_disabled_f4f447) + "  $disabledCount") })
        }
        if (filteredKeys.isEmpty()) Text(if (keys.isEmpty()) stringResource(R.string.str_no_providers_yet_add_your_firs_cf6122) else stringResource(R.string.str_no_providers_in_this_state_b94867), color = Muted, style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(filteredKeys) { ProviderCard(it, onToggle, onEdit, onDelete, onTest, onInfo) } }
    }
}

@Composable
private fun ProviderCard(key: ProviderKey, onToggle: (ProviderKey, Boolean) -> Unit, onEdit: (ProviderKey) -> Unit, onDelete: (ProviderKey) -> Unit, onTest: (ProviderKey) -> Unit, onInfo: (ProviderKey) -> Unit) {
    Card(onClick = { onInfo(key) }, colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6EAF0))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(38.dp), shape = RoundedCornerShape(10.dp), color = Color(0xFFF0F2FF)) { Icon(Icons.Default.Key, null, tint = Blue, modifier = Modifier.padding(9.dp)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(key.name, fontWeight = FontWeight.SemiBold, color = Navy); Text(key.provider, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1) }; StatusPill(key.status) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text(key.model, color = Navy, fontWeight = FontWeight.Medium); Spacer(Modifier.weight(1f)); Text(stringResource(R.string.str_card_priority_926478), color = Muted, style = MaterialTheme.typography.labelSmall) }
            val healthColor = when (key.healthStatus) { "healthy" -> Green; "unhealthy" -> Color(0xFFB91C1C); else -> Muted }
            val healthText = when (key.healthStatus) {
                "healthy" -> "Healthy · ${key.healthLatencyMs}ms"
                "unhealthy" -> "Unhealthy${if (key.healthError.isNotBlank()) " · ${key.healthError}" else ""}"
                else -> stringResource(R.string.str_health_not_checked_d4c95f)
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(healthColor)); Spacer(Modifier.width(6.dp)); Text(healthText, color = healthColor, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text(key.maskedKey, color = Muted, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.weight(1f)); TextButton(onClick = { onTest(key) }) { Text(stringResource(R.string.str_test_640ab2)) }; TextButton(onClick = { onEdit(key) }) { Text(stringResource(R.string.str_edit_530164)) }; Switch(key.status != KeyStatus.DISABLED, { onToggle(key, it) }); IconButton(onClick = { onDelete(key) }) { Icon(Icons.Default.Delete, "Delete ${key.name}", tint = Color(0xFFB91C1C)) } }
        }
    }
}

@Composable
private fun StatusPill(status: KeyStatus) {
    val (label, color) = when (status) {
        KeyStatus.ACTIVE -> stringResource(R.string.str_active_a733b8) to Green
        KeyStatus.COOLDOWN -> stringResource(R.string.str_cooldown_98fd67) to Color(0xFFC2410C)
        KeyStatus.DISABLED -> stringResource(R.string.str_disabled_f4f447) to Muted
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .12f)) { Text(label, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
}

@Composable
private fun ProviderDetailDialog(provider: StoredProvider, health: ProviderHealth?, maskedKey: String, stats: GatewayStats, onDismiss: () -> Unit) {
    val logs = remember(provider.id) { stats.recent().filter { it.provider == provider.name } }
    val usage = remember(provider.id) { stats.usageEntries().filter { it.provider == provider.name } }
    val success = logs.count { it.status in 200..299 }
    val successRate = if (logs.isEmpty()) 0 else success * 100 / logs.size
    val averageLatency = if (logs.isEmpty()) 0 else logs.map { it.latencyMs }.average().toInt()
    val promptTokens = usage.sumOf { it.promptTokens }
    val completionTokens = usage.sumOf { it.completionTokens }
    val healthStatus = when (health?.status) { "healthy" -> stringResource(R.string.str_health_healthy_f66c3e); "unhealthy" -> stringResource(R.string.str_health_unhealthy_141907); else -> stringResource(R.string.str_health_no_data_70fb16) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(provider.name) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(stringResource(R.string.str_detail_base_url_6d97c7), provider.baseUrl)
                DetailRow(stringResource(R.string.str_detail_protocol_833f61), provider.protocol.name)
                DetailRow(stringResource(R.string.str_detail_model_08f524), provider.model)
                DetailRow(stringResource(R.string.str_detail_aliases_b60a94), provider.aliases.joinToString(", ").ifBlank { "—" })
                DetailRow(stringResource(R.string.str_detail_priority_f31aca), provider.priority.toString())
                DetailRow(stringResource(R.string.str_detail_status_9729f9), when { !provider.enabled -> stringResource(R.string.str_status_disabled_6808c3); provider.isCoolingDown -> stringResource(R.string.str_status_cooling_down_4ceade); else -> stringResource(R.string.str_status_enabled_a2e77d) })
                DetailRow(stringResource(R.string.str_detail_api_key_6325d8), maskedKey)
                Text(stringResource(R.string.str_health_3703cd), fontWeight = FontWeight.SemiBold, color = Navy)
                DetailRow(stringResource(R.string.str_detail_status_9729f9), healthStatus)
                if ((health?.lastLatencyMs ?: 0L) > 0L) DetailRow(stringResource(R.string.str_detail_last_latency_d52d9e), "${health?.lastLatencyMs}ms")
                if (health?.lastError.orEmpty().isNotBlank()) DetailRow(stringResource(R.string.str_detail_last_error_18c7dd), health?.lastError.orEmpty())
                if ((health?.updatedAt ?: 0L) > 0L) DetailRow(stringResource(R.string.str_detail_checked_810874), SimpleDateFormat("MMM d, HH:mm:ss", Locale.US).format(Date(health?.updatedAt ?: 0L)))
                Text(stringResource(R.string.str_recent_activity_last_100_logge_94fa25), fontWeight = FontWeight.SemiBold, color = Navy)
                DetailRow(stringResource(R.string.str_detail_requests_21430a), logs.size.toString())
                DetailRow(stringResource(R.string.str_detail_success_rate_948aa3), "$successRate%")
                DetailRow(stringResource(R.string.str_detail_avg_latency_6ffad6), "${averageLatency}ms")
                DetailRow(stringResource(R.string.str_detail_tokens_5e84dd), if (usage.isEmpty()) stringResource(R.string.str_detail_no_usage_583f41) else "$promptTokens input · $completionTokens output")
                Text(stringResource(R.string.str_api_keys_are_never_shown_in_fu_72f5bf), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.str_close_bbfa77)) } }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp))
        Text(value, color = Navy, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MoreScreen(modifier: Modifier, updateMessage: String, updateBadge: Boolean, onSettings: () -> Unit, onChain: () -> Unit, onMonitoring: () -> Unit, onBackup: () -> Unit, onHelp: () -> Unit, onPlayground: () -> Unit, onUpdate: () -> Unit) {
    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.str_more_4bab2d), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Navy); Text(stringResource(R.string.str_control_how_jh_getway_works_7d9083), color = Muted)
        MoreItem(Icons.Default.Storage, stringResource(R.string.str_routing_chain_2a695d), stringResource(R.string.str_set_priority_and_fallback_orde_00c694), onChain)
        MoreItem(Icons.Default.CloudQueue, stringResource(R.string.str_playground_title_4d1c73), stringResource(R.string.str_playground_more_9c4e28), onPlayground)
        MoreItem(Icons.Default.Refresh, stringResource(R.string.str_request_logs_f264ea), stringResource(R.string.str_review_provider_switches_and_e_abc19f), onMonitoring)
        MoreItem(Icons.Default.Sync, stringResource(R.string.str_usage_0bb186), stringResource(R.string.str_requests_status_and_latency_su_fb0be2), onMonitoring)
        MoreItem(Icons.Default.Settings, stringResource(R.string.str_settings_c7f73b), stringResource(R.string.str_network_appearance_and_securit_f10828), onSettings)
        MoreItem(Icons.Default.Security, stringResource(R.string.str_backup_security_592249), stringResource(R.string.str_encrypted_export_and_app_lock_ff3264), onBackup)
        MoreItem(Icons.Default.Refresh, stringResource(R.string.str_help_setup_guide_9c83d5), stringResource(R.string.str_battery_tools_and_troubleshoot_d50614), onHelp)
        MoreItem(
            Icons.Default.SystemUpdate,
            stringResource(R.string.str_app_updates_16213f),
            when {
                updateBadge -> stringResource(R.string.str_update_available_6b8e04)
                updateMessage.isBlank() -> stringResource(R.string.str_check_github_for_a_newer_apk_b9d4c3)
                else -> updateMessage
            },
            onClick = onUpdate
        )
        Spacer(Modifier.weight(1f)); OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("JH GetWay v${BuildConfig.VERSION_NAME}") }
    }
}

private fun localIpAddress(): String = runCatching {
    NetworkInterface.getNetworkInterfaces().toList().asSequence()
        .flatMap { it.inetAddresses.toList().asSequence() }
        .filterIsInstance<Inet4Address>().firstOrNull { !it.isLoopbackAddress }?.hostAddress ?: "127.0.0.1"
}.getOrDefault("127.0.0.1")

@Composable
private fun HelpDialog(settings: GatewaySettings, security: GatewaySecurity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase(Locale.US) }
    var section by remember { mutableIntStateOf(0) }
    val ip = remember { localIpAddress() }
    val host = if (settings.wifiMode) ip else "127.0.0.1"
    val baseUrl = "http://$host:${settings.port}/v1"
    val key = security.gatewayKey()
    fun copy(value: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("JH GetWay setup", value))
    }
    val openBatterySettings = {
        context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.str_help_setup_guide_9c83d5)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(section == 0, { section = 0 }, label = { Text(stringResource(R.string.str_tools_4fa8cc)) })
                    FilterChip(section == 1, { section = 1 }, label = { Text(stringResource(R.string.str_battery_4a9be0)) })
                    FilterChip(section == 2, { section = 2 }, label = { Text(stringResource(R.string.str_fix_problems_9a9278)) })
                }
                when (section) {
                    0 -> {
                        Text(stringResource(R.string.str_gateway_address_b839fe), fontWeight = FontWeight.Bold, color = Navy)
                        Text(baseUrl, color = Muted, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.str_gateway_key_is_copied_only_whe_bc5126), color = Muted, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { copy(baseUrl) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.str_copy_api_address_754bd2)) }
                        OutlinedButton(onClick = { copy(key) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.str_copy_gateway_key_824df3)) }
                        Text(stringResource(R.string.str_opencode_claude_code_codex_ad34bf), fontWeight = FontWeight.Bold, color = Navy)
                        Text(stringResource(R.string.str_set_the_tool_base_url_to_the_a_e5e822), color = Muted, style = MaterialTheme.typography.bodySmall)
                        val termux = "export OPENAI_BASE_URL=$baseUrl\nexport OPENAI_API_KEY=$key"
                        OutlinedButton(onClick = { copy(termux) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.str_copy_termux_setup_7f2c18)) }
                        Text(stringResource(R.string.str_curl_test_603d5a), fontWeight = FontWeight.Bold, color = Navy)
                        Text("curl $baseUrl/models -H \"Authorization: Bearer $key\"", color = Muted, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { copy("curl $baseUrl/models -H \"Authorization: Bearer $key\"") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.str_copy_curl_test_0640b7)) }
                    }
                    1 -> {
                        Text(stringResource(R.string.str_keep_the_gateway_alive_227fb1), fontWeight = FontWeight.Bold, color = Navy)
                        Text(stringResource(R.string.str_help_battery_advice_90403d), color = Muted, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = openBatterySettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.str_open_battery_settings_2c45a5)) }
                        Text(stringResource(R.string.str_the_gateway_uses_a_foreground_38112d), color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        Text(stringResource(R.string.str_common_fixes_986ece), fontWeight = FontWeight.Bold, color = Navy)
                        Text(stringResource(R.string.str_help_troubleshooting_3a4b97), color = Muted, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.str_privacy_prompts_responses_and_380f83), color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.str_done_e9b450)) } }
    )
}

@Composable
private fun BackupDialog(providerStore: ProviderStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val backupManager = remember { BackupManager() }
    var password by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<ByteArray?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var exportMode by remember { mutableStateOf(true) }
    var replaceMode by remember { mutableStateOf(false) }
    val backupIntro = stringResource(R.string.str_backup_intro_bbb80c)
    var status by remember { mutableStateOf(backupIntro) }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) { status = context.getString(R.string.str_backup_export_cancelled_2ab62e); return@rememberLauncherForActivityResult }
        runCatching { context.contentResolver.openOutputStream(uri)?.use { output -> output.write(backupManager.export(providerStore, password.toCharArray())) } ?: error(context.getString(R.string.str_backup_cannot_open_274dea)) }.onSuccess { status = context.getString(R.string.str_backup_exported_e3eeae) }.onFailure { status = context.getString(R.string.str_backup_export_failed_reason_670b95) }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) { status = context.getString(R.string.str_backup_import_cancelled_a88478); return@rememberLauncherForActivityResult }
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error(context.getString(R.string.str_backup_cannot_read_c4a17d)) }.onSuccess { pendingImport = it; exportMode = false; showPassword = true }.onFailure { status = context.getString(R.string.str_backup_import_failed_reason_6eaaef) }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.str_backup_restore_a16162)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(status, color = Muted, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { exportMode = true; showPassword = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.str_export_encrypted_backup_86ea57)) }
            OutlinedButton(onClick = { openFile.launch(arrayOf("application/octet-stream", "application/octet-stream+zip", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.str_import_backup_1b40a3)) }
            if (pendingImport != null) Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.str_replace_existing_providers_8a480f), Modifier.weight(1f)); Switch(replaceMode, { replaceMode = it }) }
        }
    }, confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.str_done_e9b450)) } })
    if (showPassword) AlertDialog(onDismissRequest = { showPassword = false }, title = { Text(if (exportMode) stringResource(R.string.str_set_backup_password_8bcec6) else stringResource(R.string.str_backup_password_d96a60)) }, text = { OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.str_password_10_characters_45ec44)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }, confirmButton = { Button(enabled = password.length >= 10, onClick = {
        if (exportMode) { showPassword = false; createFile.launch("jh-getway-backup.aigw") }
        else { runCatching { backupManager.import(providerStore, pendingImport ?: error("No backup selected"), password.toCharArray(), replaceMode) }.onSuccess { status = context.getString(R.string.str_backup_imported_0a0269) }.onFailure { status = context.getString(R.string.str_backup_import_failed_428856) }; showPassword = false; pendingImport = null }
    }) { Text(stringResource(R.string.str_continue_2e0262)) } }, dismissButton = { OutlinedButton(onClick = { showPassword = false }) { Text(stringResource(R.string.str_cancel_77dfd2)) } })
}

private fun statusMeaning(status: Int): String = when {
    status in 200..299 -> "Success"
    status in 400..499 -> "Client error"
    status >= 500 -> "Server error"
    else -> "Unknown"
}

private fun formatTokens(count: Int): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1e6)
    count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1e3)
    else -> count.toString()
}

private fun buildDiagnostics(context: android.content.Context, stats: GatewayStats): String {
    val settings = GatewaySettings(context)
    val state = GatewayStateStore(context)
    val providers = ProviderStore(context).list()
    val healthStore = ProviderHealthStore(context)
    val logs = stats.recent()
    val success = logs.count { it.status in 200..299 }
    val average = if (logs.isEmpty()) 0 else logs.map { it.latencyMs }.average().toInt()
    return buildString {
        appendLine("JH GetWay diagnostics — v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE}) · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Gateway: ${state.state}${if (state.state == GatewayRunState.ERROR && state.error.isNotBlank()) " (${state.error})" else ""} · port ${settings.port} · Wi-Fi ${if (settings.wifiMode) "on" else "off"}")
        appendLine("Providers (${providers.size}):")
        providers.forEach { provider ->
            val health = healthStore.get(provider.id)
            val latency = health?.takeIf { it.lastLatencyMs > 0 }?.let { " · ${it.lastLatencyMs}ms" }.orEmpty()
            val error = health?.lastError?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            appendLine("- ${provider.name}: ${health?.status ?: "no data"}$latency$error")
        }
        appendLine("Recent requests: ${logs.size} logged · $success succeeded · ${logs.size - success} failed · avg ${average}ms")
        logs.take(10).forEach { log ->
            appendLine("${SimpleDateFormat("MMM d HH:mm:ss", Locale.US).format(Date(log.time))} ${log.endpoint} ${log.status} ${log.latencyMs}ms ${log.provider ?: "-"} ${log.model.ifBlank { "-" }}")
        }
    }.trim()
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Navy, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(value, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MonitoringDialog(stats: GatewayStats, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var section by remember { mutableIntStateOf(0) } // 0 Logs, 1 Usage, 2 Diagnostics
    var logs by remember { mutableStateOf(stats.recent()) }
    var logFilter by remember { mutableIntStateOf(0) } // 0 All, 1 Errors
    var expandedTime by remember { mutableStateOf<Long?>(null) }
    val filteredLogs = if (logFilter == 0) logs else logs.filter { it.status !in 200..299 }
    val total = logs.size
    val success = logs.count { it.status in 200..299 }
    val errors = total - success
    val successRate = if (total == 0) 0 else success * 100 / total
    val average = if (logs.isEmpty()) 0 else logs.map { it.latencyMs }.average().toInt()
    val usageEntries = stats.usageEntries()
    val tokensByProvider = usageEntries.groupBy { it.provider.ifBlank { "unknown" } }.map { (name, entries) -> name to entries.sumOf { it.promptTokens + it.completionTokens } }.sortedByDescending { it.second }.take(5)
    val tokensByModel = usageEntries.groupBy { it.model.ifBlank { "unknown" } }.map { (name, entries) -> name to entries.sumOf { it.promptTokens + it.completionTokens } }.sortedByDescending { it.second }.take(5)
    val requestsByProvider = logs.groupBy { it.provider ?: stringResource(R.string.str_chain_no_provider_532e25) }.map { (name, entries) -> name to entries.size }.sortedByDescending { it.second }.take(5)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (section) { 0 -> stringResource(R.string.str_request_logs_f264ea); 1 -> stringResource(R.string.str_usage_0bb186); else -> stringResource(R.string.str_diagnostics_3af227) }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(section == 0, { section = 0 }, label = { Text(stringResource(R.string.str_logs_126dd3)) })
                    FilterChip(section == 1, { section = 1 }, label = { Text(stringResource(R.string.str_usage_0bb186)) })
                    FilterChip(section == 2, { section = 2 }, label = { Text(stringResource(R.string.str_diagnostics_3af227)) })
                }
                when (section) {
                    0 -> {
                        if (logs.isEmpty()) {
                            Text(stringResource(R.string.str_no_requests_yet_start_the_gate_763d4b), color = Muted)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(logFilter == 0, { logFilter = 0 }, label = { Text(stringResource(R.string.str_all_6a7208) + "  $total") })
                                FilterChip(logFilter == 1, { logFilter = 1 }, label = { Text(stringResource(R.string.str_errors_805e86) + "  $errors") })
                            }
                            if (filteredLogs.isEmpty()) {
                                Text(stringResource(R.string.str_no_errors_logged_33e848), color = Muted)
                            } else {
                                LazyColumn(Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(filteredLogs) { log ->
                                        val expanded = expandedTime == log.time
                                        Card(onClick = { expandedTime = if (expanded) null else log.time }, colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F6FA))) {
                                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row { Text(log.provider ?: stringResource(R.string.str_chain_no_provider_532e25), fontWeight = FontWeight.SemiBold, color = Navy); Spacer(Modifier.weight(1f)); Text("HTTP ${log.status}", color = if (log.status in 200..299) Green else Color(0xFFB91C1C), style = MaterialTheme.typography.labelSmall) }
                                                Text("${log.endpoint} · ${log.latencyMs}ms", color = Muted, style = MaterialTheme.typography.bodySmall)
                                                Text(SimpleDateFormat("MMM d, HH:mm:ss", Locale.US).format(Date(log.time)), color = Muted, style = MaterialTheme.typography.labelSmall)
                                                if (expanded) {
                                                    DetailRow(stringResource(R.string.str_detail_status_9729f9), "HTTP ${log.status} — ${statusMeaning(log.status)}")
                                                    DetailRow(stringResource(R.string.str_detail_latency_b89e54), "${log.latencyMs}ms")
                                                    DetailRow(stringResource(R.string.str_detail_model_08f524), log.model.ifBlank { stringResource(R.string.str_log_not_recorded_0cebf9) })
                                                    DetailRow(stringResource(R.string.str_detail_provider_d2cc2e), log.provider ?: stringResource(R.string.str_log_none_509e55))
                                                    Text(stringResource(R.string.str_tap_again_to_collapse_a7bca0), color = Muted, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard(stringResource(R.string.str_total_requests_all_time_fda1db), stats.requestCount().toString(), Icons.Default.Sync, Modifier.fillMaxWidth())
                            MetricCard(stringResource(R.string.str_successful_d7932a), success.toString(), Icons.Default.CheckCircle, Modifier.fillMaxWidth())
                            MetricCard(stringResource(R.string.str_errors_805e86), errors.toString(), Icons.Default.Warning, Modifier.fillMaxWidth())
                            Text(stringResource(R.string.str_usage_recent_success_c413b9), color = Muted)
                            Text(stringResource(R.string.str_usage_avg_latency_0d53b4), color = Muted)
                            Text(stringResource(R.string.str_usage_tokens_619e48), color = Muted)
                            Text(stringResource(R.string.str_requests_by_provider_bf849a), fontWeight = FontWeight.SemiBold, color = Navy)
                            if (requestsByProvider.isEmpty()) Text(stringResource(R.string.str_no_provider_data_yet_4cb97e), color = Muted, style = MaterialTheme.typography.bodySmall)
                            else requestsByProvider.forEach { (name, count) -> BreakdownRow(name, count.toString()) }
                            Text(stringResource(R.string.str_tokens_by_provider_4ad0bb), fontWeight = FontWeight.SemiBold, color = Navy)
                            if (tokensByProvider.isEmpty()) Text(stringResource(R.string.str_no_usage_recorded_yet_e61023), color = Muted, style = MaterialTheme.typography.bodySmall)
                            else tokensByProvider.forEach { (name, count) -> BreakdownRow(name, formatTokens(count)) }
                            Text(stringResource(R.string.str_tokens_by_model_92019a), fontWeight = FontWeight.SemiBold, color = Navy)
                            if (tokensByModel.isEmpty()) Text(stringResource(R.string.str_no_usage_recorded_yet_e61023), color = Muted, style = MaterialTheme.typography.bodySmall)
                            else tokensByModel.forEach { (name, count) -> BreakdownRow(name, formatTokens(count)) }
                        }
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.str_a_redacted_report_for_bug_repo_ce7dd4), color = Muted, style = MaterialTheme.typography.bodySmall)
                            val report = remember { buildDiagnostics(context, stats) }
                            Text(report, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
                            OutlinedButton(onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard.setPrimaryClip(ClipData.newPlainText("JH GetWay diagnostics", buildDiagnostics(context, stats)))
                                android.widget.Toast.makeText(context, context.getString(R.string.str_diagnostics_copied_e55f8a), android.widget.Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ContentCopy, null, tint = Blue); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.str_copy_diagnostics_973495)) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.str_done_e9b450)) } },
        dismissButton = { TextButton(onClick = { stats.clear(); logs = emptyList(); expandedTime = null; section = 0 }) { Text(stringResource(R.string.str_clear_logs_532ffc)) } }
    )
}

@Composable
private fun SettingsDialog(settings: GatewaySettings, gatewaySecurity: GatewaySecurity, onSecurityChanged: () -> Unit, onAppearanceChanged: () -> Unit, onServerSettingsChanged: () -> Unit = {}, onRegenerateKey: () -> Unit = {}, onDismiss: () -> Unit) {
    var wifi by remember { mutableStateOf(settings.wifiMode) }
    var boot by remember { mutableStateOf(settings.startOnBoot) }
    var appLock by remember { mutableStateOf(settings.appLock) }
    var screenshotBlock by remember { mutableStateOf(settings.screenshotBlock) }
    var theme by remember { mutableStateOf(settings.theme) }
    var routingStrategy by remember { mutableStateOf(settings.routingStrategy) }
    var stickySession by remember { mutableStateOf(settings.stickySession) }
    var stickyTtl by remember { mutableStateOf(settings.stickyTtlSeconds.toString()) }
    var ipAllowlist by remember { mutableStateOf(settings.ipAllowlist) }
    var maxConcurrent by remember { mutableStateOf(settings.maxConcurrent.toString()) }
    var port by remember { mutableStateOf(settings.port.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.str_gateway_settings_81a3d1)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.str_changes_apply_the_next_time_th_3189c0), color = Muted, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.str_network_53ebc5), fontWeight = FontWeight.SemiBold, color = Navy)
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(stringResource(R.string.str_wi_fi_open_630746), fontWeight = FontWeight.SemiBold); Text(stringResource(R.string.str_allow_devices_on_the_same_wi_f_249f94), color = Muted, style = MaterialTheme.typography.bodySmall) }; Switch(wifi, { wifi = it }) }
            if (wifi) Text(stringResource(R.string.str_security_warning_wi_fi_mode_ex_20b29a), color = Color(0xFFB91C1C), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.str_port_fe0351)) }, singleLine = true)
            OutlinedTextField(maxConcurrent, { maxConcurrent = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.str_max_concurrent_requests_1_32_64038e)) }, singleLine = true)
            if (wifi) OutlinedTextField(ipAllowlist, { ipAllowlist = it }, label = { Text(stringResource(R.string.str_allowed_ips_comma_separated_bl_e8c56b)) }, supportingText = { Text(stringResource(R.string.str_example_192_168_1_10_192_168_1_a4fdd4)) }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(stringResource(R.string.str_start_on_boot_9a3dcc), fontWeight = FontWeight.SemiBold); Text(stringResource(R.string.str_start_gateway_after_phone_rebo_f57e2a), color = Muted, style = MaterialTheme.typography.bodySmall) }; Switch(boot, { boot = it }) }
            Text(stringResource(R.string.str_routing_7d15dd), fontWeight = FontWeight.SemiBold, color = Navy)
            AgentSetupSection(settings, gatewaySecurity)
            OutlinedButton(onClick = onRegenerateKey, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.str_regenerate_gateway_key_6b2f84))
            }
            Text(stringResource(R.string.str_routing_strategy_4a23ad), color = Muted, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(routingStrategy == "PRIORITY", { routingStrategy = "PRIORITY" }, label = { Text(stringResource(R.string.str_priority_886cbf)) })
                FilterChip(routingStrategy == "ROUND_ROBIN", { routingStrategy = "ROUND_ROBIN" }, label = { Text(stringResource(R.string.str_round_robin_b15b04)) })
                FilterChip(routingStrategy == "LEAST_LATENCY", { routingStrategy = "LEAST_LATENCY" }, label = { Text(stringResource(R.string.str_least_latency_b0c0de)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(stringResource(R.string.str_sticky_session_d3c1f7)); Text(stringResource(R.string.str_keep_a_conversation_on_the_sam_b727ee), color = Muted, style = MaterialTheme.typography.bodySmall) }; Switch(stickySession, { stickySession = it }) }
            if (stickySession) OutlinedTextField(stickyTtl, { stickyTtl = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.str_sticky_ttl_seconds_44316e)) }, singleLine = true)
            Text(stringResource(R.string.str_appearance_41def7), fontWeight = FontWeight.SemiBold, color = Navy)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(theme == "LIGHT", { theme = "LIGHT" }, label = { Text(stringResource(R.string.str_light_a36ef8)) })
                FilterChip(theme == "DARK", { theme = "DARK" }, label = { Text(stringResource(R.string.str_dark_ae1ef0)) })
                FilterChip(theme == "SYSTEM", { theme = "SYSTEM" }, label = { Text(stringResource(R.string.str_system_bc0792)) })
            }
            Text(stringResource(R.string.str_security_privacy_27f69f), fontWeight = FontWeight.SemiBold, color = Navy)
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(stringResource(R.string.str_app_lock_a34fa2), fontWeight = FontWeight.SemiBold); Text(stringResource(R.string.str_require_phone_pin_or_biometric_eb3ca5), color = Muted, style = MaterialTheme.typography.bodySmall) }; Switch(appLock, { appLock = it }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(stringResource(R.string.str_block_screenshots_6223b2), fontWeight = FontWeight.SemiBold); Text(stringResource(R.string.str_hide_app_content_from_screensh_cf2805), color = Muted, style = MaterialTheme.typography.bodySmall) }; Switch(screenshotBlock, { screenshotBlock = it }) }
        }
    }, confirmButton = { Button(onClick = { settings.wifiMode = wifi; settings.startOnBoot = boot; settings.appLock = appLock; settings.screenshotBlock = screenshotBlock; settings.theme = theme; settings.routingStrategy = routingStrategy; settings.stickySession = stickySession; settings.ipAllowlist = ipAllowlist; maxConcurrent.toIntOrNull()?.let { settings.maxConcurrent = it }; stickyTtl.toIntOrNull()?.let { settings.stickyTtlSeconds = it }; settings.lastUnlock = System.currentTimeMillis(); port.toIntOrNull()?.let { settings.port = it }; onSecurityChanged(); onAppearanceChanged(); onServerSettingsChanged(); onDismiss() }) { Text(stringResource(R.string.str_save_efc007)) } }, dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.str_cancel_77dfd2)) } })
}

@Composable
private fun ChainDialog(providerStore: ProviderStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val chainStore = remember { ChainStore(context) }
    var entries by remember { mutableStateOf(chainStore.load(providerStore.list())) }
    fun persist(next: List<ChainEntry>) { entries = next; chainStore.save(next) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.str_routing_chain_2a695d)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.str_requests_using_auto_start_from_ec5b5c), color = Muted, style = MaterialTheme.typography.bodySmall)
                ChainTier.values().forEach { tier ->
                    Text(tier.name, fontWeight = FontWeight.Bold, color = Navy)
                    val tierEntries = entries.filter { it.tier == tier }
                    if (tierEntries.isEmpty()) Text(stringResource(R.string.str_no_models_in_this_tier_ee5987), color = Muted, style = MaterialTheme.typography.labelSmall)
                    tierEntries.forEach { entry ->
                        val index = entries.indexOf(entry)
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F6FA))) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(entry.provider, fontWeight = FontWeight.SemiBold, color = Navy); Text(entry.model, color = Muted, style = MaterialTheme.typography.labelSmall) }
                                IconButton(onClick = { if (index > 0) { val next = entries.toMutableList(); val previous = next.removeAt(index - 1); next.add(index, previous); persist(next) } }) { Text(stringResource(R.string.str_x_9555ff), color = Blue) }
                                IconButton(onClick = { if (index < entries.lastIndex) { val next = entries.toMutableList(); val following = next.removeAt(index + 1); next.add(index, following); persist(next) } }) { Text(stringResource(R.string.str_x_fc4866), color = Blue) }
                                TextButton(onClick = { val nextTier = when (entry.tier) { ChainTier.BEST -> ChainTier.GOOD; ChainTier.GOOD -> ChainTier.BASIC; ChainTier.BASIC -> ChainTier.BEST }; persist(entries.map { if (it.id == entry.id) it.copy(tier = nextTier) else it }) }) { Text(stringResource(R.string.str_tier_5bd44e)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.str_done_e9b450)) } }
    )
}

@Composable
private fun UpdateDialog(settings: GatewaySettings, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val manager = remember { UpdateManager(context) }
    var update by remember { mutableStateOf(manager.cachedUpdate()) }
    var downloadedFile by remember { mutableStateOf(manager.existingApk()) }
    var downloading by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    val updateHint = if (downloadedFile != null) stringResource(R.string.str_update_ready_f0ecb9) else stringResource(R.string.str_update_tap_check_571564)
    var status by remember { mutableStateOf(updateHint) }
    val channel = if (settings.updateChannel == "BETA") UpdateManager.Channel.BETA else UpdateManager.Channel.STABLE
    fun checkLatest() {
        if (checking || downloading) return
        checking = true
        status = context.getString(R.string.str_update_checking_6c1e84)
        manager.check(channel) { result -> activity?.runOnUiThread {
            checking = false
            result.onSuccess { found ->
                update = found ?: manager.cachedUpdate()
                status = when {
                    found != null -> context.getString(R.string.str_update_found_2a7b65)
                    update != null -> context.getString(R.string.str_update_saved_available_5d1c93)
                    else -> context.getString(R.string.str_update_up_to_date_9e4b07)
                }
            }.onFailure { error ->
                val saved = manager.cachedUpdate()
                update = saved
                status = if (saved != null) {
                    context.getString(R.string.str_update_check_failed_saved_4c8e21)
                } else {
                    context.getString(R.string.str_update_check_failed_7a3b52, error.message ?: "network error")
                }
            }
        } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.str_app_updates_16213f)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.str_update_current_version_9ae4a9), color = Muted, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.str_update_channel_3e7b95), style = MaterialTheme.typography.labelSmall, color = Muted)
                FilterChip(settings.updateChannel == "STABLE", {
                    settings.updateChannel = "STABLE"
                }, label = { Text(stringResource(R.string.str_update_channel_stable_5a1c48)) })
                FilterChip(settings.updateChannel == "BETA", {
                    settings.updateChannel = "BETA"
                }, label = { Text(stringResource(R.string.str_update_channel_beta_9f3d27)) })
            }

                Text(status, color = Muted)
                if (downloading) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                if (downloading) Text(stringResource(R.string.str_update_progress_saved_8cea40), color = Muted, style = MaterialTheme.typography.labelSmall)
                if ((update != null || downloadedFile != null) && !downloading) TextButton(enabled = !checking, onClick = ::checkLatest) { Text(if (checking) stringResource(R.string.str_checking_820d60) else stringResource(R.string.str_check_again_e18504)) }
                update?.let { available ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F6FA))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.str_update_new_version_736c0b), fontWeight = FontWeight.Bold, color = Navy)
                            Text(available.notes, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { manager.openReleasePage(available) }) { Text(stringResource(R.string.str_open_github_release_page_61878c)) }
                }
            }
        },
        confirmButton = {
            when {
                downloadedFile != null -> Button(onClick = { manager.install(downloadedFile!!) { status = it } }) { Text(stringResource(R.string.str_install_update_8a3695)) }
                downloading -> Button(enabled = false, onClick = {}) { Text(stringResource(R.string.str_update_downloading_b5c780)) }
                update != null -> Button(onClick = {
                    downloading = true; progress = 0; status = "Downloading inside JH GetWay…"
                    manager.download(update!!, { value -> activity?.runOnUiThread { progress = value } }) { result -> activity?.runOnUiThread { downloading = false; result.onSuccess { downloadedFile = it; progress = 100; status = "Download complete. Tap Install update." }.onFailure { status = "Download failed: ${it.message}" } } }
                }) { Text(stringResource(R.string.str_download_update_870d57)) }
                else -> Button(enabled = !checking, onClick = ::checkLatest) { Text(if (checking) stringResource(R.string.str_checking_820d60) else stringResource(R.string.str_check_for_updates_736b90)) }
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.str_close_bbfa77)) } }
    )
}

@Composable
private fun MoreItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit = {}) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6EAF0))) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Blue, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(14.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold, color = Navy); Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Text(stringResource(R.string.str_x_60ecbc), style = MaterialTheme.typography.headlineSmall, color = Muted) } }
}

@Composable
private fun LogRow(log: RequestLog) {
    val ok = log.status in 200..299
    val color = if (ok) Green else Color(0xFFB91C1C)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(log.provider ?: stringResource(R.string.str_chain_no_provider_532e25), color = Navy, fontWeight = FontWeight.Medium); Text("${log.endpoint} · ${log.latencyMs}ms${if (log.model.isNotBlank()) " · ${log.model}" else ""}", color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1) }; Text(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.time)), color = Muted, style = MaterialTheme.typography.labelSmall) }
}
