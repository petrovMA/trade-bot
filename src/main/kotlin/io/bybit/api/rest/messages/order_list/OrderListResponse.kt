package io.bybit.api.rest.messages.order_list

data class OrderListResponse(
    val ext_code: String,
    val ext_info: String,
    val rate_limit: Int,
    val rate_limit_reset_ms: Long,
    val rate_limit_status: Int,
    val result: Result,
    val ret_code: Int,
    val ret_msg: String,
    val time_now: String
)