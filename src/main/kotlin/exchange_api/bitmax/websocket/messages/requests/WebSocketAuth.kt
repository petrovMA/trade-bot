package exchange_api.bitmax.websocket.messages.requests

data class WebSocketAuth(
        val op: String,
        val t: Long,
        val key: String,
        val sig: String
)