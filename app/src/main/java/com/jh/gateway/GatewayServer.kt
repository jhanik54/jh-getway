package com.jh.gateway

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.http.HttpStatusCode
import org.json.JSONObject
import io.ktor.util.AttributeKey
import java.util.concurrent.Semaphore

/** Local-only HTTP surface. Provider forwarding is the next milestone. */
class GatewayServer(private val store: ProviderStore, private val security: GatewaySecurity, private val stats: GatewayStats, private val settings: GatewaySettings, chainStore: ChainStore, health: ProviderHealthStore? = null) {
    private val router = ProviderRouter(store, chainStore, settings, health)
    private val requestPermits = Semaphore(settings.maxConcurrent, true)
    private val permitKey = AttributeKey<Boolean>("jh-gateway-request-permit")
    private val server = embeddedServer(CIO, host = if (settings.wifiMode) "0.0.0.0" else "127.0.0.1", port = settings.port) {
        routing {
            get("/health") {
                call.respondText(
                    "{\"status\":\"ok\",\"service\":\"JH GetWay\",\"version\":\"${BuildConfig.VERSION_NAME}\",\"versionCode\":${BuildConfig.VERSION_CODE}}",
                    ContentType.Application.Json
                )
            }
            get("/v1/models") {
                if (!call.authorized()) return@get
                val models = ModelRegistry.modelsFor(store.list()).joinToString(",") { info -> "{\"id\":\"${escape(info.id)}\",\"object\":\"model\",\"created\":1_700_000_000,\"owned_by\":\"jh-getway\",\"capabilities\":[${info.capabilities.joinToString(",") { "\"${escape(it)}\"" }}]}" }
                call.respondText("{\"object\":\"list\",\"data\":[$models]}", ContentType.Application.Json)
            }
            get("/v1/gateway/logs") {
                if (!call.authorized()) return@get
                val data = stats.recent().joinToString(",") { "{\"time\":${it.time},\"endpoint\":\"${escape(it.endpoint)}\",\"status\":${it.status},\"provider\":\"${escape(it.provider ?: "")}\",\"latencyMs\":${it.latencyMs}}" }
                call.respondText("{\"object\":\"list\",\"data\":[$data]}", ContentType.Application.Json)
            }
            post("/v1/gateway/simulate") {
                if (!call.authorized()) return@post
                try {
                    val body = call.receiveValidatedBody() ?: return@post
                    val requested = runCatching { JSONObject(body).optString("model", "auto") }.getOrDefault("auto").ifBlank { "auto" }
                    val route = router.simulateRoute(requested)
                    val data = route.joinToString(",") { provider -> "{\"id\":\"${escape(provider.id)}\",\"name\":\"${escape(provider.name)}\",\"model\":\"${escape(provider.model)}\",\"priority\":${provider.priority},\"enabled\":${provider.enabled}}" }
                    call.respondText("{\"model\":\"${escape(requested)}\",\"strategy\":\"${escape(settings.routingStrategy)}\",\"providers\":[$data]}", ContentType.Application.Json)
                } finally { call.releasePermit() }
            }
            post("/v1/chat/completions") {
                if (!call.authorized()) return@post
                try {
                    val body = call.receiveValidatedBody() ?: return@post
                    if (Regex("\\\"stream\\\"\\s*:\\s*true").containsMatchIn(body)) {
                        call.respondOutputStream(ContentType.Text.EventStream) { router.stream(body, this, ClientProtocol.OPENAI) }
                    } else {
                        val started = System.currentTimeMillis()
                        val result = router.forward(body)
                        stats.recordUsage(result.body)
                        stats.record("/v1/chat/completions", result.status, result.provider, System.currentTimeMillis() - started)
                        call.respondText(result.body, ContentType.Application.Json, HttpStatusCode.fromValue(result.status))
                    }
                } finally {
                    call.releasePermit()
                }
            }
            post("/v1/messages") {
                if (!call.authorized()) return@post
                try {
                    val body = call.receiveValidatedBody() ?: return@post
                    if (Regex("\\\"stream\\\"\\s*:\\s*true").containsMatchIn(body)) call.respondOutputStream(ContentType.Text.EventStream) { router.stream(ProtocolAdapter.toOpenAi(body, ClientProtocol.ANTHROPIC), this, ClientProtocol.ANTHROPIC) }
                    else { val result = router.forward(ProtocolAdapter.toOpenAi(body, ClientProtocol.ANTHROPIC)); stats.recordUsage(result.body); call.respondText(ProtocolAdapter.fromOpenAi(result.body, ClientProtocol.ANTHROPIC), ContentType.Application.Json, HttpStatusCode.fromValue(result.status)) }
                } finally { call.releasePermit() }
            }
            post("/v1/responses") {
                if (!call.authorized()) return@post
                try {
                    val body = call.receiveValidatedBody() ?: return@post
                    if (Regex("\\\"stream\\\"\\s*:\\s*true").containsMatchIn(body)) call.respondOutputStream(ContentType.Text.EventStream) { router.stream(ProtocolAdapter.toOpenAi(body, ClientProtocol.RESPONSES), this, ClientProtocol.RESPONSES) }
                    else { val result = router.forward(ProtocolAdapter.toOpenAi(body, ClientProtocol.RESPONSES)); stats.recordUsage(result.body); call.respondText(ProtocolAdapter.fromOpenAi(result.body, ClientProtocol.RESPONSES), ContentType.Application.Json, HttpStatusCode.fromValue(result.status)) }
                } finally { call.releasePermit() }
            }
        }
    }

    private suspend fun io.ktor.server.application.ApplicationCall.authorized(): Boolean {
        val bearer = request.headers[HttpHeaders.Authorization]
        val apiKey = request.headers["x-api-key"]
        val remoteIp = request.local.remoteHost
        if (!security.isIpAllowed(remoteIp, settings.ipAllowlist)) {
            respondText("{\"error\":{\"message\":\"Client IP is not allowlisted\",\"type\":\"permission_error\"}}", ContentType.Application.Json, HttpStatusCode.Forbidden)
            return false
        }
        if (!security.allowRequest(remoteIp)) {
            respondText("{\"error\":{\"message\":\"Too many requests from this IP\",\"type\":\"rate_limit_error\"}}", ContentType.Application.Json, HttpStatusCode.TooManyRequests)
            return false
        }
        if (!requestPermits.tryAcquire()) {
            respondText("{\"error\":{\"message\":\"Gateway concurrency limit reached\",\"type\":\"rate_limit_error\"}}", ContentType.Application.Json, HttpStatusCode.TooManyRequests)
            return false
        }
        attributes.put(permitKey, true)
        if (security.isAuthorized(bearer ?: apiKey, remoteIp)) return true
        releasePermit()
        respondText("{\"error\":{\"message\":\"Invalid or missing gateway key\",\"type\":\"authentication_error\"}}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
        return false
    }

    private fun io.ktor.server.application.ApplicationCall.releasePermit() {
        if (attributes.getOrNull(permitKey) == true) { requestPermits.release(); attributes.put(permitKey, false) }
    }

    private suspend fun io.ktor.server.application.ApplicationCall.receiveValidatedBody(): String? {
        if ((request.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L) > MAX_BODY_BYTES) {
            respondText("{\"error\":{\"message\":\"Request body is too large\",\"type\":\"invalid_request_error\"}}", ContentType.Application.Json, HttpStatusCode.PayloadTooLarge)
            return null
        }
        val body = runCatching { receiveText() }.getOrNull()
        if (body.isNullOrBlank() || runCatching { JSONObject(body) }.isFailure) {
            respondText("{\"error\":{\"message\":\"Request body must be valid JSON\",\"type\":\"invalid_request_error\"}}", ContentType.Application.Json, HttpStatusCode.BadRequest)
            return null
        }
        return body
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")

    fun start() = server.start(wait = false)
    fun stop() = server.stop(500, 1_000)

    companion object { private const val MAX_BODY_BYTES = 4L * 1024L * 1024L }
}
