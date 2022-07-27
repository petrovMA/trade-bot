package io.bybit.api.websocket.messages.response.order_book


data class OrderBookSnapshot(
    val cross_seq: Long,
    val `data`: DataSnapshot,
    val timestamp_e6: Long,
    val topic: String,
    val type: String
)

data class DataSnapshot(
    val id: Int,
    val price: String,
    val side: String,
    val size: Int,
    val symbol: String
)