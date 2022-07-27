package io.bybit.api.websocket.messages.response.kline

data class Kline(
    val `data`: List<Data>,
    val timestamp_e6: Long,
    val topic: String
)