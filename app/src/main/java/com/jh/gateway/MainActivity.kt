package com.jh.gateway

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private data class ProviderPreset(val name: String, val url: String, val model: String)
private val providerPresets = listOf(
    ProviderPreset("OpenAI", "https://api.openai.com", "gpt-4o-mini"),
    ProviderPreset("Anthropic", "https://api.anthropic.com", "claude-3-5-sonnet-latest"),
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
                else -> MoreScreen(Modifier.padding(padding), updateMessage, onSettings = { showSettings = true }) {
                    updateMessage = "Checking for updates…"
                    val activity = context as? ComponentActivity
                    UpdateManager(context).check { result ->
                        activity?.runOnUiThread {
                            result.onSuccess { update ->
                                if (update == null) updateMessage = "You are up to date"
                                else {
                                    updateMessage = "Downloading v${update.versionName}…"
                                    UpdateManager(context).downloadAndInstall(update) { download ->
                                        activity.runOnUiThread { updateMessage = download.fold({ "Installer opened" }, { "Update failed: ${it.message}" }) }
                                    }
                                }
                            }.onFailure { updateMessage = "Update check failed" }
                        }
                    }
                }
            }
        }
        if (showAddDialog) {
            AddProviderDialog(
                onDismiss = { showAddDialog = false },
                onSave = { name, url, model, key ->
                    providerStore.add(name, url, model, key)
                    keys = providerStore.list().map { it.toProviderKey(providerStore) }
                    showAddDialog = false
                }
            )
        }
        if (showSettings) SettingsDialog(gatewaySettings, onDismiss = { showSettings = false })
    }
}

private fun StoredProvider.toProviderKey(store: ProviderStore) = ProviderKey(
    name, baseUrl.removePrefix("https://").removePrefix("http://"), model,
    when { !enabled -> KeyStatus.DISABLED; isCoolingDown -> KeyStatus.COOLDOWN; else -> KeyStatus.ACTIVE },
    priority, store.apiKey(this)?.let { "${it.take(4)}••••••${it.takeLast(4)}" } ?: "••••••••"
)

@Composable
private fun AddProviderDialog(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://api.openai.com") }
    var model by remember { mutableStateOf("auto") }
    var key by remember { mutableStateOf("") }
    var showPresets by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add provider key") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose a provider preset or enter a custom endpoint", color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { showPresets = true }, modifier = Modifier.fillMaxWidth()) { Text(if (name.isBlank()) "Choose provider preset" else name) }
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            OutlinedTextField(url, { url = it }, label = { Text("Base URL") }, singleLine = true)
            OutlinedTextField(model, { model = it }, label = { Text("Default model") }, singleLine = true)
            OutlinedTextField(key, { key = it }, label = { Text("API key") }, singleLine = true)
        } },
        confirmButton = { Button(enabled = name.isNotBlank() && url.isNotBlank() && key.isNotBlank(), onClick = { onSave(name, url, model, key) }) { Text("Save securely") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (showPresets) {
        AlertDialog(
            onDismissRequest = { showPresets = false },
            title = { Text("Provider presets") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { providerPresets.forEach { preset -> TextButton(onClick = { name = preset.name; url = preset.url; model = preset.model; showPresets = false }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) { Text(preset.name, fontWeight = FontWeight.SemiBold); Text(preset.url, color = Muted, style = MaterialTheme.typography.labelSmall) } } } } },
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
private fun MoreScreen(modifier: Modifier, updateMessage: String, onSettings: () -> Unit, onUpdate: () -> Unit) {
    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp)); Text("More", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Navy); Text("Control how JH GetWay works", color = Muted)
        MoreItem(Icons.Default.Storage, "Routing chain", "Set priority and fallback order")
        MoreItem(Icons.Default.Refresh, "Request logs", "Review provider switches and errors")
        MoreItem(Icons.Default.Settings, "Settings", "Network, appearance and security", onSettings)
        MoreItem(Icons.Default.Security, "Backup & security", "Encrypted export and app lock")
        MoreItem(Icons.Default.SystemUpdate, "App updates", if (updateMessage.isBlank()) "Check GitHub for a newer APK" else updateMessage, onClick = onUpdate)
        Spacer(Modifier.weight(1f)); OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("JH GetWay v0.1.0") }
    }
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
private fun MoreItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit = {}) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6EAF0))) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Blue, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(14.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold, color = Navy); Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Text("›", style = MaterialTheme.typography.headlineSmall, color = Muted) } }
}

@Composable
private fun LogRow(log: GatewayLog) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(Green)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(log.message, color = Navy, fontWeight = FontWeight.Medium); Text(log.detail, color = Muted, style = MaterialTheme.typography.bodySmall) }; Text(log.time, color = Muted, style = MaterialTheme.typography.labelSmall) }
}
