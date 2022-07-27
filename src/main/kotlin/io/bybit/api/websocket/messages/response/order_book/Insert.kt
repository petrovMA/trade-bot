package io.bybit.api.websocket.messages.response.order_book

data class Insert(
    val id: Int,
    val price: String,
    val side: String,
    val size: Int,
    val symbol: String
)