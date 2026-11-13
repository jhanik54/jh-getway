package com.jh.gateway

import org.json.JSONArray
import org.json.JSONObject

/** How a model is billed, as far as the provider tells us. */
enum class ModelCost { FREE, PAID, UNKNOWN }

/**
 * A model discovered from a provider's model endpoint.
 * [cost] is FREE only when the provider publishes pricing metadata that says so, or
 * when the model id follows the widespread ":free" convention. Providers that publish
 * no pricing at all yield UNKNOWN rather than a guess.
 */
data class CatalogModel(
    val id: String,
    val provider: String,
    val cost: ModelCost,
    val capabilities: List<String> = emptyList(),
    val promptPrice: String = "",
    val completionPrice: String = ""
)

/** The models of one provider, split by cost. */
data class ModelCatalog(
    val models: List<CatalogModel>,
    val source: String
) {
    val free: List<CatalogModel> get() = models.filter { it.cost == ModelCost.FREE }
    val paid: List<CatalogModel> get() = models.filter { it.cost == ModelCost.PAID }
    val unknown: List<CatalogModel> get() = models.filter { it.cost == ModelCost.UNKNOWN }
    val total: Int get() = models.size
}

/**
 * Parses a provider model listing and classifies every model as free, paid or unknown.
 *
 * Recognised metadata shapes:
 *  - OpenRouter style: `{"id":"x","pricing":{"prompt":"0","completion":"0"}}`
 *  - explicit `{"id":"x","free":true}` or `{"id":"x","cost":0}`
 *  - the id convention `provider/model:free`
 */
object ModelCatalogParser {

    private val FREE_ID_MARKERS = listOf(":free", "-free", "free-")

    fun parse(body: String, providerName: String): ModelCatalog {
        val trimmed = body.trim()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> JSONObject(trimmed).optJSONArray("data") ?: JSONArray()
        }
        val models = mutableListOf<CatalogModel>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").ifBlank { item.optString("name").ifBlank { item.optString("model") } }
            if (id.isBlank()) continue
            val pricing = item.optJSONObject("pricing")
            val promptPrice = pricing?.optString("prompt").orEmpty()
            val completionPrice = pricing?.optString("completion").orEmpty()
            val cost = classify(item, id, promptPrice, completionPrice)
            val capabilities = item.optJSONArray("capabilities")?.let { caps ->
                (0 until caps.length()).mapNotNull { caps.optString(it).takeIf(String::isNotBlank) }
            } ?: ModelRegistry.capabilitiesFor(id)
            models += CatalogModel(id, providerName, cost, capabilities, promptPrice, completionPrice)
        }
        val distinct = models.distinctBy { it.id }.sortedBy { it.id }
        val source = when {
            distinct.isEmpty() -> "none"
            distinct.any { it.cost != ModelCost.UNKNOWN } -> "provider-metadata"
            else -> "id-convention"
        }
        return ModelCatalog(distinct, source)
    }

    private fun classify(item: JSONObject, id: String, promptPrice: String, completionPrice: String): ModelCost {
        val lowered = id.lowercase()
        if (FREE_ID_MARKERS.any { lowered.contains(it) }) return ModelCost.FREE
        if (item.has("free")) {
            val flag = item.opt("free")
            if (flag == true) return ModelCost.FREE
            if (flag == false) return ModelCost.PAID
        }
        if (item.has("cost")) {
            val cost = item.opt("cost")
            val zero = (cost is Number && cost.toDouble() == 0.0) || (cost is String && cost.toDoubleOrNull() == 0.0)
            return if (zero) ModelCost.FREE else ModelCost.PAID
        }
        if (promptPrice.isNotBlank() || completionPrice.isNotBlank()) {
            val prompt = promptPrice.toDoubleOrNull()
            val completion = completionPrice.toDoubleOrNull()
            // A published price of zero on both sides means the model is free to use.
            if (prompt != null && completion != null) {
                return if (prompt == 0.0 && completion == 0.0) ModelCost.FREE else ModelCost.PAID
            }
            if (prompt != null) return if (prompt == 0.0) ModelCost.FREE else ModelCost.PAID
            if (completion != null) return if (completion == 0.0) ModelCost.FREE else ModelCost.PAID
            return ModelCost.PAID
        }
        return ModelCost.UNKNOWN
    }

    /** Serialises a catalog so it can be cached between launches. */
    fun toJson(catalog: ModelCatalog): String = JSONArray().apply {
        catalog.models.forEach { model ->
            put(JSONObject().apply {
                put("id", model.id)
                put("provider", model.provider)
                put("cost", model.cost.name)
                put("capabilities", model.capabilities.joinToString(","))
                put("promptPrice", model.promptPrice)
                put("completionPrice", model.completionPrice)
            })
        }
    }.toString()

    fun fromJson(json: String, providerName: String): ModelCatalog {
        val array = runCatching { JSONArray(json) }.getOrElse { return ModelCatalog(emptyList(), "none") }
        val models = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").ifBlank { return@mapNotNull null }
            CatalogModel(
                id = id,
                provider = item.optString("provider", providerName),
                cost = runCatching { ModelCost.valueOf(item.optString("cost", "UNKNOWN")) }.getOrDefault(ModelCost.UNKNOWN),
                capabilities = item.optString("capabilities").split(',').map(String::trim).filter(String::isNotBlank),
                promptPrice = item.optString("promptPrice"),
                completionPrice = item.optString("completionPrice")
            )
        }
        return ModelCatalog(models, "cache")
    }
}
