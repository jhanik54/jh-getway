package com.jh.gateway

/** Capability-aware registry of provider models used by `/v1/models` and route simulation. */
object ModelRegistry {
    data class ModelInfo(
        val id: String,
        val provider: String,
        val aliases: List<String>,
        val capabilities: List<String>
    )

    private val visionModels = listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "claude-3", "gemini-1.5-pro", "gemini-1.5-flash", "gemini-2.0-flash", "pixtral", "llava")
    private val toolModels = listOf("gpt-4o", "gpt-4-turbo", "claude-3", "claude-3-5", "deepseek-chat", "llama-3.3", "mistral-large", "gemini-1.5", "gemini-2.0")
    private val jsonModels = listOf("gpt-4o", "gpt-4-turbo", "claude-3", "deepseek-chat", "json", "mixtral")
    private val codeModels = listOf("deepseek-coder", "codestral", "codellama", "codeqwen", "starcoder")

    fun capabilitiesFor(model: String): List<String> {
        val lower = model.lowercase()
        val result = mutableListOf("chat")
        if (visionModels.any { lower.contains(it) }) result += "vision"
        if (toolModels.any { lower.contains(it) }) result += "tools"
        if (jsonModels.any { lower.contains(it) }) result += "json_mode"
        if (codeModels.any { lower.contains(it) }) result += "code"
        return result.distinct()
    }

    /** Deterministic model list: sorted by id, duplicates removed, capabilities attached. */
    fun modelsFor(providers: List<StoredProvider>): List<ModelInfo> =
        providers.filter { it.enabled }
            .flatMap { provider ->
                (listOf(provider.model) + provider.aliases).distinct().map { model ->
                    ModelInfo(
                        id = model,
                        provider = provider.name,
                        aliases = provider.aliases,
                        capabilities = capabilitiesFor(model)
                    )
                }
            }
            .distinctBy { it.id }
            .sortedBy { it.id }
}
