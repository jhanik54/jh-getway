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

/** Local-only HTTP surface. Provider forwarding is the next milestone. */
class GatewayServer(private val port: Int = 8787) {
    private val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
        routing {
            get("/health") {
                call.respondText(
                    "{\"status\":\"ok\",\"service\":\"JH GetWay\",\"version\":\"0.1.0\"}",
                    ContentType.Application.Json
                )
            }
            post("/v1/chat/completions") {
                call.receiveText()
                call.respondText(
                    "{\"id\":\"jh_pending\",\"object\":\"chat.completion\",\"choices\":[]," +
                        "\"error\":{\"message\":\"No provider is configured yet\",\"type\":\"gateway_not_ready\"}}",
                    ContentType.Application.Json
                )
            }
        }
    }

    fun start() = server.start(wait = false)
    fun stop() = server.stop(500, 1_000)
}
