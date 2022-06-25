package io.bybit.api.rest.messages.order_list

data class Result(
    val cursor: String,
    val `data`: List<Data>
)