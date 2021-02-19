package io.bitmax.api.websocket.messages.requests

data class WebSocketAuth(
        val op: String,
        val t: Long,
        val key: String,
        val sig: String
)