package io.bybit.api.websocket.messages.response.trade

data class Data(
    val cross_seq: Long,
    val price: Double,
    val side: String,
    val size: Long,
    val symbol: String,
    val tick_direction: String,
    val timestamp: String,
    val trade_id: String,
    val trade_time_ms: Long
)