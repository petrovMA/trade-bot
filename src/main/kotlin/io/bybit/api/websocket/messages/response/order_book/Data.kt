package io.bybit.api.websocket.messages.response.order_book

data class Data(
    val delete: List<Order>,
    val insert: List<Order>,
    val transactTimeE6: Long,
    val update: List<Order>
)
