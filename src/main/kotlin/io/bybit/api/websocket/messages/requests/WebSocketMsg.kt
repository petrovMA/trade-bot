package io.bybit.api.websocket.messages.requests

data class WebSocketMsg(val op: String, val args: List<String> = emptyList())