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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
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
private val PlaygroundAmber = Color(0xFFB45309)
private val PlaygroundBlue = Color(0xFF4F67E8)
private val PlaygroundMuted = Color(0xFF667085)

/**
 * The Playground: paste an API key, let the app discover which models are free and
 * which are paid, then talk to a model without leaving the app.
 *
 * The key is stored encrypted by [ProviderStore], exactly like the providers of the
 * routing chain, so it is never kept in plain text.
 */
@Composable
fun PlaygroundDialog(
    providerStore: ProviderStore,
    settings: GatewaySettings,
    security: GatewaySecurity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val discovery = remember { ModelDiscovery(context) }

    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://opencode.ai/zen/v1") }
    var providerName by remember { mutableStateOf("OpenCode Zen") }
    var catalog by remember { mutableStateOf<ModelCatalog?>(null) }
    var discovering by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }
    var costFilter by remember { mutableIntStateOf(0) }
    var messages by remember { mutableStateOf(listOf<PlaygroundChat.Message>()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var useGateway by remember { mutableStateOf(true) }

    val text_done_e9b450 = stringResource(R.string.str_done_e9b450)
    val text_playground_all_models_8d3e72 = stringResource(R.string.str_playground_all_models_8d3e72)
    val text_playground_api_key_9a6e18 = stringResource(R.string.str_playground_api_key_9a6e18)
    val text_playground_base_url_5d3b74 = stringResource(R.string.str_playground_base_url_5d3b74)
    val text_playground_clear_chat_0b7f42 = stringResource(R.string.str_playground_clear_chat_0b7f42)
    val text_playground_create_failed_0e5b73 = stringResource(R.string.str_playground_create_failed_0e5b73)
    val text_playground_create_provider_1f8b64 = stringResource(R.string.str_playground_create_provider_1f8b64)
    val text_playground_direct_8d1f27 = stringResource(R.string.str_playground_direct_8d1f27)
    val text_playground_discovering_6d81f3 = stringResource(R.string.str_playground_discovering_6d81f3)
    val text_playground_find_models_3e8b17 = stringResource(R.string.str_playground_find_models_3e8b17)
    val text_playground_free_badge_2a8e61 = stringResource(R.string.str_playground_free_badge_2a8e61)
    val text_playground_free_only_1a5b90 = stringResource(R.string.str_playground_free_only_1a5b90)
    val text_playground_message_3e9c17 = stringResource(R.string.str_playground_message_3e9c17)
    val text_playground_paid_badge_9c3d17 = stringResource(R.string.str_playground_paid_badge_9c3d17)
    val text_playground_paid_only_7c9f43 = stringResource(R.string.str_playground_paid_only_7c9f43)
    val text_playground_provider_created_4a6d28 = stringResource(R.string.str_playground_provider_created_4a6d28)
    val text_playground_provider_name_2c8f51 = stringResource(R.string.str_playground_provider_name_2c8f51)
    val text_playground_send_5a3e91 = stringResource(R.string.str_playground_send_5a3e91)
    val text_playground_step1_7b2e90 = stringResource(R.string.str_playground_step1_7b2e90)
    val text_playground_step2_6c4e28 = stringResource(R.string.str_playground_step2_6c4e28)
    val text_playground_step3_b1e07d = stringResource(R.string.str_playground_step3_b1e07d)
    val text_playground_title_4d1c73 = stringResource(R.string.str_playground_title_4d1c73)
    val text_playground_unknown_badge_7e4b02 = stringResource(R.string.str_playground_unknown_badge_7e4b02)
    val text_playground_via_gateway_5f2b64 = stringResource(R.string.str_playground_via_gateway_5f2b64)
    val providers = providerStore.list()
    var createdProviderId by remember { mutableStateOf<String?>(null) }
    val createdProvider = providers.firstOrNull { it.id == createdProviderId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text_playground_title_4d1c73, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // --- Step 1: supply an API key -----------------------------------
                Text(text_playground_step1_7b2e90, fontWeight = FontWeight.SemiBold, color = PlaygroundBlue)
                OutlinedTextField(
                    value = providerName,
                    onValueChange = { providerName = it },
                    label = { Text(text_playground_provider_name_2c8f51) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(text_playground_base_url_5d3b74) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(text_playground_api_key_9a6e18) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !discovering && apiKey.isNotBlank() && baseUrl.isNotBlank(),
                        onClick = {
                            discovering = true
                            status = text_playground_discovering_6d81f3
                            discovery.discover(baseUrl, apiKey, providerName) { result ->
                                discovering = false
                                if (result.ok) {
                                    catalog = result.catalog
                                    selectedModel = result.catalog.free.firstOrNull()?.id
                                        ?: result.catalog.models.firstOrNull()?.id.orEmpty()
                                    status = ""
                                } else {
                                    status = result.error.orEmpty()
                                }
                            }
                        }
                    ) {
                        if (discovering) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text_playground_find_models_3e8b17)
                    }
                    OutlinedButton(
                        enabled = apiKey.isNotBlank() && baseUrl.isNotBlank() && !discovering,
                        onClick = {
                            // One "Create" action: register the key as a real provider so
                            // the gateway itself can route to it.
                            runCatching {
                                providerStore.add(providerName, baseUrl, "auto", apiKey, ProviderProtocol.OPENAI)
                            }.onSuccess {
                                createdProviderId = providerStore.list().firstOrNull { provider -> provider.name == providerName }?.id
                                apiKey = ""
                                status = text_playground_provider_created_4a6d28
                            }.onFailure {
                                status = it.message ?: text_playground_create_failed_0e5b73
                            }
                        }
                    ) { Text(text_playground_create_provider_1f8b64) }
                }
                if (status.isNotBlank()) Text(status, color = PlaygroundAmber, style = MaterialTheme.typography.bodySmall)

                // --- Step 2: free / paid model list -----------------------------
                val currentCatalog = catalog
                if (currentCatalog != null && currentCatalog.models.isNotEmpty()) {
                    Text(text_playground_step2_6c4e28, fontWeight = FontWeight.SemiBold, color = PlaygroundBlue)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(costFilter == 0, { costFilter = 0 }, label = { Text(text_playground_all_models_8d3e72 + "  ${currentCatalog.total}") })
                        FilterChip(costFilter == 1, { costFilter = 1 }, label = { Text(text_playground_free_only_1a5b90 + "  ${currentCatalog.free.size}") })
                        FilterChip(costFilter == 2, { costFilter = 2 }, label = { Text(text_playground_paid_only_7c9f43 + "  ${currentCatalog.paid.size}") })
                    }
                    Text(
                        stringResource(R.string.str_playground_cost_summary_2f6b18, currentCatalog.free.size, currentCatalog.paid.size, currentCatalog.unknown.size),
                        color = PlaygroundMuted, style = MaterialTheme.typography.bodySmall
                    )
                    val visible = when (costFilter) {
                        1 -> currentCatalog.free
                        2 -> currentCatalog.paid
                        else -> currentCatalog.models
                    }
                    LazyColumn(Modifier.heightIn(max = 190.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(visible) { model ->
                            ModelRow(model, selectedModel == model.id) { selectedModel = model.id }
                        }
                    }
                }

                // --- Step 3: chat -------------------------------------------------
                Text(text_playground_step3_b1e07d, fontWeight = FontWeight.SemiBold, color = PlaygroundBlue)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(useGateway, { useGateway = !useGateway }, label = { Text(text_playground_via_gateway_5f2b64) })
                    Text(
                        text_playground_direct_8d1f27,
                        color = PlaygroundMuted, style = MaterialTheme.typography.labelSmall
                    )
                }
                if (messages.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(messages) { message ->
                            ChatBubble(message.role, message.content)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(text_playground_message_3e9c17) },
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )
                    IconButton(
                        enabled = input.isNotBlank() && !sending && (selectedModel.isNotBlank() || createdProvider != null),
                        onClick = {
                            val history = messages + PlaygroundChat.Message("user", input)
                            messages = history
                            input = ""
                            sending = true
                            val target = when {
                                useGateway -> PlaygroundChat.gatewayTarget(context, settings.port, security.gatewayKey(), settings.wifiMode, AgentSetup.lanAddress())
                                createdProvider != null -> {
                                    val key = providerStore.apiKey(createdProvider) ?: ""
                                    PlaygroundChat.Target.Provider(createdProvider.baseUrl, key, createdProvider.protocol)
                                }
                                else -> return@IconButton
                            }
                            PlaygroundChat(context).send(history, selectedModel.ifBlank { "auto" }, target) { outcome ->
                                sending = false
                                messages = when (outcome) {
                                    is PlaygroundChat.Outcome.Success -> messages + PlaygroundChat.Message("assistant", outcome.reply)
                                    is PlaygroundChat.Outcome.Failure -> messages + PlaygroundChat.Message("assistant", "⚠ ${outcome.message}")
                                }
                            }
                        }
                    ) { Icon(Icons.Default.Send, text_playground_send_5a3e91, tint = PlaygroundBlue) }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(text_done_e9b450) } },
        dismissButton = {
            TextButton(onClick = { messages = emptyList() }) { Text(text_playground_clear_chat_0b7f42) }
        }
    )
}

@Composable
private fun ModelRow(model: CatalogModel, selected: Boolean, onSelect: () -> Unit) {
    val color = when (model.cost) {
        ModelCost.FREE -> PlaygroundGreen
        ModelCost.PAID -> Color(0xFFB91C1C)
        ModelCost.UNKNOWN -> PlaygroundMuted
    }
    val label = when (model.cost) {
        ModelCost.FREE -> text_playground_free_badge_2a8e61
        ModelCost.PAID -> text_playground_paid_badge_9c3d17
        ModelCost.UNKNOWN -> text_playground_unknown_badge_7e4b02
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
            Text(model.id, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = Color(0xFF111827))
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
                .background(if (isUser) Color(0xFFEEF1FF) else Color(0xFFF5F6FA))
                .padding(10.dp)
        ) {
            Text(content, style = MaterialTheme.typography.bodySmall, color = Color(0xFF111827))
        }
    }
}
