package com.jh.gateway

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.http.HttpStatusCode

/** Local-only HTTP surface. Provider forwarding is the next milestone. */
class GatewayServer(private val store: ProviderStore, private val port: Int = 8787) {
    private val router = ProviderRouter(store)
    private val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
        routing {
            get("/health") {
                call.respondText(
                    "{\"status\":\"ok\",\"service\":\"JH GetWay\",\"version\":\"0.1.0\"}",
                    ContentType.Application.Json
                )
            }
            post("/v1/chat/completions") {
                val result = router.forward(call.receiveText())
                call.respondText(result.body, ContentType.Application.Json, HttpStatusCode.fromValue(result.status))
            }
        }
    }

    fun start() = server.start(wait = false)
    fun stop() = server.stop(500, 1_000)
}
