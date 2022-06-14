package io.bybit.api.rest.messages.order_book

data class Result(
    val price: String,
    val side: String,
    val size: Int,
    val symbol: String
)