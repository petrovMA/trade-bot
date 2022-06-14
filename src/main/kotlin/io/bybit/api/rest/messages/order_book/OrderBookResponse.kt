package io.bybit.api.rest.messages.order_book

data class OrderBookResponse(
    val ext_code: String,
    val ext_info: String,
    val result: List<Result>,
    val ret_code: Int,
    val ret_msg: String,
    val time_now: String
)