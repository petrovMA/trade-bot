package exchange_api.bitmax.websocket.messages.requests

data class WebSocketMsg(val op: String, val id: String? = null, val ch: String)