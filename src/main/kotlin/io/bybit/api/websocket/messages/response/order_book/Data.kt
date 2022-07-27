package io.bybit.api.websocket.messages.response.order_book

data class Data(
    val delete: List<Delete>,
    val insert: List<Insert>,
    val transactTimeE6: Int,
    val update: List<Update>
)
