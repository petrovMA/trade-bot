package io.bybit.api.websocket.messages.response.order_book


data class OrderBookSnapshot(
    val cross_seq: Long,
    val `data`: List<Order>,
    val timestamp_e6: Long,
    val topic: String,
    val type: String
)