package io.bybit.api.websocket.messages.response.kline

data class Data(
    val close: Double,
    val confirm: Boolean,
    val cross_seq: Long,
    val end: Long,
    val high: Double,
    val low: Double,
    val `open`: Double,
    val start: Long,
    val timestamp: Long,
    val turnover: Double,
    val volume: Long
)