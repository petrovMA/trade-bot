package io.bybit.api.websocket.messages.response

import java.math.BigDecimal

data class OrderBook(
    val cts: Long,
    val ts: Long,
    val topic: String,
    val type: String,
    val `data`: Data
) {
    data class Data(
        val s: String,
        val seq: Long,
        val u: Long,
        val b: List<List<BigDecimal>>,
        val a: List<List<BigDecimal>>
    )
}