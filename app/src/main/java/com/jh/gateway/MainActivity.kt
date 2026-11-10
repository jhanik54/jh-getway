package com.jh.gateway

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GatewayApp(onRequestNotifications = { requestNotificationPermission() }) }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        }
    }
}

private val Navy = Color(0xFF111827)
private val Blue = Color(0xFF4F67E8)
private val Green = Color(0xFF16A36A)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GatewayApp(onRequestNotifications: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    val providerStore = remember { ProviderStore(context) }
    val gatewaySecurity = remember { GatewaySecurity(context) }
    var keys by remember { mutableStateOf(providerStore.list().map { it.toProviderKey(providerStore) }) }
    var showAddDialog by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showChainDialog by remember { mutableStateOf(false) }
    var showMonitoringDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val gatewaySettings = remember { GatewaySettings(context) }
    val tabs = listOf("Home", "Providers", "More")

    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(primary = Blue)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("JH GetWay", fontWeight = FontWeight.Bold)
                            Text("Personal AI Gateway", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                    },
                    actions = {
                        IconButton(onClick = { }) { Icon(Icons.Default.Security, "Security") }
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
                0 -> HomeScreen(Modifier.padding(padding), running, gatewaySecurity.maskedKey(), onCopyKey = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("JH GetWay key", gatewaySecurity.gatewayKey()))
                }, onToggle = {
                    running = !running
                    onRequestNotifications()
                    val intent = Intent(context, GatewayService::class.java).apply {
                        if (!running) action = GatewayService.ACTION_STOP
                    }
                    if (running) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
                })
                1 -> ProvidersScreen(Modifier.padding(padding), keys, onAdd = { showAddDialog = true })
                else -> MoreScreen(Modifier.padding(padding), updateMessage, onSettings = { showSettings = true }, onChain = { showChainDialog = true }, onMonitoring = { showMonitoringDialog = true }, onBackup = { showBackupDialog = true }, onHelp = { showHelpDialog = true }, onUpdate = { showUpdateDialog = true })
            }
        }
        if (showAddDialog) {
            AddProviderDialog(
                onDismiss = { showAddDialog = false },
                onSave = { name, url, model, key, protocol ->
                    providerStore.add(name, url, model, key, protocol)
                    keys = providerStore.list().map { it.toProviderKey(providerStore) }
                    showAddDialog = false
                }
            )
        }
        if (showSettings) SettingsDialog(gatewaySettings, onDismiss = { showSettings = false })
        if (showUpdateDialog) UpdateDialog(onDismiss = { showUpdateDialog = false })
        if (showChainDialog) ChainDialog(providerStore, onDismiss = { showChainDialog = false })
        if (showMonitoringDialog) MonitoringDialog(GatewayStats(context), onDismiss = { showMonitoringDialog = false })
        if (showBackupDialog) BackupDialog(providerStore, onDismiss = { showBackupDialog = false })
        if (showHelpDialog) HelpDialog(gatewaySettings, gatewaySecurity, onDismiss = { showHelpDialog = false })
    }
}

private fun StoredProvider.toProviderKey(store: ProviderStore) = ProviderKey(
    name, baseUrl.removePrefix("https://").removePrefix("http://"), model,
    when { !enabled -> KeyStatus.DISABLED; isCoolingDown -> KeyStatus.COOLDOWN; else -> KeyStatus.ACTIVE },
    priority, store.apiKey(this)?.let { "${it.take(4)}••••••${it.takeLast(4)}" } ?: "••••••••"
)

@Composable
private fun AddProviderDialog(onDismiss: () -> Unit, onSave: (String, String, String, String, ProviderProtocol) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://api.openai.com") }
    var model by remember { mutableStateOf("auto") }
    var key by remember { mutableStateOf("") }
    var showPresets by remember { mutableStateOf(false) }
    var protocol by remember { mutableStateOf(ProviderProtocol.OPENAI) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var fetching by remember { mutableStateOf(false) }
    var fetchMessage by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add provider key") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose a provider preset or enter a custom endpoint", color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { showPresets = true }, modifier = Modifier.fillMaxWidth()) { Text(if (name.isBlank()) "Choose provider preset" else name) }
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            OutlinedTextField(url, { url = it }, label = { Text("Base URL") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(model, { model = it }, label = { Text("Default model") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                OutlinedButton(enabled = !fetching && url.isNotBlank() && key.isNotBlank(), onClick = {
                    fetching = true; fetchMessage = "Fetching models…"
                    val activity = context as? ComponentActivity
                    ProviderModelFetcher.fetch(url, key, protocol) { result -> activity?.runOnUiThread { fetching = false; result.onSuccess { fetched -> models = fetched; fetchMessage = "${fetched.size} models found" }.onFailure { fetchMessage = it.message ?: "Model fetch failed" } } }
                }) { Text(if (fetching) "…" else "Fetch") }
            }
            if (fetchMessage.isNotBlank()) Text(fetchMessage, color = if (models.isNotEmpty()) Green else Muted, style = MaterialTheme.typography.labelSmall)
            if (models.isNotEmpty()) {
                Text("Select a model", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                Column(Modifier.height(120.dp)) { models.take(12).forEach { item -> FilterChip(selected = model == item, onClick = { model = item }, label = { Text(item, maxLines = 1) }) } }
            }
            OutlinedTextField(key, { key = it }, label = { Text("API key") }, singleLine = true)
        } },
        confirmButton = { Button(enabled = name.isNotBlank() && url.isNotBlank() && key.isNotBlank(), onClick = { onSave(name, url, model, key, protocol) }) { Text("Save securely") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (showPresets) {
        AlertDialog(
            onDismissRequest = { showPresets = false },
            title = { Text("Provider presets") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { providerPresets.forEach { preset -> TextButton(onClick = { name = preset.name; url = preset.url; model = preset.model; protocol = preset.protocol; showPresets = false }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) { Text(preset.name, fontWeight = FontWeight.SemiBold); Text(preset.url, color = Muted, style = MaterialTheme.typography.labelSmall) } } } } },
            confirmButton = { OutlinedButton(onClick = { showPresets = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HomeScreen(modifier: Modifier, running: Boolean, gatewayKey: String, onCopyKey: () -> Unit, onToggle: () -> Unit) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Good morning 👋", style = MaterialTheme.typography.titleMedium, color = Muted)
            Text("Your AI traffic, simplified.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Navy)
            Spacer(Modifier.height(4.dp))
        }
        item { StatusCard(running, onToggle) }
        item { GatewayKeyCard(gatewayKey, onCopyKey) }
        item { MetricRow() }
        item { SectionTitle("Active route", "View chain") }
        item { RouteCard() }
        item { SectionTitle("Recent activity", "View logs") }
        items(demoLogs.take(2)) { LogRow(it) }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun GatewayKeyCard(key: String, onCopy: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7FF)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E2FF))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(38.dp), shape = RoundedCornerShape(11.dp), color = Color(0xFFE9EDFF)) { Icon(Icons.Default.Security, null, tint = Blue, modifier = Modifier.padding(9.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text("Gateway key", fontWeight = FontWeight.SemiBold, color = Navy); Text(key, color = Muted, style = MaterialTheme.typography.bodySmall); Text("Required by OpenCode and other tools", color = Muted, style = MaterialTheme.typography.labelSmall) }
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy gateway key", tint = Blue) }
        }
    }
}

@Composable
private fun StatusCard(running: Boolean, onToggle: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (running) Navy else Color(0xFF29344B)), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (running) Color(0xFF56E39F) else Color(0xFFFFC857)))
                Spacer(Modifier.width(10.dp))
                Text(if (running) "Gateway is running" else "Gateway is paused", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Dns, null, tint = Color.White.copy(alpha = .7f))
            }
            Text(if (running) "Ready to receive requests from your coding tools" else "Start the gateway to connect your coding tools", color = Color.White.copy(alpha = .72f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (running) "127.0.0.1:8787" else "Local only", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Button(onClick = onToggle, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Navy)) {
                    Text(if (running) "Stop" else "Start gateway")
                }
            }
        }
    }
}

@Composable
private fun MetricRow() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("Requests", "0", Icons.Default.Sync, Modifier.weight(1f))
        MetricCard("Keys ready", "2 / 3", Icons.Default.Key, Modifier.weight(1f))
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
private fun SectionTitle(title: String, action: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.weight(1f))
        Text(action, color = Blue, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RouteCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6EAF0))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(42.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFFE9EDFF)) { Icon(Icons.Default.CloudQueue, null, tint = Blue, modifier = Modifier.padding(10.dp)) }
            Spacer(Modifier.width(12.dp))
            Column { Text("Best available", fontWeight = FontWeight.SemiBold, color = Navy); Text("Priority chain · 3 providers", color = Muted, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.CheckCircle, null, tint = Green)
        }
    }
}

@Composable
private fun ProvidersScreen(modifier: Modifier, keys: List<ProviderKey>, onAdd: () -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Providers", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Navy); Text("Manage your AI keys and models", color = Muted) }; Button(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Add key") } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(true, {}, label = { Text("All  3") }); FilterChip(false, {}, label = { Text("Active  2") }); FilterChip(false, {}, label = { Text("Cooldown  1") }) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(keys) { ProviderCard(it) } }
    }
}

@Composable
private fun ProviderCard(key: ProviderKey) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6EAF0))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(38.dp), shape = RoundedCornerShape(10.dp), color = Color(0xFFF0F2FF)) { Icon(Icons.Default.Key, null, tint = Blue, modifier = Modifier.padding(9.dp)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(key.name, fontWeight = FontWeight.SemiBold, color = Navy); Text(key.provider, color = Muted, style = MaterialTheme.typography.bodySmall) }; StatusPill(key.status) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text(key.model, color = Navy, fontWeight = FontWeight.Medium); Spacer(Modifier.weight(1f)); Text("Priority ${key.priority}", color = Muted, style = MaterialTheme.typography.labelSmall) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text(key.maskedKey, color = Muted, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.weight(1f)); IconButton(onClick = {}) { Icon(Icons.Default.ContentCopy, "Copy", tint = Muted) } }
        }
    }
}

@Composable
private fun StatusPill(status: KeyStatus) {
    val active = status == KeyStatus.ACTIVE
    val color = if (active) Green else Color(0xFFE58B18)
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .12f)) { Text(if (active) "Active" else "Cooldown", color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
}

@Composable
private fun MoreScreen(modifier: Modifier, updateMessage: String, onSettings: () -> Unit, onChain: () -> Unit, onMonitoring: () -> Unit, onBackup: () -> Unit, onHelp: () -> Unit, onUpdate: () -> Unit) {
    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp)); Text("More", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Navy); Text("Control how JH GetWay works", color = Muted)
        MoreItem(Icons.Default.Storage, "Routing chain", "Set priority and fallback order", onChain)
        MoreItem(Icons.Default.Refresh, "Request logs", "Review provider switches and errors", onMonitoring)
        MoreItem(Icons.Default.Sync, "Usage", "Requests, status and latency summary", onMonitoring)
        MoreItem(Icons.Default.Settings, "Settings", "Network, appearance and security", onSettings)
        MoreItem(Icons.Default.Security, "Backup & security", "Encrypted export and app lock", onBackup)
        MoreItem(Icons.Default.Refresh, "Help & setup guide", "Battery, tools and troubleshooting", onHelp)
        MoreItem(Icons.Default.SystemUpdate, "App updates", if (updateMessage.isBlank()) "Check GitHub for a newer APK" else updateMessage, onClick = onUpdate)
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
        title = { Text("Help & setup guide") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(section == 0, { section = 0 }, label = { Text("Tools") })
                    FilterChip(section == 1, { section = 1 }, label = { Text("Battery") })
                    FilterChip(section == 2, { section = 2 }, label = { Text("Fix problems") })
                }
                when (section) {
                    0 -> {
                        Text("Gateway address", fontWeight = FontWeight.Bold, color = Navy)
                        Text(baseUrl, color = Muted, style = MaterialTheme.typography.bodySmall)
                        Text("Gateway key is copied only when you tap Copy. Keep it private.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { copy(baseUrl) }, modifier = Modifier.fillMaxWidth()) { Text("Copy API address") }
                        OutlinedButton(onClick = { copy(key) }, modifier = Modifier.fillMaxWidth()) { Text("Copy gateway key") }
                        Text("OpenCode / Claude Code / Codex", fontWeight = FontWeight.Bold, color = Navy)
                        Text("Set the tool base URL to the address above and use the gateway key as its API key. Select model: auto.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        val termux = "export OPENAI_BASE_URL=$baseUrl\nexport OPENAI_API_KEY=$key"
                        OutlinedButton(onClick = { copy(termux) }, modifier = Modifier.fillMaxWidth()) { Text("Copy Termux setup") }
                        Text("curl test", fontWeight = FontWeight.Bold, color = Navy)
                        Text("curl $baseUrl/models -H \"Authorization: Bearer $key\"", color = Muted, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { copy("curl $baseUrl/models -H \"Authorization: Bearer $key\"") }, modifier = Modifier.fillMaxWidth()) { Text("Copy curl test") }
                    }
                    1 -> {
                        Text("Keep the gateway alive", fontWeight = FontWeight.Bold, color = Navy)
                        Text("For $manufacturer and especially Poco/Xiaomi: enable Autostart, set Battery saver to No restrictions, and lock JH GetWay in Recent apps. Then enable Start on boot in Settings.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = openBatterySettings, modifier = Modifier.fillMaxWidth()) { Text("Open battery settings") }
                        Text("The gateway uses a foreground service, but some phone brands still stop background apps unless these settings are changed.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        Text("Common fixes", fontWeight = FontWeight.Bold, color = Navy)
                        Text("• 401: use the gateway key, not the provider key.\n• Connection refused: start the gateway and check the port.\n• Wi-Fi device cannot connect: enable Wi-Fi open and use the phone IP.\n• All providers unavailable: check provider keys and Routing chain.\n• Port busy: choose another port in Settings, then restart the gateway.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        Text("Privacy: prompts, responses and API keys are not included in logs.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
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
    var status by remember { mutableStateOf("Export your encrypted providers or import a backup file.") }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) { status = "Export cancelled"; return@rememberLauncherForActivityResult }
        runCatching { context.contentResolver.openOutputStream(uri)?.use { output -> output.write(backupManager.export(providerStore, password.toCharArray())) } ?: error("Could not open selected file") }.onSuccess { status = "Backup exported successfully" }.onFailure { status = "Export failed: ${it.message}" }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) { status = "Import cancelled"; return@rememberLauncherForActivityResult }
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read selected file") }.onSuccess { pendingImport = it; exportMode = false; showPassword = true }.onFailure { status = "Import failed: ${it.message}" }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Backup & restore") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(status, color = Muted, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { exportMode = true; showPassword = true }, modifier = Modifier.fillMaxWidth()) { Text("Export encrypted backup") }
            OutlinedButton(onClick = { openFile.launch(arrayOf("application/octet-stream", "application/octet-stream+zip", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Import backup") }
            if (pendingImport != null) Row(verticalAlignment = Alignment.CenterVertically) { Text("Replace existing providers", Modifier.weight(1f)); Switch(replaceMode, { replaceMode = it }) }
        }
    }, confirmButton = { Button(onClick = onDismiss) { Text("Done") } })
    if (showPassword) AlertDialog(onDismissRequest = { showPassword = false }, title = { Text(if (exportMode) "Set backup password" else "Backup password") }, text = { OutlinedTextField(password, { password = it }, label = { Text("Password (10+ characters)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }, confirmButton = { Button(enabled = password.length >= 10, onClick = {
        if (exportMode) { showPassword = false; createFile.launch("jh-getway-backup.aigw") }
        else { runCatching { backupManager.import(providerStore, pendingImport ?: error("No backup selected"), password.toCharArray(), replaceMode) }.onSuccess { status = "Backup imported successfully" }.onFailure { status = "Import failed: wrong password or damaged file" }; showPassword = false; pendingImport = null }
    }) { Text("Continue") } }, dismissButton = { OutlinedButton(onClick = { showPassword = false }) { Text("Cancel") } })
}

@Composable
private fun MonitoringDialog(stats: GatewayStats, onDismiss: () -> Unit) {
    var section by remember { mutableIntStateOf(0) }
    var logs by remember { mutableStateOf(stats.recent()) }
    val total = logs.size
    val success = logs.count { it.status in 200..299 }
    val average = if (logs.isEmpty()) 0 else logs.map { it.latencyMs }.average().toInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (section == 0) "Request logs" else "Usage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(section == 0, { section = 0 }, label = { Text("Logs") }); FilterChip(section == 1, { section = 1 }, label = { Text("Usage") }) }
                if (section == 0) {
                    if (logs.isEmpty()) Text("No requests yet. Start the gateway and connect a coding tool.", color = Muted)
                    else LazyColumn(Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(logs) { log ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F6FA))) { Column(Modifier.fillMaxWidth().padding(10.dp)) { Row { Text(log.provider ?: "No provider", fontWeight = FontWeight.SemiBold, color = Navy); Spacer(Modifier.weight(1f)); Text("HTTP ${log.status}", color = if (log.status in 200..299) Green else Color(0xFFD64545), style = MaterialTheme.typography.labelSmall) }; Text("${log.endpoint} · ${log.latencyMs}ms", color = Muted, style = MaterialTheme.typography.bodySmall); Text(SimpleDateFormat("MMM d, HH:mm:ss", Locale.US).format(Date(log.time)), color = Muted, style = MaterialTheme.typography.labelSmall) } }
                    } }
                } else {
                    MetricCard("Total requests", total.toString(), Icons.Default.Sync, Modifier.fillMaxWidth())
                    MetricCard("Successful", success.toString(), Icons.Default.CheckCircle, Modifier.fillMaxWidth())
                    MetricCard("Errors", (total - success).coerceAtLeast(0).toString(), Icons.Default.Warning, Modifier.fillMaxWidth())
                    Text("Average latency: ${average}ms", color = Muted)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = { stats.clear(); logs = emptyList() }) { Text("Clear logs") } }
    )
}

@Composable
private fun SettingsDialog(settings: GatewaySettings, onDismiss: () -> Unit) {
    var wifi by remember { mutableStateOf(settings.wifiMode) }
    var boot by remember { mutableStateOf(settings.startOnBoot) }
    var port by remember { mutableStateOf(settings.port.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Gateway settings") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Changes apply the next time the gateway starts.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Wi-Fi open", fontWeight = FontWeight.SemiBold); Text("Allow devices on the same Wi-Fi", color = Muted, style = MaterialTheme.typography.bodySmall) }; Switch(wifi, { wifi = it }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Start on boot", fontWeight = FontWeight.SemiBold); Text("Start gateway after phone reboot", color = Muted, style = MaterialTheme.typography.bodySmall) }; Switch(boot, { boot = it }) }
            OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true)
        }
    }, confirmButton = { Button(onClick = { settings.wifiMode = wifi; settings.startOnBoot = boot; port.toIntOrNull()?.let { settings.port = it }; onDismiss() }) { Text("Save") } }, dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ChainDialog(providerStore: ProviderStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val chainStore = remember { ChainStore(context) }
    var entries by remember { mutableStateOf(chainStore.load(providerStore.list())) }
    fun persist(next: List<ChainEntry>) { entries = next; chainStore.save(next) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Routing chain") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Requests using auto start from Best, then Good, then Basic.", color = Muted, style = MaterialTheme.typography.bodySmall)
                ChainTier.values().forEach { tier ->
                    Text(tier.name, fontWeight = FontWeight.Bold, color = Navy)
                    val tierEntries = entries.filter { it.tier == tier }
                    if (tierEntries.isEmpty()) Text("No models in this tier", color = Muted, style = MaterialTheme.typography.labelSmall)
                    tierEntries.forEach { entry ->
                        val index = entries.indexOf(entry)
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F6FA))) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(entry.provider, fontWeight = FontWeight.SemiBold, color = Navy); Text(entry.model, color = Muted, style = MaterialTheme.typography.labelSmall) }
                                IconButton(onClick = { if (index > 0) { val next = entries.toMutableList(); val previous = next.removeAt(index - 1); next.add(index, previous); persist(next) } }) { Text("↑", color = Blue) }
                                IconButton(onClick = { if (index < entries.lastIndex) { val next = entries.toMutableList(); val following = next.removeAt(index + 1); next.add(index, following); persist(next) } }) { Text("↓", color = Blue) }
                                TextButton(onClick = { val nextTier = when (entry.tier) { ChainTier.BEST -> ChainTier.GOOD; ChainTier.GOOD -> ChainTier.BASIC; ChainTier.BASIC -> ChainTier.BEST }; persist(entries.map { if (it.id == entry.id) it.copy(tier = nextTier) else it }) }) { Text("Tier") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val manager = remember { UpdateManager(context) }
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Tap Check now to look for a new release.") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App updates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current version ${BuildConfig.VERSION_NAME}", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(status, color = Muted)
                if (downloading) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                if (downloading) Text("$progress% · Saved inside JH GetWay", color = Muted, style = MaterialTheme.typography.labelSmall)
                update?.let { available ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F6FA))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("New version ${available.versionName}", fontWeight = FontWeight.Bold, color = Navy)
                            Text(available.notes, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { manager.openReleasePage(available) }) { Text("Open GitHub release page") }
                }
            }
        },
        confirmButton = {
            when {
                downloadedFile != null -> Button(onClick = { manager.install(downloadedFile!!) { status = it } }) { Text("Install update") }
                downloading -> Button(enabled = false, onClick = {}) { Text("Downloading $progress%") }
                update != null -> Button(onClick = {
                    downloading = true; progress = 0; status = "Downloading inside JH GetWay…"
                    manager.download(update!!, { value -> activity?.runOnUiThread { progress = value } }) { result -> activity?.runOnUiThread { downloading = false; result.onSuccess { downloadedFile = it; progress = 100; status = "Download complete. Tap Install update." }.onFailure { status = "Download failed: ${it.message}" } } }
                }) { Text("Download update") }
                else -> Button(onClick = {
                    status = "Checking GitHub releases…"
                    manager.check { result -> activity?.runOnUiThread { result.onSuccess { found -> update = found; status = if (found == null) "You are up to date." else "New update found." }.onFailure { status = "Check failed: ${it.message}" } } }
                }) { Text("Check now") }
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun MoreItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit = {}) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6EAF0))) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Blue, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(14.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold, color = Navy); Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Text("›", style = MaterialTheme.typography.headlineSmall, color = Muted) } }
}

@Composable
private fun LogRow(log: GatewayLog) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(Green)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(log.message, color = Navy, fontWeight = FontWeight.Medium); Text(log.detail, color = Muted, style = MaterialTheme.typography.bodySmall) }; Text(log.time, color = Muted, style = MaterialTheme.typography.labelSmall) }
}
