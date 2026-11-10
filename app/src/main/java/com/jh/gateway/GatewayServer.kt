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

/** Local-only HTTP surface. Provider forwarding is the next milestone. */
class GatewayServer(private val store: ProviderStore, private val security: GatewaySecurity, private val stats: GatewayStats, settings: GatewaySettings, chainStore: ChainStore) {
    private val router = ProviderRouter(store, chainStore)
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
                val models = store.list().filter { it.enabled }.joinToString(",") { "{\"id\":\"${escape(it.model)}\",\"object\":\"model\",\"owned_by\":\"${escape(it.name)}\"}" }
                call.respondText("{\"object\":\"list\",\"data\":[$models]}", ContentType.Application.Json)
            }
            get("/v1/gateway/logs") {
                if (!call.authorized()) return@get
                val data = stats.recent().joinToString(",") { "{\"time\":${it.time},\"endpoint\":\"${escape(it.endpoint)}\",\"status\":${it.status},\"provider\":\"${escape(it.provider ?: "")}\",\"latencyMs\":${it.latencyMs}}" }
                call.respondText("{\"object\":\"list\",\"data\":[$data]}", ContentType.Application.Json)
            }
            post("/v1/chat/completions") {
                if (!call.authorized()) return@post
                val body = call.receiveText()
                if (Regex("\\\"stream\\\"\\s*:\\s*true").containsMatchIn(body)) {
                    call.respondOutputStream(ContentType.Text.EventStream) { router.stream(body, this, ClientProtocol.OPENAI) }
                } else {
                    val started = System.currentTimeMillis()
                    val result = router.forward(body)
                    stats.recordUsage(result.body)
                    stats.record("/v1/chat/completions", result.status, result.provider, System.currentTimeMillis() - started)
                    call.respondText(result.body, ContentType.Application.Json, HttpStatusCode.fromValue(result.status))
                }
            }
            post("/v1/messages") {
                if (!call.authorized()) return@post
                val body = call.receiveText()
                if (Regex("\\\"stream\\\"\\s*:\\s*true").containsMatchIn(body)) call.respondOutputStream(ContentType.Text.EventStream) { router.stream(ProtocolAdapter.toOpenAi(body, ClientProtocol.ANTHROPIC), this, ClientProtocol.ANTHROPIC) }
                else { val result = router.forward(ProtocolAdapter.toOpenAi(body, ClientProtocol.ANTHROPIC)); stats.recordUsage(result.body); call.respondText(ProtocolAdapter.fromOpenAi(result.body, ClientProtocol.ANTHROPIC), ContentType.Application.Json, HttpStatusCode.fromValue(result.status)) }
            }
            post("/v1/responses") {
                if (!call.authorized()) return@post
                val body = call.receiveText()
                if (Regex("\\\"stream\\\"\\s*:\\s*true").containsMatchIn(body)) call.respondOutputStream(ContentType.Text.EventStream) { router.stream(ProtocolAdapter.toOpenAi(body, ClientProtocol.RESPONSES), this, ClientProtocol.RESPONSES) }
                else { val result = router.forward(ProtocolAdapter.toOpenAi(body, ClientProtocol.RESPONSES)); stats.recordUsage(result.body); call.respondText(ProtocolAdapter.fromOpenAi(result.body, ClientProtocol.RESPONSES), ContentType.Application.Json, HttpStatusCode.fromValue(result.status)) }
            }
        }
    }

    private suspend fun io.ktor.server.application.ApplicationCall.authorized(): Boolean {
        val bearer = request.headers[HttpHeaders.Authorization]
        val apiKey = request.headers["x-api-key"]
        if (security.isAuthorized(bearer ?: apiKey, request.local.remoteHost)) return true
        respondText("{\"error\":{\"message\":\"Invalid or missing gateway key\",\"type\":\"authentication_error\"}}", ContentType.Application.Json, HttpStatusCode.Unauthorized)
        return false
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")

    fun start() = server.start(wait = false)
    fun stop() = server.stop(500, 1_000)
}
