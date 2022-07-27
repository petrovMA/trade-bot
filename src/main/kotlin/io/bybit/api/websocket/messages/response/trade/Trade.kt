package io.bybit.api.websocket.messages.response.trade

data class Trade(
    val `data`: List<Data>,
    val topic: String
)