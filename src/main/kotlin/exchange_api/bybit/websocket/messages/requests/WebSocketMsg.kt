package exchange_api.bybit.websocket.messages.requests

data class WebSocketMsg(val op: String, val args: List<String> = emptyList())