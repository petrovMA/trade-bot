package io.bybit.api.websocket.messages.response.order_book

data class Order(
    val id: Int,
    val price: String,
    val side: String,
    val size: Int? = null,
    val symbol: String
)