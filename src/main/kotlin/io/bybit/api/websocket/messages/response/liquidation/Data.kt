package io.bybit.api.websocket.messages.response.liquidation

data class Data(
    val price: String,
    val qty: String,
    val side: String,
    val symbol: String,
    val time: Long
)