package io.bybit.api.rest.response.order_list

data class Result(
    val cursor: String,
    val `data`: List<Data>
)