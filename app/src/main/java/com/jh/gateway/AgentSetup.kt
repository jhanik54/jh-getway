package com.jh.gateway

import org.json.JSONObject

/**
 * Generates ready-to-paste configuration for AI agents and SDKs that should talk to
 * this gateway instead of a provider directly.
 *
 * The generated snippets always point at the local gateway address and use the gateway
 * key, so the agent inherits the routing chain, the fallback and the rate limits.
 */
object AgentSetup {

    /** One ready-made configuration. */
    data class Snippet(val id: String, val label: String, val language: String, val content: String)

    /** The address other devices should use to reach this gateway. */
    fun lanAddress(): String = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList().asSequence()
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<java.net.Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }?.hostAddress
    }.getOrNull().orEmpty()

    fun baseUrl(port: Int, lan: Boolean): String {
        val host = if (lan) lanAddress() ?: "127.0.0.1" else "127.0.0.1"
        return "http://$host:$port/v1"
    }

    /**
     * Builds the configuration snippets for the given gateway address and key.
     */
    fun snippets(port: Int, gatewayKey: String, lan: Boolean): List<Snippet> {
        val base = baseUrl(port, lan)
        val loopback = baseUrl(port, lan = false)
        val model = "auto"
        return listOf(
            Snippet(
                "opencode", "OpenCode (opencode.json)", "json",
                JSONObject().apply {
                    put(
                        "\$schema", "https://opencode.ai/config.json"
                    )
                    put(
                        "provider", JSONObject().apply {
                            put(
                                "jh-getway", JSONObject().apply {
                                    put("npm", "@ai-sdk/openai-compatible")
                                    put("name", "JH GetWay")
                                    put(
                                        "options", JSONObject().apply {
                                            put("baseURL", base)
                                            put("apiKey", gatewayKey)
                                        }
                                    )
                                    put(
                                        "models", JSONObject().apply {
                                            put(model, JSONObject().put("name", "Auto (routed by JH GetWay)"))
                                        }
                                    )
                                }
                            )
                        }
                    )
                    put("model", "jh-getway/$model")
                }.toString(2)
            ),
            Snippet(
                "openai_env", "OpenAI SDK / any OpenAI client", "bash",
                "# Point an OpenAI-compatible client at the gateway\n" +
                    "export OPENAI_BASE_URL=\"$base\"\n" +
                    "export OPENAI_API_KEY=\"$gatewayKey\"\n" +
                    "export OPENAI_MODEL=\"$model\"\n\n" +
                    "# curl $base/chat/completions -H \"Authorization: Bearer $gatewayKey\" \\\n" +
                    "  -H \"Content-Type: application/json\" \\\n" +
                    "  -d '{\"model\":\"$model\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}'"
            ),
            Snippet(
                "claude_code", "Claude Code", "bash",
                "# Claude Code against the local gateway\n" +
                    "export ANTHROPIC_BASE_URL=\"$loopback\"\n" +
                    "export ANTHROPIC_AUTH_TOKEN=\"$gatewayKey\"\n" +
                    "export ANTHROPIC_MODEL=\"$model\"\n" +
                    "# Note: the gateway translates Anthropic Messages requests to the\n" +
                    "# provider protocol, so Claude Code works unchanged."
            ),
            Snippet(
                "cline", "Cline / Roo Code (VS Code settings.json)", "json",
                JSONObject().apply {
                    put("cline.apiProvider", "openai")
                    put("cline.openAiBaseUrl", loopback)
                    put("cline.openAiApiKey", gatewayKey)
                    put("cline.openAiModelId", model)
                }.toString(2)
            ),
            Snippet(
                "generic", "Any HTTP client", "bash",
                "curl $base/chat/completions \\\n" +
                    "  -H \"Authorization: Bearer $gatewayKey\" \\\n" +
                    "  -H \"Content-Type: application/json\" \\\n" +
                    "  -d '{\"model\":\"$model\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}'"
            )
        )
    }
}
