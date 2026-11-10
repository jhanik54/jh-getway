package com.jh.gateway

data class ProviderKey(
    val name: String,
    val provider: String,
    val model: String,
    val status: KeyStatus,
    val priority: Int,
    val maskedKey: String
)

enum class KeyStatus { ACTIVE, COOLDOWN, DISABLED }

data class GatewayLog(
    val time: String,
    val message: String,
    val detail: String
)

val demoKeys = listOf(
    ProviderKey("Gemini Primary", "Google Gemini", "gemini-2.0-flash", KeyStatus.ACTIVE, 1, "AIza••••••7f2a"),
    ProviderKey("OpenRouter Backup", "OpenRouter", "auto", KeyStatus.ACTIVE, 2, "sk-or••••••c91d"),
    ProviderKey("Groq Fast", "Groq", "llama-3.3-70b-versatile", KeyStatus.COOLDOWN, 3, "gsk_••••••1a20")
)

val demoLogs = listOf(
    GatewayLog("10:42 AM", "Gateway initialized", "Ready on 127.0.0.1"),
    GatewayLog("10:40 AM", "Key selected", "Gemini Primary · auto route"),
    GatewayLog("09:18 AM", "Cooldown detected", "Groq Fast · retry in 42 min")
)
