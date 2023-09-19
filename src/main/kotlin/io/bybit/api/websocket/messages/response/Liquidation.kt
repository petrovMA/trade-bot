package io.bybit.api.websocket.messages.response

data class Liquidation(
    val `data`: Data,
    val topic: String
) {
    data class Data(
        val price: String,
        val qty: String,
        val side: String,
        val symbol: String,
        val time: Long
    )
}