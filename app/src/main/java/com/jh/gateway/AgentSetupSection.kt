package com.jh.gateway

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings section that hands the gateway address and key to other agents.
 *
 * The snippets are generated for the current port and key, so copying one is enough to
 * point OpenCode, Claude Code, Cline or any OpenAI-compatible client at this device.
 */
@Composable
fun AgentSetupSection(settings: GatewaySettings, gatewaySecurity: GatewaySecurity) {
    val context = LocalContext.current
    var useLan by remember { mutableStateOf(settings.wifiMode) }
    var snippetIndex by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }

    val snippets = remember(useLan, settings.port, gatewaySecurity.gatewayKey()) {
        AgentSetup.snippets(settings.port, gatewaySecurity.gatewayKey(), useLan)
    }
    val current = snippets.getOrNull(snippetIndex) ?: snippets.first()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.str_agent_setup_title_c7e390), fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
        Text(stringResource(R.string.str_agent_setup_intro_2f6b81), color = Color(0xFF667085), style = MaterialTheme.typography.bodySmall)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.str_agent_setup_base_url_1a4d83), style = MaterialTheme.typography.labelSmall, color = Color(0xFF667085))
                Text(AgentSetup.baseUrl(settings.port, useLan), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
            }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("JH GetWay URL", AgentSetup.baseUrl(settings.port, useLan)))
                copied = true
            }) { Text(stringResource(R.string.str_agent_setup_copy_9d1c47)) }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.str_agent_setup_key_8c2f61), style = MaterialTheme.typography.labelSmall, color = Color(0xFF667085))
                Text(gatewaySecurity.maskedKey(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
            }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("JH GetWay key", gatewaySecurity.gatewayKey()))
                copied = true
            }) { Text(stringResource(R.string.str_agent_setup_copy_9d1c47)) }
        }

        FilterChip(useLan, { useLan = it }, label = { Text(stringResource(R.string.str_wi_fi_open_630746)) })

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            snippets.forEachIndexed { index, snippet ->
                FilterChip(snippetIndex == index, { snippetIndex = index; copied = false }, label = { Text(snippet.label) })
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 190.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF5F6FA))
                .verticalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            Text(current.content, style = MaterialTheme.typography.labelSmall, color = Color(0xFF111827))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText(current.label, current.content))
                copied = true
            }) { Text(stringResource(R.string.str_agent_setup_copy_9d1c47)) }
            if (copied) Text(stringResource(R.string.str_agent_setup_copied_5b7e26), color = Color(0xFF15803D), style = MaterialTheme.typography.labelSmall)
        }
    }
}
