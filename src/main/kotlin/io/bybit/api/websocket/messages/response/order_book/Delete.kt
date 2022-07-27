package io.bybit.api.websocket.messages.response.order_book

data class Delete(
    val id: Int,
    val price: String,
    val side: String,
    val symbol: String
)