package com.jh.gateway

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val PlaygroundGreen = Color(0xFF15803D)
private val PlaygroundRed = Color(0xFFB91C1C)
private val PlaygroundBlue = Color(0xFF4F67E8)
private val PlaygroundMuted = Color(0xFF667085)

/**
 * The Playground: a chat surface that is ready to use the moment it is opened.
 *
 * A single API key is enough: the app asks that provider for its model list, works out
 * which of those models are free and which are paid, and lets the user talk to any of
 * them. The provider keeps doing its own rotation underneath, so one key replaces a
 * long list of keys.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundPage(
    providerStore: ProviderStore,
    settings: GatewaySettings,
    security: GatewaySecurity,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val discovery = remember { ModelDiscovery(context) }

    var messages by remember { mutableStateOf(listOf<PlaygroundChat.Message>()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var useGateway by remember { mutableStateOf(true) }

    var catalog by remember { mutableStateOf<ModelCatalog?>(null) }
    var selectedModel by remember { mutableStateOf("") }
    var showModelSheet by remember { mutableStateOf(false) }
    var showConnectSheet by remember { mutableStateOf(false) }

    // The catalog of every provider that has a key, so the model picker can offer them.
    val providers = providerStore.list()
    var catalogs by remember { mutableStateOf<Map<String, ModelCatalog>>(emptyMap()) }
    LaunchedEffect(providers.map { it.id }) {
        val loaded = providers.mapNotNull { provider ->
            val key = providerStore.apiKey(provider) ?: return@mapNotNull null
            discovery.discover(provider.baseUrl, key, provider.name) { result ->
                if (result.ok) catalogs = catalogs + (provider.name to result.catalog)
            }
            provider.name to discovery.cached(provider.name)
        }.toMap()
        catalogs = loaded
    }

    val allModels = remember(catalogs) {
        catalogs.values.flatMap { it.models }.distinctBy { it.id }
    }
    val currentModel = allModels.firstOrNull { it.id == selectedModel }

    val target = when {
        useGateway -> PlaygroundChat.gatewayTarget(context, settings.port, security.gatewayKey(), settings.wifiMode, AgentSetup.lanAddress())
        else -> {
            val provider = providers.firstOrNull { it.name == catalogs.keys.firstOrNull { name -> catalogs[name]?.models?.any { it.id == selectedModel } == true } }
            val key = provider?.let { providerStore.apiKey(it) }.orEmpty()
            provider?.let { PlaygroundChat.Target.Provider(it.baseUrl, key, it.protocol) }
        }
    }

    AppPageScaffold(title = stringResource(R.string.str_playground_title_4d1c73), onBack = onBack) {
        Column(Modifier.fillMaxSize()) {
            // --- model picker -------------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    onClick = { showModelSheet = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                currentModel?.id ?: stringResource(R.string.str_playground_pick_model_4b8e37),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                when {
                                    currentModel == null -> stringResource(R.string.str_playground_no_model_hint_7d3a82)
                                    currentModel.cost == ModelCost.FREE -> stringResource(R.string.str_playground_free_badge_2a8e61)
                                    currentModel.cost == ModelCost.PAID -> stringResource(R.string.str_playground_paid_badge_9c3d17)
                                    else -> stringResource(R.string.str_playground_unknown_badge_7e4b02)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    currentModel?.cost == ModelCost.FREE -> PlaygroundGreen
                                    currentModel?.cost == ModelCost.PAID -> PlaygroundRed
                                    else -> PlaygroundMuted
                                }
                            )
                        }
                        Icon(Icons.Default.ExpandMore, null)
                    }
                }
                FilterChip(useGateway, { useGateway = !useGateway }, label = { Text(stringResource(R.string.str_playground_via_gateway_5f2b64)) })
            }

            // --- conversation -------------------------------------------------
            if (messages.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(24.dp))
                    Icon(Icons.Default.Add, null, tint = PlaygroundBlue, modifier = Modifier.size(40.dp))
                    Text(
                        stringResource(R.string.str_playground_empty_title_6e1c47),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.str_playground_empty_body_9b3d25),
                        color = PlaygroundMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = { showConnectSheet = true }) {
                        Text(stringResource(R.string.str_playground_connect_provider_5d8b34))
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
                ) {
                    items(messages) { message -> ChatBubble(message.role, message.content) }
                }
            }

            // --- composer -----------------------------------------------------
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.str_playground_message_3e9c17)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 5
                )
                IconButton(
                    enabled = input.isNotBlank() && !sending && target != null,
                    onClick = {
                        val history = messages + PlaygroundChat.Message("user", input)
                        messages = history
                        input = ""
                        sending = true
                        val chosen = selectedModel.ifBlank { "auto" }
                        val route = target
                        if (route != null) {
                            PlaygroundChat(context).send(history, chosen, route) { outcome ->
                                sending = false
                                messages = messages + PlaygroundChat.Message(
                                    "assistant",
                                    when (outcome) {
                                        is PlaygroundChat.Outcome.Success -> outcome.reply
                                        is PlaygroundChat.Outcome.Failure -> "⚠ ${outcome.message}"
                                    }
                                )
                            }
                        }
                    }
                ) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.str_playground_send_5a3e91), tint = PlaygroundBlue) }
            }
        }
    }

    // --- model sheet ------------------------------------------------------
    if (showModelSheet) {
        ModalBottomSheet(onDismissRequest = { showModelSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.str_playground_pick_model_4b8e37), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                val free = allModels.filter { it.cost == ModelCost.FREE }
                val paid = allModels.filter { it.cost == ModelCost.PAID }
                val unknown = allModels.filter { it.cost == ModelCost.UNKNOWN }
                if (allModels.isEmpty()) {
                    Text(stringResource(R.string.str_playground_no_models_9c7e14), color = PlaygroundMuted)
                }
                if (free.isNotEmpty()) {
                    Text(stringResource(R.string.str_playground_free_section_6d3a81), fontWeight = FontWeight.SemiBold, color = PlaygroundGreen)
                    free.forEach { ModelRow(it, selectedModel == it.id) { selectedModel = it.id; showModelSheet = false } }
                }
                if (unknown.isNotEmpty()) {
                    Text(stringResource(R.string.str_playground_unknown_section_1e8b47), fontWeight = FontWeight.SemiBold, color = PlaygroundMuted)
                    unknown.forEach { ModelRow(it, selectedModel == it.id) { selectedModel = it.id; showModelSheet = false } }
                }
                if (paid.isNotEmpty()) {
                    Text(stringResource(R.string.str_playground_paid_section_7a2c36), fontWeight = FontWeight.SemiBold, color = PlaygroundRed)
                    paid.forEach { ModelRow(it, selectedModel == it.id) { selectedModel = it.id; showModelSheet = false } }
                }
                OutlinedButton(onClick = { showModelSheet = false; showConnectSheet = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.str_playground_connect_provider_5d8b34))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // --- connect sheet ----------------------------------------------------
    if (showConnectSheet) {
        ConnectProviderSheet(
            onDismiss = { showConnectSheet = false },
            onConnected = { providerName, discovered ->
                catalogs = catalogs + (providerName to discovered)
                selectedModel = discovered.free.firstOrNull()?.id ?: discovered.models.firstOrNull()?.id.orEmpty()
                showConnectSheet = false
            }
        )
    }
}

/**
 * Collects one API key and turns it into a provider. The provider's own rotation keeps
 * working behind it, so a single key is enough.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ConnectProviderSheet(onDismiss: () -> Unit, onConnected: (String, ModelCatalog) -> Unit) {
    val context = LocalContext.current
    val providerStore = remember { ProviderStore(context) }
    val discovery = remember { ModelDiscovery(context) }

    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://opencode.ai/zen/v1") }
    var providerName by remember { mutableStateOf("OpenCode Zen") }
    var discovering by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var discovered by remember { mutableStateOf<ModelCatalog?>(null) }

    val textTitle = stringResource(R.string.str_playground_connect_provider_5d8b34)
    val textFinding = stringResource(R.string.str_playground_discovering_6d81f3)
    val textCreated = stringResource(R.string.str_playground_provider_created_4a6d28)
    val textFailed = stringResource(R.string.str_playground_create_failed_0e5b73)
    val textFind = stringResource(R.string.str_playground_find_models_3e8b17)
    val textCreate = stringResource(R.string.str_playground_create_provider_1f8b64)
    val textUse = stringResource(R.string.str_playground_use_this_provider_3c7a92)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(textTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.str_playground_one_key_explains_8b4d19),
                color = PlaygroundMuted,
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(value = providerName, onValueChange = { providerName = it }, label = { Text(stringResource(R.string.str_playground_provider_name_2c8f51)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text(stringResource(R.string.str_playground_base_url_5d3b74)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text(stringResource(R.string.str_playground_api_key_9a6e18)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !discovering && apiKey.isNotBlank() && baseUrl.isNotBlank(),
                    onClick = {
                        discovering = true
                        status = textFinding
                        discovery.discover(baseUrl, apiKey, providerName) { result ->
                            discovering = false
                            if (result.ok) {
                                discovered = result.catalog
                                status = ""
                            } else {
                                status = result.error.orEmpty()
                            }
                        }
                    }
                ) {
                    if (discovering) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(textFind)
                }
                OutlinedButton(
                    enabled = apiKey.isNotBlank() && baseUrl.isNotBlank() && !discovering,
                    onClick = {
                        runCatching { providerStore.add(providerName, baseUrl, "auto", apiKey, ProviderProtocol.OPENAI) }
                            .onSuccess {
                                apiKey = ""
                                status = textCreated
                                discovery.discover(baseUrl, apiKey, providerName) { }
                            }
                            .onFailure { status = it.message ?: textFailed }
                    }
                ) { Text(textCreate) }
            }

            if (status.isNotBlank()) Text(status, color = PlaygroundRed, style = MaterialTheme.typography.bodySmall)

            val catalog = discovered
            if (catalog != null && catalog.models.isNotEmpty()) {
                Text(
                    stringResource(R.string.str_playground_cost_summary_2f6b18, catalog.free.size, catalog.paid.size, catalog.unknown.size),
                    color = PlaygroundMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = { onConnected(providerName, catalog) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(textUse) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelRow(model: CatalogModel, selected: Boolean, onSelect: () -> Unit) {
    val color = when (model.cost) {
        ModelCost.FREE -> PlaygroundGreen
        ModelCost.PAID -> PlaygroundRed
        ModelCost.UNKNOWN -> PlaygroundMuted
    }
    val label = when (model.cost) {
        ModelCost.FREE -> stringResource(R.string.str_playground_free_badge_2a8e61)
        ModelCost.PAID -> stringResource(R.string.str_playground_paid_badge_9c3d17)
        ModelCost.UNKNOWN -> stringResource(R.string.str_playground_unknown_badge_7e4b02)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFFEEF1FF) else Color.Transparent)
            .clickable { onSelect() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(model.id, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = MaterialTheme.colorScheme.onBackground)
            if (model.promptPrice.isNotBlank() || model.completionPrice.isNotBlank()) {
                Text(
                    stringResource(R.string.str_playground_price_5f7b21, model.promptPrice, model.completionPrice),
                    style = MaterialTheme.typography.labelSmall, color = PlaygroundMuted
                )
            }
        }
        Box(
            Modifier
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ChatBubble(role: String, content: String) {
    val isUser = role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isUser) Color(0xFFEEF1FF) else MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp)
        ) {
            Text(content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
