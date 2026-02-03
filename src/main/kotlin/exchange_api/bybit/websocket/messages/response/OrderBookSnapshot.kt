package exchange_api.bybit.websocket.messages.response


data class OrderBookSnapshot(
    val cross_seq: Long,
    val `data`: List<Order>,
    val timestamp_e6: Long,
    val topic: String,
    val type: String
)