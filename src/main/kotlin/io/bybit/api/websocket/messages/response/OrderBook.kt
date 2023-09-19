package io.bybit.api.websocket.messages.response

data class OrderBook(
    val cross_seq: Long,
    val timestamp_e6: Long,
    val topic: String,
    val type: String,
    val `data`: Data
) {
    data class Data(
        val delete: List<Order>,
        val insert: List<Order>,
        val transactTimeE6: Long,
        val update: List<Order>
    )
}