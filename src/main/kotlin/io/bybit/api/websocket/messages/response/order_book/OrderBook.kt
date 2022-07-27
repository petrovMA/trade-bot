package io.bybit.api.websocket.messages.response.order_book

data class OrderBook(
    val cross_seq: Long,
    val timestamp_e6: Long,
    val topic: String,
    val type: String,
    val `data`: Data
)